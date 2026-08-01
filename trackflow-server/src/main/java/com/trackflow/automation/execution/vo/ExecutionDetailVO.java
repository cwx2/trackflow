package com.trackflow.automation.execution.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExecutionDetailVO {
    private String id;
    private String automationId;
    private String status;
    private Object input;
    private Object output;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private List<NodeExecutionVO> nodeExecutions;
}
