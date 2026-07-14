package com.trackflow.workflow.strategy;

import com.trackflow.issue.entity.Issue;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.workflow.dto.ActionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 项目负责人策略 —— 将 Issue 分配给项目的 lead。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectLeadStrategy implements AssignmentStrategy {

    private final ProjectMapper projectMapper;

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

        return leadId;
    }
}
