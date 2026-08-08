package com.trackflow.common.constant;

/**
 * 角色类型常量 — 对应 sys_role.role_type 字段的可选值。
 *
 * <p>所有需要引用角色类型的代码必须使用本类常量，禁止硬编码字符串。</p>
 *
 * @author TrackFlow
 * @since 1.0
 */
public final class RoleTypes {

    private RoleTypes() {
        // 工具类禁止实例化
    }

    /** 全局角色 */
    public static final String GLOBAL = "global";

    /** 项目级角色 */
    public static final String PROJECT = "project";
}
