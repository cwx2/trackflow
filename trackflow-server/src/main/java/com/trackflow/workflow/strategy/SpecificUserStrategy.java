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
 * 指定用户策略 —— 直接返回配置中的用户 ID。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpecificUserStrategy implements AssignmentStrategy {

    private final ProjectMemberMapper projectMemberMapper;

    @Override
    public String getKey() {
        return "specific_user";
    }

    @Override
    public Long resolve(Issue issue, ActionConfig config, Long projectId) {
        Long userId = config.getUserId();
        if (userId == null) {
            log.warn("[SpecificUserStrategy] config.userId is null, issue={}", issue.getId());
            return null;
        }

        // 验证目标用户是否为活跃项目成员
        boolean isMember = projectMemberMapper.exists(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );

        if (!isMember) {
            log.warn("[SpecificUserStrategy] 用户 {} 不是项目 {} 的活跃成员，跳过分配",
                    userId, projectId);
            return null;
        }

        return userId;
    }
}
