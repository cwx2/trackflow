package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 组织级访问控制实体
 */
@Data
@TableName("org_access")
public class OrgAccess implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orgId;
    private Long userId;
    private Long roleId;
    private LocalDateTime createdAt;
    private Long createdBy;
}
