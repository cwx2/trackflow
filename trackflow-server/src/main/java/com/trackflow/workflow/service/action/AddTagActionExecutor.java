package com.trackflow.workflow.service.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueTagRelation;
import com.trackflow.issue.mapper.IssueTagRelationMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 添加标签动作执行器 — 为工单添加标签（幂等）。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class AddTagActionExecutor extends WorkflowActionSupport {

    @Autowired
    private IssueTagRelationMapper tagRelationMapper;

    @Override
    public String actionType() {
        return "add_tag";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String tagIdStr = textOf(actionConfig, "tagId");
        if (tagIdStr == null || tagIdStr.isBlank()) return ActionResult.NONE;
        try {
            Long tagId = Long.parseLong(tagIdStr);
            Long cnt = tagRelationMapper.selectCount(new LambdaQueryWrapper<IssueTagRelation>()
                    .eq(IssueTagRelation::getIssueId, issue.getId())
                    .eq(IssueTagRelation::getTagId, tagId));
            if (cnt > 0) return ActionResult.NONE;
            IssueTagRelation rel = new IssueTagRelation();
            rel.setIssueId(issue.getId());
            rel.setTagId(tagId);
            rel.setCreatedAt(LocalDateTime.now());
            tagRelationMapper.insert(rel);
            logActivity(issue.getId(), rule, "tag_added", "tag", null, tagIdStr);
        } catch (NumberFormatException ignored) {
            // Invalid tagId format — skip silently
        }
        return ActionResult.NONE;
    }
}
