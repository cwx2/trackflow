package com.trackflow.timeentry.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工时查询结果封装 DO（替代 Map&lt;String, Object&gt;）。
 * <p>
 * 用于 TimeEntryMapper 中多表 JOIN 查询的结果映射，涵盖
 * selectEntriesWithIssueKey / selectEntriesByProjectForUser /
 * selectEntriesByProject / selectEntriesByGroupMembers 四个查询场景。
 * </p>
 *
 * <p>命名约定：此 DO 不是数据库 Entity，也不是对外 VO，
 * 仅作为 Mapper→Service 层之间的内部数据传输对象，放在 dto/ 目录下。</p>
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class TimeEntryQueryDO {

    // ===== time_entry 主表字段 =====
    private Long id;
    private Long issueId;
    private Long projectId;
    private Long userId;
    private Long loggedBy;
    private LocalDate workDate;
    private Integer duration;
    private Integer startTime;
    private String description;
    private Boolean ongoing;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== issue 关联字段 =====
    private String issueKey;
    private String issueTitle;
    /** 工单是否已被软删除（LEFT JOIN 时 issue.id IS NULL 或 deleted_at IS NOT NULL） */
    private Boolean issueDeleted;

    // ===== project 关联字段（仅 selectEntriesByProjectForUser 有） =====
    private String projectName;
    private String projectKey;

    // ===== sys_user 关联字段 =====
    /** 工时归属人显示名（user.display_name，仅 selectEntriesByProject 和 selectEntriesByGroupMembers 有） */
    private String userName;
    /** 工时归属人登录名（user.username，仅 selectEntriesByGroupMembers 有） */
    private String userUsername;
    /** 工时归属人头像 URL（仅 selectEntriesByGroupMembers 有） */
    private String userAvatarUrl;
    /** 代录人显示名（logged_by 用户的 display_name，仅当 logged_by != user_id 时有值） */
    private String loggedByName;

    // ===== work_item_attribute_value 关联字段（Work type） =====
    private String workType;
    private Long workTypeId;
    private String workTypeColor;
}
