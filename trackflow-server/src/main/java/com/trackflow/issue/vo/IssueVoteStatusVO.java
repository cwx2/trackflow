package com.trackflow.issue.vo;

import lombok.Data;

/**
 * 工单投票状态 VO - 返回当前用户投票状态和投票总数
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class IssueVoteStatusVO {

    /**
     * 当前用户是否已投票
     */
    private boolean voted;

    /**
     * 投票者总数
     */
    private int voteCount;
}
