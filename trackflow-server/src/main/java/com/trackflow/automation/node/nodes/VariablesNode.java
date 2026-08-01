package com.trackflow.automation.node.nodes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class VariablesNode implements NodeDefinition, NodeExecutor {

    private final ObjectMapper objectMapper;

    public VariablesNode(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override public String getType()        { return "variables"; }
    @Override public String getTitle()       { return "变量设置"; }
    @Override public String getIcon()        { return "📝"; }
    @Override public String getColor()       { return "#0ea5e9"; }
    @Override public String getDescription() { return "定义并输出变量"; }
    @Override public List<InputPortDef>  getInputPorts()  { return List.of(); }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(new OutputPortDef("vars", "object", "所有已定义变量的集合"));
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node, ExecutionContext ctx)
            throws NodeExecutionException {
        Map<String, Object> config = node.config() != null ? node.config() : Map.of();
        Object varsObj = config.get("vars");
        Map<String, Object> vars;
        try {
            if (varsObj instanceof String json && !json.isBlank()) {
                vars = objectMapper.readValue(json, new TypeReference<>() {});
            } else if (varsObj instanceof Map<?,?> m) {
                Map<String, Object> tmp = new HashMap<>();
                m.forEach((k, v) -> tmp.put(String.valueOf(k), v));
                vars = tmp;
            } else {
                vars = new HashMap<>();
            }
        } catch (Exception e) {
            throw new NodeExecutionException(node.id(), "变量配置格式错误: " + e.getMessage());
        }
        return Map.of("vars", vars);
    }
}
