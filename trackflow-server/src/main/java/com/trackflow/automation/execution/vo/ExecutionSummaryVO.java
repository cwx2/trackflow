package com.trackflow.automation.execution.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ExecutionSummaryVO {
    private String id;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
}
