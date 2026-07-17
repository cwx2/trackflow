package com.trackflow.query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户查询收藏关联 — 实现个性化查询面板
 */
@Data
@TableName("user_query_favorite")
public class UserQueryFavorite implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long queryId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
