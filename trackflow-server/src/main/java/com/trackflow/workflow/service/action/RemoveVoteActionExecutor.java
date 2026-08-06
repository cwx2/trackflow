package com.trackflow.workflow.service.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueVote;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueVoteMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 移除投票动作执行器 — 以规则创建者身份取消工单投票（幂等）。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class RemoveVoteActionExecutor extends WorkflowActionSupport {

    private final IssueVoteMapper voteMapper;

    public RemoveVoteActionExecutor(IssueActivityMapper activityMapper,
                                    SysUserMapper sysUserMapper,
                                    NotificationMapper notificationMapper,
                                    ProjectService projectService,
                                    IssueVoteMapper voteMapper) {
        super(activityMapper, sysUserMapper, notificationMapper, projectService);
        this.voteMapper = voteMapper;
    }

    @Override
    public String actionType() {
        return "remove_vote";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        Long userId = rule.getCreatedBy();
        if (!voteMapper.isVoted(issue.getId(), userId)) {
            log.debug("[RuleEngine] remove_vote: user {} has no vote on issue {}, skipping",
                    userId, issue.getIssueKey());
            return ActionResult.NONE;
        }

        voteMapper.delete(new LambdaQueryWrapper<IssueVote>()
                .eq(IssueVote::getIssueId, issue.getId())
                .eq(IssueVote::getUserId, userId));
        voteMapper.refreshVoteCount(issue.getId());

        logActivity(issue.getId(), rule, "unvoted", null, null, null);
        log.info("[RuleEngine] remove_vote: user {} unvoted on issue {} by rule '{}' (id={})",
                userId, issue.getIssueKey(), rule.getName(), rule.getId());
        return ActionResult.NONE;
    }
}
