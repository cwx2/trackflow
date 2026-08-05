package com.trackflow.workflow.service.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueVote;
import com.trackflow.issue.mapper.IssueVoteMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 添加投票动作执行器 — 以规则创建者身份为工单投票（幂等）。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class AddVoteActionExecutor extends WorkflowActionSupport {

    @Autowired
    private IssueVoteMapper voteMapper;

    @Override
    public String actionType() {
        return "add_vote";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        Long userId = rule.getCreatedBy();
        if (voteMapper.isVoted(issue.getId(), userId)) {
            log.debug("[RuleEngine] add_vote: user {} already voted on issue {}, skipping",
                    userId, issue.getIssueKey());
            return ActionResult.NONE;
        }

        IssueVote vote = new IssueVote();
        vote.setIssueId(issue.getId());
        vote.setUserId(userId);
        vote.setCreatedAt(LocalDateTime.now());
        voteMapper.insert(vote);
        voteMapper.refreshVoteCount(issue.getId());

        logActivity(issue.getId(), rule, "voted", null, null, null);
        log.info("[RuleEngine] add_vote: user {} voted on issue {} by rule '{}' (id={})",
                userId, issue.getIssueKey(), rule.getName(), rule.getId());
        return ActionResult.NONE;
    }
}
