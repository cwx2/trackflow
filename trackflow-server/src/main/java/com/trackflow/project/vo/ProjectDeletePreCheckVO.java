package com.trackflow.project.vo;

import lombok.Data;

/**
 * 项目删除预检查结果 — 展示删除时受影响的数据量
 */
@Data
public class ProjectDeletePreCheckVO {
    /** 项目名称 */
    private String projectName;
    /** 项目 Key */
    private String projectKey;
    /** 工单数量 */
    private int issueCount;
    /** Sprint 数量 */
    private int sprintCount;
    /** 成员数量 */
    private int memberCount;
    /** 未关闭的工单数量 */
    private int openIssueCount;
    /** 工时记录数量 */
    private int timeEntryCount;
    /** 是否允许删除（active 或 archived 状态都可以删除） */
    private boolean deletable;
    /** 不允许删除的原因（如有） */
    private String reason;
}
