package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("issue_status")
public class IssueStatus implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String code;
    private String color;
    private String category;
    private Boolean isDefault;
    private Boolean isClosed;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
