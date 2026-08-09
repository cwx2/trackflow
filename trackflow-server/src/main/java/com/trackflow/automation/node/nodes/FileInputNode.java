package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import com.trackflow.common.service.MinioService;
import com.trackflow.issue.entity.IssueAttachment;
import com.trackflow.issue.service.IssueAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * 读取工作流执行身份有权访问的工单附件文本。
 * 不允许读取服务器任意路径，避免工作流配置成为本地文件读取入口。
 */
@Component
@RequiredArgsConstructor
public class FileInputNode implements NodeDefinition, NodeExecutor {
    private static final int MAX_CONTENT_BYTES = 1_048_576;

    private final IssueAttachmentService attachmentService;
    private final MinioService minioService;

    @Override public String getType() { return "file-input"; }
    @Override public String getTitle() { return "文件输入"; }
    @Override public String getIcon() { return "📁"; }
    @Override public String getColor() { return "#8b5cf6"; }
    @Override public String getDescription() { return "读取有访问权限的工单附件文本"; }
    @Override public String getCategory() { return "数据处理"; }
    @Override public List<InputPortDef> getInputPorts() {
        return List.of(new InputPortDef("attachmentId", "number", true, "工单附件 ID"));
    }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(
                new OutputPortDef("content", "string", "附件文本内容"),
                new OutputPortDef("size", "number", "附件大小（字节）")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node, ExecutionContext context)
            throws NodeExecutionException {
        Long attachmentId = asLong(inputs.get("attachmentId"), node.id());
        try {
            IssueAttachment attachment = attachmentService.requireReadableAttachment(attachmentId);
            if (attachment.getFileSize() != null && attachment.getFileSize() > MAX_CONTENT_BYTES) {
                throw new NodeExecutionException(node.id(), "附件超过节点可读取上限（1 MB），请缩小附件后再试运行");
            }
            Charset charset = resolveCharset(node.config(), node.id());
            String content;
            try (InputStream stream = minioService.getObject(attachment.getFilePath())) {
                byte[] bytes = stream.readNBytes(MAX_CONTENT_BYTES + 1);
                if (bytes.length > MAX_CONTENT_BYTES) {
                    throw new NodeExecutionException(node.id(), "附件超过节点可读取上限（1 MB），请缩小附件后再试运行");
                }
                content = new String(bytes, charset);
            }
            return Map.of(
                    "content", applyReadMode(content, node.config()),
                    "size", attachment.getFileSize() != null ? attachment.getFileSize() : content.getBytes(charset).length
            );
        } catch (NodeExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NodeExecutionException(node.id(), exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new NodeExecutionException(node.id(), "读取附件失败: " + exception.getMessage(), exception);
        }
    }

    private Long asLong(Object value, String nodeId) throws NodeExecutionException {
        if (value == null || value.toString().isBlank()) {
            throw new NodeExecutionException(nodeId, "文件输入节点必须提供附件 ID");
        }
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            throw new NodeExecutionException(nodeId, "附件 ID 必须是数字", exception);
        }
    }

    private Charset resolveCharset(Map<String, Object> config, String nodeId) throws NodeExecutionException {
        Object configured = config != null ? config.get("encoding") : null;
        String encoding = configured == null || configured.toString().isBlank() ? "UTF-8" : configured.toString();
        try {
            return Charset.forName(encoding);
        } catch (Exception exception) {
            throw new NodeExecutionException(nodeId, "不支持的文件编码: " + encoding, exception);
        }
    }

    private String applyReadMode(String content, Map<String, Object> config) {
        String mode = config != null && config.get("readMode") != null ? config.get("readMode").toString() : "full";
        if ("full".equals(mode)) return content;
        String[] lines = content.split("\\R", -1);
        if ("head".equals(mode)) {
            return String.join("\\n", java.util.Arrays.copyOfRange(lines, 0, Math.min(1000, lines.length)));
        }
        if ("tail".equals(mode)) {
            return String.join("\\n", java.util.Arrays.copyOfRange(lines, Math.max(0, lines.length - 1000), lines.length));
        }
        return content;
    }
}
