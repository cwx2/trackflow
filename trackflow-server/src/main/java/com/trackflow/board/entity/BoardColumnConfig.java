package com.trackflow.board.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 看板列配置实体。
 * 不继承 BaseEntity，因为这是项目级配置表，无需 createdBy/updatedBy 审计字段。
 */
@Data
@TableName("board_column_config")
public class BoardColumnConfig implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long statusId;
    private Boolean visible;
    private Integer sortOrder;
    private Boolean collapsed;
    private Integer wipMin;
    private Integer wipMax;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
