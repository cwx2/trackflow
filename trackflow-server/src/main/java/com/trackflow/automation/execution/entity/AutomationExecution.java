package com.trackflow.automation.execution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.List;

@TableName("automation_execution")
public class AutomationExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long automationId;
    private String status;
    private String input;
    private String output;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private Long createdBy;
    private transient List<AutomationNodeExecution> nodeExecutions;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAutomationId() { return automationId; }
    public void setAutomationId(Long automationId) { this.automationId = automationId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public List<AutomationNodeExecution> getNodeExecutions() { return nodeExecutions; }
    public void setNodeExecutions(List<AutomationNodeExecution> nodeExecutions) { this.nodeExecutions = nodeExecutions; }
}
