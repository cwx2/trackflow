package com.trackflow.common.util;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 分页工具类
 * <p>
 * 安全说明：排序字段通过白名单校验防止 SQL 注入。
 * 所有使用排序功能的调用方必须提供 allowedFields 白名单。
 */
public final class PageHelper {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 安全的列名格式：仅允许字母、数字、下划线，且以字母或下划线开头
     */
    private static final Pattern SAFE_COLUMN_PATTERN = Pattern.compile("^[a-z_][a-z0-9_]{0,63}$");

    private PageHelper() {
    }

    /**
     * 构建分页对象（带排序白名单校验）
     *
     * @param page          页码（从 1 开始）
     * @param pageSize      每页条数
     * @param sort          排序字段（-fieldName 表示降序，fieldName 表示升序，逗号分隔多字段）
     * @param allowedFields 允许排序的数据库列名白名单（snake_case）
     */
    public static <T> Page<T> buildPage(Integer page, Integer pageSize, String sort, Set<String> allowedFields) {
        int p = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int s = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        Page<T> pageObj = new Page<>(p, s);

        if (sort != null && !sort.isBlank() && allowedFields != null && !allowedFields.isEmpty()) {
            parseSortParam(pageObj, sort, allowedFields);
        }

        return pageObj;
    }

    /**
     * 构建分页对象（无排序）
     */
    public static <T> Page<T> buildPage(Integer page, Integer pageSize) {
        return buildPage(page, pageSize, null, Set.of());
    }

    private static <T> void parseSortParam(Page<T> page, String sort, Set<String> allowedFields) {
        String[] sortFields = sort.split(",");
        for (String field : sortFields) {
            field = field.trim();
            if (field.isEmpty()) continue;

            boolean desc = field.startsWith("-");
            String fieldName = desc ? field.substring(1) : field;
            String columnName = camelToSnake(fieldName);

            // 安全校验：列名格式检查 + 白名单检查
            if (!SAFE_COLUMN_PATTERN.matcher(columnName).matches()) {
                continue; // 含特殊字符，静默跳过
            }
            if (!allowedFields.contains(columnName)) {
                continue; // 不在白名单中，静默跳过
            }

            page.addOrder(desc ? OrderItem.desc(columnName) : OrderItem.asc(columnName));
        }
    }

    /**
     * 驼峰转下划线
     */
    static String camelToSnake(String camel) {
        if (camel == null || camel.isEmpty()) return camel;
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
