package com.trackflow.external.sug;

import com.trackflow.external.common.ExternalAdapter;
import com.trackflow.external.common.ExternalEvent;
import com.trackflow.external.common.ExternalEventType;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * SUG 系统集成适配器（骨架实现）。
 * <p>
 * 负责将工单事件同步到 SUG 系统。
 * 后续将实现：
 * - 工单创建时同步到 SUG
 * - 状态变更时更新 SUG 对应记录
 * - SUG 回调时更新 TrackFlow 工单状态
 */
@Slf4j
public class SugAdapter implements ExternalAdapter {

    private static final String ADAPTER_TYPE = "sug";

    @Override
    public String getAdapterType() {
        return ADAPTER_TYPE;
    }

    @Override
    public String getDisplayName() {
        return "SUG 系统对接";
    }

    @Override
    public Set<ExternalEventType> getSupportedEvents() {
        return Set.of(
                ExternalEventType.ISSUE_CREATED,
                ExternalEventType.ISSUE_STATUS_CHANGED,
                ExternalEventType.ISSUE_CANCELLED
        );
    }

    @Override
    public void handle(ExternalEvent event) {
        // 骨架实现 — 后续独立需求中实现
        log.debug("[SugAdapter] 收到事件 {} (referenceId={}), 当前为骨架实现，跳过",
                event.getEventType(), event.getReferenceId());
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
