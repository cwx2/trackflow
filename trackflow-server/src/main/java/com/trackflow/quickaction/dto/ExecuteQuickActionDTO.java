package com.trackflow.quickaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 执行快捷动作请求 DTO
 */
@Data
public class ExecuteQuickActionDTO {

    /** 表单数据 JSON 字符串 */
    @NotNull(message = "表单数据不能为空")
    private String formData;

    /** 执行类型：comment_and_mail / only_comment */
    @NotBlank(message = "执行类型不能为空")
    private String resultType;

    /** 选择的邮件模板 ID（当 resultType = comment_and_mail 时必填） */
    private Long mailTemplateId;
}
