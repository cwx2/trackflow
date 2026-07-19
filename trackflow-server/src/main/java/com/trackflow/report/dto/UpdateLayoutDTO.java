package com.trackflow.report.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量更新 Widget 位置 DTO（拖拽后保存）
 */
@Data
public class UpdateLayoutDTO {

    @NotEmpty(message = "布局项不能为空")
    @Valid
    private List<LayoutItem> items;

    /** 乐观锁版本号（前端从 DashboardDetailVO 获取后回传） */
    @NotNull(message = "版本号不能为空")
    private Integer version;

    @Data
    public static class LayoutItem {

        @NotNull(message = "微件ID不能为空")
        private Long widgetId;

        @Min(value = 0, message = "位置X不能为负")
        @NotNull(message = "位置X不能为空")
        private Integer positionX;

        @Min(value = 0, message = "位置Y不能为负")
        @NotNull(message = "位置Y不能为空")
        private Integer positionY;

        @Min(value = 1, message = "宽度最小为1")
        @NotNull(message = "宽度不能为空")
        private Integer width;

        @Min(value = 1, message = "高度最小为1")
        @NotNull(message = "高度不能为空")
        private Integer height;
    }
}
