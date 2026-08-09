package com.trackflow.automation.execution.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NodeTestResultVO {
    private String nodeId;
    /** success / failed / simulated */
    private String status;
    private Map<String, Object> input;
    private Map<String, Object> output;
    private String error;
    private String message;
    private Long durationMs;
}
