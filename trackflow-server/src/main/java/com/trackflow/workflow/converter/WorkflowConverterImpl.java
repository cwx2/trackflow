package com.trackflow.workflow.converter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.converter.BaseConverter;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.entity.WorkflowActivity;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.vo.WorkflowActivityVO;
import com.trackflow.workflow.vo.WorkflowTransitionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作流模块转换器
 * 由于 Activity VO 转换需要查询关联数据（用户名、项目名、角色名），
 * 使用手动实现而非 MapStruct。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowConverterImpl implements WorkflowConverter {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final ProjectMapper projectMapper;
    private final IssueStatusMapper statusMapper;
    private final ObjectMapper objectMapper;

    @Override
    public WorkflowTransitionVO toVO(WorkflowTransition entity) {
        if (entity == null) return null;
        WorkflowTransitionVO vo = new WorkflowTransitionVO();
        vo.setId(longToString(entity.getId()));
        vo.setProjectId(longToString(entity.getProjectId()));
        vo.setRoleId(longToString(entity.getRoleId()));
        vo.setOldStatusId(longToString(entity.getOldStatusId()));
        vo.setNewStatusId(longToString(entity.getNewStatusId()));
        vo.setIssueType(entity.getIssueType());
        vo.setAuthor(entity.getAuthor());
        vo.setAssignee(entity.getAssignee());
        return vo;
    }

    @Override
    public List<WorkflowTransitionVO> toVOList(List<WorkflowTransition> entities) {
        if (entities == null) return List.of();
        return entities.stream().map(this::toVO).toList();
    }

    @Override
    public List<WorkflowActivityVO> toActivityVOList(List<WorkflowActivity> activities) {
        if (activities == null || activities.isEmpty()) {
            return List.of();
        }

        // 批量获取关联数据（避免 N+1）
        Set<Long> userIds = activities.stream()
                .map(WorkflowActivity::getUserId)
                .collect(Collectors.toSet());
        Set<Long> roleIds = activities.stream()
                .map(WorkflowActivity::getRoleId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Set<Long> projectIds = activities.stream()
                .map(WorkflowActivity::getProjectId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getId, userIds))
                        .stream().collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName));

        Map<Long, String> roleNameMap = roleIds.isEmpty() ? Map.of() :
                roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds))
                        .stream().collect(Collectors.toMap(SysRole::getId, SysRole::getName));

        Map<Long, String> projectNameMap = projectIds.isEmpty() ? Map.of() :
                projectMapper.selectList(new LambdaQueryWrapper<Project>().in(Project::getId, projectIds))
                        .stream().collect(Collectors.toMap(Project::getId, Project::getName));

        // 获取所有状态的 ID→显示名称映射，用于动态解析历史记录中的状态名
        Map<Long, String> statusNameMap = getStatusNameMap();

        return activities.stream()
                .map(a -> toActivityVO(a, userNameMap, roleNameMap, projectNameMap, statusNameMap))
                .toList();
    }

    /**
     * 获取所有状态的 ID→本地化显示名称映射。
     * 优先使用 displayName（中文），fallback 到 name（英文）。
     */
    private Map<Long, String> getStatusNameMap() {
        List<IssueStatus> allStatuses = statusMapper.selectList(new LambdaQueryWrapper<>());
        return allStatuses.stream()
                .collect(Collectors.toMap(IssueStatus::getId, IssueStatus::getLocalizedName));
    }

    private WorkflowActivityVO toActivityVO(WorkflowActivity activity,
                                            Map<Long, String> userNameMap,
                                            Map<Long, String> roleNameMap,
                                            Map<Long, String> projectNameMap,
                                            Map<Long, String> statusNameMap) {
        WorkflowActivityVO vo = new WorkflowActivityVO();
        vo.setId(longToString(activity.getId()));
        vo.setProjectId(longToString(activity.getProjectId()));
        vo.setProjectName(activity.getProjectId() != null
                ? projectNameMap.getOrDefault(activity.getProjectId(), "未知项目")
                : "全局");
        vo.setIssueType(activity.getIssueType());
        vo.setRoleId(longToString(activity.getRoleId()));
        vo.setRoleName(activity.getRoleId() != null
                ? roleNameMap.getOrDefault(activity.getRoleId(), "未知角色")
                : null);
        vo.setUserId(longToString(activity.getUserId()));
        vo.setUserDisplayName(userNameMap.getOrDefault(activity.getUserId(), "未知用户"));
        vo.setAction(activity.getAction());
        vo.setCreatedAt(activity.getCreatedAt());

        // 解析 details JSON，使用 statusId 动态解析当前显示名称
        parseDetails(activity.getDetails(), vo, statusNameMap);

        // 动态重建 summary，确保使用当前状态的本地化名称
        vo.setSummary(buildDynamicSummary(vo));

        return vo;
    }

    private void parseDetails(String detailsJson, WorkflowActivityVO vo, Map<Long, String> statusNameMap) {
        if (detailsJson == null || detailsJson.isBlank()) {
            vo.setAdded(Collections.emptyList());
            vo.setRemoved(Collections.emptyList());
            return;
        }
        try {
            Map<String, Object> details = objectMapper.readValue(detailsJson, new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, String>> addedRaw = (List<Map<String, String>>) details.getOrDefault("added", Collections.emptyList());
            @SuppressWarnings("unchecked")
            List<Map<String, String>> removedRaw = (List<Map<String, String>>) details.getOrDefault("removed", Collections.emptyList());

            vo.setAdded(addedRaw.stream().map(raw -> toChangeItem(raw, statusNameMap)).toList());
            vo.setRemoved(removedRaw.stream().map(raw -> toChangeItem(raw, statusNameMap)).toList());
        } catch (Exception e) {
            log.warn("Failed to parse workflow activity details: {}", e.getMessage());
            vo.setAdded(Collections.emptyList());
            vo.setRemoved(Collections.emptyList());
        }
    }

    /**
     * 将 JSON 中的转换项解析为 VO，优先使用 statusId 动态解析当前显示名称。
     * 如果 statusId 不存在（极旧记录）或状态已被删除，降级使用存储的文本。
     */
    private WorkflowActivityVO.TransitionChangeItem toChangeItem(Map<String, String> raw, Map<Long, String> statusNameMap) {
        WorkflowActivityVO.TransitionChangeItem item = new WorkflowActivityVO.TransitionChangeItem();

        String fromStatusId = raw.get("fromStatusId");
        String toStatusId = raw.get("toStatusId");

        // 优先使用 statusId 动态解析当前本地化名称
        if (fromStatusId != null) {
            try {
                Long id = Long.parseLong(fromStatusId);
                item.setFromStatus(statusNameMap.getOrDefault(id, raw.getOrDefault("fromStatus", "未知状态")));
            } catch (NumberFormatException e) {
                item.setFromStatus(raw.getOrDefault("fromStatus", "未知状态"));
            }
        } else {
            item.setFromStatus(raw.getOrDefault("fromStatus", "未知状态"));
        }

        if (toStatusId != null) {
            try {
                Long id = Long.parseLong(toStatusId);
                item.setToStatus(statusNameMap.getOrDefault(id, raw.getOrDefault("toStatus", "未知状态")));
            } catch (NumberFormatException e) {
                item.setToStatus(raw.getOrDefault("toStatus", "未知状态"));
            }
        } else {
            item.setToStatus(raw.getOrDefault("toStatus", "未知状态"));
        }

        return item;
    }

    /**
     * 根据已解析的 added/removed 列表动态重建摘要，确保状态名称统一使用本地化名。
     */
    private String buildDynamicSummary(WorkflowActivityVO vo) {
        List<WorkflowActivityVO.TransitionChangeItem> added = vo.getAdded();
        List<WorkflowActivityVO.TransitionChangeItem> removed = vo.getRemoved();

        if ((added == null || added.isEmpty()) && (removed == null || removed.isEmpty())) {
            return "工作流已更新";
        }

        StringBuilder sb = new StringBuilder();
        if (added != null && !added.isEmpty()) {
            sb.append("新增 ").append(added.size()).append(" 条转换");
            if (added.size() <= 3) {
                sb.append("（");
                sb.append(added.stream()
                        .map(item -> item.getFromStatus() + "→" + item.getToStatus())
                        .collect(Collectors.joining("，")));
                sb.append("）");
            }
        }
        if (removed != null && !removed.isEmpty()) {
            if (added != null && !added.isEmpty()) {
                sb.append("；");
            }
            sb.append("删除 ").append(removed.size()).append(" 条转换");
            if (removed.size() <= 3) {
                sb.append("（");
                sb.append(removed.stream()
                        .map(item -> item.getFromStatus() + "→" + item.getToStatus())
                        .collect(Collectors.joining("，")));
                sb.append("）");
            }
        }
        return sb.toString();
    }
}
