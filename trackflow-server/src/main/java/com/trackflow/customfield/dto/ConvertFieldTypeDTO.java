package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 转换字段类型请求 DTO
 */
@Data
public class ConvertFieldTypeDTO {

    /**
     * 目标字段类型
     */
    @NotBlank(message = "目标类型不能为空")
    private String targetFormat;

    /**
     * 数值类型转换选项（int/float -> period 时使用）
     * <ul>
     *   <li>MINUTES: 将数值视为分钟数（默认）</li>
     *   <li>HOURS: 将数值视为小时数（乘以 60）</li>
     *   <li>DAYS: 将数值视为天数（乘以 480，按 8 小时/天）</li>
     * </ul>
     */
    private String periodUnit;
}
