package com.trackflow.automation.execution;

import com.trackflow.automation.node.model.WorkflowEdgeModel;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DAGBuilderTest {

    private final DAGBuilder builder = new DAGBuilder();

    @Test
    void rejectsEdgesThatReferenceMissingNodes() {
        List<WorkflowNodeModel> nodes = List.of(node("start", "start"));
        List<WorkflowEdgeModel> edges = List.of(
                new WorkflowEdgeModel("edge", "start", "trigger", "missing", "input")
        );

        assertThrows(DAGBuilder.InvalidWorkflowException.class,
                () -> builder.topologicalSort(nodes, edges));
    }

    @Test
    void rejectsCycles() {
        List<WorkflowNodeModel> nodes = List.of(node("a", "variables"), node("b", "variables"));
        List<WorkflowEdgeModel> edges = List.of(
                new WorkflowEdgeModel("ab", "a", "output", "b", "input"),
                new WorkflowEdgeModel("ba", "b", "output", "a", "input")
        );

        assertThrows(DAGBuilder.CyclicWorkflowException.class,
                () -> builder.topologicalSort(nodes, edges));
    }

    @Test
    void returnsStableTopologicalOrder() {
        List<WorkflowNodeModel> nodes = List.of(
                node("start", "start"), node("condition", "condition"), node("end", "end")
        );
        List<WorkflowEdgeModel> edges = List.of(
                new WorkflowEdgeModel("one", "start", "trigger", "condition", "value"),
                new WorkflowEdgeModel("two", "condition", "true", "end", "result")
        );

        assertEquals(List.of("start", "condition", "end"), builder.topologicalSort(nodes, edges)
                .stream().map(WorkflowNodeModel::id).toList());
    }

    private WorkflowNodeModel node(String id, String type) {
        return new WorkflowNodeModel(id, type, null, null, List.of(), List.of(), Map.of());
    }
}
