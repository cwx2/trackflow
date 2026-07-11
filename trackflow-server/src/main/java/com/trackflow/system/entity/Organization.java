package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("organization")
public class Organization extends BaseEntity {

    private String name;
    private String code;
    private String description;
}
