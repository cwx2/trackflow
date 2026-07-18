package com.trackflow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/**
 * 复制项目请求 DTO
 */
@Data
public class CopyProjectDTO {

    @NotNull(message = "源项目ID不能为空")
    private Long sourceProjectId;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称不能超过200字符")
    private String name;

    @NotBlank(message = "项目Key不能为空")
    @Size(min = 2, max = 20, message = "项目Key长度必须在2-20之间")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "项目Key只能包含大写字母、数字和下划线，且以字母开头")
    private String key;

    @Size(max = 2000, message = "描述不能超过2000字符")
    private String description;

    /**
     * 要复制的模块选项集合。
     * 可选值: workflow, tags, custom_fields, board, actions, members, queries
     */
    private Set<String> copyOptions;
}
