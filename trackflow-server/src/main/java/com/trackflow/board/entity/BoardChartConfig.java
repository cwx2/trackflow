package com.trackflow.board.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 看板图表配置实体。
 * 项目级配置，控制看板图表类型和计算方式。
 */
@Data
@TableName("board_chart_config")
public class BoardChartConfig implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;

    /** 图表类型: burndown / cumulative_flow */
    private String chartType;

    /** Burndown 计算方式: issue_count / estimation / work_items */
    private String burndownCalculation;

    /** Issue 过滤器模式: all_cards / custom */
    private String issueFilterMode;

    /** 自定义过滤条件（当 issue_filter_mode='custom' 时使用） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String issueFilterQuery;

    /** 当前估算字段 ID（引用 custom_field_definition.id） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long estimationFieldId;

    /** 原始估算字段 ID（用于 Burndown 偏差计算） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long originalEstimationFieldId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
