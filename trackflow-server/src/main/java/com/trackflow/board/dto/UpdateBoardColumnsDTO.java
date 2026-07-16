package com.trackflow.board.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量更新看板列配置 DTO
 */
@Data
public class UpdateBoardColumnsDTO {

    @NotEmpty(message = "列配置不能为空")
    @Valid
    private List<ColumnItem> columns;

    @Data
    public static class ColumnItem {
        @NotNull(message = "状态 ID 不能为空")
        private Long statusId;

        @NotNull(message = "可见性不能为空")
        private Boolean visible;

        /** 显示顺序 */
        private Integer sortOrder;

        /** 是否折叠 */
        private Boolean collapsed;

        /** 最小在制品数量，null 表示不限制 */
        private Integer wipMin;

        /** 最大在制品数量，null 表示不限制 */
        private Integer wipMax;
    }
}
