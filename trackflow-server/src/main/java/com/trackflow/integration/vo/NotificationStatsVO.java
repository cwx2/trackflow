package com.trackflow.integration.vo;

import lombok.Data;

import java.util.Map;

/**
 * 通知统计概览 VO
 */
@Data
public class NotificationStatsVO {

    /** 通知总数 */
    private Long totalCount;
    /** 未读通知数 */
    private Long unreadCount;
    /** 已读通知数 */
    private Long readCount;
    /** 今日发送量 */
    private Long todayCount;
    /** 本周发送量 */
    private Long weekCount;
    /** 各类型通知分布 (type -> count) */
    private Map<String, Long> typeDistribution;
}
