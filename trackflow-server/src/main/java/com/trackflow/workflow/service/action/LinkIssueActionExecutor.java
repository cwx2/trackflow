package com.trackflow.workflow.service.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueLink;
import com.trackflow.issue.mapper.IssueLinkMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建工单关联动作执行器 — 在工单之间创建关联关系。
 * <p>
 * target 支持：
 * - "from_block:N"（引用本规则第 N 个 create_issue 的结果）
 * - 数字 ID（直接引用工单 ID）
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class LinkIssueActionExecutor extends WorkflowActionSupport {

    @Autowired
    private IssueLinkMapper issueLinkMapper;

    @Override
    public String actionType() {
        return "link_issue";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String linkType = textOf(actionConfig, "linkType");
        if (linkType == null || linkType.isBlank()) linkType = "relates_to";

        String targetRef = textOf(actionConfig, "target");
        if (targetRef == null || targetRef.isBlank()) {
            log.warn("[RuleEngine] link_issue: target is empty in rule '{}' (id={})", rule.getName(), rule.getId());
            return ActionResult.NONE;
        }

        Long targetIssueId;
        List<Issue> createdIssues = context.getCreatedIssues();

        if (targetRef.startsWith("from_block:")) {
            try {
                int blockIndex = Integer.parseInt(targetRef.substring("from_block:".length()));
                if (blockIndex < 0 || blockIndex >= createdIssues.size()) {
                    log.warn("[RuleEngine] link_issue: block index {} out of range (created {} issues) in rule '{}'",
                            blockIndex, createdIssues.size(), rule.getName());
                    return ActionResult.NONE;
                }
                targetIssueId = createdIssues.get(blockIndex).getId();
            } catch (NumberFormatException e) {
                log.warn("[RuleEngine] link_issue: invalid block reference '{}' in rule '{}'", targetRef, rule.getName());
                return ActionResult.NONE;
            }
        } else {
            try {
                targetIssueId = Long.parseLong(targetRef);
            } catch (NumberFormatException e) {
                log.warn("[RuleEngine] link_issue: invalid target ID '{}' in rule '{}'", targetRef, rule.getName());
                return ActionResult.NONE;
            }
        }

        Long sourceIssueId = issue.getId();

        // Check if link already exists (idempotent)
        Long existingCount = issueLinkMapper.selectCount(new LambdaQueryWrapper<IssueLink>()
                .eq(IssueLink::getSourceIssueId, sourceIssueId)
                .eq(IssueLink::getTargetIssueId, targetIssueId)
                .eq(IssueLink::getLinkType, linkType));
        if (existingCount > 0) {
            log.debug("[RuleEngine] link_issue: link already exists between {} and {} (type={})",
                    sourceIssueId, targetIssueId, linkType);
            return ActionResult.NONE;
        }

        IssueLink link = new IssueLink();
        link.setSourceIssueId(sourceIssueId);
        link.setTargetIssueId(targetIssueId);
        link.setLinkType(linkType);
        link.setCreatedBy(rule.getCreatedBy());
        link.setCreatedAt(LocalDateTime.now());
        issueLinkMapper.insert(link);

        logActivity(issue.getId(), rule, "link_added", "link", null,
                linkType + " → " + targetIssueId);
        log.info("[RuleEngine] link_issue: created link {} -> {} (type={}) by rule '{}' (id={})",
                sourceIssueId, targetIssueId, linkType, rule.getName(), rule.getId());
        return ActionResult.NONE;
    }
}
