package com.trackflow.timeentry.vo;

import lombok.Data;

/**
 * 时间表用户选择器中的用户信息 VO（轻量级）
 */
@Data
public class TimeEntryUserVO {
    private String id;
    private String username;
    private String displayName;
    private String avatarUrl;
}
