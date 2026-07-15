package com.trackflow.project.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.handler.JsonbTypeHandler;
import com.trackflow.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "project", autoResultMap = true)
public class Project extends BaseEntity {

    private String name;
    private String key;
    private String description;
    private Long orgId;
    private Long leadId;
    private String status;
    private String visibility;
    private Integer issueSequence;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String settings;
}
