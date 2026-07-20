package com.trackflow.customfield.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个选项的使用统计
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptionUsageItemVO {
    /** 选项 ID */
    private String optionId;
    /** 选项值文本 */
    private String optionValue;
    /** 选项颜色 */
    private String color;
    /** 引用该选项的工单数量（distinct issue_id） */
    private long issueCount;
}
