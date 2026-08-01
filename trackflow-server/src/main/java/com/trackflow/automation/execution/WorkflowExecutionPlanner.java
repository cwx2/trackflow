package com.trackflow.automation.execution;

import com.trackflow.automation.node.model.WorkflowEdgeModel;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流控制流规划器。
 *
 * <p>每条边都维护独立状态。节点只有在所有入边都已经产生结果，且至少一条入边成功时
 * 才可运行；如果所有入边都被跳过，则节点及其出边也会被跳过。该语义与 Coze 的
 * 控制流模型一致，可以在保持 DAG 的同时正确支持条件分支。</p>
 */
@Component
public class WorkflowExecutionPlanner {

    public enum EdgeState {
        PENDING,
        SUCCESS,
        SKIPPED
    }

    public enum NodeDecision {
        READY,
        WAITING,
        SKIPPED
    }

    public Map<String, EdgeState> initializeEdges(List<WorkflowEdgeModel> edges) {
        Map<String, EdgeState> states = new LinkedHashMap<>();
        for (WorkflowEdgeModel edge : edges) {
            states.put(edge.id(), EdgeState.PENDING);
        }
        return states;
    }

    public NodeDecision decide(Collection<WorkflowEdgeModel> incomingEdges,
                               Map<String, EdgeState> edgeStates) {
        if (incomingEdges.isEmpty()) {
            return NodeDecision.READY;
        }

        boolean hasPending = false;
        boolean hasSuccess = false;
        for (WorkflowEdgeModel edge : incomingEdges) {
            EdgeState state = edgeStates.getOrDefault(edge.id(), EdgeState.PENDING);
            hasPending |= state == EdgeState.PENDING;
            hasSuccess |= state == EdgeState.SUCCESS;
        }

        if (hasPending) {
            return NodeDecision.WAITING;
        }
        return hasSuccess ? NodeDecision.READY : NodeDecision.SKIPPED;
    }

    public void completeNode(Collection<WorkflowEdgeModel> outgoingEdges,
                             Map<String, Object> outputs,
                             Map<String, EdgeState> edgeStates) {
        for (WorkflowEdgeModel edge : outgoingEdges) {
            Object portValue = outputs.get(edge.sourcePortName());
            boolean selected = !(portValue instanceof Boolean booleanValue) || booleanValue;
            edgeStates.put(edge.id(), selected ? EdgeState.SUCCESS : EdgeState.SKIPPED);
        }
    }

    public void skipNode(Collection<WorkflowEdgeModel> outgoingEdges,
                         Map<String, EdgeState> edgeStates) {
        for (WorkflowEdgeModel edge : outgoingEdges) {
            edgeStates.put(edge.id(), EdgeState.SKIPPED);
        }
    }
}
