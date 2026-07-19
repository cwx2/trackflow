package com.trackflow.common.util;

/**
 * SQL 相关工具方法
 */
public final class SqlUtils {

    private SqlUtils() {
        // 工具类禁止实例化
    }

    /**
     * 转义 SQL LIKE 通配符，确保用户输入被视为字面文本。
     * 参考 OpenProject sanitize_sql_like() / Rails ActiveRecord 实现。
     * <p>
     * 转义规则（使用 {@code \} 作为 ESCAPE 字符）：
     * <ul>
     *   <li>{@code \} → {@code \\}</li>
     *   <li>{@code %} → {@code \%}</li>
     *   <li>{@code _} → {@code \_}</li>
     * </ul>
     * <p>
     * 使用时 SQL 中必须添加 {@code ESCAPE '\'} 子句。
     * <p>
     * 示例：
     * <pre>
     * wrapper.apply("column LIKE {0} ESCAPE '\\'", "%" + SqlUtils.escapeLikePattern(input) + "%");
     * </pre>
     *
     * @param input 用户输入的搜索文本
     * @return 转义后的文本，可安全用于 LIKE 模式
     */
    public static String escapeLikePattern(String input) {
        if (input == null) return null;
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
