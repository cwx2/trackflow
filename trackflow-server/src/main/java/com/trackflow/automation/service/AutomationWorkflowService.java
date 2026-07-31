package com.trackflow.automation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.automation.dto.CreateWorkflowDTO;
import com.trackflow.automation.dto.UpdateWorkflowDTO;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.mapper.AutomationWorkflowMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 工作流服务 - 处理工作流的 CRUD 操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationWorkflowService {

    private final AutomationWorkflowMapper workflowMapper;

    /**
     * 查询工作流列表（按更新时间倒序）
     */
    public List<AutomationWorkflow> listWorkflows() {
        return workflowMapper.selectList(
            new LambdaQueryWrapper<AutomationWorkflow>()
                .orderByDesc(AutomationWorkflow::getUpdatedAt)
        );
    }

    /**
     * 获取工作流详情
     */
    public AutomationWorkflow getById(Long id) {
        AutomationWorkflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作流不存在: " + id);
        }
        return workflow;
    }

    /**
     * 创建工作流
     */
    @Transactional(rollbackFor = Exception.class)
    public AutomationWorkflow create(CreateWorkflowDTO dto) {
        AutomationWorkflow workflow = new AutomationWorkflow();
        workflow.setName(dto.getName());
        workflow.setDescription(dto.getDescription());
        // 初始化空的 definition
        workflow.setDefinition("{\"variables\":{},\"nodes\":[],\"edges\":[]}");
        workflowMapper.insert(workflow);
        log.info("创建工作流: id={}, name={}", workflow.getId(), workflow.getName());
        return workflow;
    }

    /**
     * 更新工作流（支持部分更新）
     */
    @Transactional(rollbackFor = Exception.class)
    public AutomationWorkflow update(Long id, UpdateWorkflowDTO dto) {
        AutomationWorkflow workflow = getById(id);

        if (StringUtils.hasText(dto.getName())) {
            workflow.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            workflow.setDescription(dto.getDescription());
        }
        if (dto.getDefinition() != null) {
            workflow.setDefinition(dto.getDefinition());
        }

        workflowMapper.updateById(workflow);
        log.info("更新工作流: id={}", id);
        return workflowMapper.selectById(id);
    }

    /**
     * 删除工作流
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AutomationWorkflow workflow = getById(id);
        workflowMapper.deleteById(id);
        log.info("删除工作流: id={}, name={}", id, workflow.getName());
    }
}
