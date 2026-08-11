package com.trackflow.automation.execution;

import com.trackflow.automation.node.NodeRegistry;
import com.trackflow.automation.node.model.InputParameter;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.trackflow.automation.node.model.WorkflowEdgeModel;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import com.trackflow.automation.node.nodes.EndNode;
import com.trackflow.automation.node.nodes.StartNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Ensures execution-order edges never masquerade as data-port bindings. */
class WorkflowControlFlowContractTest {

    @Test
    void acceptsTheDedicatedFlowPortBetweenStartAndEnd() {
        StartNode start = new StartNode();
        EndNode end = new EndNode();
        WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator(
                new DAGBuilder(), new NodeRegistry(List.of(start, end), List.of(start, end)));

        WorkflowDefinitionModel definition = new WorkflowDefinitionModel(Map.of(), List.of(
                node("start", start.getType(), start.getInputPorts(), start.getOutputPorts()),
                node("end", end.getType(), end.getInputPorts(), end.getOutputPorts())), List.of(
                new WorkflowEdgeModel("start-end", "start", "__flow", "end", "__flow")));

        assertDoesNotThrow(() -> validator.validateExecutable(definition));
    }

    private WorkflowNodeModel node(String id, String type, List<InputPortDef> inputs,
                                   List<OutputPortDef> outputs) {
        return new WorkflowNodeModel(id, type, null, null, inputs.stream()
                .map(port -> new InputParameter(port.name(), port.label(), port.valueType(), port.required(),
                        port.description(), port.optional(), null, port.cardinality(), port.semanticType()))
                .toList(), outputs, Map.of());
    }
}
