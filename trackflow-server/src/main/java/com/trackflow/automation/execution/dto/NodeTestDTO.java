package com.trackflow.automation.execution.dto;

import lombok.Data;

import java.util.Map;

/** 单节点试运行请求。inputOverrides 只作用于本次试运行，不会写入工作流定义。 */
@Data
public class NodeTestDTO {
    private Map<String, Object> inputOverrides;
    private Boolean confirmSideEffects;
}
