package com.trackflow.automation.node;

public class NodeExecutionException extends Exception {
    private final String nodeId;
    private final boolean fatal;

    public NodeExecutionException(String nodeId, String message) {
        super(message); this.nodeId = nodeId; this.fatal = true;
    }
    public NodeExecutionException(String nodeId, String message, boolean fatal) {
        super(message); this.nodeId = nodeId; this.fatal = fatal;
    }
    public NodeExecutionException(String nodeId, String message, Throwable cause) {
        super(message, cause); this.nodeId = nodeId; this.fatal = true;
    }
    public String getNodeId() { return nodeId; }
    public boolean isFatal()  { return fatal; }
}
