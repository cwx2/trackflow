package com.trackflow.board.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 看板列合并配置实体。
 * 将多个状态合并显示为同一列。
 */
@Data
@TableName("board_column_merge")
public class BoardColumnMerge implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    /** 合并组标识（同一 group 内的状态合并为一列） */
    private String mergeGroupId;
    /** 合并后的列标题 */
    private String mergeTitle;
    /** 被合并的状态 ID */
    private Long statusId;
    /** 组内排序 */
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
