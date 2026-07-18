package com.trackflow.quickaction.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件模板 VO
 */
@Data
public class MailTemplateVO {

    private String id;

    private String projectId;

    private String actionKey;

    private String name;

    private String subjectTemplate;

    private String bodyTemplate;

    private String recipientsRule;

    private Integer sortOrder;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
