package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.PortCardinality;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 工单内容聚合节点：将工单对象的多个字段拼装成结构化的 Markdown 摘要文本，
 * 可直接作为 Agent 的 context 参数使用。
 */
@Component
@RequiredArgsConstructor
public class IssueContextNode implements NodeDefinition, NodeExecutor {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override public String getType() { return "trackflow-issue-context"; }
    @Override public String getTitle() { return "准备工单上下文"; }
    @Override public String getIcon() { return "📝"; }
    @Override public String getColor() { return "#8b5cf6"; }
    @Override public String getDescription() { return "将工单信息聚合为结构化 Markdown 文本，供 Agent 读取"; }
    @Override public String getCategory() { return "TrackFlow"; }

    @Override
    public List<InputPortDef> getInputPorts() {
        return List.of(
                new InputPortDef("issue", "工单对象", "object", true,
                        "来自获取需求或批处理当前项的完整工单对象", false,
                        PortCardinality.single, "issue"),
                new InputPortDef("includeComments", "boolean", false, "是否包含评论（默认 true）"),
                new InputPortDef("includeCustomFields", "boolean", false, "是否包含自定义字段（默认 true）"),
                new InputPortDef("maxLength", "number", false, "最大字符数（默认 4000）")
        );
    }

    @Override
    public List<OutputPortDef> getOutputPorts() {
        return List.of(
                new OutputPortDef("context", "string", "Markdown 格式的工单摘要，可直接作为 Agent context"),
                new OutputPortDef("summary", "string", "一行简短摘要：[priority] issueKey title")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node,
                                       ExecutionContext context) throws NodeExecutionException {
        Object issueObj = inputs.get("issue");
        if (issueObj == null) {
            throw new NodeExecutionException(node.id(), "issue 输入不能为空");
        }
        if (!(issueObj instanceof Map<?, ?>)) {
            throw new NodeExecutionException(node.id(), "issue 输入必须是对象类型");
        }
        Map<String, Object> issue = (Map<String, Object>) issueObj;

        boolean includeComments = boolValue(inputs.get("includeComments"), true);
        boolean includeCustomFields = boolValue(inputs.get("includeCustomFields"), true);
        int maxLength = intValue(inputs.get("maxLength"), 4000);

        // 提取基础字段
        String issueKey = str(issue.get("key"));
        String title = str(issue.get("title"));
        String priority = str(issue.get("priority"));
        String issueType = str(issue.get("issueType"));
        String statusName = str(issue.get("statusName"));
        String assigneeName = str(issue.get("assigneeName"));
        String reporterName = str(issue.get("reporterName"));
        String description = str(issue.get("description"));
        String createdAt = formatDateTime(issue.get("createdAt"));

        // 构建 Markdown 上下文
        StringBuilder sb = new StringBuilder();
        sb.append("## 工单 ").append(issueKey != null ? issueKey : "").append("：")
                .append(title != null ? title : "无标题").append("\n\n");
        sb.append("**状态**：").append(statusName != null ? statusName : "未知");
        sb.append("  **优先级**：").append(priority != null ? priority : "未设置");
        sb.append("  **类型**：").append(issueType != null ? issueType : "未设置").append("\n");
        sb.append("**负责人**：").append(assigneeName != null ? assigneeName : "未分配");
        sb.append("  **报告人**：").append(reporterName != null ? reporterName : "未知");
        sb.append("  **创建时间**：").append(createdAt != null ? createdAt : "未知").append("\n");

        // 描述
        if (description != null && !description.isBlank()) {
            sb.append("\n### 描述\n");
            sb.append(description).append("\n");
        }

        // 评论
        if (includeComments) {
            Object commentsObj = issue.get("comments");
            if (commentsObj instanceof List<?> comments && !comments.isEmpty()) {
                int displayCount = Math.min(comments.size(), 10);
                sb.append("\n### 最近评论（").append(displayCount).append("条）\n");
                int startIdx = Math.max(0, comments.size() - displayCount);
                for (int i = startIdx; i < comments.size(); i++) {
                    Object commentItem = comments.get(i);
                    if (commentItem instanceof Map<?, ?> comment) {
                        String author = str(comment.get("authorName"));
                        String content = str(comment.get("content"));
                        String commentDate = formatDateTime(comment.get("createdAt"));
                        sb.append("- ").append(author != null ? author : "匿名");
                        if (commentDate != null) sb.append("（").append(commentDate).append("）");
                        sb.append("：").append(content != null ? truncate(content, 200) : "").append("\n");
                    }
                }
            }
        }

        // 标签
        Object tagsObj = issue.get("tags");
        if (tagsObj instanceof List<?> tags && !tags.isEmpty()) {
            sb.append("\n### 标签\n");
            for (Object tagItem : tags) {
                if (tagItem instanceof Map<?, ?> tag) {
                    sb.append("`").append(str(tag.get("name"))).append("` ");
                }
            }
            sb.append("\n");
        }

        // 自定义字段
        if (includeCustomFields) {
            Object customFieldsObj = issue.get("customFields");
            if (customFieldsObj instanceof Map<?, ?> customFields && !customFields.isEmpty()) {
                sb.append("\n### 自定义字段\n");
                for (Map.Entry<?, ?> entry : customFields.entrySet()) {
                    sb.append("- **").append(entry.getKey()).append("**：")
                            .append(entry.getValue()).append("\n");
                }
            }
        }

        // 截断到最大长度
        String contextText = sb.length() > maxLength
                ? sb.substring(0, maxLength) + "\n\n... (内容已截断)"
                : sb.toString();

        // 构建简短摘要
        String summary = String.format("[%s] %s %s",
                priority != null ? priority : "?",
                issueKey != null ? issueKey : "?",
                title != null ? title : "无标题");

        return Map.of("context", contextText, "summary", summary);
    }

    private boolean boolValue(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private int intValue(Object value, int defaultValue) {
        if (value == null || value.toString().isBlank()) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    private String str(Object value) {
        return value != null ? value.toString() : null;
    }

    private String formatDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt.format(DATE_FMT);
        String s = value.toString();
        try {
            LocalDateTime ldt = LocalDateTime.parse(s);
            return ldt.format(DATE_FMT);
        } catch (Exception e) {
            // 可能是已格式化的字符串，直接取前16位
            return s.length() > 16 ? s.substring(0, 16) : s;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
