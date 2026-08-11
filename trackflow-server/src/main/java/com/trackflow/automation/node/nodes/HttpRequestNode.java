package com.trackflow.automation.node.nodes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.NodeRuntimePolicy;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP 请求节点：发送 HTTP 请求，调用外部 API 或服务
 */
@Component
public class HttpRequestNode implements NodeDefinition, NodeExecutor {

    private final ObjectMapper objectMapper;

    public HttpRequestNode(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override public String getType()        { return "http-request"; }
    @Override public String getTitle()       { return "HTTP 请求"; }
    @Override public String getIcon()        { return "🌐"; }
    @Override public String getColor()       { return "#0891b2"; }
    @Override public String getDescription() { return "发送 HTTP 请求，调用外部 API 或服务"; }
    @Override public String getCategory()    { return "业务逻辑"; }
    @Override public NodeRuntimePolicy getRuntimePolicy() { return NodeRuntimePolicy.sideEffect(); }

    @Override
    public List<InputPortDef> getInputPorts() {
        return List.of(
            new InputPortDef("url",  "string", true,  "请求 URL，支持变量引用"),
            new InputPortDef("body", "string", false, "POST/PUT 请求体（JSON 字符串）")
        );
    }

    @Override
    public List<OutputPortDef> getOutputPorts() {
        return List.of(
            new OutputPortDef("responseBody", "string", "HTTP 响应 body 内容"),
            new OutputPortDef("statusCode",   "number", "HTTP 状态码，如 200、404")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node, ExecutionContext ctx)
            throws NodeExecutionException {
        Map<String, Object> config = node.config() != null ? node.config() : Map.of();
        String method      = ((String) config.getOrDefault("method", "GET")).toUpperCase();
        String headersJson = (String) config.getOrDefault("headers", "{}");
        int    timeoutSec  = ((Number) config.getOrDefault("timeout", 30)).intValue();

        String url  = (String) inputs.get("url");
        String body = (String) inputs.getOrDefault("body", null);

        if (url == null || url.isBlank()) {
            throw new NodeExecutionException(node.id(), "请求 URL 不能为空");
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .build();

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec));

            // 解析并设置请求头（失败则忽略）
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> headersMap = objectMapper.readValue(headersJson, Map.class);
                headersMap.forEach(reqBuilder::header);
            } catch (Exception ignored) {
                // ignore malformed headers
            }

            // 设置请求体和方法
            HttpRequest.BodyPublisher publisher = (body != null && !body.isBlank())
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();

            reqBuilder.method(method, publisher);
            HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            return Map.of(
                "responseBody", response.body() != null ? response.body() : "",
                "statusCode",   response.statusCode()
            );
        } catch (Exception e) {
            throw new NodeExecutionException(node.id(), "HTTP 请求失败: " + e.getMessage());
        }
    }
}
