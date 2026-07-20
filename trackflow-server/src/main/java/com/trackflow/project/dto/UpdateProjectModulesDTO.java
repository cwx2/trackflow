package com.trackflow.project.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 更新项目启用模块 DTO
 */
@Data
public class UpdateProjectModulesDTO {

    /** 要启用的模块列表 */
    @NotEmpty(message = "至少需要启用一个模块")
    private List<String> enabledModules;
}
