package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 手动排序请求 DTO
 */
@Data
public class ManualOrderDTO {

    /** 排序上下文类型：project / query */
    @NotNull(message = "上下文类型不能为空")
    private String contextType;

    /** 上下文 ID */
    @NotNull(message = "上下文 ID 不能为空")
    private Long contextId;

    /**
     * 排序后的完整 issue ID 列表（有序）
     * 列表中的顺序即为手动排序的 position
     */
    @NotNull(message = "排序列表不能为空")
    @Size(min = 1, message = "排序列表不能为空")
    private List<Long> issueIds;
}
