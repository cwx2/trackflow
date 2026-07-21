package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单投票实体 - 记录用户对工单的投票
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
@TableName("issue_vote")
public class IssueVote implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long issueId;

    private Long userId;

    private LocalDateTime createdAt;
}

