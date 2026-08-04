package com.trackflow.customfield.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 值集合合并结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MergeOptionsResultVO {
    /** 新增的选项数量 */
    private int addedCount;
    /** 因名称重复跳过的选项数量 */
    private int skippedCount;
    /** 合并后当前字段总选项数 */
    private int totalCount;
}
