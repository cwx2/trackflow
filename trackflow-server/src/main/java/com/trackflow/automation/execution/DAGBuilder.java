package com.trackflow.automation.execution;

import com.trackflow.automation.node.model.WorkflowEdgeModel;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DAGBuilder {

    public static class CyclicWorkflowException extends RuntimeException {
        public CyclicWorkflowException() { super("工作流存在循环依赖，请检查连线"); }
    }

    public static class InvalidWorkflowException extends RuntimeException {
        public InvalidWorkflowException(String message) { super(message); }
    }

    public List<WorkflowNodeModel> topologicalSort(
            List<WorkflowNodeModel> nodes,
            List<WorkflowEdgeModel> edges) {

        validate(nodes, edges);

        Map<String, Integer>        inDegree = new HashMap<>();
        Map<String, List<String>>   graph    = new HashMap<>();
        Map<String, WorkflowNodeModel> nodeMap = new HashMap<>();

        for (WorkflowNodeModel n : nodes) {
            inDegree.put(n.id(), 0);
            graph.put(n.id(), new ArrayList<>());
            nodeMap.put(n.id(), n);
        }
        for (WorkflowEdgeModel e : edges) {
            graph.computeIfAbsent(e.sourceNodeId(), k -> new ArrayList<>()).add(e.targetNodeId());
            inDegree.merge(e.targetNodeId(), 1, Integer::sum);
        }

        Queue<String> queue = new ArrayDeque<>();
        inDegree.forEach((id, deg) -> { if (deg == 0) queue.offer(id); });

        List<WorkflowNodeModel> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            WorkflowNodeModel node = nodeMap.get(id);
            if (node != null) sorted.add(node);
            for (String next : graph.getOrDefault(id, List.of())) {
                int newDeg = inDegree.merge(next, -1, Integer::sum);
                if (newDeg == 0) queue.offer(next);
            }
        }

        if (sorted.size() != nodes.size()) throw new CyclicWorkflowException();
        return sorted;
    }

    public Map<String, List<WorkflowEdgeModel>> groupIncomingEdges(
            List<WorkflowNodeModel> nodes,
            List<WorkflowEdgeModel> edges) {
        Map<String, List<WorkflowEdgeModel>> result = initializeEdgeGroups(nodes);
        for (WorkflowEdgeModel edge : edges) {
            result.get(edge.targetNodeId()).add(edge);
        }
        return result;
    }

    public Map<String, List<WorkflowEdgeModel>> groupOutgoingEdges(
            List<WorkflowNodeModel> nodes,
            List<WorkflowEdgeModel> edges) {
        Map<String, List<WorkflowEdgeModel>> result = initializeEdgeGroups(nodes);
        for (WorkflowEdgeModel edge : edges) {
            result.get(edge.sourceNodeId()).add(edge);
        }
        return result;
    }

    private Map<String, List<WorkflowEdgeModel>> initializeEdgeGroups(List<WorkflowNodeModel> nodes) {
        Map<String, List<WorkflowEdgeModel>> result = new LinkedHashMap<>();
        for (WorkflowNodeModel node : nodes) {
            result.put(node.id(), new ArrayList<>());
        }
        return result;
    }

    private void validate(List<WorkflowNodeModel> nodes, List<WorkflowEdgeModel> edges) {
        if (nodes == null || nodes.isEmpty()) {
            throw new InvalidWorkflowException("工作流至少需要一个节点");
        }
        if (edges == null) {
            throw new InvalidWorkflowException("工作流连线列表不能为空");
        }

        Set<String> nodeIds = new HashSet<>();
        for (WorkflowNodeModel node : nodes) {
            if (node == null || node.id() == null || node.id().isBlank()) {
                throw new InvalidWorkflowException("工作流包含缺少 ID 的节点");
            }
            if (!nodeIds.add(node.id())) {
                throw new InvalidWorkflowException("工作流包含重复节点 ID: " + node.id());
            }
        }

        Set<String> edgeIds = new HashSet<>();
        for (WorkflowEdgeModel edge : edges) {
            validateEdge(edge, nodeIds, edgeIds);
        }
    }

    private void validateEdge(WorkflowEdgeModel edge, Set<String> nodeIds, Set<String> edgeIds) {
        if (edge == null || edge.id() == null || edge.id().isBlank()) {
            throw new InvalidWorkflowException("工作流包含缺少 ID 的连线");
        }
        if (!edgeIds.add(edge.id())) {
            throw new InvalidWorkflowException("工作流包含重复连线 ID: " + edge.id());
        }
        if (!nodeIds.contains(edge.sourceNodeId()) || !nodeIds.contains(edge.targetNodeId())) {
            throw new InvalidWorkflowException("连线 " + edge.id() + " 引用了不存在的节点");
        }
        if (Objects.equals(edge.sourceNodeId(), edge.targetNodeId())) {
            throw new InvalidWorkflowException("节点不能连接到自身: " + edge.sourceNodeId());
        }
        if (edge.sourcePortName() == null || edge.sourcePortName().isBlank()) {
            throw new InvalidWorkflowException("连线 " + edge.id() + " 缺少来源端口");
        }
        if (edge.targetPortName() == null || edge.targetPortName().isBlank()) {
            throw new InvalidWorkflowException("连线 " + edge.id() + " 缺少目标端口");
        }
    }
}
