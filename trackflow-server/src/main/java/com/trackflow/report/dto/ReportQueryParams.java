package com.trackflow.report.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 报表执行引擎查询参数 — 替代 Map&lt;String, Object&gt; 传参，提供编译期类型安全
 *
 * <p>对应 ReportStatisticsMapper.xml 中 {@code selectReportGrouped} 和 {@code selectReportCross}
 * 两个 SQL 的动态参数。XML 中通过 {@code params.xxx} 引用本类属性。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class ReportQueryParams {

    // ─── 分组维度 ──────────────────────────────────────────

    /** 主分组维度（status/priority/type/assignee/project/custom_field_{id}） */
    private String groupBy;

    /** 第二分组维度（双维度交叉报表时使用） */
    private String secondGroupBy;

    /** 排序方式（count_desc/count_asc/label_asc/label_desc） */
    private String sortBy;

    // ─── 项目范围 ──────────────────────────────────────────

    /** 限定项目 ID 列表（权限过滤后的可访问项目） */
    private List<Long> projectIds;

    // ─── 时间范围 ──────────────────────────────────────────

    /** 时间范围起始 */
    private LocalDateTime timeStart;

    /** 时间范围截止 */
    private LocalDateTime timeEnd;

    /** 时间字段（created_at / updated_at / resolved_at） */
    private String timeField;

    // ─── 筛选条件 ──────────────────────────────────────────

    /** 包含的状态名称列表 */
    private List<String> statuses;

    /** 排除的状态名称列表 */
    private List<String> statusesExclude;

    /** 包含的优先级列表 */
    private List<String> priorities;

    /** 包含的工单类型列表 */
    private List<String> issueTypes;

    /** 包含的负责人 ID 列表 */
    private List<Long> assigneeIds;

    /** 指定 Sprint ID */
    private Long sprintId;

    /** 活跃 Sprint ID 列表（activeSprint 语义筛选时使用） */
    private List<Long> activeSprintIds;

    // ─── 语义快捷筛选 ──────────────────────────────────────

    /** 已关闭状态 ID 集合（配合 onlyClosedStatus / excludeClosedStatus 使用） */
    private Collection<Long> closedStatusIds;

    /** 仅查询已关闭状态的工单 */
    private Boolean onlyClosedStatus;

    /** 排除已关闭状态的工单 */
    private Boolean excludeClosedStatus;

    /** 仅查询未分配的工单 */
    private Boolean unassigned;

    /** 仅查询逾期工单（due_date < today 且未关闭） */
    private Boolean overdue;

    // ─── 主维度自定义字段分组 ──────────────────────────────

    /** 是否为自定义字段分组 */
    private Boolean isCustomFieldGroupBy;

    /** 自定义字段 ID */
    private Long customFieldId;

    /** 自定义字段是否为 list 类型（需要 JOIN option 表） */
    private Boolean customFieldIsListType;

    /** 自定义字段是否为 user 类型（需要 JOIN sys_user 表） */
    private Boolean customFieldIsUserType;

    // ─── 第二维度自定义字段分组 ──────────────────────────────

    /** 第二维度是否为自定义字段分组 */
    private Boolean isSecondCustomFieldGroupBy;

    /** 第二维度自定义字段 ID */
    private Long secondCustomFieldId;

    /** 第二维度自定义字段是否为 list 类型 */
    private Boolean secondCustomFieldIsListType;

    /** 第二维度自定义字段是否为 user 类型 */
    private Boolean secondCustomFieldIsUserType;
}
