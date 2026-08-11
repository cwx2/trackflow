package com.trackflow.automation.execution;

import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.NodeRegistry;
import com.trackflow.automation.node.model.InputParameter;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.InputValue;
import com.trackflow.automation.node.model.NodeRuntimePolicy;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.PortCardinality;
import com.trackflow.automation.node.model.VariableRef;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.trackflow.automation.node.model.WorkflowEdgeModel;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import com.trackflow.automation.node.nodes.BatchNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchNodeContractTest {

    @Test
    void allowsACollectionToEnterTheExplicitBatchBoundary() {
        CollectionSource source = new CollectionSource();
        BatchNode batch = new BatchNode();
        WorkflowDefinitionValidator validator = validator(
                new NodeDefinition[]{source, batch}, new NodeExecutor[]{source, batch});

        WorkflowNodeModel sourceNode = node("search", source, Map.of());
        WorkflowNodeModel batchNode = node("batch", batch, Map.of(
                "items", refInput(batch.getInputPorts().getFirst(), "search", "items")));
        WorkflowDefinitionModel definition = new WorkflowDefinitionModel(Map.of(),
                List.of(sourceNode, batchNode), List.of(
                new WorkflowEdgeModel("search-batch", "search", "items", "batch", "items")));

        assertDoesNotThrow(() -> validator.validateDraft(definition));
    }

    @Test
    void rejectsACollectionConnectedDirectlyToAnItemPort() {
        CollectionSource source = new CollectionSource();
        SingleIssueConsumer consumer = new SingleIssueConsumer();
        WorkflowDefinitionValidator validator = validator(
                new NodeDefinition[]{source, consumer}, new NodeExecutor[]{source, consumer});

        WorkflowNodeModel sourceNode = node("search", source, Map.of());
        WorkflowNodeModel consumerNode = node("context", consumer, Map.of(
                "item", refInput(consumer.getInputPorts().getFirst(), "search", "items")));
        WorkflowDefinitionModel definition = new WorkflowDefinitionModel(Map.of(),
                List.of(sourceNode, consumerNode), List.of(
                new WorkflowEdgeModel("search-context", "search", "items", "context", "item")));

        assertThrows(DAGBuilder.InvalidWorkflowException.class,
                () -> validator.validateDraft(definition));
    }

    private WorkflowDefinitionValidator validator(NodeDefinition[] definitions, NodeExecutor[] executors) {
        return new WorkflowDefinitionValidator(new DAGBuilder(), new NodeRegistry(
                List.of(definitions), List.of(executors)));
    }

    private WorkflowNodeModel node(String id, NodeDefinition definition,
                                   Map<String, InputParameter> configuredInputs) {
        List<InputParameter> inputs = definition.getInputPorts().stream()
                .map(port -> configuredInputs.getOrDefault(port.name(), input(port, null)))
                .toList();
        return new WorkflowNodeModel(id, definition.getType(), null, null, inputs,
                definition.getOutputPorts(), Map.of());
    }

    private InputParameter refInput(InputPortDef port, String nodeId, String outputName) {
        return input(port, new VariableRef("ref", nodeId, outputName, null));
    }

    private InputParameter input(InputPortDef port, InputValue value) {
        return new InputParameter(port.name(), port.label(), port.valueType(), port.required(),
                port.description(), port.optional(), value, port.cardinality(), port.semanticType());
    }

    private abstract static class TestNode implements NodeDefinition, NodeExecutor {
        @Override public String getIcon() { return ""; }
        @Override public String getColor() { return "#000000"; }
        @Override public String getDescription() { return ""; }
        @Override public NodeRuntimePolicy getRuntimePolicy() { return NodeRuntimePolicy.safe(); }
        @Override public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node,
                                                      ExecutionContext context) throws NodeExecutionException {
            return Map.of();
        }
    }

    private static final class CollectionSource extends TestNode {
        @Override public String getType() { return "collection-source"; }
        @Override public String getTitle() { return "Collection source"; }
        @Override public List<InputPortDef> getInputPorts() { return List.of(); }
        @Override public List<OutputPortDef> getOutputPorts() {
            return List.of(new OutputPortDef("items", "Items", "array", "",
                    PortCardinality.collection, "issue"));
        }
    }

    private static final class SingleIssueConsumer extends TestNode {
        @Override public String getType() { return "single-consumer"; }
        @Override public String getTitle() { return "Single issue consumer"; }
        @Override public List<InputPortDef> getInputPorts() {
            return List.of(new InputPortDef("item", "Item", "object", true, "", false,
                    PortCardinality.single, "issue"));
        }
        @Override public List<OutputPortDef> getOutputPorts() { return List.of(); }
    }
}
