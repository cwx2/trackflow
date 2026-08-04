package com.trackflow.system.vo;

import lombok.Data;

/**
 * 用户摘要 VO — 用于悬停卡片等轻量级用户信息展示场景
 * 不含敏感信息，所有认证用户可访问
 */
@Data
public class UserSummaryVO {
    private String id;
    private String username;
    private String displayName;
    private String email;
    private String avatarUrl;
}
