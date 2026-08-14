package com.trackflow.automation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.automation.dto.CreateTemplateFromWorkflowDTO;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.entity.AutomationWorkflowTemplate;
import com.trackflow.automation.mapper.AutomationWorkflowMapper;
import com.trackflow.automation.mapper.AutomationWorkflowTemplateMapper;
import com.trackflow.automation.execution.WorkflowDefinitionValidator;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    private final WorkflowDefinitionValidator workflowDefinitionValidator;

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
            throw BusinessException.notFound("工作流", dto.getWorkflowId());
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
        validateExecutableTemplate(definition);

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
            throw BusinessException.notFound("模板", templateId);
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无法识别当前用户，不能创建工作流");
        }
        if (!Boolean.TRUE.equals(template.getIsBuiltin())
                && !currentUserId.equals(template.getCreatedBy())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权使用其他用户的私有模板");
        }
        validateExecutableTemplate(template.getDefinition());

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
        // 模板创建者就是默认执行身份：复制后可直接试运行、发布和启动，
        // 不需要用户额外填写一个与当前登录用户重复的配置。
        workflow.setActorUserId(currentUserId);
        workflow.setRuntimeEnabled(false);
        workflowMapper.insert(workflow);

        log.info("从模板克隆工作流: templateId={}, templateName={}, newWorkflowId={}",
                templateId, template.getName(), workflow.getId());
        return workflow;
    }

    /** 模板必须在保存与克隆时完整可执行，不能把端口或拓扑错误延后到用户试运行时。 */
    private void validateExecutableTemplate(String definition) {
        try {
            WorkflowDefinitionModel parsed = objectMapper.readValue(definition, WorkflowDefinitionModel.class);
            workflowDefinitionValidator.validateExecutable(parsed);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板工作流结构错误: " + exception.getMessage());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板工作流不是合法 JSON");
        }
    }

    /**
     * 删除模板（内置模板不允许删除，自定义模板只有创建者或 admin 可删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long templateId) {
        AutomationWorkflowTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw BusinessException.notFound("模板", templateId);
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
