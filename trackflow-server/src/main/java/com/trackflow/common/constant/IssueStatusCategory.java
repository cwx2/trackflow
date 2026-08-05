package com.trackflow.common.constant;

/**
 * Issue 状态类别枚举 — 统一管理 issue_status.category 字段的合法取值。
 *
 * <p>所有需要判断状态类别的代码必须使用本枚举常量，禁止硬编码字符串。</p>
 *
 * <p>注意：数据库中 issue_status.category 存储的是 {@link #getValue()} 返回的小写字符串。</p>
 *
 * @author TrackFlow
 * @since 1.0
 */
public enum IssueStatusCategory {

    /** 新建/待处理 */
    OPEN("open"),
    /** 进行中 */
    IN_PROGRESS("in_progress"),
    /** 已完成 */
    DONE("done"),
    /** 已取消 */
    CANCELLED("cancelled");

    /** 数据库存储值 */
    private final String value;

    IssueStatusCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 判断给定 category 字符串是否表示"已关闭"状态（done 或 cancelled）。
     *
     * @param category category 字段值
     * @return true 表示已关闭
     */
    public static boolean isClosed(String category) {
        return DONE.value.equals(category) || CANCELLED.value.equals(category);
    }

    /**
     * 根据数据库存储值查找枚举。
     *
     * @param value category 字符串值
     * @return 对应枚举，未匹配返回 null
     */
    public static IssueStatusCategory fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (IssueStatusCategory category : values()) {
            if (category.value.equals(value)) {
                return category;
            }
        }
        return null;
    }
}
