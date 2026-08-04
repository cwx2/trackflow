package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 内联添加枚举字段选项值 DTO。
 * 用于工单详情页/创建表单中直接添加新选项。
 */
@Data
public class AddOptionDTO {

    @NotBlank(message = "选项值不能为空")
    @Size(max = 256, message = "选项值不能超过256个字符")
    private String value;

    /** 选项颜色（HEX 格式，可选） */
    private String color;

    /** 选项负责人用户 ID（仅 ownedField 类型使用，可选） */
    private Long ownerUserId;
}
