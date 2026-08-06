package com.trackflow.workflow.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.integration.service.EmailSendService;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 发送邮件动作执行器 — 向指定用户发送邮件通知。
 * <p>
 * target 支持：reporter、assignee、creator、具体邮箱地址、用户名。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class SendEmailActionExecutor extends WorkflowActionSupport {

    private final EmailSendService emailSendService;

    public SendEmailActionExecutor(IssueActivityMapper activityMapper,
                                   SysUserMapper sysUserMapper,
                                   NotificationMapper notificationMapper,
                                   ProjectService projectService,
                                   EmailSendService emailSendService) {
        super(activityMapper, sysUserMapper, notificationMapper, projectService);
        this.emailSendService = emailSendService;
    }

    @Override
    public String actionType() {
        return "send_email";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String target = textOf(actionConfig, "target");
        String subject = textOf(actionConfig, "subject");
        String body = textOf(actionConfig, "body");

        if (target == null || target.isBlank()) {
            log.warn("[RuleEngine] send_email: target not specified in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return ActionResult.NONE;
        }
        if (subject == null || subject.isBlank()) {
            subject = "[TrackFlow] 工单 " + issue.getIssueKey() + " 规则通知";
        }
        if (body == null || body.isBlank()) {
            body = "规则 '" + rule.getName() + "' 触发了邮件通知。";
        }

        subject = interpolateVariables(subject, issue, rule);
        body = interpolateVariables(body, issue, rule);

        String toAddress = resolveEmailTarget(target, issue, rule);
        if (toAddress == null || toAddress.isBlank()) {
            log.warn("[RuleEngine] send_email: cannot resolve email for target '{}' in rule '{}' (id={})",
                    target, rule.getName(), rule.getId());
            return ActionResult.NONE;
        }

        if (!emailSendService.isEmailAvailable()) {
            log.warn("[RuleEngine] send_email: email service not available, skipping in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return ActionResult.NONE;
        }

        String htmlBody = buildRuleEmailHtml(subject, body, issue, rule);
        emailSendService.sendNotificationEmail(toAddress, subject, htmlBody);
        logActivity(issue.getId(), rule, "email_sent", null, null, toAddress);
        log.info("[RuleEngine] send_email: sent to {} for issue {} by rule '{}' (id={})",
                toAddress, issue.getIssueKey(), rule.getName(), rule.getId());
        return ActionResult.NONE;
    }

    private String buildRuleEmailHtml(String subject, String body, Issue issue, WorkflowRule rule) {
        return """
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #1f2328; margin: 0 0 16px 0;">%s</h2>
                  <div style="color: #57606a; font-size: 14px; line-height: 1.6; margin-bottom: 16px;">
                    %s
                  </div>
                  <div style="background: #f6f8fa; border-radius: 6px; padding: 12px 16px; margin-bottom: 16px;">
                    <p style="margin: 0; font-size: 13px; color: #57606a;">
                      <strong>工单:</strong> %s - %s<br/>
                      <strong>触发规则:</strong> %s
                    </p>
                  </div>
                  <hr style="border: none; border-top: 1px solid #d1d9e0; margin: 24px 0;" />
                  <p style="color: #8b949e; font-size: 12px;">
                    此邮件由 TrackFlow 工作流规则自动发送。
                  </p>
                </div>
                """.formatted(
                escapeHtml(subject),
                escapeHtml(body).replace("\n", "<br/>"),
                escapeHtml(issue.getIssueKey() != null ? issue.getIssueKey() : ""),
                escapeHtml(issue.getTitle() != null ? issue.getTitle() : ""),
                escapeHtml(rule.getName())
        );
    }
}
