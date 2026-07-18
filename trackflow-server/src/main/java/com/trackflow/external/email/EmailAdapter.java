package com.trackflow.external.email;

import com.trackflow.external.common.ExternalAdapter;
import com.trackflow.external.common.ExternalEvent;
import com.trackflow.external.common.ExternalEventType;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 邮件集成适配器（骨架实现）。
 * <p>
 * 负责将工单事件通过邮件发送给订阅者。
 * 后续将实现：
 * - 新工单创建通知邮件
 * - 状态变更通知邮件
 * - 评论通知邮件
 * - 邮件入站（通过邮件创建/回复工单）
 * <p>
 * 当前为骨架实现，仅定义接口。实际启用需要配置 SMTP 并设置 enabled=true。
 */
@Slf4j
public class EmailAdapter implements ExternalAdapter {

    private static final String ADAPTER_TYPE = "email";

    @Override
    public String getAdapterType() {
        return ADAPTER_TYPE;
    }

    @Override
    public String getDisplayName() {
        return "邮件通知";
    }

    @Override
    public Set<ExternalEventType> getSupportedEvents() {
        return Set.of(
                ExternalEventType.ISSUE_CREATED,
                ExternalEventType.ISSUE_STATUS_CHANGED,
                ExternalEventType.ISSUE_CANCELLED,
                ExternalEventType.ISSUE_ASSIGNED,
                ExternalEventType.COMMENT_CREATED
        );
    }

    @Override
    public void handle(ExternalEvent event) {
        // 骨架实现 — 后续 REQ-466-2 或独立需求中实现
        log.debug("[EmailAdapter] 收到事件 {} (referenceId={}), 当前为骨架实现，跳过",
                event.getEventType(), event.getReferenceId());
    }

    @Override
    public boolean isEnabled() {
        // 骨架阶段默认禁用，避免注入后触发空操作
        return false;
    }
}
