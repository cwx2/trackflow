package com.trackflow.common.constant;

/**
 * 用户状态常量 — 对应 sys_user.status 字段的可选值。
 *
 * <p>所有需要引用用户状态的代码必须使用本类常量，禁止硬编码字符串。</p>
 *
 * @author TrackFlow
 * @since 1.0
 */
public final class UserStatus {

    private UserStatus() {
        // 工具类禁止实例化
    }

    /** 活跃状态 */
    public static final String ACTIVE = "active";

    /** 已禁用 */
    public static final String DISABLED = "disabled";
}
