package com.trackflow.customfield.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Issue 自定义字段值（EAV 模式）
 */
@Data
@TableName("custom_field_value")
public class CustomFieldValue implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long issueId;

    private Long customFieldId;

    /** 所有类型统一存为 TEXT */
    private String value;

    /** 冗余标记：是否为多值字段的行（用于条件唯一索引） */
    private Boolean isMulti;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
