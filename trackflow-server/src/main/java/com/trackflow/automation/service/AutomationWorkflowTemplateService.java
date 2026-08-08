package com.trackflow.automation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.entity.AutomationWorkflowTemplate;
import com.trackflow.automation.mapper.AutomationWorkflowMapper;
import com.trackflow.automation.mapper.AutomationWorkflowTemplateMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
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
     * 获取所有模板列表（按 sort_order 排序）
     */
    public List<AutomationWorkflowTemplate> listTemplates() {
        return templateMapper.selectList(
            new LambdaQueryWrapper<AutomationWorkflowTemplate>()
                .orderByAsc(AutomationWorkflowTemplate::getSortOrder)
        );
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
     * 删除模板（内置模板不允许删除）
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
        templateMapper.deleteById(templateId);
        log.info("删除工作流模板: id={}, name={}", templateId, template.getName());
    }
}
