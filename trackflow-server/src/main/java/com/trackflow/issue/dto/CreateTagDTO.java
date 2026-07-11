package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTagDTO {
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称不能超过100字符")
    private String name;

    private String color;
}
