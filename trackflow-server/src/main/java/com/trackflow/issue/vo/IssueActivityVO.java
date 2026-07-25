package com.trackflow.issue.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IssueActivityVO {
    private String id;
    private String issueId;
    private String userId;
    private String userName;
    private String userAvatar;
    private String action;
    private String fieldName;
    private String oldValue;
    private String newValue;
    /**
     * 操作元数据（JSON 字符串），存储变更来源等信息。
     * 示例值：
     * - {"source":"action_rule"} — 快捷动作规则触发
     * - {"source":"automation","ruleName":"自动分配"} — 工作流自动化规则触发
     * - {"reason":"member_removed"} — 成员移除导致的自动清除
     * - {"reason":"manual_override"} — 用户手动设置
     * - {"actionKey":"postpone_one_week","executedActions":[...]} — 规则执行摘要
     */
    private String detail;
    private LocalDateTime createdAt;
}
