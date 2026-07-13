package com.trackflow.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户信息 VO
 * 对应 GET /api/v1/auth/me 的响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO {

    /** Keycloak subject ID */
    private String keycloakId;

    /** 登录用户名 */
    private String username;

    /** 显示名称 */
    private String displayName;

    /** 邮箱 */
    private String email;
}
