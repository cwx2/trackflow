package com.trackflow.issue.vo;

import lombok.Data;

/**
 * 批量操作中每个状态的可达性信息 VO
 */
@Data
public class BatchAvailableStatusVO {

    /** 状态 ID */
    private String id;

    /** 状态名称 */
    private String name;

    /** 状态颜色 */
    private String color;

    /** 状态分类 */
    private String category;

    /** 是否关闭状态 */
    private Boolean isClosed;

    /** 排序 */
    private Integer sortOrder;

    /** 可以转换到此状态的工单数量 */
    private int reachableCount;

    /** 选中的工单总数 */
    private int totalCount;

    /** 是否完全可达（reachableCount == totalCount） */
    public boolean isFullyReachable() {
        return reachableCount == totalCount;
    }

    /** 是否部分可达（0 < reachableCount < totalCount） */
    public boolean isPartiallyReachable() {
        return reachableCount > 0 && reachableCount < totalCount;
    }

    /** 是否完全不可达（reachableCount == 0） */
    public boolean isUnreachable() {
        return reachableCount == 0;
    }
}
