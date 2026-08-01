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

    public List<WorkflowNodeModel> topologicalSort(
            List<WorkflowNodeModel> nodes,
            List<WorkflowEdgeModel> edges) {

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
}
