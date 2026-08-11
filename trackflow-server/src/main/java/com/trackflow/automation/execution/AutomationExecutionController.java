package com.trackflow.automation.execution;

import com.trackflow.automation.execution.dto.ExecuteWorkflowDTO;
import com.trackflow.automation.execution.dto.NodeTestDTO;
import com.trackflow.automation.execution.vo.ExecutionDetailVO;
import com.trackflow.automation.execution.vo.ExecutionStartedVO;
import com.trackflow.automation.execution.vo.ExecutionSummaryVO;
import com.trackflow.automation.execution.vo.NodeTestResultVO;
import com.trackflow.automation.node.NodeRegistry;
import com.trackflow.automation.node.model.NodeContract;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/automation")
public class AutomationExecutionController {

    private final AutomationExecutionService executionService;
    private final AutomationNodeTestService nodeTestService;
    private final AutomationExecutionQueryService executionQueryService;
    private final SseNotifier sseNotifier;
    private final NodeRegistry nodeRegistry;

    // ── 节点元数据查询 ────────────────────────────────────────────

    @GetMapping("/node-definitions")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<List<NodeContract>> getNodeDefinitions() {
        return R.ok(List.copyOf(nodeRegistry.getAllContracts()));
    }

    // ── 执行触发 ──────────────────────────────────────────────────

    @PostMapping("/workflows/{id}/execute")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<ExecutionStartedVO> execute(
            @PathVariable String id,
            @RequestBody(required = false) ExecuteWorkflowDTO body) {

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Map<String, Object> inputs = body != null && body.getInputs() != null
                ? body.getInputs() : Map.of();
        Long executionId = executionService.startDraft(Long.parseLong(id), inputs, currentUserId);
        return R.ok(new ExecutionStartedVO(String.valueOf(executionId)));
    }

    /** 在不创建工作流执行记录的情况下，试运行画布中的单个节点。 */
    @PostMapping("/workflows/{id}/nodes/{nodeId}/test")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<NodeTestResultVO> testNode(
            @PathVariable String id,
            @PathVariable String nodeId,
            @RequestBody(required = false) NodeTestDTO body) {
        return R.ok(nodeTestService.test(Long.parseLong(id), nodeId, body,
                SecurityUtils.getCurrentUserId()));
    }

    // ── SSE 实时流 ────────────────────────────────────────────────

    @GetMapping("/executions/{executionId}/stream")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public SseEmitter stream(
            @PathVariable Long executionId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        executionService.requireExecution(executionId);
        return sseNotifier.register(executionId, parseLastEventId(lastEventId));
    }

    private Long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) return null;
        try {
            return Long.parseLong(lastEventId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // ── 执行历史 ──────────────────────────────────────────────────

    @GetMapping("/workflows/{id}/executions")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<PageResult<ExecutionSummaryVO>> listExecutions(
            @PathVariable String id,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(executionQueryService.list(Long.parseLong(id), page, pageSize));
    }

    @GetMapping("/executions/{executionId}")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<ExecutionDetailVO> getExecution(@PathVariable Long executionId) {
        return R.ok(executionQueryService.detail(executionId));
    }

    @PostMapping("/executions/{executionId}/cancel")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<Void> cancelExecution(@PathVariable Long executionId) {
        executionService.cancel(executionId);
        return R.ok();
    }
}
