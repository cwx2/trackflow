package com.trackflow.board.vo;

import com.trackflow.customfield.vo.CustomFieldValueVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板卡片精简 VO — 仅包含卡片渲染所需的字段。
 * <p>
 * 相比 IssueVO（30+ 字段），本 VO 仅保留看板卡片展示所需的核心字段，
 * 配合单次 JOIN SQL 查询，避免循环分页 + 全套关联逻辑的性能浪费。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class BoardCardVO {

    private String id;
    private String projectId;
    /** 项目 Key（如 DE4、APP），多项目看板时用于区分来源项目 */
    private String projectKey;
    private String issueKey;
    private String title;
    private String issueType;
    private String statusId;
    private String statusName;
    private String statusColor;
    private String priority;
    private String assigneeId;
    private String assigneeName;
    private String assigneeAvatarUrl;
    private String sprintId;
    private String sprintName;
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    /** 直接子工单总数 */
    private Integer childCount;
    /** 已关闭的直接子工单数 */
    private Integer childClosedCount;

    /** 父工单 ID（用于 Issues 类型泳道分组） */
    private String parentId;
    /** 父工单 issue key */
    private String parentIssueKey;
    /** 父工单标题 */
    private String parentTitle;

    /**
     * 自定义字段结构化详情（仅包含卡片配置中 visibleFields 指定的自定义字段）
     */
    private List<CustomFieldValueVO> customFieldDetails;
}
