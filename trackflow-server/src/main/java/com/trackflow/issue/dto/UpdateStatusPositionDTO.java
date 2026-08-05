package com.trackflow.issue.dto;

import lombok.Data;

/**
 * 更新状态节点在工作流画布上的坐标位置
 */
@Data
public class UpdateStatusPositionDTO {
    private Double canvasX;
    private Double canvasY;
}
