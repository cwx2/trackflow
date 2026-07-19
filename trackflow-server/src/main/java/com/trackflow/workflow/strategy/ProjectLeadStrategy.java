package com.trackflow.workflow.strategy;

import com.trackflow.issue.entity.Issue;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.dto.ActionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 项目负责人策略 —— 将 Issue 分配给项目的 lead。
 * <p>
 * 如果 lead 用户已被禁用或不存在，返回 null 触发 fallback 策略。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectLeadStrategy implements AssignmentStrategy {

    private final ProjectMapper projectMapper;
    private final SysUserMapper userMapper;

    @Override
    public String getKey() {
        return "project_lead";
    }

    @Override
    public Long resolve(Issue issue, ActionConfig config, Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            log.warn("[ProjectLeadStrategy] 项目 {} 不存在", projectId);
            return null;
        }

        Long leadId = project.getLeadId();
        if (leadId == null) {
            log.warn("[ProjectLeadStrategy] 项目 {} 未配置 lead", projectId);
            return null;
        }

        // 校验 lead 用户是否仍为活跃状态
        SysUser leadUser = userMapper.selectById(leadId);
        if (leadUser == null || !"active".equals(leadUser.getStatus())) {
            log.warn("[ProjectLeadStrategy] 项目 {} 的 lead(userId={}) 状态非 active，跳过分配",
                    projectId, leadId);
            return null;
        }

        return leadId;
    }
}
