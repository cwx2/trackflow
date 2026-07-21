package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单 Key 变更历史 - 记录工单在项目间移动时旧 Key 到新 Key 的映射，支持旧 Key 重定向
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
@TableName("issue_key_history")
public class IssueKeyHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long issueId;

    private String oldKey;

    private String newKey;

    private LocalDateTime changedAt;

    private Long changedBy;
}
