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
    void selectsOnlyTheMatchingControlFlowBranch() {
        WorkflowEdgeModel accepted = new WorkflowEdgeModel(
                "accepted", "condition", "__flow", "accepted-end", "__flow", null, "true");
        WorkflowEdgeModel rejected = new WorkflowEdgeModel(
                "rejected", "condition", "__flow", "rejected-end", "__flow", null, "false");
        Map<String, WorkflowExecutionPlanner.EdgeState> states =
                planner.initializeEdges(List.of(accepted, rejected));

        planner.completeNode(List.of(accepted, rejected), Map.of("true", true, "false", false), states);

        assertEquals(WorkflowExecutionPlanner.EdgeState.SUCCESS, states.get("accepted"));
        assertEquals(WorkflowExecutionPlanner.EdgeState.SKIPPED, states.get("rejected"));
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

    @Test
    void continuesAnUnconditionalControlEdge() {
        WorkflowEdgeModel edge = new WorkflowEdgeModel(
                "next", "start", "__flow", "search", "__flow");
        Map<String, WorkflowExecutionPlanner.EdgeState> states = planner.initializeEdges(List.of(edge));

        planner.completeNode(List.of(edge), Map.of("trigger", Map.of()), states);

        assertEquals(WorkflowExecutionPlanner.EdgeState.SUCCESS, states.get("next"));
    }

    private WorkflowEdgeModel edge(String id, String sourceNodeId, String sourcePort, String targetNodeId) {
        return new WorkflowEdgeModel(id, sourceNodeId, sourcePort, targetNodeId, "input");
    }
}
