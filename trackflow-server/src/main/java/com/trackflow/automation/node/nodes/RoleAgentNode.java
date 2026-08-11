package com.trackflow.automation.node.nodes;

import com.trackflow.automation.agent.AgentRuntimeService;
import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.NodeRuntimePolicy;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RoleAgentNode implements NodeDefinition, NodeExecutor {
    private final AgentRuntimeService runtimeService;

    @Override public String getType() { return "role-agent"; }
    @Override public String getTitle() { return "角色 Agent"; }
    @Override public String getIcon() { return "🧠"; }
    @Override public String getColor() { return "#7c3aed"; }
    @Override public String getDescription() { return "以可复用角色、工具白名单和输出协议执行任务"; }
    @Override public String getCategory() { return "Agent"; }
    @Override public NodeRuntimePolicy getRuntimePolicy() { return NodeRuntimePolicy.sideEffect(); }
    @Override public List<InputPortDef> getInputPorts() {
        return List.of(
                new InputPortDef("roleId", "number", true, "Agent 角色 ID", false),
                new InputPortDef("task", "string", true, "本次任务", false),
                new InputPortDef("context", "object", false, "需求、代码或前序结果", true),
                new InputPortDef("workDir", "string", false, "角色允许范围内的工作目录", true)
        );
    }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(
                new OutputPortDef("output", "string", "Agent 原始输出"),
                new OutputPortDef("structuredOutput", "object", "按角色 Schema 校验后的输出"),
                new OutputPortDef("success", "boolean", "成功分支")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node,
                                       ExecutionContext context) throws NodeExecutionException {
        try {
            Object roleValue = inputs.get("roleId");
            if (roleValue == null) throw new IllegalArgumentException("roleId 不能为空");
            Long roleId = roleValue instanceof Number number
                    ? number.longValue() : Long.valueOf(roleValue.toString());
            return runtimeService.execute(roleId,
                    inputs.get("task") != null ? inputs.get("task").toString() : null,
                    inputs.get("context"),
                    inputs.get("workDir") != null ? inputs.get("workDir").toString() : null,
                    node.id(), context);
        } catch (RuntimeException exception) {
            throw new NodeExecutionException(node.id(), exception.getMessage(), exception);
        }
    }
}
