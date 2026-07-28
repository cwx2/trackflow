package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "issue_activity", autoResultMap = true)
public class IssueActivity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long issueId;
    private Long userId;
    private String action;
    private String fieldName;
    private String oldValue;
    private String newValue;
    /** 旧值的人类可读展示文本（当 oldValue 存储 ID 时，此字段存储对应名称） */
    private String oldDisplayValue;
    /** 新值的人类可读展示文本（当 newValue 存储 ID 时，此字段存储对应名称） */
    private String newDisplayValue;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String detail;
    /**
     * 活动记录来源标识。
     * <ul>
     *   <li>{@code null} / {@code "manual"} — 用户手动操作</li>
     *   <li>{@code "automation"} — 工作流自动化规则触发</li>
     *   <li>{@code "workflow_action"} — 状态转换动作触发</li>
     *   <li>{@code "system"} — 系统内部操作（如成员移除触发的清空）</li>
     * </ul>
     */
    private String source;
    private LocalDateTime createdAt;
}
