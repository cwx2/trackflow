package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DelayNode implements NodeDefinition, NodeExecutor {
    @Override public String getType()        { return "delay"; }
    @Override public String getTitle()       { return "延时等待"; }
    @Override public String getIcon()        { return "⏱"; }
    @Override public String getColor()       { return "#64748b"; }
    @Override public String getDescription() { return "暂停指定时长后继续"; }
    @Override public String getCategory()    { return "控制流"; }
    @Override public List<InputPortDef>  getInputPorts()  {
        return List.of(new InputPortDef("duration", "number", false, "等待秒数（覆盖配置项）"));
    }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(new OutputPortDef("done", "boolean", "等待完成，值恒为 true"));
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node, ExecutionContext ctx)
            throws NodeExecutionException {
        Map<String, Object> config = node.config() != null ? node.config() : Map.of();
        int seconds = 5;
        Object durationInput = inputs.get("duration");
        if (durationInput != null) {
            seconds = ((Number) durationInput).intValue();
        } else {
            Object configSec = config.get("seconds");
            if (configSec != null) seconds = ((Number) configSec).intValue();
        }
        seconds = Math.max(0, Math.min(seconds, 3600));
        try {
            Thread.sleep((long) seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NodeExecutionException(node.id(), "延时被中断");
        }
        return Map.of("done", true);
    }
}
