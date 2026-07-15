package com.trackflow.customfield.dto;

import lombok.Data;

/**
 * 更新单个自定义字段值的请求体
 */
@Data
public class UpdateCustomFieldValueDTO {
    private String value;
}
