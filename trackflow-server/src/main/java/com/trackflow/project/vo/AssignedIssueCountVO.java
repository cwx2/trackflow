package com.trackflow.project.vo;

import lombok.Data;

/**
 * 成员被分配工单数量 VO — 用于移除成员前的预检
 */
@Data
public class AssignedIssueCountVO {

    /** 该成员在该项目中被分配的工单数量 */
    private Integer count;
}
