package com.trackflow.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件静音 token 实体。
 * <p>
 * 每封邮件通知中嵌入唯一 token，点击"静音此工单"链接时通过此 token
 * 识别用户身份和目标资源，完成静音操作，无需用户登录。
 */
@Data
@TableName("notification_email_mute_token")
public class EmailMuteToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 安全随机 token（UUID v4），嵌入邮件链接中 */
    private String token;

    /** 目标用户 ID */
    private Long userId;

    /** 资源类型（如 "issue"） */
    private String resourceType;

    /** 资源 ID */
    private Long resourceId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** token 有效期（默认 30 天） */
    private LocalDateTime expiresAt;

    /** 首次使用时间，null 表示未使用 */
    private LocalDateTime usedAt;
}
