package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单关注（Watcher）实体。
 * 每条记录表示一个用户关注了一个工单，关注后该工单的所有更新都会通知该用户。
 */
@Data
@TableName("issue_watcher")
public class IssueWatcher implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 被关注的工单ID */
    private Long issueId;

    /** 关注者用户ID */
    private Long userId;

    private LocalDateTime createdAt;
}
