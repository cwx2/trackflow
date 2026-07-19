package com.trackflow.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long actorId;
    private Long projectId;
    private String title;
    private String content;
    private String type;
    private String resourceType;
    private Long resourceId;
    private String resourceUrl;
    private String reason;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer aggregationCount;
    /**
     * 邮件是否已发送（延迟投递模型）。
     * false = 待发邮件（定时任务在聚合窗口结束后检查并发送）。
     * true = 已发送或已跳过（用户已读 IAN 则跳过）。
     */
    private Boolean mailSent;
    private LocalDateTime mailSentAt;
}
