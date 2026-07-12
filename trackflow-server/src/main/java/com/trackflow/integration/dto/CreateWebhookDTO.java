package com.trackflow.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

/**
 * 创建 Webhook 请求参数
 */
@Data
public class CreateWebhookDTO {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "名称不能为空")
    @Size(max = 200, message = "名称不能超过200个字符")
    private String name;

    @NotBlank(message = "URL不能为空")
    @URL(message = "URL格式不正确")
    @Size(max = 2048, message = "URL不能超过2048个字符")
    private String url;

    /** Webhook 密钥（可选，用于签名验证） */
    @Size(max = 255, message = "密钥不能超过255个字符")
    private String secret;

    /** 订阅事件列表 JSON，如 ["issue.created", "issue.status_changed"] */
    @NotBlank(message = "事件列表不能为空")
    @Size(max = 4000, message = "事件列表不能超过4000个字符")
    private String events;

    /** 是否启用 */
    private Boolean active = true;
}
