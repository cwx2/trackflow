package com.trackflow.timeentry.vo;

import lombok.Data;

import java.util.List;

/**
 * 工作组工时汇总 VO（Work Groups 视图用）
 * 展示一个用户组及其成员的工时聚合
 */
@Data
public class GroupTimeSummaryVO {

    /** 用户组 ID */
    private String groupId;

    /** 用户组名称 */
    private String groupName;

    /** 用户组描述 */
    private String groupDescription;

    /** 组内所有成员工时总计（分钟） */
    private int totalDuration;

    /** 成员数量 */
    private int memberCount;

    /** 各成员工时汇总列表 */
    private List<MemberTimeSummaryVO> members;

    @Data
    public static class MemberTimeSummaryVO {
        /** 用户 ID */
        private String userId;

        /** 用户名 */
        private String username;

        /** 显示名称 */
        private String displayName;

        /** 头像 URL */
        private String avatarUrl;

        /** 该成员在查询期间的总工时（分钟） */
        private int totalDuration;

        /** 该成员的工时记录详情 */
        private List<TimeEntryVO> entries;
    }
}
