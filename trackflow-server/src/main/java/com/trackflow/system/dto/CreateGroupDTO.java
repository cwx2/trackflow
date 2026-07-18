package com.trackflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建用户组 DTO
 */
@Data
public class CreateGroupDTO {

    @NotBlank(message = "组名不能为空")
    @Size(max = 256, message = "组名最长 256 字符")
    private String name;

    @Size(max = 1000, message = "描述最长 1000 字符")
    private String description;
}
