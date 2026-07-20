package com.trackflow.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目启用模块实体。
 * 参考 OpenProject 的 enabled_modules 机制——每个项目可独立启用/禁用功能模块。
 * module_name 对应 sys_permission.category，权限检查时只有启用模块下的权限才生效。
 */
@Data
@TableName("project_enabled_module")
public class ProjectEnabledModule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /**
     * 模块名称，对应 sys_permission.category:
     * issue, sprint, time_tracking, report, integration, query, project
     */
    private String moduleName;

    private LocalDateTime createdAt;
}
