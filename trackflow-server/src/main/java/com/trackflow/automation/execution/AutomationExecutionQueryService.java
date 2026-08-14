package com.trackflow.automation.execution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.execution.entity.AutomationExecution;
import com.trackflow.automation.execution.entity.AutomationNodeExecution;
import com.trackflow.automation.execution.mapper.AutomationExecutionMapper;
import com.trackflow.automation.execution.mapper.AutomationNodeExecutionMapper;
import com.trackflow.automation.execution.vo.ExecutionDetailVO;
import com.trackflow.automation.execution.vo.ExecutionSummaryVO;
import com.trackflow.automation.execution.vo.NodeExecutionVO;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AutomationExecutionQueryService {
    private final AutomationExecutionMapper executionMapper;
    private final AutomationNodeExecutionMapper nodeExecutionMapper;
    private final ObjectMapper objectMapper;

    public PageResult<ExecutionSummaryVO> list(Long automationId, int page, int pageSize) {
        Page<AutomationExecution> resultPage = new Page<>(page, pageSize);
        executionMapper.selectPage(resultPage, new LambdaQueryWrapper<AutomationExecution>()
                .eq(AutomationExecution::getAutomationId, automationId)
                .orderByDesc(AutomationExecution::getStartedAt));
        List<ExecutionSummaryVO> rows = resultPage.getRecords().stream().map(this::summary).toList();
        return new PageResult<>(rows, resultPage.getTotal(), page, pageSize);
    }

    public ExecutionDetailVO detail(Long executionId) {
        AutomationExecution execution = executionMapper.selectById(executionId);
        if (execution == null) {
            throw BusinessException.notFound("执行记录", executionId);
        }
        List<AutomationNodeExecution> nodes = nodeExecutionMapper.selectList(
                new LambdaQueryWrapper<AutomationNodeExecution>()
                        .eq(AutomationNodeExecution::getExecutionId, executionId)
                        .orderByAsc(AutomationNodeExecution::getStartedAt));
        ExecutionDetailVO result = new ExecutionDetailVO();
        result.setId(String.valueOf(execution.getId()));
        result.setAutomationId(String.valueOf(execution.getAutomationId()));
        result.setStatus(execution.getStatus());
        result.setInput(readJson(execution.getInput()));
        result.setOutput(readJson(execution.getOutput()));
        result.setErrorMessage(execution.getErrorMessage());
        result.setStartedAt(execution.getStartedAt());
        result.setFinishedAt(execution.getFinishedAt());
        result.setDurationMs(execution.getDurationMs());
        result.setNodeExecutions(nodes.stream().map(this::node).toList());
        return result;
    }

    private ExecutionSummaryVO summary(AutomationExecution execution) {
        ExecutionSummaryVO result = new ExecutionSummaryVO();
        result.setId(String.valueOf(execution.getId()));
        result.setStatus(execution.getStatus());
        result.setStartedAt(execution.getStartedAt());
        result.setFinishedAt(execution.getFinishedAt());
        result.setDurationMs(execution.getDurationMs());
        return result;
    }

    private NodeExecutionVO node(AutomationNodeExecution node) {
        NodeExecutionVO result = new NodeExecutionVO();
        result.setId(String.valueOf(node.getId()));
        result.setNodeId(node.getNodeId());
        result.setNodeType(node.getNodeType());
        result.setNodeName(node.getNodeName() != null ? node.getNodeName() : node.getNodeType());
        result.setStatus(node.getStatus());
        result.setAttempt(node.getAttempt());
        result.setOutcome(node.getOutcome());
        result.setInput(readJson(node.getInput()));
        result.setOutput(readJson(node.getOutput()));
        result.setErrorInfo(node.getErrorInfo());
        result.setDurationMs(node.getDurationMs());
        result.setStartedAt(node.getStartedAt());
        result.setFinishedAt(node.getFinishedAt());
        return result;
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception exception) {
            return Map.of("unreadable", true);
        }
    }
}
