package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.integration.entity.Webhook;
import com.trackflow.integration.mapper.WebhookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookMapper webhookMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public List<Webhook> listByProject(Long projectId) {
        return webhookMapper.selectList(
                new LambdaQueryWrapper<Webhook>()
                        .eq(Webhook::getProjectId, projectId)
                        .orderByDesc(Webhook::getCreatedAt)
        );
    }

    public Webhook getById(Long id) {
        return webhookMapper.selectById(id);
    }

    @Transactional
    public Webhook create(Webhook webhook) {
        webhookMapper.insert(webhook);
        return webhook;
    }

    @Transactional
    public void delete(Long id) {
        webhookMapper.deleteById(id);
    }

    /**
     * 触发 Webhook 事件
     */
    @Async("notificationExecutor")
    public void fire(String event, Long projectId, Object payload) {
        List<Webhook> webhooks = webhookMapper.selectList(
                new LambdaQueryWrapper<Webhook>()
                        .eq(Webhook::getActive, true)
                        .and(w -> w.eq(Webhook::getProjectId, projectId).or().isNull(Webhook::getProjectId))
        );

        for (Webhook webhook : webhooks) {
            if (!matchesEvent(webhook, event)) continue;

            try {
                String body = objectMapper.writeValueAsString(Map.of(
                        "event", event,
                        "timestamp", LocalDateTime.now().toString(),
                        "payload", payload
                ));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhook.getUrl()))
                        .header("Content-Type", "application/json")
                        .header("X-TrackFlow-Event", event)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.debug("Webhook {} fired: event={}, status={}", webhook.getId(), event, response.statusCode());
            } catch (Exception e) {
                log.warn("Webhook {} failed: event={}, error={}", webhook.getId(), event, e.getMessage());
            }
        }
    }

    private boolean matchesEvent(Webhook webhook, String event) {
        try {
            List<String> events = objectMapper.readValue(webhook.getEvents(), new TypeReference<List<String>>() {});
            return events.contains(event) || events.contains("*");
        } catch (Exception e) {
            return false;
        }
    }
}
