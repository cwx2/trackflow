package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-角色关联实体
 */
@Data
@TableName("user_role")
public class UserRole implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long roleId;
    private LocalDateTime createdAt;

    /**
     * 角色分配来源：
     * - "keycloak"：由 Keycloak 角色同步自动分配（可被反向同步撤销）
     * - "manual"：由管理员通过 TrackFlow 后台手动分配（不被 Keycloak 同步撤销）
     */
    private String source;

    /** 来源常量：Keycloak 同步分配 */
    public static final String SOURCE_KEYCLOAK = "keycloak";
    /** 来源常量：管理员手动分配 */
    public static final String SOURCE_MANUAL = "manual";
}
