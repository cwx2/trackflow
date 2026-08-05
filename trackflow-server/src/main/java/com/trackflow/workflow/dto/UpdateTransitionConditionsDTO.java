package com.trackflow.workflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新工作流转换守卫条件 DTO
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class UpdateTransitionConditionsDTO {

    /** 守卫条件列表（null 或空列表 = 清除所有守卫条件） */
    private List<ConditionItem> conditions;

    /**
     * 单个守卫条件项。
     * 多个条件之间是 AND 关系。
     * <p>
     * 两种模式：
     * <ul>
     *   <li>字段条件：设置 field + operator [+ value]</li>
     *   <li>高级条件：设置 conditionType [+ linkType 等参数]</li>
     * </ul>
     */
    @Data
    public static class ConditionItem {

        /**
         * 高级条件类型。设置此字段时 field/operator 可为空。
         * 支持：links_resolved, children_resolved
         */
        private String conditionType;

        /**
         * links_resolved 条件的关联类型。
         * 支持：subtask_of, parent_of, blocks
         */
        private String linkType;

        /**
         * 评估的字段名（字段条件模式）。
         * 支持：assignee_id, status_id, priority, issue_type, reporter_id, sprint_id, due_date, title
         */
        private String field;

        /**
         * 操作符（字段条件模式）。
         * 支持：equals, not_equals, contains, in, is_empty, is_not_empty
         */
        private String operator;

        /**
         * 期望值（is_empty / is_not_empty 操作符不需要此字段）。
         * in 操作符时为逗号分隔的多个值。
         */
        private String value;
    }
}
