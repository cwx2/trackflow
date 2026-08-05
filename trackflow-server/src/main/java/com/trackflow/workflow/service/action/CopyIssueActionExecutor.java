package com.trackflow.workflow.service.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.common.event.WorkflowRuleEvent;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueAttachment;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueAttachmentMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.workflow.entity.WorkflowInitialStatus;
import com.trackflow.workflow.entity.WorkflowRule;
import com.trackflow.workflow.mapper.WorkflowInitialStatusMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 克隆工单动作执行器 — 克隆工单到同项目或其他项目。
 * <p>
 * 复制字段：标题（可加前缀）、描述、类型、优先级。
 * 状态重置为目标项目初始状态；附件可选复制。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class CopyIssueActionExecutor extends WorkflowActionSupport {

    @Autowired
    private IssueMapper issueMapper;

    @Autowired
    private IssueAttachmentMapper attachmentMapper;

    @Autowired
    private IssueStatusMapper statusMapper;

    @Autowired
    private WorkflowInitialStatusMapper initialStatusMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public String actionType() {
        return "copy_issue";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        Long targetProjectId = resolveProjectId(textOf(actionConfig, "targetProjectId"), issue);
        if (targetProjectId == null) {
            log.warn("[RuleEngine] copy_issue: targetProjectId could not be resolved in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return ActionResult.NONE;
        }

        com.trackflow.project.entity.Project targetProject = projectService.getById(targetProjectId);
        if (targetProject == null) {
            log.warn("[RuleEngine] copy_issue: target project {} not found in rule '{}' (id={})",
                    targetProjectId, rule.getName(), rule.getId());
            return ActionResult.NONE;
        }

        String summaryPrefix = textOf(actionConfig, "summaryPrefix");
        if (summaryPrefix == null) summaryPrefix = "";
        summaryPrefix = interpolateVariables(summaryPrefix, issue, rule);
        String title = summaryPrefix + (issue.getTitle() != null ? issue.getTitle() : "");

        int seq = projectService.nextIssueSequence(targetProjectId);
        String issueKey = targetProject.getKey() + "-" + seq;

        Issue copy = new Issue();
        copy.setProjectId(targetProjectId);
        copy.setIssueKey(issueKey);
        copy.setTitle(title);
        copy.setDescription(issue.getDescription());
        copy.setIssueType(issue.getIssueType());
        copy.setPriority(issue.getPriority());
        copy.setReporterId(rule.getCreatedBy());
        copy.setCreatedBy(rule.getCreatedBy());

        Long initialStatusId = resolveInitialStatus(targetProjectId, issue.getIssueType());
        copy.setStatusId(initialStatusId);

        boolean copySprint = Boolean.TRUE.equals(boolOf(actionConfig, "copySprint"));
        if (copySprint && Objects.equals(targetProjectId, issue.getProjectId()) && issue.getSprintId() != null) {
            copy.setSprintId(issue.getSprintId());
        }

        issueMapper.insert(copy);
        context.addCreatedIssue(copy);

        // 附件复制
        boolean copyAttachments = Boolean.TRUE.equals(boolOf(actionConfig, "copyAttachments"));
        if (copyAttachments) {
            List<IssueAttachment> attachments = attachmentMapper.selectList(
                    new LambdaQueryWrapper<IssueAttachment>()
                            .eq(IssueAttachment::getIssueId, issue.getId()));
            for (IssueAttachment att : attachments) {
                IssueAttachment newAtt = new IssueAttachment();
                newAtt.setIssueId(copy.getId());
                newAtt.setFileName(att.getFileName());
                newAtt.setFilePath(att.getFilePath());
                newAtt.setFileSize(att.getFileSize());
                newAtt.setContentType(att.getContentType());
                newAtt.setUploadedBy(rule.getCreatedBy());
                newAtt.setCreatedAt(LocalDateTime.now());
                attachmentMapper.insert(newAtt);
            }
        }

        logActivity(copy.getId(), rule, "created", null, null, null);
        log.info("[RuleEngine] copy_issue: cloned {} → {} (project={}) by rule '{}' (id={})",
                issue.getIssueKey(), issueKey, targetProjectId, rule.getName(), rule.getId());

        eventPublisher.publishEvent(new WorkflowRuleEvent.IssueCreated(copy.getId(), copy.getProjectId()));

        return ActionResult.NONE;
    }

    private Long resolveInitialStatus(Long projectId, String issueType) {
        String effectiveIssueType = (issueType == null || issueType.isBlank()) ? "*" : issueType;

        if (projectId != null && !"*".equals(effectiveIssueType)) {
            WorkflowInitialStatus config = initialStatusMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInitialStatus>()
                            .eq(WorkflowInitialStatus::getProjectId, projectId)
                            .eq(WorkflowInitialStatus::getIssueType, effectiveIssueType));
            if (config != null) return config.getStatusId();
        }
        if (projectId != null) {
            WorkflowInitialStatus config = initialStatusMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInitialStatus>()
                            .eq(WorkflowInitialStatus::getProjectId, projectId)
                            .eq(WorkflowInitialStatus::getIssueType, "*"));
            if (config != null) return config.getStatusId();
        }
        if (!"*".equals(effectiveIssueType)) {
            WorkflowInitialStatus config = initialStatusMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInitialStatus>()
                            .isNull(WorkflowInitialStatus::getProjectId)
                            .eq(WorkflowInitialStatus::getIssueType, effectiveIssueType));
            if (config != null) return config.getStatusId();
        }
        WorkflowInitialStatus config = initialStatusMapper.selectOne(
                new LambdaQueryWrapper<WorkflowInitialStatus>()
                        .isNull(WorkflowInitialStatus::getProjectId)
                        .eq(WorkflowInitialStatus::getIssueType, "*"));
        if (config != null) return config.getStatusId();

        IssueStatus status = statusMapper.selectOne(new LambdaQueryWrapper<IssueStatus>()
                .eq(IssueStatus::getIsClosed, false)
                .orderByAsc(IssueStatus::getSortOrder)
                .last("LIMIT 1"));
        return status != null ? status.getId() : null;
    }
}
