package com.trackflow.integration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 测试邮件发送 DTO
 */
@Data
public class SendTestEmailDTO {

    /** 目标邮件地址 */
    @NotBlank(message = "目标邮件地址不能为空")
    @Email(message = "目标邮件地址格式不正确")
    private String toAddress;
}
