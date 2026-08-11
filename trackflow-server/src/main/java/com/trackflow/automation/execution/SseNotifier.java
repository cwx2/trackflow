package com.trackflow.automation.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseNotifier {

    private static final Logger log = LoggerFactory.getLogger(SseNotifier.class);

    private final ObjectMapper objectMapper;
    private static final int MAX_REPLAY_EVENTS = 400;
    private static final long TERMINAL_REPLAY_RETENTION_MS = 5 * 60 * 1000L;

    /**
     * 执行开始和浏览器建立 SSE 之间可能只有数毫秒。保留一小段有序事件历史，
     * 让前端仍能得到完整的实时路径，而不是错过快速节点。
     */
    private final Map<Long, ExecutionStream> streams = new ConcurrentHashMap<>();

    public SseNotifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter register(Long executionId, Long lastEventId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        ExecutionStream stream = streams.computeIfAbsent(executionId, ignored -> new ExecutionStream());
        emitter.onCompletion(() -> removeEmitter(executionId, emitter));
        emitter.onTimeout(()    -> removeEmitter(executionId, emitter));
        emitter.onError(error   -> removeEmitter(executionId, emitter));

        synchronized (stream) {
            stream.emitters.add(emitter);
            for (ReplayEvent event : stream.eventsAfter(lastEventId)) {
                if (!send(emitter, event)) {
                    stream.emitters.remove(emitter);
                    return emitter;
                }
            }
            if (stream.completedAt > 0) emitter.complete();
        }
        return emitter;
    }

    public void send(Long executionId, Object event) {
        ExecutionStream stream = streams.computeIfAbsent(executionId, ignored -> new ExecutionStream());
        synchronized (stream) {
            if (stream.completedAt > 0) return;
            ReplayEvent replayEvent = stream.append(event);
            for (SseEmitter emitter : new ArrayList<>(stream.emitters)) {
                if (!send(emitter, replayEvent)) stream.emitters.remove(emitter);
            }
        }
    }

    public void complete(Long executionId) {
        ExecutionStream stream = streams.computeIfAbsent(executionId, ignored -> new ExecutionStream());
        synchronized (stream) {
            stream.completedAt = System.currentTimeMillis();
            for (SseEmitter emitter : new ArrayList<>(stream.emitters)) {
                try { emitter.complete(); } catch (Exception ignored) { }
            }
            stream.emitters.clear();
        }
    }

    /** 已结束的短期回放缓存自动释放，避免高频执行累积在内存中。 */
    @Scheduled(fixedDelay = 60_000L)
    public void cleanupCompletedStreams() {
        long expiredBefore = System.currentTimeMillis() - TERMINAL_REPLAY_RETENTION_MS;
        streams.entrySet().removeIf(entry -> {
            ExecutionStream stream = entry.getValue();
            synchronized (stream) {
                return stream.completedAt > 0 && stream.completedAt < expiredBefore;
            }
        });
    }

    private void removeEmitter(Long executionId, SseEmitter emitter) {
        ExecutionStream stream = streams.get(executionId);
        if (stream == null) return;
        synchronized (stream) {
            stream.emitters.remove(emitter);
        }
    }

    private boolean send(SseEmitter emitter, ReplayEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(event.id))
                    .data(objectMapper.writeValueAsString(event.payload)));
            return true;
        } catch (Exception exception) {
            log.debug("SSE send failed: {}", exception.getMessage());
            return false;
        }
    }

    private static final class ExecutionStream {
        private long nextEventId = 1;
        private long completedAt;
        private final Deque<ReplayEvent> events = new ArrayDeque<>();
        private final Set<SseEmitter> emitters = new HashSet<>();

        private ReplayEvent append(Object payload) {
            ReplayEvent event = new ReplayEvent(nextEventId++, payload);
            events.addLast(event);
            while (events.size() > MAX_REPLAY_EVENTS) events.removeFirst();
            return event;
        }

        private List<ReplayEvent> eventsAfter(Long lastEventId) {
            long after = lastEventId != null ? lastEventId : 0L;
            return events.stream().filter(event -> event.id > after).toList();
        }
    }

    private record ReplayEvent(long id, Object payload) { }

    public static Map<String, Object> nodeRunning(String nodeId) {
        return Map.of("type", "node_running", "nodeId", nodeId, "startTime", System.currentTimeMillis());
    }
    public static Map<String, Object> nodeSuccess(String nodeId, Map<String,Object> outputs, long ms) {
        return Map.of("type", "node_success", "nodeId", nodeId, "outputs", outputs != null ? outputs : Map.of(), "durationMs", ms);
    }
    public static Map<String, Object> nodeFailed(String nodeId, String error, long ms) {
        return Map.of("type", "node_failed", "nodeId", nodeId, "error", error, "durationMs", ms);
    }
    public static Map<String, Object> nodeSkipped(String nodeId) {
        return Map.of("type", "node_skipped", "nodeId", nodeId, "reason", "branch_not_selected");
    }
    public static Map<String, Object> nodeStreaming(String nodeId, String chunk) {
        return Map.of("type", "node_streaming_output", "nodeId", nodeId, "chunk", chunk);
    }
    public static Map<String, Object> workflowSuccess(Map<String,Object> output, long ms) {
        return Map.of("type", "workflow_success", "output", output != null ? output : Map.of(), "totalDurationMs", ms);
    }
    public static Map<String, Object> workflowFailed(String error) {
        return Map.of("type", "workflow_failed", "error", error != null ? error : "工作流执行失败");
    }
    public static Map<String, Object> workflowCancelled() {
        return Map.of("type", "workflow_cancelled");
    }
    public static Map<String, Object> workflowWaitingTimer(String nodeId, LocalDateTime wakeUpAt) {
        return Map.of(
                "type", "workflow_waiting_timer",
                "nodeId", nodeId,
                "wakeUpAt", wakeUpAt.toString()
        );
    }

    public static Map<String, Object> workflowWaitingApproval(String nodeId, Long approvalId) {
        return Map.of("type", "workflow_waiting_approval", "nodeId", nodeId,
                "approvalId", String.valueOf(approvalId));
    }
}
