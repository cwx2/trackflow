package com.trackflow.automation.execution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("automation_node_execution")
public class AutomationNodeExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long executionId;
    private String nodeId;
    private String nodeType;
    private String nodeName;
    private String status;
    private String input;
    private String output;
    private String errorInfo;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public String getErrorInfo() { return errorInfo; }
    public void setErrorInfo(String errorInfo) { this.errorInfo = errorInfo; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}
