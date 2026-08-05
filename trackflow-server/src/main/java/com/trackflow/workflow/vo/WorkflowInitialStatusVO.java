package com.trackflow.workflow.vo;

import lombok.Data;

/**
 * 工作流初始状态配置 VO
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class WorkflowInitialStatusVO {
    private String id;
    private String projectId;
    private String issueType;
    private String statusId;
}
