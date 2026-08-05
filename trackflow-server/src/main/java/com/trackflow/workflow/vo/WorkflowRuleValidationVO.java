package com.trackflow.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工作流规则有效性校验结果 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRuleValidationVO {

    /** 是否全部通过 */
    private boolean valid;

    /** 错误列表 */
    private List<ValidationError> errors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        /** 错误位置：action / condition */
        private String location;
        /** 错误描述 */
        private String message;
        /** 引用的资源类型：field / tag / status / sprint */
        private String resourceType;
        /** 引用的资源标识 */
        private String resourceRef;
    }

    public static WorkflowRuleValidationVO ok() {
        return new WorkflowRuleValidationVO(true, List.of());
    }

    public static WorkflowRuleValidationVO fail(List<ValidationError> errors) {
        return new WorkflowRuleValidationVO(false, errors);
    }
}
