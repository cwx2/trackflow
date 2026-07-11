package com.trackflow.common.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> list;
    private Pagination pagination;

    public PageResult(List<T> list, long total, int page, int pageSize) {
        this.list = list;
        this.pagination = new Pagination(page, pageSize, total);
    }

    @Data
    public static class Pagination implements Serializable {
        private int page;
        private int pageSize;
        private long total;
        private int totalPages;

        public Pagination(int page, int pageSize, long total) {
            this.page = page;
            this.pageSize = pageSize;
            this.total = total;
            this.totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
        }
    }
}
