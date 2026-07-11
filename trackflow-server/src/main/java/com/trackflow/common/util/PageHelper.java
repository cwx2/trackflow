package com.trackflow.common.util;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页工具类
 */
public final class PageHelper {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private PageHelper() {
    }

    /**
     * 构建分页对象
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @param sort     排序字段（-fieldName 表示降序，fieldName 表示升序）
     */
    public static <T> Page<T> buildPage(Integer page, Integer pageSize, String sort) {
        int p = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int s = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        Page<T> pageObj = new Page<>(p, s);

        if (sort != null && !sort.isBlank()) {
            parseSortParam(pageObj, sort);
        }

        return pageObj;
    }

    /**
     * 使用默认分页参数
     */
    public static <T> Page<T> buildPage(Integer page, Integer pageSize) {
        return buildPage(page, pageSize, null);
    }

    private static <T> void parseSortParam(Page<T> page, String sort) {
        String[] sortFields = sort.split(",");
        for (String field : sortFields) {
            field = field.trim();
            if (field.isEmpty()) continue;

            if (field.startsWith("-")) {
                String columnName = camelToSnake(field.substring(1));
                page.addOrder(OrderItem.desc(columnName));
            } else {
                String columnName = camelToSnake(field);
                page.addOrder(OrderItem.asc(columnName));
            }
        }
    }

    /**
     * 驼峰转下划线
     */
    private static String camelToSnake(String camel) {
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
