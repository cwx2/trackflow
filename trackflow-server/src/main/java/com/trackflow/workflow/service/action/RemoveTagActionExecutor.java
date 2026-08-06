package com.trackflow.workflow.service.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueTagRelation;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueTagRelationMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 移除标签动作执行器 — 从工单移除指定标签（幂等）。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class RemoveTagActionExecutor extends WorkflowActionSupport {

    private final IssueTagRelationMapper tagRelationMapper;

    public RemoveTagActionExecutor(IssueActivityMapper activityMapper,
                                   SysUserMapper sysUserMapper,
                                   NotificationMapper notificationMapper,
                                   ProjectService projectService,
                                   IssueTagRelationMapper tagRelationMapper) {
        super(activityMapper, sysUserMapper, notificationMapper, projectService);
        this.tagRelationMapper = tagRelationMapper;
    }

    @Override
    public String actionType() {
        return "remove_tag";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String tagIdStr = textOf(actionConfig, "tagId");
        if (tagIdStr == null || tagIdStr.isBlank()) return ActionResult.NONE;
        try {
            Long tagId = Long.parseLong(tagIdStr);
            int deleted = tagRelationMapper.delete(new LambdaQueryWrapper<IssueTagRelation>()
                    .eq(IssueTagRelation::getIssueId, issue.getId())
                    .eq(IssueTagRelation::getTagId, tagId));
            if (deleted > 0) {
                logActivity(issue.getId(), rule, "tag_removed", "tag", tagIdStr, null);
                log.info("[RuleEngine] remove_tag: removed tag {} from issue {} by rule '{}' (id={})",
                        tagId, issue.getIssueKey(), rule.getName(), rule.getId());
            }
        } catch (NumberFormatException e) {
            log.warn("[RuleEngine] remove_tag: invalid tagId '{}' in rule '{}' (id={})",
                    tagIdStr, rule.getName(), rule.getId());
        }
        return ActionResult.NONE;
    }
}
