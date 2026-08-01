package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import com.trackflow.automation.service.AutomationIssueFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IssueCommentNode implements NodeDefinition, NodeExecutor {
    private final AutomationIssueFacade issueFacade;

    @Override public String getType() { return "trackflow-issue-comment"; }
    @Override public String getTitle() { return "记录处理结果"; }
    @Override public String getIcon() { return "💬"; }
    @Override public String getColor() { return "#14b8a6"; }
    @Override public String getDescription() { return "向需求添加 Agent 的计划、进度或测试结果"; }
    @Override public String getCategory() { return "TrackFlow"; }
    @Override public List<InputPortDef> getInputPorts() {
        return List.of(
                new InputPortDef("issueId", "number", true, "工单 ID"),
                new InputPortDef("content", "string", true, "评论内容")
        );
    }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(new OutputPortDef("comment", "object", "创建的评论"));
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node,
                                       ExecutionContext context) throws NodeExecutionException {
        try {
            Object issueValue = inputs.get("issueId");
            if (issueValue == null) throw new IllegalArgumentException("issueId 不能为空");
            Long issueId = issueValue instanceof Number number
                    ? number.longValue() : Long.valueOf(issueValue.toString());
            String content = inputs.get("content") != null ? inputs.get("content").toString() : null;
            return Map.of("comment", issueFacade.comment(context.getActorUserId(), issueId, content));
        } catch (RuntimeException exception) {
            throw new NodeExecutionException(node.id(), exception.getMessage(), exception);
        }
    }
}
