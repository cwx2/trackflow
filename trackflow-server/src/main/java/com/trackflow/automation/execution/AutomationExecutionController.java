package com.trackflow.automation.execution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.execution.entity.AutomationExecution;
import com.trackflow.automation.execution.entity.AutomationNodeExecution;
import com.trackflow.automation.execution.mapper.AutomationExecutionMapper;
import com.trackflow.automation.execution.mapper.AutomationNodeExecutionMapper;
import com.trackflow.automation.mapper.AutomationWorkflowMapper;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeRegistry;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/automation")
public class AutomationExecutionController {

    private final AutomationWorkflowMapper workflowMapper;
    private final AutomationExecutionMapper executionMapper;
    private final AutomationNodeExecutionMapper nodeExecutionMapper;
    private final DAGExecutor dagExecutor;
    private final SseNotifier sseNotifier;
    private final NodeRegistry nodeRegistry;
    private final ObjectMapper objectMapper;

    // ── 节点元数据查询 ────────────────────────────────────────────

    @GetMapping("/node-definitions")
    @PreAuthorize("isAuthenticated()")
    public R<List<Map<String, Object>>> getNodeDefinitions() {
        List<Map<String, Object>> defs = nodeRegistry.getAllDefinitions().stream()
            .map(d -> Map.<String, Object>of(
                "type",        d.getType(),
                "title",       d.getTitle(),
                "icon",        d.getIcon(),
                "color",       d.getColor(),
                "description", d.getDescription(),
                "category",    d.getCategory(),
                "inputPorts",  d.getInputPorts(),
                "outputPorts", d.getOutputPorts()
            ))
            .collect(Collectors.toList());
        return R.ok(defs);
    }

    // ── 执行触发 ──────────────────────────────────────────────────

    @PostMapping("/workflows/{id}/execute")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, String>> execute(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {

        AutomationWorkflow workflow = workflowMapper.selectById(id);
        if (workflow == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作流不存在: " + id);

        // 解析工作流定义
        WorkflowDefinitionModel def;
        try {
            def = objectMapper.readValue(
                workflow.getDefinition() != null ? workflow.getDefinition() : "{}",
                WorkflowDefinitionModel.class
            );
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "工作流定义格式错误: " + e.getMessage());
        }

        // 创建执行记录
        AutomationExecution execution = new AutomationExecution();
        execution.setAutomationId(Long.parseLong(id));
        execution.setStatus("running");
        execution.setStartedAt(LocalDateTime.now());
        execution.setCreatedBy(SecurityUtils.getCurrentUserId());
        Map<String, Object> inputs = body != null ? (Map<String, Object>) body.getOrDefault("inputs", Map.of()) : Map.of();
        try { execution.setInput(objectMapper.writeValueAsString(inputs)); } catch (Exception ignored) {}
        executionMapper.insert(execution);

        // 异步执行
        dagExecutor.executeAsync(def, execution.getId(), inputs);

        return R.ok(Map.of("executionId", String.valueOf(execution.getId())));
    }

    // ── SSE 实时流 ────────────────────────────────────────────────

    @GetMapping("/executions/{executionId}/stream")
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(@PathVariable Long executionId) {
        // 如果执行已完成，直接返回一个立即关闭的 emitter
        AutomationExecution exec = executionMapper.selectById(executionId);
        if (exec != null && !"running".equals(exec.getStatus())) {
            SseEmitter done = new SseEmitter(0L);
            done.complete();
            return done;
        }
        return sseNotifier.register(executionId);
    }

    // ── 执行历史 ──────────────────────────────────────────────────

    @GetMapping("/workflows/{id}/executions")
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<Map<String, Object>>> listExecutions(
            @PathVariable String id,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AutomationExecution> p =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize);

        executionMapper.selectPage(p, new LambdaQueryWrapper<AutomationExecution>()
            .eq(AutomationExecution::getAutomationId, id)
            .orderByDesc(AutomationExecution::getStartedAt));

        List<Map<String, Object>> list = p.getRecords().stream()
            .map(e -> Map.<String, Object>of(
                "id",          String.valueOf(e.getId()),
                "status",      e.getStatus(),
                "startedAt",   e.getStartedAt() != null ? e.getStartedAt().toString() : null,
                "finishedAt",  e.getFinishedAt() != null ? e.getFinishedAt().toString() : null,
                "durationMs",  e.getDurationMs() != null ? e.getDurationMs() : 0
            ))
            .collect(Collectors.toList());

        return R.ok(new PageResult<>(list, p.getTotal(), page, pageSize));
    }

    @GetMapping("/executions/{executionId}")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getExecution(@PathVariable Long executionId) {
        AutomationExecution exec = executionMapper.selectById(executionId);
        if (exec == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "执行记录不存在: " + executionId);

        List<AutomationNodeExecution> nodeExecs = nodeExecutionMapper.selectList(
            new LambdaQueryWrapper<AutomationNodeExecution>()
                .eq(AutomationNodeExecution::getExecutionId, executionId)
                .orderByAsc(AutomationNodeExecution::getStartedAt)
        );

        List<Map<String, Object>> nodeList = nodeExecs.stream().map(n -> {
            Object inputObj = null, outputObj = null;
            try {
                if (n.getInput()  != null) inputObj  = objectMapper.readValue(n.getInput(),  Object.class);
                if (n.getOutput() != null) outputObj = objectMapper.readValue(n.getOutput(), Object.class);
            } catch (Exception ignored) {}
            return Map.<String, Object>of(
                "id",         String.valueOf(n.getId()),
                "nodeId",     n.getNodeId(),
                "nodeType",   n.getNodeType(),
                "nodeName",   n.getNodeName() != null ? n.getNodeName() : n.getNodeType(),
                "status",     n.getStatus(),
                "input",      inputObj != null ? inputObj : Map.of(),
                "output",     outputObj != null ? outputObj : Map.of(),
                "errorInfo",  n.getErrorInfo() != null ? n.getErrorInfo() : "",
                "durationMs", n.getDurationMs() != null ? n.getDurationMs() : 0
            );
        }).collect(Collectors.toList());

        Object wfOutput = null;
        try { if (exec.getOutput() != null) wfOutput = objectMapper.readValue(exec.getOutput(), Object.class); }
        catch (Exception ignored) {}

        return R.ok(Map.of(
            "id",             String.valueOf(exec.getId()),
            "automationId",   String.valueOf(exec.getAutomationId()),
            "status",         exec.getStatus(),
            "output",         wfOutput != null ? wfOutput : Map.of(),
            "errorMessage",   exec.getErrorMessage() != null ? exec.getErrorMessage() : "",
            "startedAt",      exec.getStartedAt() != null ? exec.getStartedAt().toString() : null,
            "finishedAt",     exec.getFinishedAt() != null ? exec.getFinishedAt().toString() : null,
            "durationMs",     exec.getDurationMs() != null ? exec.getDurationMs() : 0,
            "nodeExecutions", nodeList
        ));
    }
}
