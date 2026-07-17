package com.trackflow.integration.vo;

import lombok.Data;

/**
 * 邮件服务器配置 VO（管理员视图）
 * 密码字段返回时脱敏
 */
@Data
public class EmailConfigVO {

    /** SMTP 服务器地址 */
    private String host;

    /** SMTP 端口 */
    private Integer port;

    /** 连接协议：plain / ssl / starttls */
    private String protocol;

    /** 认证用户名 */
    private String username;

    /** 密码（脱敏展示：已配置时返回 "******"，未配置返回空） */
    private String password;

    /** 是否启用 SSL */
    private Boolean sslEnabled;

    /** 发件人地址 */
    private String fromAddress;

    /** 回复地址 */
    private String replyToAddress;

    /** 是否已配置（所有必填字段都有值） */
    private Boolean configured;
}
