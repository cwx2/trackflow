package com.trackflow.issue.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 手动排序结果 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualOrderVO {

    /** 排序上下文类型 */
    private String contextType;

    /** 上下文 ID */
    private String contextId;

    /** 是否为所有者排序（全局可见） */
    private boolean ownerOrder;

    /** 手动排序的 issue ID 列表（有序） */
    private List<String> issueIds;
}
