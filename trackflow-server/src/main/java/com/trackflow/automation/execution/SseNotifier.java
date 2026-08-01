package com.trackflow.automation.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseNotifier {

    private static final Logger log = LoggerFactory.getLogger(SseNotifier.class);

    private final ObjectMapper objectMapper;
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseNotifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter register(Long executionId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.put(executionId, emitter);
        emitter.onCompletion(() -> emitters.remove(executionId));
        emitter.onTimeout(()    -> emitters.remove(executionId));
        emitter.onError(e       -> emitters.remove(executionId));
        return emitter;
    }

    public void send(Long executionId, Object event) {
        SseEmitter emitter = emitters.get(executionId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
        } catch (Exception e) {
            log.warn("SSE send failed for executionId={}: {}", executionId, e.getMessage());
            emitters.remove(executionId);
        }
    }

    public void complete(Long executionId) {
        SseEmitter emitter = emitters.remove(executionId);
        if (emitter != null) { try { emitter.complete(); } catch (Exception ignored) {} }
    }

    public static Map<String, Object> nodeRunning(String nodeId) {
        return Map.of("type", "node_running", "nodeId", nodeId, "startTime", System.currentTimeMillis());
    }
    public static Map<String, Object> nodeSuccess(String nodeId, Map<String,Object> outputs, long ms) {
        return Map.of("type", "node_success", "nodeId", nodeId, "outputs", outputs != null ? outputs : Map.of(), "durationMs", ms);
    }
    public static Map<String, Object> nodeFailed(String nodeId, String error, long ms) {
        return Map.of("type", "node_failed", "nodeId", nodeId, "error", error, "durationMs", ms);
    }
    public static Map<String, Object> nodeStreaming(String nodeId, String chunk) {
        return Map.of("type", "node_streaming_output", "nodeId", nodeId, "chunk", chunk);
    }
    public static Map<String, Object> workflowSuccess(Map<String,Object> output, long ms) {
        return Map.of("type", "workflow_success", "output", output != null ? output : Map.of(), "totalDurationMs", ms);
    }
    public static Map<String, Object> workflowFailed(String error) {
        return Map.of("type", "workflow_failed", "error", error);
    }
}
