package com.trackflow.automation.execution;

import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeRegistry;
import com.trackflow.automation.node.model.InputParameter;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.InputValue;
import com.trackflow.automation.node.model.InputBindingMode;
import com.trackflow.automation.node.model.LiteralValue;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.VariableRef;
import com.trackflow.automation.node.model.TemplateValue;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.trackflow.automation.node.model.WorkflowEdgeModel;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作流的唯一结构契约校验器。
 *
 * <p>节点定义由后端注册表提供；保存的 inputs/outputs 只是该定义在画布上的快照，
 * 因而必须与注册表完全一致。这样错误的端口名、陈旧模板和不存在的节点不会流入执行器。</p>
 */
@Component
public class WorkflowDefinitionValidator {
    private static final Set<String> VALUE_TYPES = Set.of(
            "string", "number", "boolean", "object", "array", "any");

    private final DAGBuilder dagBuilder;
    private final NodeRegistry nodeRegistry;

    public WorkflowDefinitionValidator(DAGBuilder dagBuilder, NodeRegistry nodeRegistry) {
        this.dagBuilder = dagBuilder;
        this.nodeRegistry = nodeRegistry;
    }

    /** 用于草稿保存：保证 JSON、节点和端口完整，但允许暂未配置的必填输入。 */
    public void validateDraft(WorkflowDefinitionModel definition) {
        validateStructure(definition, false);
    }

    /** 用于发布和执行：必须是可达、可结束、必填输入已配置的完整工作流。 */
    public void validateExecutable(WorkflowDefinitionModel definition) {
        validateStructure(definition, true);
    }

    /**
     * 用于单节点试运行。节点调试不能被同一草稿中无关的旧边或未完成节点阻断，
     * 但节点自身的类型、端口快照仍必须严格匹配正式注册表。
     */
    public void validateNodeForTest(WorkflowNodeModel node) {
        if (node == null || node.id() == null || node.id().isBlank()) {
            throw invalid("试运行节点不能为空");
        }
        NodeDefinition nodeDefinition = nodeRegistry.getDefinition(node.type());
        if (nodeDefinition == null || nodeRegistry.getExecutor(node.type()) == null) {
            throw invalid("节点没有可用实现: " + node.type() + " (" + node.id() + ")");
        }
        validateNodeSnapshot(node, nodeDefinition, false);
        validateNodeConfiguration(node, nodeDefinition, true);
    }

    private void validateStructure(WorkflowDefinitionModel definition, boolean executable) {
        if (definition == null) {
            throw invalid("工作流定义不能为空");
        }
        List<WorkflowNodeModel> nodes = definition.nodes();
        List<WorkflowEdgeModel> edges = definition.edges();
        if (!executable && nodes != null && nodes.isEmpty() && edges != null && edges.isEmpty()) {
            return;
        }
        dagBuilder.topologicalSort(nodes, edges);

        Map<String, WorkflowNodeModel> nodeById = nodes.stream()
                .collect(Collectors.toMap(WorkflowNodeModel::id, Function.identity()));
        Map<String, NodeDefinition> definitionByNodeId = new HashMap<>();
        for (WorkflowNodeModel node : nodes) {
            NodeDefinition nodeDefinition = nodeRegistry.getDefinition(node.type());
            if (nodeDefinition == null || nodeRegistry.getExecutor(node.type()) == null) {
                throw invalid("节点没有可用实现: " + node.type() + " (" + node.id() + ")");
            }
            definitionByNodeId.put(node.id(), nodeDefinition);
            validateNodeSnapshot(node, nodeDefinition, executable);
        }

        validateEdges(edges, nodeById, definitionByNodeId);
        if (executable) {
            validateExecutionTopology(nodes, edges);
        }
    }

    private void validateNodeSnapshot(WorkflowNodeModel node, NodeDefinition expected, boolean executable) {
        if (node.inputs() == null || node.outputs() == null) {
            throw invalid("节点缺少端口快照: " + node.id());
        }
        Map<String, InputPortDef> expectedInputs = byInputName(expected.getInputPorts());
        Map<String, InputParameter> actualInputs = byParameterName(node.inputs(), node.id());
        if (!expectedInputs.keySet().equals(actualInputs.keySet())) {
            throw invalid("节点输入端口与定义不一致: " + node.id());
        }
        for (InputPortDef expectedInput : expected.getInputPorts()) {
            InputParameter actual = actualInputs.get(expectedInput.name());
            if (!expectedInput.valueType().equals(actual.valueType())
                    || expectedInput.required() != actual.required()
                    || expectedInput.optional() != actual.optional()
                    || expectedInput.cardinality() != actual.cardinality()
                    || !java.util.Objects.equals(expectedInput.semanticType(), actual.semanticType())) {
                throw invalid("节点输入端口契约不一致: " + node.id() + "." + expectedInput.name());
            }
            if (!VALUE_TYPES.contains(actual.valueType())) {
                throw invalid("节点输入端口类型不支持: " + node.id() + "." + actual.name());
            }
            if (actual.value() != null && !expectedInput.bindingModes().contains(bindingMode(actual.value()))) {
                throw invalid("节点输入不允许该值来源: " + node.id() + "." + actual.name());
            }
            if (executable && expectedInput.required() && actual.value() == null) {
                throw invalid("缺少必填输入: " + node.id() + "." + actual.name());
            }
        }

        Map<String, OutputPortDef> expectedOutputs = byOutputName(expected.getOutputPorts());
        Map<String, OutputPortDef> actualOutputs = byOutputName(node.outputs());
        if (!expectedOutputs.keySet().equals(actualOutputs.keySet())) {
            throw invalid("节点输出端口与定义不一致: " + node.id());
        }
        for (OutputPortDef expectedOutput : expected.getOutputPorts()) {
            OutputPortDef actual = actualOutputs.get(expectedOutput.name());
            if (!expectedOutput.valueType().equals(actual.valueType())
                    || expectedOutput.cardinality() != actual.cardinality()
                    || !java.util.Objects.equals(expectedOutput.semanticType(), actual.semanticType())) {
                throw invalid("节点输出端口契约不一致: " + node.id() + "." + expectedOutput.name());
            }
            if (!VALUE_TYPES.contains(actual.valueType())) {
                throw invalid("节点输出端口类型不支持: " + node.id() + "." + actual.name());
            }
        }
        validateNodeConfiguration(node, expected, executable);
    }

    private void validateNodeConfiguration(WorkflowNodeModel node, NodeDefinition definition,
                                           boolean executable) {
        try {
            definition.validateConfiguration(node, executable);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
    }

    private void validateEdges(List<WorkflowEdgeModel> edges,
                               Map<String, WorkflowNodeModel> nodeById,
                               Map<String, NodeDefinition> definitionByNodeId) {
        for (WorkflowEdgeModel edge : edges) {
            NodeDefinition source = definitionByNodeId.get(edge.sourceNodeId());
            NodeDefinition target = definitionByNodeId.get(edge.targetNodeId());
            OutputPortDef sourcePort = byOutputName(source.getOutputPorts()).get(edge.sourcePortName());
            InputPortDef targetPort = byInputName(target.getInputPorts()).get(edge.targetPortName());
            if (sourcePort == null) {
                throw invalid("连线来源端口不存在: " + edge.id() + " -> " + edge.sourcePortName());
            }
            if (targetPort == null) {
                throw invalid("连线目标端口不存在: " + edge.id() + " -> " + edge.targetPortName());
            }
            if (sourcePort.cardinality() != targetPort.cardinality()) {
                throw invalid("集合与单条数据不能直接连线，需配置批处理: "
                        + edge.sourceNodeId() + "." + edge.sourcePortName() + " → "
                        + edge.targetNodeId() + "." + edge.targetPortName());
            }
            if (sourcePort.semanticType() != null && targetPort.semanticType() != null
                    && !sourcePort.semanticType().equals(targetPort.semanticType())) {
                throw invalid("端口业务语义不兼容: " + edge.sourceNodeId() + "." + edge.sourcePortName()
                        + " → " + edge.targetNodeId() + "." + edge.targetPortName());
            }
        }

        for (WorkflowNodeModel node : nodeById.values()) {
            for (InputParameter input : node.inputs()) {
                validateInputValue(node, input, nodeById, definitionByNodeId);
            }
        }
    }

    private void validateInputValue(WorkflowNodeModel node, InputParameter input,
                                    Map<String, WorkflowNodeModel> nodeById,
                                    Map<String, NodeDefinition> definitionByNodeId) {
        InputValue value = input.value();
        if (!(value instanceof VariableRef ref)) return;
        if (!"ref".equals(ref.type()) || ref.nodeId() == null || ref.outputName() == null) {
            throw invalid("变量引用格式错误: " + node.id() + "." + input.name());
        }
        WorkflowNodeModel sourceNode = nodeById.get(ref.nodeId());
        if (sourceNode == null) {
            throw invalid("变量引用了不存在的节点: " + ref.nodeId());
        }
        OutputPortDef sourcePort = byOutputName(definitionByNodeId.get(sourceNode.id()).getOutputPorts())
                .get(ref.outputName());
        if (sourcePort == null) {
            throw invalid("变量引用了不存在的输出端口: " + ref.nodeId() + "." + ref.outputName());
        }
        boolean explicitPath = ref.path() != null && !ref.path().isBlank();
        if (!explicitPath && !isCompatible(sourcePort.valueType(), input.valueType())) {
            throw invalid("变量类型不兼容: " + ref.nodeId() + "." + ref.outputName()
                    + " 不能赋给 " + node.id() + "." + input.name());
        }
        if (!explicitPath && sourcePort.cardinality() != input.cardinality()) {
            throw invalid("集合与单条数据不能直接引用，需配置批处理: "
                    + ref.nodeId() + "." + ref.outputName() + " → " + node.id() + "." + input.name());
        }
        if (explicitPath && !("object".equals(sourcePort.valueType())
                || "array".equals(sourcePort.valueType()) || "any".equals(sourcePort.valueType()))) {
            throw invalid("只有 object、array 或 any 输出可以使用 path: "
                    + ref.nodeId() + "." + ref.outputName());
        }
    }

    private InputBindingMode bindingMode(InputValue value) {
        if (value instanceof LiteralValue) return InputBindingMode.literal;
        if (value instanceof VariableRef) return InputBindingMode.reference;
        if (value instanceof TemplateValue) return InputBindingMode.template;
        throw invalid("不支持的输入值类型");
    }

    private void validateExecutionTopology(List<WorkflowNodeModel> nodes, List<WorkflowEdgeModel> edges) {
        long starts = nodes.stream().filter(node -> "start".equals(node.type())).count();
        if (starts != 1) throw invalid("工作流必须且只能包含一个开始节点");
        if (nodes.stream().noneMatch(node -> "end".equals(node.type()))) {
            throw invalid("工作流至少需要一个结束节点");
        }
        Set<String> reachable = traverse(nodes.stream().filter(node -> "start".equals(node.type()))
                .findFirst().orElseThrow().id(), edges, true);
        Set<String> canReachEnd = new HashSet<>();
        for (WorkflowNodeModel end : nodes.stream().filter(node -> "end".equals(node.type())).toList()) {
            canReachEnd.addAll(traverse(end.id(), edges, false));
        }
        for (WorkflowNodeModel node : nodes) {
            if (!reachable.contains(node.id())) throw invalid("节点无法从开始节点到达: " + node.id());
            if (!canReachEnd.contains(node.id())) throw invalid("节点无法到达结束节点: " + node.id());
        }
    }

    private Set<String> traverse(String root, List<WorkflowEdgeModel> edges, boolean forward) {
        Map<String, List<String>> graph = new HashMap<>();
        for (WorkflowEdgeModel edge : edges) {
            String from = forward ? edge.sourceNodeId() : edge.targetNodeId();
            String to = forward ? edge.targetNodeId() : edge.sourceNodeId();
            graph.computeIfAbsent(from, ignored -> new java.util.ArrayList<>()).add(to);
        }
        Set<String> visited = new HashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (visited.add(current)) queue.addAll(graph.getOrDefault(current, List.of()));
        }
        return visited;
    }

    private boolean isCompatible(String source, String target) {
        return "any".equals(source) || "any".equals(target) || source.equals(target);
    }

    private Map<String, InputPortDef> byInputName(Collection<InputPortDef> ports) {
        return ports.stream().collect(Collectors.toMap(InputPortDef::name, Function.identity(),
                (left, right) -> { throw invalid("节点定义存在重复输入端口: " + left.name()); }));
    }

    private Map<String, InputParameter> byParameterName(Collection<InputParameter> ports, String nodeId) {
        return ports.stream().collect(Collectors.toMap(InputParameter::name, Function.identity(),
                (left, right) -> { throw invalid("节点输入端口重复: " + nodeId + "." + left.name()); }));
    }

    private Map<String, OutputPortDef> byOutputName(Collection<OutputPortDef> ports) {
        return ports.stream().collect(Collectors.toMap(OutputPortDef::name, Function.identity(),
                (left, right) -> { throw invalid("节点定义存在重复输出端口: " + left.name()); }));
    }

    private DAGBuilder.InvalidWorkflowException invalid(String message) {
        return new DAGBuilder.InvalidWorkflowException(message);
    }
}
