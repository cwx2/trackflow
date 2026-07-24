package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 标签收藏排序 DTO
 */
@Data
public class ReorderTagFavoritesDTO {
    @NotNull(message = "标签ID列表不能为空")
    private List<Long> tagIds;
}
