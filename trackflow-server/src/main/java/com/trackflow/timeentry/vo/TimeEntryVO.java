package com.trackflow.timeentry.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TimeEntryVO {
    private String id;
    private String issueId;
    private String issueKey;
    private String issueTitle;
    private String projectId;
    private String userId;
    private String userName;
    private String workDate;
    private Integer duration;       // minutes (null when ongoing=true)
    private Integer startTime;      // minutes from midnight
    private String description;
    private String createdAt;
    private String updatedAt;

    /**
     * 是否正在计时。
     * 为 true 时 duration 为 null，前端应根据 startedAt 实时计算已经过时间。
     */
    private Boolean ongoing;

    /**
     * 计时器启动时间（ISO 格式）。
     * 仅当 ongoing=true 时有意义，前端用 (now - startedAt) 显示实时时长。
     * 值等于 createdAt。
     */
    private String startedAt;

    /**
     * 记录操作人 ID（谁输入的这条工时）。
     * 若 loggedBy != userId，说明是代他人录入的。
     */
    private String loggedBy;

    /**
     * 记录操作人姓名。
     * 仅当 loggedBy != userId 时前端需要展示"由 XXX 代录"。
     */
    private String loggedByName;

    /**
     * 工单是否已被删除（软删除）。
     * 为 true 时前端应显示 "[已删除]" 标签。
     */
    private Boolean issueDeleted;

    /**
     * 工作类型名称（从 time_entry_attribute_value 解析得出）
     * 用于向下兼容展示，前端筛选应使用 activityId
     */
    private String workType;

    /**
     * 工作类型属性值 ID（用于筛选）
     */
    private String workTypeId;

    /**
     * 工作类型颜色
     */
    private String workTypeColor;

    /**
     * 工作项属性值列表
     * 每项包含: attributeId, attributeName, valueId, valueName, valueColor
     */
    private List<Map<String, String>> attributeValues;
}
