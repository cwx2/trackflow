package com.trackflow.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新系统调色板 DTO
 */
@Data
public class UpdateColorPaletteDTO {

    /**
     * 颜色列表，每个元素为 HEX 格式（如 '#4CAF50'），最多 30 个颜色
     */
    @NotNull(message = "颜色列表不能为空")
    @Size(max = 30, message = "调色板颜色数量不能超过30个")
    private List<String> colors;
}
