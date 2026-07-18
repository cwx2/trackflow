package com.trackflow.external.migration;

import com.trackflow.external.common.ExternalAdapter;
import com.trackflow.external.common.ExternalEvent;
import com.trackflow.external.common.ExternalEventType;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 数据迁移适配器（骨架实现）。
 * <p>
 * 负责从外部系统（YouTrack、Jira 等）导入数据到 TrackFlow。
 * 与其他适配器不同，这是纯入站操作，不响应出站事件。
 * <p>
 * 后续将实现：
 * - YouTrack CSV/JSON 导入
 * - Jira 数据导入
 * - 批量数据校验与冲突处理
 */
@Slf4j
public class MigrationAdapter implements ExternalAdapter {

    private static final String ADAPTER_TYPE = "migration";

    @Override
    public String getAdapterType() {
        return ADAPTER_TYPE;
    }

    @Override
    public String getDisplayName() {
        return "数据迁移";
    }

    @Override
    public Set<ExternalEventType> getSupportedEvents() {
        // 数据迁移是入站操作，不订阅出站事件
        return Set.of();
    }

    @Override
    public void handle(ExternalEvent event) {
        // 数据迁移不响应出站事件，此方法不会被调用
        log.warn("[MigrationAdapter] 不应收到出站事件: {}", event.getEventType());
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
