package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("issue_attachment")
public class IssueAttachment implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long issueId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
