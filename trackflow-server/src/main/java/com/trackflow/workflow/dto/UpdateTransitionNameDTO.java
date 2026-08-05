package com.trackflow.workflow.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新工作流转换显示名称 DTO
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class UpdateTransitionNameDTO {

    /**
     * 转换显示名称。
     * 设置后，工单状态下拉将优先显示此名称而非目标状态名。
     * 传 null 或空字符串表示清除名称（回退到目标状态名）。
     */
    @Size(max = 100, message = "转换名称不能超过100字")
    private String transitionName;
}
