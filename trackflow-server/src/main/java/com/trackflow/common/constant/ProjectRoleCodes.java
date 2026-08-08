package com.trackflow.common.constant;

import java.util.List;
import java.util.Set;

/**
 * 项目角色代码常量 — 对应 sys_role.code 字段中的项目级角色值。
 *
 * <p>所有需要引用角色代码字符串的代码必须使用本类常量，禁止硬编码字符串。</p>
 *
 * <p>注意与 {@link SystemRoleIds} 的区别：
 * <ul>
 *     <li>{@code SystemRoleIds} — 角色的数据库 ID（Long 类型）</li>
 *     <li>{@code ProjectRoleCodes} — 角色的代码字符串（String 类型）</li>
 * </ul>
 *
 * @author TrackFlow
 * @since 1.0
 */
public final class ProjectRoleCodes {

    private ProjectRoleCodes() {
        // 工具类禁止实例化
    }

    /** 项目管理员 */
    public static final String PROJECT_ADMIN = "project_admin";

    /** 技术负责人 */
    public static final String TECH_LEAD = "tech_lead";

    /** 开发人员 */
    public static final String DEVELOPER = "developer";

    /** 产品经理 */
    public static final String PRODUCT_MANAGER = "product_manager";

    /** 测试人员 */
    public static final String TESTER = "tester";

    /** 观察者 */
    public static final String OBSERVER = "observer";

    /** 系统管理员（全局角色，但某些上下文中也作为代码字符串引用） */
    public static final String SYSTEM_ADMIN = "system_admin";

    /** 所有项目级角色代码集合（不含 system_admin） */
    public static final List<String> ALL_PROJECT_ROLES = List.of(
            PROJECT_ADMIN, TECH_LEAD, DEVELOPER, PRODUCT_MANAGER, TESTER, OBSERVER
    );

    /** 所有项目级角色代码集合（Set 版本，用于校验） */
    public static final Set<String> ALL_PROJECT_ROLES_SET = Set.of(
            PROJECT_ADMIN, TECH_LEAD, DEVELOPER, PRODUCT_MANAGER, TESTER, OBSERVER
    );

    /** 默认可编辑角色：项目管理员 + 技术负责人 */
    public static final List<String> DEFAULT_EDIT_ROLES = List.of(
            PROJECT_ADMIN, TECH_LEAD
    );
}
