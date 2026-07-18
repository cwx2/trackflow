package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户组实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_group")
public class UserGroup extends BaseEntity {

    private String name;
    private String description;
}
