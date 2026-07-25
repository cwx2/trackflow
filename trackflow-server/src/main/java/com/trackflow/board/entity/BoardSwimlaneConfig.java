package com.trackflow.board.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板泳道配置实体。
 * 存储项目的 Swimlane 分组字段设置及值选择配置。
 */
@Data
@TableName(value = "board_swimlane_config", autoResultMap = true)
public class BoardSwimlaneConfig implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    /** 泳道分组字段：none/assignee/priority/type/sprint/tag/parent */
    private String groupByField;
    /** 选中的泳道值列表（JSONB），null 表示全选（向后兼容） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> selectedValues;
    /** 是否显示"未分类"泳道 */
    private Boolean showUncategorized;
    /** 未分类泳道位置：top / bottom */
    private String uncategorizedPosition;
    /**
     * Issues 模式下作为泳道行的 Issue 类型（如 "Epic"、"Feature"）。
     * 仅当 groupByField = "parent" 时有效。
     */
    private String swimlaneIssueType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
