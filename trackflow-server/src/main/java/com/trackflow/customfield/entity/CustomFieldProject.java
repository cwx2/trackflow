package com.trackflow.customfield.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 自定义字段与项目的关联
 */
@Data
@TableName("custom_field_project")
public class CustomFieldProject implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long customFieldId;

    private Long projectId;

    /** 字段在项目中的显示顺序 */
    private Integer position;
}
