package com.trackflow.quickaction.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 快捷动作执行记录 VO
 */
@Data
public class QuickActionLogVO {

    private String id;

    private String issueId;

    private String actionKey;

    private String operatorId;

    private String operatorName;

    private String formData;

    private String mailTemplateId;

    private String mailTemplateName;

    private String resultType;

    private String commentId;

    private Boolean mailSent;

    private String mailError;

    private String statusBefore;

    private String statusAfter;

    private LocalDateTime createdAt;
}
