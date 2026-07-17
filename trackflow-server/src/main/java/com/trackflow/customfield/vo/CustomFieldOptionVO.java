package com.trackflow.customfield.vo;

import lombok.Data;

@Data
public class CustomFieldOptionVO {
    private String id;
    private String customFieldId;
    private String value;
    private Integer position;
    private Boolean isDefault;
    private Boolean isArchived;
}
