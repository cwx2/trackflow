package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户标签收藏 — 用于侧边栏 Tags 分区展示
 */
@Data
@TableName("user_tag_favorite")
public class UserTagFavorite implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long tagId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
