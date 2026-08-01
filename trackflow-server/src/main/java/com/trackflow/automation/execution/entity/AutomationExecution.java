package com.trackflow.automation.execution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.trackflow.common.handler.JsonbTypeHandler;

import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "automation_execution", autoResultMap = true)
public class AutomationExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long automationId;
    private String status;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String input;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String output;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private Long createdBy;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String checkpoint;
    private LocalDateTime heartbeatAt;
    private LocalDateTime wakeUpAt;
    private Long actorUserId;
    private String correlationId;
    private Integer definitionVersion;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String definitionSnapshot;
    private Long parentExecutionId;
    private String parentNodeId;
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
    public String getCheckpoint() { return checkpoint; }
    public void setCheckpoint(String checkpoint) { this.checkpoint = checkpoint; }
    public LocalDateTime getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(LocalDateTime heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public LocalDateTime getWakeUpAt() { return wakeUpAt; }
    public void setWakeUpAt(LocalDateTime wakeUpAt) { this.wakeUpAt = wakeUpAt; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public Integer getDefinitionVersion() { return definitionVersion; }
    public void setDefinitionVersion(Integer definitionVersion) { this.definitionVersion = definitionVersion; }
    public String getDefinitionSnapshot() { return definitionSnapshot; }
    public void setDefinitionSnapshot(String definitionSnapshot) { this.definitionSnapshot = definitionSnapshot; }
    public Long getParentExecutionId() { return parentExecutionId; }
    public void setParentExecutionId(Long parentExecutionId) { this.parentExecutionId = parentExecutionId; }
    public String getParentNodeId() { return parentNodeId; }
    public void setParentNodeId(String parentNodeId) { this.parentNodeId = parentNodeId; }
    public List<AutomationNodeExecution> getNodeExecutions() { return nodeExecutions; }
    public void setNodeExecutions(List<AutomationNodeExecution> nodeExecutions) { this.nodeExecutions = nodeExecutions; }
}
