package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限定义实体
 * 对应 sys_permission 表，存储系统所有可用权限的元数据
 */
@Data
@TableName("sys_permission")
public class SysPermission implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 权限代码，如 'issue:create'
     */
    private String code;

    /**
     * 显示名称，如 '创建工单'
     */
    private String name;

    /**
     * 分类，如 'issue'、'project'、'system'
     */
    private String category;

    /**
     * 权限作用域：global（全局）/ project（项目级）
     */
    private String scope;

    /**
     * 详细描述
     */
    private String description;

    /**
     * 分类内排序
     */
    private Integer sortOrder;

    /**
     * 是否启用
     */
    private Boolean enabled;

    private LocalDateTime createdAt;
}
