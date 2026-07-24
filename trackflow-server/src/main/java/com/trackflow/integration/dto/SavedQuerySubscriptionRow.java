package com.trackflow.integration.dto;

import lombok.Data;

/**
 * 通知订阅 JOIN 查询结果行——包含订阅用户 ID 和关联的 SavedQuery filters。
 * 用于 collectSavedQuerySubscribers() 批量查询优化，避免 O(M) 次独立 SQL。
 */
@Data
public class SavedQuerySubscriptionRow {

    /** 订阅用户 ID */
    private Long userId;

    /** 关联的 SavedQuery ID */
    private Long sourceId;

    /** SavedQuery 的过滤条件 JSON */
    private String filters;
}
