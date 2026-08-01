package com.trackflow.automation.node;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.model.WorkflowNodeModel;

import java.util.Map;

public interface NodeExecutor {
    String getType();
    Map<String, Object> execute(
        Map<String, Object> inputs,
        WorkflowNodeModel node,
        ExecutionContext ctx
    ) throws NodeExecutionException;
}
