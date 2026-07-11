package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("issue_tag_relation")
public class IssueTagRelation implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long issueId;
    private Long tagId;
    private LocalDateTime createdAt;
}
