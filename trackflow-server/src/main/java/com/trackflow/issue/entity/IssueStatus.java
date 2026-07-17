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
    private String displayName;
    private String code;
    private String color;
    private String category;
    private Boolean isDefault;
    private Boolean isClosed;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    /**
     * 获取面向用户的显示名称（优先 displayName，fallback 到 name）。
     * 用于通知、活动记录等后端生成的文本。
     */
    public String getLocalizedName() {
        return displayName != null && !displayName.isBlank() ? displayName : name;
    }
}
