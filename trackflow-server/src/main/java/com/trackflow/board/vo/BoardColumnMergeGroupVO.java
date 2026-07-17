package com.trackflow.board.vo;

import lombok.Data;

import java.util.List;

/**
 * 看板列合并组 VO。
 * 一组合并的状态展示为同一列。
 */
@Data
public class BoardColumnMergeGroupVO {
    /** 合并组标识 */
    private String mergeGroupId;
    /** 合并后的列标题 */
    private String mergeTitle;
    /** 该组中合并的状态 ID 列表（按排序） */
    private List<String> statusIds;
}
