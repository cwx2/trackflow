package com.trackflow.automation.trigger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/** 与业务事务一起落库的自动化事件，之后由调度器可靠投递到工作队列。 */
@Data
@TableName(value = "automation_event_inbox", autoResultMap = true)
public class AutomationEventInbox {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String eventType;
    private String eventKey;
    private Long projectId;
    private Long issueId;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String payload;
    private LocalDateTime occurredAt;
    private LocalDateTime processedAt;
    private String errorMessage;
    private Integer attempt;
    private Integer maxAttempts;
    private LocalDateTime availableAt;
}
