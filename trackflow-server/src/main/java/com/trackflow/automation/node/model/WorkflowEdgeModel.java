package com.trackflow.automation.node.model;

public record WorkflowEdgeModel(
    String id,
    String sourceNodeId,
    String sourcePortName,
    String targetNodeId,
    String targetPortName,
    CollectionBindingMode collectionBindingMode
) {
    /**
     * Kept for persisted definitions and tests written before collection-to-item
     * bindings became an explicit execution contract.
     */
    public WorkflowEdgeModel(String id, String sourceNodeId, String sourcePortName,
                             String targetNodeId, String targetPortName) {
        this(id, sourceNodeId, sourcePortName, targetNodeId, targetPortName,
                CollectionBindingMode.direct);
    }

    public WorkflowEdgeModel {
        collectionBindingMode = collectionBindingMode == null
                ? CollectionBindingMode.direct : collectionBindingMode;
    }
}
