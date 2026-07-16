package com.trackflow.sprint.vo;

import lombok.Data;

import java.util.List;

/**
 * Sprint 删除预览 VO：展示受影响工单数量和可迁移的目标 Sprint
 */
@Data
public class DeletionPreviewVO {

    /** Sprint 名称 */
    private String sprintName;

    /** Sprint 日期范围描述（如 "7/29 - 8/11"） */
    private String dateRange;

    /** 关联工单总数 */
    private int totalIssues;

    /** 可迁移的目标 Sprint 列表（当前项目中 planned/active 状态的 Sprint，排除自身） */
    private List<TargetSprintItem> targetSprints;

    @Data
    public static class TargetSprintItem {
        private String id;
        private String name;
        private String status;
    }
}
