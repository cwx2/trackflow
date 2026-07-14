package com.trackflow.workflow.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.workflow.dto.ActionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 报告人策略 —— 将 Issue 分配给其报告人。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReporterStrategy implements AssignmentStrategy {

    private final ProjectMemberMapper projectMemberMapper;

    @Override
    public String getKey() {
        return "reporter";
    }

    @Override
    public Long resolve(Issue issue, ActionConfig config, Long projectId) {
        Long reporterId = issue.getReporterId();
        if (reporterId == null) {
            log.warn("[ReporterStrategy] Issue {} 无 reporterId", issue.getId());
            return null;
        }

        // 验证报告人是否仍为活跃项目成员
        boolean isMember = projectMemberMapper.exists(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, reporterId)
        );

        if (!isMember) {
            log.warn("[ReporterStrategy] 报告人 {} 不是项目 {} 的活跃成员，跳过分配",
                    reporterId, projectId);
            return null;
        }

        return reporterId;
    }
}
