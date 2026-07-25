package com.trackflow.board.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 看板收藏实体 - 记录用户收藏的看板（项目）
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
@TableName("board_favorite")
public class BoardFavorite {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long projectId;
    private LocalDateTime createdAt;
}
