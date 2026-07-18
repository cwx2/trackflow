package com.trackflow.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户项目收藏实体
 */
@Data
@TableName("user_project_favorite")
public class UserProjectFavorite {

    private Long id;
    private Long userId;
    private Long projectId;
    private LocalDateTime createdAt;
}
