package com.trackflow.common.constant;

/**
 * 系统角色 ID 常量 — 对应 V2__seed_roles.sql 种子数据中的固定角色 ID。
 *
 * <p>所有需要引用角色 ID 的代码必须使用本类常量，禁止硬编码 Magic Number。</p>
 *
 * @author TrackFlow
 * @since 1.0
 */
public final class SystemRoleIds {

    private SystemRoleIds() {
        // 工具类禁止实例化
    }

    /** 系统管理员（全局角色） */
    public static final Long SYSTEM_ADMIN = 1L;

    /** 项目管理员 */
    public static final Long PROJECT_ADMIN = 2L;

    /** 开发人员 */
    public static final Long DEVELOPER = 3L;

    /** 测试人员 */
    public static final Long TESTER = 4L;

    /** 观察者 */
    public static final Long OBSERVER = 5L;

    /** 产品经理 */
    public static final Long PRODUCT_MANAGER = 6L;

    /** 技术负责人 */
    public static final Long TECH_LEAD = 7L;

    /** 非成员 */
    public static final Long NON_MEMBER = 8L;

    /** 匿名用户 */
    public static final Long ANONYMOUS = 9L;

    /** 用户管理员 */
    public static final Long USER_MANAGER = 10L;

    /** 项目创建者 */
    public static final Long PROJECT_CREATOR = 11L;
}
