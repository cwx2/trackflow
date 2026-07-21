package com.trackflow.project.vo;

import lombok.Data;

/**
 * 成员操作结果 VO — 用于成员移除/角色变更等操作的响应
 */
@Data
public class MemberOperationResultVO {

    /** 受影响的工单数量 */
    private Integer affectedIssueCount;
}
