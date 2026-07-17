package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统设置实体 - 键值对存储全局配置
 */
@Data
@TableName("system_setting")
public class SystemSetting {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 配置键，如 'time_tracking.hours_per_day' */
    private String settingKey;

    /** 配置值 */
    private String value;

    /** 描述 */
    private String description;

    /** 分类 */
    private String category;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
