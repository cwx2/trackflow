package com.trackflow.workflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 工作流矩阵响应 VO（含版本号，用于乐观锁）
 */
@Data
public class WorkflowMatrixVO {

    /** 转换规则列表 */
    private List<WorkflowTransitionVO> transitions;

    /** 当前版本号（用于后续更新时的并发控制） */
    private Integer version;
}
