package com.trackflow.issue.service;

import com.trackflow.common.constant.IssueStatusCategory;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.entity.IssueTag;
import com.trackflow.issue.mapper.IssueSprintMapper;
import com.trackflow.issue.mapper.IssueVisibilityUserMapper;
import com.trackflow.issue.mapper.result.ChildIssueRow;
import com.trackflow.issue.mapper.result.IssueDetailRow;
import com.trackflow.issue.vo.*;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Issue 详情 VO 组装器 — 负责将 IssueDetailRow（Mapper JOIN 查询结果）转换为 IssueDetailVO。
 * <p>
 * 从 IssueService.getDetail() 中抽取，遵循分层架构规范：
 * - Service 返回 DO/Row（查询结果）
 * - Controller 通过 Assembler/Converter 转换为 VO
 * <p>
 * 与 IssueVOAssembler（列表批量组装）互补，本类专注于单条详情的复杂组装。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueDetailVOAssembler {

    private final IssueConverter issueConverter;
    private final IssueSprintMapper issueSprintMapper;
    private final SprintMapper sprintMapper;
    private final IssueVisibilityUserMapper visibilityUserMapper;
    private final SysUserMapper sysUserMapper;
    private final CustomFieldService customFieldService;
    private final IssueTagService tagService;

    /**
     * 将 IssueDetailRow 转换为完整的 IssueDetailVO。
     * <p>
     * 包含：基础字段映射 + 状态对象 + 多Sprint关联 + 可见性信息 + 自定义字段 + 子任务 + 标签。
     *
     * @param row      Mapper JOIN 查询结果行
     * @param children 子任务列表（已从 Mapper 查询）
     * @return 完整的详情 VO
     */
    public IssueDetailVO assemble(IssueDetailRow row, List<ChildIssueRow> children) {
        IssueDetailVO vo = new IssueDetailVO();

        // ===== 基础字段映射 =====
        vo.setId(String.valueOf(row.getId()));
        vo.setProjectId(String.valueOf(row.getProjectId()));
        vo.setProjectName(row.getProjectName());
        vo.setProjectStatus(row.getProjectStatus());
        vo.setIssueKey(row.getIssueKey());
        vo.setTitle(row.getTitle());
        vo.setDescription(row.getDescription());
        vo.setIssueType(row.getIssueType());
        vo.setStatusId(String.valueOf(row.getStatusId()));
        vo.setPriority(row.getPriority());
        vo.setAssigneeId(row.getAssigneeId() != null ? String.valueOf(row.getAssigneeId()) : null);
        vo.setAssigneeName(row.getAssigneeName());
        vo.setAssigneeAvatarUrl(row.getAssigneeAvatarUrl());
        vo.setReporterId(String.valueOf(row.getReporterId()));
        vo.setReporterName(row.getReporterName());
        vo.setSprintId(row.getSprintId() != null ? String.valueOf(row.getSprintId()) : null);
        vo.setSprintName(row.getSprintName());
        vo.setSprintStatus(row.getSprintStatus());
        vo.setParentId(row.getParentId() != null ? String.valueOf(row.getParentId()) : null);
        vo.setParentKey(row.getParentKey());
        vo.setDueDate(row.getDueDate());
        vo.setEstimatedHours(row.getEstimatedHours());
        vo.setSpentHours(row.getSpentHours());
        vo.setDerivedEstimatedHours(row.getDerivedEstimatedHours());
        vo.setDerivedSpentHours(row.getDerivedSpentHours());
        vo.setResolvedAt(row.getResolvedAt());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt());

        // 创建者/更新者信息
        vo.setCreatedById(row.getCreatedById() != null ? String.valueOf(row.getCreatedById()) : null);
        vo.setCreatedByName(row.getCreatedByName());
        vo.setUpdatedById(row.getUpdatedById() != null ? String.valueOf(row.getUpdatedById()) : null);
        vo.setUpdatedByName(row.getUpdatedByName());

        // 乐观锁版本号
        vo.setVersion(row.getVersion());

        // ===== 状态对象 =====
        assembleStatus(vo, row);

        // ===== 多 Sprint 关联 =====
        assembleMultiSprint(vo, row.getId());

        // ===== 标签（多对多） =====
        List<IssueTag> tags = tagService.listIssueTags(row.getId());
        vo.setTags(issueConverter.toTagVOList(tags));

        // ===== 可见性 =====
        assembleVisibility(vo, row);

        // ===== 自定义字段 =====
        Long projectIdLong = row.getProjectId();
        vo.setCustomFieldDetails(customFieldService.getValuesForDisplay(row.getId(), projectIdLong, row.getIssueType()));

        // ===== 子任务 + 进度 =====
        if (children != null && !children.isEmpty()) {
            List<ChildIssueVO> childVOs = children.stream()
                    .map(this::mapChildRow)
                    .toList();
            vo.setChildren(childVOs);
            vo.setChildProgress(calculateChildProgress(childVOs));
        }

        return vo;
    }

    /**
     * 映射子任务行为 VO
     */
    public ChildIssueVO mapChildRow(ChildIssueRow row) {
        ChildIssueVO vo = new ChildIssueVO();
        vo.setId(String.valueOf(row.getId()));
        vo.setIssueKey(row.getIssueKey());
        vo.setTitle(row.getTitle());
        vo.setIssueType(row.getIssueType());
        vo.setPriority(row.getPriority());
        vo.setStatusName(row.getStatusName());
        vo.setStatusColor(row.getStatusColor());
        vo.setStatusCategory(row.getStatusCategory());
        vo.setAssigneeName(row.getAssigneeName());
        return vo;
    }

    /**
     * 计算子任务进度汇总
     */
    public ChildProgressVO calculateChildProgress(List<ChildIssueVO> children) {
        if (children == null || children.isEmpty()) return null;

        ChildProgressVO progress = new ChildProgressVO();
        progress.setTotal(children.size());

        int closed = 0;
        for (ChildIssueVO child : children) {
            String cat = child.getStatusCategory();
            if (IssueStatusCategory.isClosed(cat)) {
                closed++;
            }
        }
        progress.setClosed(closed);
        progress.setPercent(children.isEmpty() ? 0 : Math.round((float) closed * 100 / children.size()));

        return progress;
    }

    // ===== 内部组装方法 =====

    private void assembleStatus(IssueDetailVO vo, IssueDetailRow row) {
        if (row.getStatusName() != null) {
            IssueStatusVO statusVO = new IssueStatusVO();
            statusVO.setId(String.valueOf(row.getStatusId()));
            statusVO.setName(row.getStatusName());
            statusVO.setDisplayName(row.getStatusDisplayName());
            statusVO.setCode(row.getStatusCode());
            statusVO.setColor(row.getStatusColor());
            statusVO.setCategory(row.getStatusCategory());
            statusVO.setIsDefault(row.getStatusIsDefault());
            statusVO.setIsClosed(row.getStatusIsClosed());
            vo.setStatus(statusVO);
        }
    }

    private void assembleMultiSprint(IssueDetailVO vo, Long issueId) {
        List<Long> relatedSprintIds = issueSprintMapper.selectSprintIdsByIssueId(issueId);
        if (relatedSprintIds.size() > 1) {
            Map<Long, String> sprintNameMap = sprintMapper.selectBatchIds(relatedSprintIds).stream()
                    .collect(Collectors.toMap(
                            com.trackflow.sprint.entity.Sprint::getId,
                            com.trackflow.sprint.entity.Sprint::getName,
                            (a, b) -> a));
            List<String> sIds = new ArrayList<>();
            List<String> sNames = new ArrayList<>();
            for (Long sid : relatedSprintIds) {
                sIds.add(String.valueOf(sid));
                sNames.add(sprintNameMap.getOrDefault(sid, ""));
            }
            vo.setSprintIds(sIds);
            vo.setSprintNames(sNames);
        }
    }

    private void assembleVisibility(IssueDetailVO vo, IssueDetailRow row) {
        vo.setVisibility(row.getVisibility() != null ? row.getVisibility() : "public");
        if ("restricted".equals(vo.getVisibility())) {
            List<Long> visibleUserIds = visibilityUserMapper.selectUserIdsByIssueId(row.getId());
            if (!visibleUserIds.isEmpty()) {
                List<SysUser> visibleUsers = sysUserMapper.selectBatchIds(visibleUserIds);
                vo.setVisibilityUserIds(visibleUsers.stream().map(u -> String.valueOf(u.getId())).toList());
                vo.setVisibilityUserNames(visibleUsers.stream().map(SysUser::getDisplayName).toList());
            } else {
                vo.setVisibilityUserIds(List.of());
                vo.setVisibilityUserNames(List.of());
            }
        }
    }
}
