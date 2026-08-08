package com.trackflow.automation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.automation.dto.CreateTemplateFromWorkflowDTO;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.entity.AutomationWorkflowTemplate;
import com.trackflow.automation.mapper.AutomationWorkflowMapper;
import com.trackflow.automation.mapper.AutomationWorkflowTemplateMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 自动化工作流模板服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationWorkflowTemplateService {

    private final AutomationWorkflowTemplateMapper templateMapper;
    private final AutomationWorkflowMapper workflowMapper;

    /**
     * 获取当前用户可见的模板列表：
     * - 内置模板对所有登录用户可见
     * - 自定义模板仅创建者可见
     */
    public List<AutomationWorkflowTemplate> listTemplates() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return templateMapper.selectList(
            new LambdaQueryWrapper<AutomationWorkflowTemplate>()
                .and(w -> w
                    .eq(AutomationWorkflowTemplate::getIsBuiltin, true)
                    .or(i -> i.eq(AutomationWorkflowTemplate::getCreatedBy, currentUserId))
                )
                .orderByAsc(AutomationWorkflowTemplate::getIsBuiltin) // 自定义模板在前
                .orderByAsc(AutomationWorkflowTemplate::getSortOrder)
        );
    }

    /**
     * 将已有工作流保存为自定义模板
     *
     * @param dto 请求参数
     * @return 新创建的模板
     */
    @Transactional(rollbackFor = Exception.class)
    public AutomationWorkflowTemplate saveAsTemplate(CreateTemplateFromWorkflowDTO dto) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 验证工作流存在
        AutomationWorkflow workflow = workflowMapper.selectById(dto.getWorkflowId());
        if (workflow == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作流不存在: " + dto.getWorkflowId());
        }

        // 验证权限：工作流所有者才能保存为模板
        if (!workflow.getCreatedBy().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有工作流创建者才能将其保存为模板");
        }

        // 使用已发布定义（如有），否则用草稿定义
        String definition = workflow.getPublishedDefinition() != null
            ? workflow.getPublishedDefinition()
            : workflow.getDefinition();

        if (definition == null || definition.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "工作流定义为空，无法保存为模板");
        }

        AutomationWorkflowTemplate template = new AutomationWorkflowTemplate();
        template.setName(dto.getName());
        template.setDescription(dto.getDescription());
        template.setCategory(dto.getCategory() != null ? dto.getCategory() : "custom");
        template.setIcon(dto.getIcon() != null ? dto.getIcon() : "📋");
        template.setDefinition(definition);
        template.setSortOrder(100); // 自定义模板排序靠后
        template.setIsBuiltin(false);
        template.setCreatedBy(currentUserId);

        templateMapper.insert(template);
        log.info("工作流保存为模板: workflowId={}, templateId={}, templateName={}, userId={}",
                dto.getWorkflowId(), template.getId(), template.getName(), currentUserId);
        return template;
    }

    /**
     * 从模板克隆一条工作流
     *
     * @param templateId 模板 ID
     * @return 新创建的工作流（草稿状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public AutomationWorkflow cloneFromTemplate(Long templateId) {
        AutomationWorkflowTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "模板不存在: " + templateId);
        }

        AutomationWorkflow workflow = new AutomationWorkflow();
        workflow.setName(template.getName() + "（副本）");
        workflow.setDescription(template.getDescription());
        workflow.setDefinition(template.getDefinition());
        workflow.setStatus("draft");
        workflow.setVersion(1);
        workflow.setTriggerType("manual");
        workflow.setTriggerConfig("{}");
        workflow.setConcurrencyMode("queue");
        workflow.setMaxConcurrent(1);
        workflow.setRuntimeEnabled(false);
        workflowMapper.insert(workflow);

        log.info("从模板克隆工作流: templateId={}, templateName={}, newWorkflowId={}",
                templateId, template.getName(), workflow.getId());
        return workflow;
    }

    /**
     * 删除模板（内置模板不允许删除，自定义模板只有创建者或 admin 可删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long templateId) {
        AutomationWorkflowTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "模板不存在: " + templateId);
        }
        if (Boolean.TRUE.equals(template.getIsBuiltin())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "内置模板不允许删除");
        }

        // 自定义模板只有创建者可删除（admin 权限在 Controller @PreAuthorize 层判断）
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (template.getCreatedBy() != null && !template.getCreatedBy().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有模板创建者才能删除");
        }

        templateMapper.deleteById(templateId);
        log.info("删除工作流模板: id={}, name={}, userId={}", templateId, template.getName(), currentUserId);
    }
}
