package com.trackflow.automation.execution;

import com.trackflow.automation.node.model.WorkflowEdgeModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowExecutionPlannerTest {

    private final WorkflowExecutionPlanner planner = new WorkflowExecutionPlanner();

    @Test
    void selectsOnlyTheMatchingBooleanBranch() {
        WorkflowEdgeModel trueEdge = edge("true-edge", "condition", "true", "accepted");
        WorkflowEdgeModel falseEdge = edge("false-edge", "condition", "false", "rejected");
        Map<String, WorkflowExecutionPlanner.EdgeState> states =
                planner.initializeEdges(List.of(trueEdge, falseEdge));

        planner.completeNode(List.of(trueEdge, falseEdge),
                Map.of("true", true, "false", false), states);

        assertEquals(WorkflowExecutionPlanner.EdgeState.SUCCESS, states.get("true-edge"));
        assertEquals(WorkflowExecutionPlanner.EdgeState.SKIPPED, states.get("false-edge"));
        assertEquals(WorkflowExecutionPlanner.NodeDecision.READY,
                planner.decide(List.of(trueEdge), states));
        assertEquals(WorkflowExecutionPlanner.NodeDecision.SKIPPED,
                planner.decide(List.of(falseEdge), states));
    }

    @Test
    void mergeNodeRunsWhenAtLeastOneCompletedInputSucceeded() {
        WorkflowEdgeModel selected = edge("selected", "left", "output", "merge");
        WorkflowEdgeModel skipped = edge("skipped", "right", "output", "merge");
        Map<String, WorkflowExecutionPlanner.EdgeState> states = Map.of(
                "selected", WorkflowExecutionPlanner.EdgeState.SUCCESS,
                "skipped", WorkflowExecutionPlanner.EdgeState.SKIPPED
        );

        assertEquals(WorkflowExecutionPlanner.NodeDecision.READY,
                planner.decide(List.of(selected, skipped), states));
    }

    @Test
    void waitsUntilEveryIncomingEdgeHasProducedAResult() {
        WorkflowEdgeModel completed = edge("completed", "left", "output", "merge");
        WorkflowEdgeModel pending = edge("pending", "right", "output", "merge");
        Map<String, WorkflowExecutionPlanner.EdgeState> states = Map.of(
                "completed", WorkflowExecutionPlanner.EdgeState.SUCCESS,
                "pending", WorkflowExecutionPlanner.EdgeState.PENDING
        );

        assertEquals(WorkflowExecutionPlanner.NodeDecision.WAITING,
                planner.decide(List.of(completed, pending), states));
    }

    private WorkflowEdgeModel edge(String id, String sourceNodeId, String sourcePort, String targetNodeId) {
        return new WorkflowEdgeModel(id, sourceNodeId, sourcePort, targetNodeId, "input");
    }
}
