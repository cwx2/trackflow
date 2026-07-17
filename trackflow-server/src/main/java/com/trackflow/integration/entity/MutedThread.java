package com.trackflow.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知线程静音实体。
 * 存储用户对特定资源（通常是工单）的静音关系。
 * 静音后该资源的通知不再推送给该用户（@提及除外）。
 */
@Data
@TableName("notification_muted_thread")
public class MutedThread implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String resourceType;

    private Long resourceId;

    private LocalDateTime createdAt;
}
