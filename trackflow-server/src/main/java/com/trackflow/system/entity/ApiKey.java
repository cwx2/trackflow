package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * API Key 实体
 */
@Data
@TableName("api_key")
public class ApiKey implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String name;
    private String keyHash;
    private String prefix;
    private String permissions;  // JSON array string
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
