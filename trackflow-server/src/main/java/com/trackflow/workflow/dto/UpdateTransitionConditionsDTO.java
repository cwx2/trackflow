package com.trackflow.workflow.dto;

import jakarta.validation.constraints.NotNull;
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
     */
    @Data
    public static class ConditionItem {

        /**
         * 评估的字段名。
         * 支持：assignee_id, status_id, priority, issue_type, reporter_id, sprint_id, due_date, title
         */
        @NotNull(message = "字段名不能为空")
        private String field;

        /**
         * 操作符。
         * 支持：equals, not_equals, contains, in, is_empty, is_not_empty
         */
        @NotNull(message = "操作符不能为空")
        private String operator;

        /**
         * 期望值（is_empty / is_not_empty 操作符不需要此字段）。
         * in 操作符时为逗号分隔的多个值。
         */
        private String value;
    }
}
