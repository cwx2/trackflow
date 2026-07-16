package com.trackflow.workflow.converter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.converter.BaseConverter;
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

        return activities.stream()
                .map(a -> toActivityVO(a, userNameMap, roleNameMap, projectNameMap))
                .toList();
    }

    private WorkflowActivityVO toActivityVO(WorkflowActivity activity,
                                            Map<Long, String> userNameMap,
                                            Map<Long, String> roleNameMap,
                                            Map<Long, String> projectNameMap) {
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
        vo.setSummary(activity.getSummary());
        vo.setCreatedAt(activity.getCreatedAt());

        // 解析 details JSON
        parseDetails(activity.getDetails(), vo);

        return vo;
    }

    private void parseDetails(String detailsJson, WorkflowActivityVO vo) {
        if (detailsJson == null || detailsJson.isBlank()) {
            vo.setAdded(Collections.emptyList());
            vo.setRemoved(Collections.emptyList());
            return;
        }
        try {
            Map<String, List<Map<String, String>>> details = objectMapper.readValue(
                    detailsJson, new TypeReference<>() {});

            List<Map<String, String>> addedRaw = details.getOrDefault("added", Collections.emptyList());
            List<Map<String, String>> removedRaw = details.getOrDefault("removed", Collections.emptyList());

            vo.setAdded(addedRaw.stream().map(this::toChangeItem).toList());
            vo.setRemoved(removedRaw.stream().map(this::toChangeItem).toList());
        } catch (Exception e) {
            log.warn("Failed to parse workflow activity details: {}", e.getMessage());
            vo.setAdded(Collections.emptyList());
            vo.setRemoved(Collections.emptyList());
        }
    }

    private WorkflowActivityVO.TransitionChangeItem toChangeItem(Map<String, String> raw) {
        WorkflowActivityVO.TransitionChangeItem item = new WorkflowActivityVO.TransitionChangeItem();
        item.setFromStatus(raw.getOrDefault("fromStatus", "Unknown"));
        item.setToStatus(raw.getOrDefault("toStatus", "Unknown"));
        return item;
    }
}
