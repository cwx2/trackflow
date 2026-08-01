package com.trackflow.automation.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.agent.entity.AutomationRoleProfile;
import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.execution.SseNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** 受角色策略约束的 Agent provider 运行时。 */
@Service
@RequiredArgsConstructor
public class AgentRuntimeService {
    private final AutomationRoleProfileService roleService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> execute(Long roleId, String task, Object taskContext,
                                       String requestedWorkDir, String nodeId,
                                       ExecutionContext context) {
        AutomationRoleProfile role = roleService.get(roleId);
        if (!Boolean.TRUE.equals(role.getEnabled())) {
            throw new IllegalStateException("Agent 角色已停用: " + role.getName());
        }
        if (task == null || task.isBlank()) throw new IllegalArgumentException("Agent 任务不能为空");
        String prompt = buildPrompt(role, task, taskContext);
        return switch (role.getProviderType()) {
            case "cli" -> executeCli(role, prompt, requestedWorkDir, nodeId, context);
            case "http", "openai_compatible" -> executeHttp(role, prompt, taskContext);
            default -> throw new IllegalStateException("不支持的 Agent provider: " + role.getProviderType());
        };
    }

    private Map<String, Object> executeCli(AutomationRoleProfile role, String prompt,
                                           String requestedWorkDir, String nodeId,
                                           ExecutionContext context) {
        Map<String, Object> tools = readMap(role.getToolPolicy());
        String command = stringValue(tools.get("command"));
        Set<String> allowedCommands = stringSet(tools.get("allowedCommands"));
        if (command == null || !allowedCommands.contains(command)) {
            throw new IllegalStateException("CLI 命令未在角色白名单中");
        }
        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        Object arguments = tools.get("arguments");
        if (arguments instanceof List<?> list) {
            list.stream().map(String::valueOf).forEach(commandLine::add);
        }
        boolean promptByArgument = "argument".equals(tools.get("promptMode"));
        if (promptByArgument) commandLine.add(prompt);

        Path workDir = resolveWorkDir(role, requestedWorkDir);
        int timeoutSeconds = boundedInt(tools.get("timeoutSeconds"), 1800, 1, 7200);
        int maxOutputBytes = boundedInt(tools.get("maxOutputBytes"), 2_000_000, 1024, 10_000_000);

        ProcessBuilder builder = new ProcessBuilder(commandLine)
                .directory(workDir.toFile()).redirectErrorStream(true);
        try {
            Process process = builder.start();
            if (!promptByArgument) {
                process.getOutputStream().write(prompt.getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().close();
            }
            StringBuilder output = new StringBuilder();
            CompletableFuture<Void> readerFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (output.length() + line.length() + 1 > maxOutputBytes) {
                            process.destroyForcibly();
                            throw new IllegalStateException("Agent 输出超过角色限制");
                        }
                        output.append(line).append('\n');
                        context.sendSseEvent(SseNotifier.nodeStreaming(nodeId, line + "\n"));
                        if (context.isCancelled()) process.destroyForcibly();
                    }
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished || context.isCancelled()) {
                process.destroyForcibly();
                throw new IllegalStateException(context.isCancelled() ? "Agent 执行已取消" : "Agent 执行超时");
            }
            readerFuture.join();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Agent 命令失败，exitCode=" + process.exitValue()
                        + "\n" + output);
            }
            return result(role, output.toString(), process.exitValue());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Agent 执行被中断", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("Agent CLI 启动失败: " + exception.getMessage(), exception);
        }
    }

    private Map<String, Object> executeHttp(AutomationRoleProfile role, String prompt, Object context) {
        Map<String, Object> tools = readMap(role.getToolPolicy());
        URI endpoint = URI.create(String.valueOf(tools.get("endpoint")));
        validateEndpoint(endpoint, stringSet(tools.get("allowedHosts")));
        int timeoutSeconds = boundedInt(tools.get("timeoutSeconds"), 300, 1, 1800);
        String secretEnv = stringValue(tools.get("apiKeyEnv"));
        String secret = secretEnv != null ? System.getenv(secretEnv) : null;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", role.getModel());
        body.put("system", role.getSystemPrompt());
        body.put("prompt", prompt);
        body.put("context", context);
        body.put("output_schema", readMap(role.getOutputSchema()));
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (secret != null && !secret.isBlank()) request.header("Authorization", "Bearer " + secret);
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20)).build()
                    .send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Agent HTTP 调用失败，status=" + response.statusCode());
            }
            String output = extractProviderOutput(response.body());
            return result(role, output, 0);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Agent HTTP 调用被中断", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("Agent HTTP 调用失败: " + exception.getMessage(), exception);
        }
    }

    private Map<String, Object> result(AutomationRoleProfile role, String output, int exitCode) {
        Map<String, Object> schema = readMap(role.getOutputSchema());
        Object structured = Map.of();
        if (!schema.isEmpty()) {
            try {
                JsonNode json = objectMapper.readTree(stripCodeFence(output));
                validateStructuredOutput(json, schema);
                structured = objectMapper.convertValue(json, Object.class);
            } catch (Exception exception) {
                throw new IllegalStateException("Agent 输出不符合角色结构化协议: " + exception.getMessage(), exception);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", output);
        result.put("structuredOutput", structured);
        result.put("exitCode", exitCode);
        result.put("role", role.getName());
        result.put("success", true);
        return result;
    }

    private void validateStructuredOutput(JsonNode output, Map<String, Object> schema) {
        Object requiredValue = schema.get("required");
        if (requiredValue instanceof List<?> required) {
            for (Object field : required) {
                if (!output.hasNonNull(String.valueOf(field))) {
                    throw new IllegalArgumentException("缺少字段 " + field);
                }
            }
        }
        Object propertiesValue = schema.get("properties");
        if (!(propertiesValue instanceof Map<?, ?> properties)) return;
        properties.forEach((field, ruleValue) -> {
            JsonNode value = output.get(String.valueOf(field));
            if (value == null || value.isNull() || !(ruleValue instanceof Map<?, ?> rule)) return;
            String type = String.valueOf(rule.get("type"));
            boolean valid = switch (type) {
                case "string" -> value.isTextual();
                case "number", "integer" -> value.isNumber();
                case "boolean" -> value.isBoolean();
                case "array" -> value.isArray();
                case "object" -> value.isObject();
                default -> true;
            };
            if (!valid) throw new IllegalArgumentException("字段 " + field + " 类型应为 " + type);
        });
    }

    private Path resolveWorkDir(AutomationRoleProfile role, String requested) {
        Map<String, Object> workspace = readMap(role.getWorkspacePolicy());
        String candidate = requested != null && !requested.isBlank()
                ? requested : stringValue(workspace.get("defaultWorkDir"));
        if (candidate == null) throw new IllegalStateException("角色未配置工作目录");
        Path resolved = Path.of(candidate).toAbsolutePath().normalize();
        List<Path> roots = new ArrayList<>();
        Object configuredRoots = workspace.get("allowedRoots");
        if (configuredRoots instanceof List<?> list) {
            list.stream().map(String::valueOf)
                    .map(path -> Path.of(path).toAbsolutePath().normalize()).forEach(roots::add);
        }
        if (roots.isEmpty() || roots.stream().noneMatch(resolved::startsWith)) {
            throw new IllegalStateException("工作目录不在角色允许范围内: " + resolved);
        }
        File directory = resolved.toFile();
        if (!directory.isDirectory()) throw new IllegalStateException("工作目录不存在: " + resolved);
        return resolved;
    }

    private void validateEndpoint(URI endpoint, Set<String> allowedHosts) {
        String host = endpoint.getHost();
        boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        if (!("https".equalsIgnoreCase(endpoint.getScheme())
                || (local && "http".equalsIgnoreCase(endpoint.getScheme())))) {
            throw new IllegalStateException("Agent endpoint 必须使用 HTTPS（localhost 可用 HTTP）");
        }
        if (host == null || !allowedHosts.contains(host)) {
            throw new IllegalStateException("Agent endpoint host 未在角色白名单中");
        }
    }

    private String buildPrompt(AutomationRoleProfile role, String task, Object context) {
        try {
            return role.getSystemPrompt() + "\n\n任务：\n" + task + "\n\n上下文：\n"
                    + objectMapper.writeValueAsString(context != null ? context : Map.of());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Agent 上下文无法序列化", exception);
        }
    }

    private String extractProviderOutput(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (root.has("output")) return root.get("output").isTextual()
                ? root.get("output").asText() : root.get("output").toString();
        JsonNode content = root.at("/choices/0/message/content");
        if (!content.isMissingNode()) return content.asText();
        return body;
    }

    private String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstLine = trimmed.indexOf('\n');
        int end = trimmed.lastIndexOf("```");
        return firstLine >= 0 && end > firstLine ? trimmed.substring(firstLine + 1, end).trim() : trimmed;
    }

    private Map<String, Object> readMap(String json) {
        try {
            return json == null || json.isBlank() ? Map.of()
                    : objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("角色策略 JSON 损坏", exception);
        }
    }
    private Set<String> stringSet(Object value) {
        if (!(value instanceof List<?> list)) return Set.of();
        return list.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
    }
    private String stringValue(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
    private int boundedInt(Object value, int fallback, int min, int max) {
        int parsed = value instanceof Number number ? number.intValue()
                : value != null ? Integer.parseInt(value.toString()) : fallback;
        return Math.max(min, Math.min(max, parsed));
    }
}
