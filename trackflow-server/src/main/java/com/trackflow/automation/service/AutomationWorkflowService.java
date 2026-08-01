package com.trackflow.automation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.automation.dto.CreateWorkflowDTO;
import com.trackflow.automation.dto.UpdateWorkflowDTO;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.mapper.AutomationWorkflowMapper;
import com.trackflow.automation.execution.DAGBuilder;
import com.trackflow.automation.node.NodeRegistry;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.Map;
import java.time.ZoneId;
import org.springframework.scheduling.support.CronExpression;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * 工作流服务 - 处理工作流的 CRUD 操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationWorkflowService {

    private final AutomationWorkflowMapper workflowMapper;
    private final ObjectMapper objectMapper;
    private final DAGBuilder dagBuilder;
    private final NodeRegistry nodeRegistry;

    private static final Set<String> TRIGGERS = Set.of(
            "manual", "schedule", "issue_created", "issue_changed", "webhook");
    private static final Set<String> CONCURRENCY_MODES = Set.of("queue", "skip", "parallel");

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
        workflow.setProjectId(dto.getProjectId());
        workflow.setStatus("draft");
        workflow.setVersion(1);
        workflow.setTriggerType("manual");
        workflow.setTriggerConfig("{}");
        workflow.setConcurrencyMode("queue");
        workflow.setMaxConcurrent(1);
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

        if (dto.getVersion() != null && !dto.getVersion().equals(workflow.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "工作流已被其他人修改，请刷新后重试");
        }

        if (StringUtils.hasText(dto.getName())) {
            workflow.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            workflow.setDescription(dto.getDescription());
        }
        if (dto.getDefinition() != null) {
            workflow.setDefinition(dto.getDefinition());
        }
        if (dto.getProjectId() != null) workflow.setProjectId(dto.getProjectId());
        if (dto.getTriggerType() != null) {
            requireAllowed(dto.getTriggerType(), TRIGGERS, "触发类型");
            workflow.setTriggerType(dto.getTriggerType());
        }
        if (dto.getTriggerConfig() != null) workflow.setTriggerConfig(dto.getTriggerConfig());
        if (dto.getConcurrencyMode() != null) {
            requireAllowed(dto.getConcurrencyMode(), CONCURRENCY_MODES, "并发模式");
            workflow.setConcurrencyMode(dto.getConcurrencyMode());
        }
        if (dto.getMaxConcurrent() != null) {
            if (dto.getMaxConcurrent() < 1 || dto.getMaxConcurrent() > 50) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "最大并发数必须在 1 到 50 之间");
            }
            workflow.setMaxConcurrent(dto.getMaxConcurrent());
        }
        if (dto.getActorUserId() != null) workflow.setActorUserId(dto.getActorUserId());
        workflow.setVersion(workflow.getVersion() + 1);

        workflowMapper.updateById(workflow);
        log.info("更新工作流: id={}", id);
        return workflowMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AutomationWorkflow publish(Long id) {
        AutomationWorkflow workflow = getById(id);
        validateDefinition(workflow.getDefinition());
        validateTriggerConfig(workflow);
        if (workflow.getActorUserId() == null && !"manual".equals(workflow.getTriggerType())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "自动触发工作流必须配置执行身份");
        }
        workflow.setPublishedDefinition(workflow.getDefinition());
        workflow.setStatus("published");
        workflow.setPublishedAt(LocalDateTime.now());
        workflow.setVersion(workflow.getVersion() + 1);
        workflowMapper.updateById(workflow);
        return workflowMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AutomationWorkflow disable(Long id) {
        AutomationWorkflow workflow = getById(id);
        workflow.setStatus("disabled");
        workflow.setVersion(workflow.getVersion() + 1);
        workflowMapper.updateById(workflow);
        return workflowMapper.selectById(id);
    }

    private void validateDefinition(String definitionJson) {
        try {
            WorkflowDefinitionModel definition = objectMapper.readValue(
                    definitionJson != null ? definitionJson : "{}", WorkflowDefinitionModel.class);
            dagBuilder.topologicalSort(definition.nodes(), definition.edges());
            long startCount = definition.nodes().stream().filter(node -> "start".equals(node.type())).count();
            if (startCount != 1) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "工作流必须且只能包含一个开始节点");
            }
            if (definition.nodes().stream().noneMatch(node -> "end".equals(node.type()))) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "工作流至少需要一个结束节点");
            }
            definition.nodes().forEach(node -> {
                if (nodeRegistry.getExecutor(node.type()) == null) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                            "节点尚无可用执行器: " + node.type());
                }
            });
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "工作流定义校验失败: " + exception.getMessage());
        }
    }

    private void requireAllowed(String value, Set<String> allowed, String fieldName) {
        if (!allowed.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, fieldName + "不支持: " + value);
        }
    }

    private void validateTriggerConfig(AutomationWorkflow workflow) {
        try {
            Map<String, Object> config = workflow.getTriggerConfig() == null ? Map.of()
                    : objectMapper.readValue(workflow.getTriggerConfig(), new TypeReference<>() {});
            if ("schedule".equals(workflow.getTriggerType())) {
                String cron = String.valueOf(config.getOrDefault("cron", ""));
                cron = switch (cron.toLowerCase()) {
                    case "hourly" -> "0 0 * * * *";
                    case "daily" -> "0 0 9 * * *";
                    case "weekly" -> "0 0 9 * * MON";
                    default -> cron;
                };
                if (!CronExpression.isValidExpression(cron)) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "定时触发的 cron 表达式无效");
                }
                ZoneId.of(String.valueOf(config.getOrDefault("timezone", "Asia/Shanghai")));
            }
            if ("webhook".equals(workflow.getTriggerType())) {
                String tokenHash = String.valueOf(config.getOrDefault("tokenSha256", ""));
                if (!tokenHash.matches("[a-fA-F0-9]{64}")) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                            "Webhook 触发必须配置 64 位 tokenSha256");
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "触发配置无效: " + exception.getMessage());
        }
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
