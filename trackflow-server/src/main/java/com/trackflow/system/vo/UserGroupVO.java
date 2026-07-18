package com.trackflow.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户组 VO
 */
@Data
public class UserGroupVO {

    private String id;
    private String name;
    private String description;
    private Integer memberCount;
    private Integer roleCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
