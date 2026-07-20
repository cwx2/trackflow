package com.trackflow.issue.vo;

import lombok.Data;

/**
 * 工单关注状态 VO，返回当前用户是否关注 + 关注者总数
 */
@Data
public class IssueWatcherStatusVO {
    /** 当前用户是否已关注 */
    private boolean watching;
    /** 关注者总数 */
    private int watcherCount;
}
