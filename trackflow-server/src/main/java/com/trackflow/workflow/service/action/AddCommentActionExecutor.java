package com.trackflow.workflow.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueCommentMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 添加评论动作执行器 — 自动为工单添加评论。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class AddCommentActionExecutor extends WorkflowActionSupport {

    private final IssueCommentMapper commentMapper;

    public AddCommentActionExecutor(IssueActivityMapper activityMapper,
                                    SysUserMapper sysUserMapper,
                                    NotificationMapper notificationMapper,
                                    ProjectService projectService,
                                    IssueCommentMapper commentMapper) {
        super(activityMapper, sysUserMapper, notificationMapper, projectService);
        this.commentMapper = commentMapper;
    }

    @Override
    public String actionType() {
        return "add_comment";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String content = textOf(actionConfig, "content");
        if (content == null || content.isBlank()) return ActionResult.NONE;
        content = interpolateVariables(content, issue, rule);
        IssueComment comment = new IssueComment();
        comment.setIssueId(issue.getId());
        comment.setUserId(rule.getCreatedBy());
        comment.setContent(content);
        comment.setSource("automation");
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        logActivity(issue.getId(), rule, "commented", null, null, null);
        return ActionResult.NONE;
    }
}
