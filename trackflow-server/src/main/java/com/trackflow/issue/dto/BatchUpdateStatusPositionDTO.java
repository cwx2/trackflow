package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量更新状态节点在工作流画布上的坐标位置（用于自动布局、批量拖拽等场景）
 */
@Data
public class BatchUpdateStatusPositionDTO {

    @NotEmpty(message = "位置列表不能为空")
    private List<StatusPositionItem> positions;

    @Data
    public static class StatusPositionItem {
        private Long statusId;
        private Double canvasX;
        private Double canvasY;
    }
}
