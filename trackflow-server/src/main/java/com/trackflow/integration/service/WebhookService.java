package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.integration.entity.Webhook;
import com.trackflow.integration.entity.WebhookLog;
import com.trackflow.integration.mapper.WebhookLogMapper;
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
    private final WebhookLogMapper webhookLogMapper;
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

    @Transactional(rollbackFor = Exception.class)
        public Webhook create(Webhook webhook) {
        webhookMapper.insert(webhook);
        return webhook;
    }

    @Transactional(rollbackFor = Exception.class)
        public Webhook update(Webhook webhook) {
        webhookMapper.updateById(webhook);
        return webhookMapper.selectById(webhook.getId());
    }

    @Transactional(rollbackFor = Exception.class)
        public void delete(Long id) {
        webhookMapper.deleteById(id);
        // 同时清理投递日志
        webhookLogMapper.delete(
                new LambdaQueryWrapper<WebhookLog>().eq(WebhookLog::getWebhookId, id)
        );
    }

    /**
     * 查询 Webhook 投递日志（分页）
     */
    public Page<WebhookLog> getDeliveryLogs(Long webhookId, int page, int pageSize) {
        Page<WebhookLog> pageObj = new Page<>(page, pageSize);
        return webhookLogMapper.selectPage(pageObj,
                new LambdaQueryWrapper<WebhookLog>()
                        .eq(WebhookLog::getWebhookId, webhookId)
                        .orderByDesc(WebhookLog::getCreatedAt)
        );
    }

    /**
     * 手动测试触发 Webhook
     */
    public WebhookLog testTrigger(Webhook webhook) {
        String event = "test.ping";
        Map<String, Object> payload = Map.of(
                "message", "This is a test webhook delivery",
                "webhook_id", webhook.getId(),
                "timestamp", LocalDateTime.now().toString()
        );
        return sendAndLog(webhook, event, payload);
    }

    /**
     * 触发 Webhook 事件（异步）
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
            sendAndLog(webhook, event, payload);
        }
    }

    /**
     * 发送 HTTP 请求并记录日志
     */
    private WebhookLog sendAndLog(Webhook webhook, String event, Object payload) {
        WebhookLog logEntry = new WebhookLog();
        logEntry.setWebhookId(webhook.getId());
        logEntry.setEvent(event);

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "event", event,
                    "timestamp", LocalDateTime.now().toString(),
                    "payload", payload
            ));
            logEntry.setPayload(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhook.getUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-TrackFlow-Event", event)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logEntry.setResponseStatus(response.statusCode());
            logEntry.setResponseBody(truncate(response.body(), 4000));
            logEntry.setSuccess(response.statusCode() >= 200 && response.statusCode() < 300);

            log.debug("Webhook {} fired: event={}, status={}", webhook.getId(), event, response.statusCode());
        } catch (Exception e) {
            logEntry.setResponseStatus(0);
            logEntry.setResponseBody(e.getMessage());
            logEntry.setSuccess(false);
            log.warn("Webhook {} failed: event={}, error={}", webhook.getId(), event, e.getMessage());
        }

        webhookLogMapper.insert(logEntry);
        return logEntry;
    }

    private boolean matchesEvent(Webhook webhook, String event) {
        try {
            List<String> events = objectMapper.readValue(webhook.getEvents(), new TypeReference<List<String>>() {});
            return events.contains(event) || events.contains("*");
        } catch (Exception e) {
            return false;
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }
}
