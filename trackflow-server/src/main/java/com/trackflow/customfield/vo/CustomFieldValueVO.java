package com.trackflow.customfield.vo;

import lombok.Data;

@Data
public class CustomFieldValueVO {
    private String customFieldId;
    private String fieldName;
    private String fieldFormat;
    private String value;
    private String displayValue;
}
