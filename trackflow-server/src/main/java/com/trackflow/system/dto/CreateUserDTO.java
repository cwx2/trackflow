package com.trackflow.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建用户请求 DTO
 */
@Data
public class CreateUserDTO {

    /** 用户名（登录名），仅允许字母/数字/下划线/连字符 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3-50 个字符之间")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]*$", message = "用户名必须以字母开头，只能包含字母、数字、下划线和连字符")
    private String username;

    /** 邮箱 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式无效")
    private String email;

    /** 显示名称（全名） */
    @NotBlank(message = "显示名称不能为空")
    @Size(max = 100, message = "显示名称不能超过 100 个字符")
    private String displayName;

    /** 临时密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度需在 6-128 个字符之间")
    private String password;
}
