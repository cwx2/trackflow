package com.trackflow.issue.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
public class UpdateIssueDTO {

    @Size(max = 500, message = "标题不能超过500字符")
    private String title;

    private String description;
    private String issueType;
    private String priority;
    private Long assigneeId;
    private Long sprintId;
    private Long parentId;
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private Map<String, Object> customFields;

    /**
     * 乐观锁版本号。前端从 GET 响应中获取，PUT 时携带回来。
     * 为 null 时兼容旧客户端（跳过版本校验）。
     */
    private Integer version;
}
