package com.trackflow.issue.vo;

import lombok.Data;

/**
 * 工单投票者信息 VO - 返回投票者的用户信息
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class IssueVoterVO {

    private String userId;

    private String displayName;

    private String username;

    private String avatarUrl;
}
