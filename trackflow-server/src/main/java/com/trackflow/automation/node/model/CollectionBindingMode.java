package com.trackflow.automation.node.model;

/**
 * Describes how a collection output binds to a downstream input.
 *
 * <p>{@link #each} is the explicit collection-to-single boundary: the runtime
 * creates one item scope for every element and carries that scope through the
 * downstream data path. This is deliberately an edge concern, rather than a
 * hidden dependency on a child workflow.</p>
 */
public enum CollectionBindingMode {
    direct,
    each
}
