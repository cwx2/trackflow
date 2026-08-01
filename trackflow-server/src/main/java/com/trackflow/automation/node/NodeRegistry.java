package com.trackflow.automation.node;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class NodeRegistry {
    private final Map<String, NodeDefinition> definitions = new HashMap<>();
    private final Map<String, NodeExecutor>   executors   = new HashMap<>();

    public NodeRegistry(List<NodeDefinition> defs, List<NodeExecutor> execs) {
        defs.forEach(d  -> definitions.put(d.getType(), d));
        execs.forEach(e -> executors.put(e.getType(), e));
    }

    public NodeDefinition getDefinition(String type)       { return definitions.get(type); }
    public NodeExecutor   getExecutor(String type)         { return executors.get(type); }
    public Collection<NodeDefinition> getAllDefinitions()   { return definitions.values(); }
}
