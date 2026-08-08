package com.trackflow.issue.vo;

import com.trackflow.issue.dto.TransitStatusResult;

/**
 * @deprecated Use {@link TransitStatusResult} instead.
 * This class is kept for backward compatibility during migration.
 */
@Deprecated(forRemoval = true)
public class TransitStatusResultVO extends TransitStatusResult {

    public TransitStatusResultVO() {
        super();
    }

    public TransitStatusResultVO(Integer version, com.trackflow.workflow.vo.ActionExecutionResult actionResult) {
        super(version, actionResult);
    }

    public static TransitStatusResultVO of(Integer version, com.trackflow.workflow.vo.ActionExecutionResult actionResult) {
        return new TransitStatusResultVO(version, actionResult);
    }
}
