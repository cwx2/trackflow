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

/**
 * 变量设置节点 — 定义并输出变量
 *
 * 支持两种模式（向后兼容）：
 * 1. 旧模式：config.vars 是一个 JSON 对象，整体输出为 "vars" 端口
 * 2. 新模式：node.outputs 中有多个自定义端口，每个变量独立输出
 *    config.varValues 存储 { portName: value } 的映射
 */
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

        // 新模式：如果有 varValues，按独立端口输出
        Object varValuesObj = config.get("varValues");
        if (varValuesObj != null) {
            Map<String, Object> varValues = parseMap(varValuesObj, "varValues");
            // 同时保留旧 "vars" 端口输出（向后兼容，引用 variables_node.vars.xxx 仍然可用）
            Map<String, Object> result = new HashMap<>(varValues);
            result.put("vars", varValues);
            return result;
        }

        // 旧模式：从 config.vars 读取整体对象
        Object varsObj = config.get("vars");
        Map<String, Object> vars = parseMap(varsObj, "vars");
        return Map.of("vars", vars);
    }

    private Map<String, Object> parseMap(Object obj, String fieldName) throws NodeExecutionException {
        try {
            if (obj instanceof String json && !json.isBlank()) {
                return objectMapper.readValue(json, new TypeReference<>() {});
            } else if (obj instanceof Map<?,?> m) {
                Map<String, Object> tmp = new HashMap<>();
                m.forEach((k, v) -> tmp.put(String.valueOf(k), v));
                return tmp;
            } else {
                return new HashMap<>();
            }
        } catch (Exception e) {
            throw new NodeExecutionException(fieldName, fieldName + " 格式错误: " + e.getMessage());
        }
    }
}
