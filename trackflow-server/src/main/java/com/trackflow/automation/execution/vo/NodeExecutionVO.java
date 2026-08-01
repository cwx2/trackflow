package com.trackflow.automation.execution.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NodeExecutionVO {
    private String id;
    private String nodeId;
    private String nodeType;
    private String nodeName;
    private String status;
    private Integer attempt;
    private String outcome;
    private Object input;
    private Object output;
    private String errorInfo;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
