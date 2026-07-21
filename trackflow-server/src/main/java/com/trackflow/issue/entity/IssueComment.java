package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.handler.LongArrayTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "issue_comment", autoResultMap = true)
public class IssueComment implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long issueId;
    private Long userId;
    private String content;
    private String source;
    private String emailMessageId;

    /**
     * 可见性限制：NULL=全体可见，非空列表=仅指定组ID的成员可见
     */
    @TableField(typeHandler = LongArrayTypeHandler.class)
    private List<Long> visibleToGroupIds;

    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
