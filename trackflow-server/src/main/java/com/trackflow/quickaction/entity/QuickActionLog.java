package com.trackflow.quickaction.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 快捷动作执行记录
 */
@Data
@TableName(value = "quick_action_log", autoResultMap = true)
public class QuickActionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long issueId;

    private String actionKey;

    private Long operatorId;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String formData;

    private Long mailTemplateId;

    private String resultType;

    private Long commentId;

    private Boolean mailSent;

    private String mailError;

    private String statusBefore;

    private String statusAfter;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
