package com.trackflow.board.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新看板基本设置 DTO。
 */
@Data
public class UpdateBoardGeneralConfigDTO {

    /** 乐观锁版本号（从 GET 响应中获取，用于并发冲突检测） */
    private Integer configVersion;

    /** 看板名称（可为空，为空时前端使用默认名） */
    @Size(max = 100, message = "看板名称不能超过 100 个字符")
    private String name;

    /** 可查看看板的角色代码列表 */
    @NotNull(message = "查看权限角色列表不能为空")
    @Size(min = 1, message = "至少需要一个角色可以查看看板")
    private List<String> canViewRoles;

    /** 可编辑看板设置的角色代码列表 */
    @NotNull(message = "编辑权限角色列表不能为空")
    @Size(min = 1, message = "至少需要一个角色可以编辑看板设置")
    private List<String> canEditRoles;

    /** 看板过滤模式：all | active_sprint | query */
    @Pattern(regexp = "^(all|active_sprint|query)$", message = "过滤模式只允许: all, active_sprint, query")
    private String filterMode;

    /** 查询过滤条件（JSON 数组字符串），当 filterMode='query' 时必填 */
    @Size(max = 4000, message = "查询过滤条件不能超过 4000 个字符")
    private String filterQuery;

    /** 已完成工单保留天数（null 表示不限制） */
    @Min(value = 1, message = "保留天数必须大于 0")
    private Integer doneRetentionDays;

    /** 看板列标识字段：status | priority（默认 status） */
    @Pattern(regexp = "^(status|priority)$", message = "列标识字段只允许: status, priority")
    private String columnField;

    /**
     * 是否允许卡片分配到多个迭代（Sprint）。
     * 对应 YouTrack Board Settings > Cards Tab: Allow cards to be assigned to multiple sprints。
     * null 时默认为 false（向后兼容）。
     */
    private Boolean allowMultipleSprints;

    /**
     * Backlog 视图模式：list=平铺列表（默认），tree=树形层级。
     * null 时保持原值不变（向后兼容）。
     */
    @Pattern(regexp = "^(list|tree)$", message = "Backlog 视图模式只允许: list, tree")
    private String backlogViewMode;

    /**
     * 过滤 Backlog 工单的保存搜索 ID。
     * null 表示使用默认过滤（不在看板上的工单）。
     */
    private Long backlogSavedQueryId;
}
