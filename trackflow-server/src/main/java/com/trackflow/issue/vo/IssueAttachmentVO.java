package com.trackflow.issue.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class IssueAttachmentVO {
    private String id;
    private String issueId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private String uploadedBy;
    private LocalDateTime createdAt;
}
