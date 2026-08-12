package com.trackflow.issue.service;

import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.customfield.mapper.CustomFieldOptionMapper;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.customfield.vo.CustomFieldValueVO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueSprint;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.entity.IssueTag;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueSprintMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.vo.BatchAvailableStatusVO;
import com.trackflow.issue.vo.IssueTagVO;
import com.trackflow.issue.vo.IssueVO;
import com.trackflow.issue.vo.SimilarIssueVO;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.service.WorkflowService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Issue VO 组装器 — 负责批量填充 IssueVO 的关联展示数据。
 * <p>
 * 将原 IssueService 中的 fill* 方法抽取至此，遵循单一职责原则：
 * - IssueService 负责业务逻辑（CRUD、状态流转、权限校验）
 * - IssueVOAssembler 负责 VO 展示数据的批量组装（查关联表填充）
 * <p>
 * 可被 IssueService、IssueSearchController、ReportService 等复用。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueVOAssembler {

    private final SysUserMapper sysUserMapper;
    private final IssueStatusMapper statusMapper;
    private final SprintMapper sprintMapper;
    private final IssueSprintMapper issueSprintMapper;
    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final CustomFieldService customFieldService;
    private final CustomFieldOptionMapper customFieldOptionMapper;
    private final IssueTagService tagService;
    private final WorkflowService workflowService;
    private final com.trackflow.board.mapper.BoardGeneralConfigMapper boardGeneralConfigMapper;

    /**
     * 批量组装 IssueVO 列表的全部关联数据（用于列表页）
     *
     * @param issues  原始 Issue 实体列表
     * @param voList  已通过 Converter 转换的 VO 列表（与 issues 一一对应）
     */
    public void assemble(List<Issue> issues, List<IssueVO> voList) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        fillProjectInfo(issues, voList);
        fillUserInfo(issues, voList);
        fillChildProgress(issues, voList);
        fillStatusInfo(issues, voList);
        fillSprintInfo(issues, voList);
        fillMultiSprintInfo(issues, voList);
        fillPriorityAndTypeInfo(issues, voList);
        fillCustomFieldValues(issues, voList);
        fillTagInfo(issues, voList);
    }

    /**
     * 批量填充 projectKey/projectName（用于跨项目视图中展示项目列）
     */
    private void fillProjectInfo(List<Issue> issues, List<IssueVO> voList) {
        Set<Long> projectIds = new HashSet<>();
        for (Issue issue : issues) {
            if (issue.getProjectId() != null) projectIds.add(issue.getProjectId());
        }
        if (projectIds.isEmpty()) return;

        Map<Long, Project> projectMap = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p, (a, b) -> a));

        for (int i = 0; i < issues.size(); i++) {
            Issue issue = issues.get(i);
            if (issue.getProjectId() != null) {
                Project project = projectMap.get(issue.getProjectId());
                if (project != null) {
                    voList.get(i).setProjectKey(project.getKey());
                    voList.get(i).setProjectName(project.getName());
                }
            }
        }
    }

    /**
     * 批量填充 assigneeName/assigneeAvatarUrl/reporterName
     */
    private void fillUserInfo(List<Issue> issues, List<IssueVO> voList) {
        Set<Long> userIds = new HashSet<>();
        for (Issue issue : issues) {
            if (issue.getAssigneeId() != null) userIds.add(issue.getAssigneeId());
            if (issue.getReporterId() != null) userIds.add(issue.getReporterId());
        }
        if (userIds.isEmpty()) return;

        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        for (int i = 0; i < issues.size(); i++) {
            Issue issue = issues.get(i);
            if (issue.getAssigneeId() != null) {
                SysUser user = userMap.get(issue.getAssigneeId());
                if (user != null) {
                    voList.get(i).setAssigneeName(user.getDisplayName());
                    voList.get(i).setAssigneeAvatarUrl(user.getAvatarUrl());
                }
            }
            if (issue.getReporterId() != null) {
                SysUser user = userMap.get(issue.getReporterId());
                if (user != null) {
                    voList.get(i).setReporterName(user.getDisplayName());
                }
            }
        }
    }

    /**
     * 填充子任务进度字段（childCount / childClosedCount）
     */
    private void fillChildProgress(List<Issue> issues, List<IssueVO> voList) {
        for (int i = 0; i < issues.size(); i++) {
            voList.get(i).setChildCount(issues.get(i).getChildCount());
            voList.get(i).setChildClosedCount(issues.get(i).getChildClosedCount());
        }
    }

    /**
     * 批量填充 statusName/statusColor（status 表数据极少，全量查出缓存）
     */
    private void fillStatusInfo(List<Issue> issues, List<IssueVO> voList) {
        Map<Long, IssueStatus> statusMap = statusMapper.selectList(null).stream()
                .collect(Collectors.toMap(IssueStatus::getId, s -> s, (a, b) -> a));

        for (int i = 0; i < issues.size(); i++) {
            Issue issue = issues.get(i);
            if (issue.getStatusId() != null) {
                IssueStatus status = statusMap.get(issue.getStatusId());
                if (status != null) {
                    voList.get(i).setStatusName(status.getLocalizedName());
                    voList.get(i).setStatusColor(status.getColor());
                }
            }
        }
    }

    /**
     * 批量填充 sprintName 和 sprintStatus（仅查询用到的 Sprint）
     */
    private void fillSprintInfo(List<Issue> issues, List<IssueVO> voList) {
        Set<Long> sprintIds = new HashSet<>();
        for (Issue issue : issues) {
            if (issue.getSprintId() != null) sprintIds.add(issue.getSprintId());
        }
        if (sprintIds.isEmpty()) return;

        Map<Long, Sprint> sprintMap = sprintMapper.selectBatchIds(sprintIds).stream()
                .collect(Collectors.toMap(Sprint::getId, s -> s, (a, b) -> a));

        for (int i = 0; i < issues.size(); i++) {
            Issue issue = issues.get(i);
            if (issue.getSprintId() != null) {
                Sprint sprint = sprintMap.get(issue.getSprintId());
                if (sprint != null) {
                    voList.get(i).setSprintName(sprint.getName());
                    if (sprint.getStatus() != null) {
                        voList.get(i).setSprintStatus(sprint.getStatus().getValue());
                    }
                }
            }
        }
    }

    /**
     * 批量填充多 Sprint 信息（issue_sprint 关联表）
     */
    private void fillMultiSprintInfo(List<Issue> issues, List<IssueVO> voList) {
        List<Long> issueIds = issues.stream().map(Issue::getId).toList();
        if (issueIds.isEmpty()) return;

        List<IssueSprint> allRelations = issueSprintMapper.selectByIssueIds(issueIds);
        if (allRelations.isEmpty()) return;

        // 按 issueId 分组
        Map<Long, List<Long>> issueSprintMap = new HashMap<>();
        Set<Long> allSprintIds = new HashSet<>();
        for (IssueSprint rel : allRelations) {
            issueSprintMap.computeIfAbsent(rel.getIssueId(), k -> new ArrayList<>()).add(rel.getSprintId());
            allSprintIds.add(rel.getSprintId());
        }

        // 批量查询 Sprint 名称
        Map<Long, String> sprintNameMap = Collections.emptyMap();
        if (!allSprintIds.isEmpty()) {
            sprintNameMap = sprintMapper.selectBatchIds(allSprintIds).stream()
                    .collect(Collectors.toMap(Sprint::getId, Sprint::getName, (a, b) -> a));
        }

        // 仅填充有多个 Sprint 关联的工单
        for (int i = 0; i < issues.size(); i++) {
            Long issueId = issues.get(i).getId();
            List<Long> relSprintIds = issueSprintMap.get(issueId);
            if (relSprintIds != null && relSprintIds.size() > 1) {
                List<String> ids = new ArrayList<>();
                List<String> names = new ArrayList<>();
                for (Long sid : relSprintIds) {
                    ids.add(String.valueOf(sid));
                    names.add(sprintNameMap.getOrDefault(sid, ""));
                }
                voList.get(i).setSprintIds(ids);
                voList.get(i).setSprintNames(names);
            }
        }
    }

    /**
     * 批量填充 priority/issueType 的颜色字段（从 custom_field_option 关联查询）
     */
    private void fillPriorityAndTypeInfo(List<Issue> issues, List<IssueVO> voList) {
        Set<Long> optionIds = new HashSet<>();
        for (Issue issue : issues) {
            if (issue.getPriorityOptionId() != null) optionIds.add(issue.getPriorityOptionId());
            if (issue.getIssueTypeOptionId() != null) optionIds.add(issue.getIssueTypeOptionId());
        }
        if (optionIds.isEmpty()) return;

        Map<Long, CustomFieldOption> optionMap = customFieldOptionMapper.selectBatchIds(optionIds)
                .stream().collect(Collectors.toMap(CustomFieldOption::getId, o -> o, (a, b) -> a));

        for (int i = 0; i < issues.size(); i++) {
            Issue issue = issues.get(i);
            IssueVO vo = voList.get(i);

            if (issue.getPriorityOptionId() != null) {
                CustomFieldOption opt = optionMap.get(issue.getPriorityOptionId());
                if (opt != null) {
                    vo.setPriority(opt.getValue());
                    vo.setPriorityColor(opt.getColor());
                }
            }
            if (issue.getIssueTypeOptionId() != null) {
                CustomFieldOption opt = optionMap.get(issue.getIssueTypeOptionId());
                if (opt != null) {
                    vo.setIssueType(opt.getValue());
                    vo.setIssueTypeColor(opt.getColor());
                }
            }
        }
    }

    /**
     * 批量填充自定义字段展示值和颜色
     */
    private void fillCustomFieldValues(List<Issue> issues, List<IssueVO> voList) {
        List<Long> issueIds = issues.stream().map(Issue::getId).toList();
        if (issueIds.isEmpty()) return;

        // 使用结构化详情填充（支持多值字段独立渲染）
        Map<Long, List<CustomFieldValueVO>> cfDetailsMap =
                customFieldService.getBatchCustomFieldDetails(issueIds);

        for (int i = 0; i < issues.size(); i++) {
            Long issueId = issues.get(i).getId();
            List<CustomFieldValueVO> details = cfDetailsMap.get(issueId);
            if (details != null && !details.isEmpty()) {
                voList.get(i).setCustomFieldDetails(details);
            }
        }
    }

    /**
     * 批量填充工单标签信息（list 查询使用）
     */
    private void fillTagInfo(List<Issue> issues, List<IssueVO> voList) {
        List<Long> issueIds = issues.stream().map(Issue::getId).toList();
        if (issueIds.isEmpty()) return;

        Map<Long, List<IssueTag>> tagMap = tagService.batchListIssueTags(issueIds);
        for (int i = 0; i < issues.size(); i++) {
            Long issueId = issues.get(i).getId();
            List<IssueTag> tags = tagMap.get(issueId);
            if (tags != null && !tags.isEmpty()) {
                List<IssueTagVO> tagVOs = tags.stream().map(tag -> {
                    IssueTagVO vo = new IssueTagVO();
                    vo.setId(String.valueOf(tag.getId()));
                    vo.setName(tag.getName());
                    vo.setColor(tag.getColor());
                    return vo;
                }).toList();
                voList.get(i).setTags(tagVOs);
            }
        }
    }

    // ========== 以下方法从 IssueService 迁移，属于 VO 组装逻辑 ==========

    /**
     * 将 Issue 实体列表转为 SimilarIssueVO 列表（包含状态名和负责人名）
     */
    public List<SimilarIssueVO> buildSimilarIssueVOs(List<Issue> issues) {
        if (issues.isEmpty()) return List.of();

        Map<Long, IssueStatus> statusMap = statusMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(IssueStatus::getId, s -> s, (a, b) -> a));

        Set<Long> userIds = issues.stream()
                .map(Issue::getAssigneeId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        return issues.stream().map(issue -> {
            SimilarIssueVO vo = new SimilarIssueVO();
            vo.setId(String.valueOf(issue.getId()));
            vo.setIssueKey(issue.getIssueKey());
            vo.setTitle(issue.getTitle());
            if (issue.getStatusId() != null) {
                IssueStatus status = statusMap.get(issue.getStatusId());
                if (status != null) {
                    vo.setStatusName(status.getLocalizedName());
                    vo.setStatusColor(status.getColor());
                }
            }
            if (issue.getAssigneeId() != null) {
                SysUser user = userMap.get(issue.getAssigneeId());
                if (user != null) {
                    vo.setAssigneeName(user.getDisplayName());
                }
            }
            return vo;
        }).toList();
    }

    /**
     * 获取批量操作中每个状态的可达性信息。
     * 对选中的所有工单，统计每个状态可被多少个工单转换到。
     * <p>
     * 性能优化：按 (projectId, issueType, statusId, isAuthor, isAssignee) 分组，
     * 相同组合的工单共享同一工作流转换结果，将 O(N) 次 DB 调用降至 O(G) 次（G=分组数，通常 2-5）。
     */
    public List<BatchAvailableStatusVO> getBatchAvailableTransitions(List<Long> issueIds) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. 批量获取所有工单（1 次 DB 查询）
        List<Issue> issues = issueMapper.selectBatchIds(issueIds);
        if (issues.isEmpty()) {
            return List.of();
        }

        // 2. 获取所有状态（1 次 DB 查询）
        List<IssueStatus> allStatuses = statusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>().orderByAsc(IssueStatus::getSortOrder));

        // 3. 按 (projectId, issueType, statusId, isAuthor, isAssignee) 分组
        Map<String, List<Issue>> groupedIssues = issues.stream()
                .collect(java.util.stream.Collectors.groupingBy(issue -> buildTransitionGroupKey(issue, currentUserId)));

        // 4. 每组只查一次可用转换（通常 2-5 组）
        Map<Long, Integer> reachabilityMap = new HashMap<>();

        for (Map.Entry<String, List<Issue>> entry : groupedIssues.entrySet()) {
            Issue representative = entry.getValue().get(0);
            int groupSize = entry.getValue().size();

            try {
                List<IssueStatus> available = workflowService.getAvailableTransitions(representative, currentUserId);
                for (IssueStatus status : available) {
                    reachabilityMap.merge(status.getId(), groupSize, Integer::sum);
                }
            } catch (Exception e) {
                log.warn("获取工单可用转换失败 groupKey={}, representativeId={}",
                        entry.getKey(), representative.getId(), e);
            }
        }

        // 5. 构建结果
        int totalCount = issues.size();
        List<BatchAvailableStatusVO> result = new ArrayList<>();
        for (IssueStatus status : allStatuses) {
            int reachable = reachabilityMap.getOrDefault(status.getId(), 0);
            if (reachable == 0) {
                continue;
            }
            BatchAvailableStatusVO vo = new BatchAvailableStatusVO();
            vo.setId(String.valueOf(status.getId()));
            vo.setName(status.getName());
            vo.setColor(status.getColor());
            vo.setCategory(status.getCategory());
            vo.setIsClosed(status.getIsClosed());
            vo.setSortOrder(status.getSortOrder());
            vo.setReachableCount(reachable);
            vo.setTotalCount(totalCount);
            result.add(vo);
        }

        return result;
    }

    private String buildTransitionGroupKey(Issue issue, Long currentUserId) {
        boolean isAuthor = currentUserId.equals(issue.getCreatedBy());
        boolean isAssignee = currentUserId.equals(issue.getAssigneeId());
        return issue.getProjectId() + ":" + issue.getIssueType() + ":"
                + issue.getStatusId() + ":" + isAuthor + ":" + isAssignee;
    }
}
