package com.trackflow.customfield.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 列表类型自定义字段的可选项
 */
@Data
@TableName("custom_field_option")
public class CustomFieldOption implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long customFieldId;

    private String value;

    private Integer position;

    private Boolean isDefault;

    /** 是否已归档（归档选项不出现在新值选择列表中，但已引用的值仍可正确展示） */
    private Boolean isArchived;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
