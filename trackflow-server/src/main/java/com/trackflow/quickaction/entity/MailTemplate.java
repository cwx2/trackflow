package com.trackflow.quickaction.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件模板
 */
@Data
@TableName(value = "mail_template", autoResultMap = true)
public class MailTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String actionKey;

    private String name;

    private String subjectTemplate;

    private String bodyTemplate;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String recipientsRule;

    private Integer sortOrder;

    private Boolean enabled;

    private Long createdBy;

    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
