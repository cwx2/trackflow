package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Make Independent Copy 请求 DTO
 * 将字段的全局共享选项集复制为项目独立副本
 */
@Data
public class MakeIndependentCopyDTO {
    
    /**
     * 是否只创建副本不包含现有选项。
     * false (默认): 复制当前全局选项集到项目
     * true: 创建空的独立选项集，由项目管理员从零开始配置
     */
    private Boolean emptyOptions;
}
