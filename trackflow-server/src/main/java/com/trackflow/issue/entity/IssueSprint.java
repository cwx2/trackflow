package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单-Sprint 多对多关联实体。
 * 当看板配置 allowMultipleSprints=true 时，一个工单可属于多个 Sprint。
 */
@Data
@TableName("issue_sprint")
public class IssueSprint implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long issueId;

    private Long sprintId;

    private LocalDateTime createdAt;
}
