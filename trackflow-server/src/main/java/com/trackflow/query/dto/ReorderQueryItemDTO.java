package com.trackflow.query.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 查询面板排序单项 DTO
 */
@Data
public class ReorderQueryItemDTO {

    @NotNull(message = "查询 ID 不能为空")
    private Long id;

    private Integer sortOrder;

    private Boolean pinned;
}
