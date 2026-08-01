package com.trackflow.automation.execution;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 可序列化的工作流执行检查点。
 */
public class WorkflowExecutionCheckpoint {
    private Map<String, Map<String, Object>> nodeOutputs = new LinkedHashMap<>();
    private Map<String, String> edgeStates = new LinkedHashMap<>();
    private Set<String> completedNodeIds = new LinkedHashSet<>();
    private Set<String> skippedNodeIds = new LinkedHashSet<>();
    private Map<String, Integer> nodeAttempts = new LinkedHashMap<>();
    private String waitingNodeId;
    private Long waitingNodeExecutionId;
    private String waitingKind;
    private Map<String, Object> waitingResult = new LinkedHashMap<>();
    private Map<String, Object> waitingMetadata = new LinkedHashMap<>();
    private LocalDateTime waitingUntil;

    public Map<String, Map<String, Object>> getNodeOutputs() { return nodeOutputs; }
    public void setNodeOutputs(Map<String, Map<String, Object>> nodeOutputs) {
        this.nodeOutputs = nodeOutputs != null ? nodeOutputs : new LinkedHashMap<>();
    }
    public Map<String, String> getEdgeStates() { return edgeStates; }
    public void setEdgeStates(Map<String, String> edgeStates) {
        this.edgeStates = edgeStates != null ? edgeStates : new LinkedHashMap<>();
    }
    public Set<String> getCompletedNodeIds() { return completedNodeIds; }
    public void setCompletedNodeIds(Set<String> completedNodeIds) {
        this.completedNodeIds = completedNodeIds != null ? completedNodeIds : new LinkedHashSet<>();
    }
    public Set<String> getSkippedNodeIds() { return skippedNodeIds; }
    public void setSkippedNodeIds(Set<String> skippedNodeIds) {
        this.skippedNodeIds = skippedNodeIds != null ? skippedNodeIds : new LinkedHashSet<>();
    }
    public Map<String, Integer> getNodeAttempts() { return nodeAttempts; }
    public void setNodeAttempts(Map<String, Integer> nodeAttempts) {
        this.nodeAttempts = nodeAttempts != null ? nodeAttempts : new LinkedHashMap<>();
    }
    public String getWaitingNodeId() { return waitingNodeId; }
    public void setWaitingNodeId(String waitingNodeId) { this.waitingNodeId = waitingNodeId; }
    public Long getWaitingNodeExecutionId() { return waitingNodeExecutionId; }
    public void setWaitingNodeExecutionId(Long waitingNodeExecutionId) {
        this.waitingNodeExecutionId = waitingNodeExecutionId;
    }
    public String getWaitingKind() { return waitingKind; }
    public void setWaitingKind(String waitingKind) { this.waitingKind = waitingKind; }
    public Map<String, Object> getWaitingResult() { return waitingResult; }
    public void setWaitingResult(Map<String, Object> waitingResult) {
        this.waitingResult = waitingResult != null ? waitingResult : new LinkedHashMap<>();
    }
    public Map<String, Object> getWaitingMetadata() { return waitingMetadata; }
    public void setWaitingMetadata(Map<String, Object> waitingMetadata) {
        this.waitingMetadata = waitingMetadata != null ? waitingMetadata : new LinkedHashMap<>();
    }
    public LocalDateTime getWaitingUntil() { return waitingUntil; }
    public void setWaitingUntil(LocalDateTime waitingUntil) { this.waitingUntil = waitingUntil; }
}
