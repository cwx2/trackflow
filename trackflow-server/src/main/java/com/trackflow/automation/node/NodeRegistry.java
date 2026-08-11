package com.trackflow.automation.node;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.trackflow.automation.node.model.NodeContract;

@Component
public class NodeRegistry {
    private final Map<String, NodeDefinition> definitions = new HashMap<>();
    private final Map<String, NodeExecutor>   executors   = new HashMap<>();

    public NodeRegistry(List<NodeDefinition> defs, List<NodeExecutor> execs) {
        defs.forEach(d  -> register(definitions, d.getType(), d, "定义"));
        execs.forEach(e -> register(executors, e.getType(), e, "执行器"));
        verifyExecutableCatalog();
    }

    private <T> void register(Map<String, T> target, String type, T value, String kind) {
        if (type == null || type.isBlank()) throw new IllegalStateException("自动化节点" + kind + "类型不能为空");
        if (target.putIfAbsent(type, value) != null) {
            throw new IllegalStateException("自动化节点" + kind + "重复注册: " + type);
        }
    }

    /**
     * 节点库的定义和执行器必须一一对应；否则画布能添加的节点必然会在运行时失败。
     * 在应用启动阶段直接失败，比让用户在试运行时才发现问题更安全。
     */
    private void verifyExecutableCatalog() {
        Set<String> missingExecutors = new java.util.TreeSet<>(definitions.keySet());
        missingExecutors.removeAll(executors.keySet());
        Set<String> missingDefinitions = new java.util.TreeSet<>(executors.keySet());
        missingDefinitions.removeAll(definitions.keySet());
        if (!missingExecutors.isEmpty() || !missingDefinitions.isEmpty()) {
            throw new IllegalStateException("自动化节点注册不完整: 缺少执行器=" + missingExecutors
                    + ", 缺少定义=" + missingDefinitions);
        }
    }

    public NodeDefinition getDefinition(String type)       { return definitions.get(type); }
    public NodeExecutor   getExecutor(String type)         { return executors.get(type); }
    public NodeContract getContract(String type) {
        NodeDefinition definition = getDefinition(type);
        return definition == null ? null : definition.getContract();
    }
    public Collection<NodeDefinition> getAllDefinitions()   {
        return definitions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue).toList();
    }
    public Collection<NodeContract> getAllContracts() {
        return getAllDefinitions().stream().map(NodeDefinition::getContract).toList();
    }
}
