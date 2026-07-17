package com.trackflow.board.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 看板泳道配置实体。
 * 存储项目的 Swimlane 分组字段设置。
 */
@Data
@TableName("board_swimlane_config")
public class BoardSwimlaneConfig implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    /** 泳道分组字段：none/assignee/priority/type/sprint/tag */
    private String groupByField;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
