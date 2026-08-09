package com.trackflow.automation.execution.dto;

import com.trackflow.automation.node.model.WorkflowNodeModel;
import lombok.Data;

import java.util.Map;

/** 单节点试运行请求。inputOverrides 只作用于本次试运行，不会写入工作流定义。 */
@Data
public class NodeTestDTO {
    private Map<String, Object> inputOverrides;
    private Boolean confirmSideEffects;

    /**
     * 当前画布中的节点快照。单节点试运行只校验并执行此节点，
     * 不保存、也不校验画布内其他节点和连线。
     */
    private WorkflowNodeModel node;
}
