package com.trackflow.integration.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮件通知可用状态 VO —— 供普通用户查询全局邮件通知是否可用。
 * <p>
 * 前端用户偏好页根据此状态决定邮件通知开关是否可操作：
 * - available=true：用户可自由启用/禁用邮件通知
 * - available=false：开关禁用，显示不可用原因
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailAvailabilityVO {

    /**
     * 邮件通知是否可用（全局启用 + SMTP 已配置）
     */
    private Boolean available;

    /**
     * 全局邮件渠道是否启用（管理员开关）
     */
    private Boolean globalEnabled;

    /**
     * SMTP 服务器是否已配置
     */
    private Boolean smtpConfigured;

    /**
     * 不可用原因提示（available=false 时有值）
     */
    private String reason;
}
