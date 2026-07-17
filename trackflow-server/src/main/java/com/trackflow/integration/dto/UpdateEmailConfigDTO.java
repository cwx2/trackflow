package com.trackflow.integration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新邮件服务器配置 DTO
 */
@Data
public class UpdateEmailConfigDTO {

    /** SMTP 服务器地址 */
    @Size(max = 255, message = "服务器地址不能超过 255 个字符")
    private String host;

    /** SMTP 端口 */
    @Min(value = 1, message = "端口号不能小于 1")
    @Max(value = 65535, message = "端口号不能大于 65535")
    private Integer port;

    /** 连接协议：plain / ssl / starttls */
    @Size(max = 20, message = "协议类型不能超过 20 个字符")
    private String protocol;

    /** 认证用户名 */
    @Size(max = 255, message = "用户名不能超过 255 个字符")
    private String username;

    /** 密码（明文传入，后端加密存储；传空字符串表示清除密码） */
    @Size(max = 500, message = "密码不能超过 500 个字符")
    private String password;

    /** 是否启用 SSL */
    private Boolean sslEnabled;

    /** 发件人地址 */
    @Email(message = "发件人地址格式不正确")
    @Size(max = 255, message = "发件人地址不能超过 255 个字符")
    private String fromAddress;

    /** 回复地址 */
    @Email(message = "回复地址格式不正确")
    @Size(max = 255, message = "回复地址不能超过 255 个字符")
    private String replyToAddress;
}
