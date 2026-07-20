package com.trackflow.integration.entity;

/**
 * 通知接收原因枚举——回答"为什么通知我"。
 * <p>
 * 参考 OpenProject Notification#reason 设计，简化为适合 TrackFlow 的核心集合。
 * 数据库中存储 name()（如 "assigned"），前端用相同字符串做标签映射。
 * <p>
 * 扩展规则：新增原因时
 * 1. 在此枚举添加常量
 * 2. 前端 api/notification.ts 的 NotificationReason 类型同步添加
 * 3. 前端通知展示组件同步添加原因标签映射
 */
public enum NotificationReason {

    /** 被分配为负责人 */
    assigned("你是负责人"),

    /** 作为报告人 */
    reporter("你是报告人"),

    /** 之前评论过此工单 */
    commenter("你评论过此工单"),

    /** 在评论中被 @提及 */
    mentioned("你被@提及"),

    /** 作为项目成员 */
    member("你是项目成员"),

    /** 关注了此工单（预留） */
    watched("你关注了此工单"),

    /** 通过订阅规则匹配（基于标签或保存搜索） */
    subscription("你的订阅规则匹配"),


    /** 工作流自动分配 */
    auto_assigned("工作流自动分配给你"),

    /** 自动化规则触发通知 */
    rule_triggered("自动化规则触发");

    private final String displayLabel;

    NotificationReason(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    /**
     * 获取面向用户的中文展示标签
     */
    public String getDisplayLabel() {
        return displayLabel;
    }
}
