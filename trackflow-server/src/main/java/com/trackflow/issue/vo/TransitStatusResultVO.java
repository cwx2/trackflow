package com.trackflow.issue.vo;

import com.trackflow.workflow.vo.ActionExecutionResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态转换 API 响应体。
 * <p>
 * 包含更新后的版本号（乐观锁同步）和可选的动作执行结果摘要。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransitStatusResultVO {

    /** 更新后的乐观锁版本号 */
    private Integer version;

    /**
     * 自动化动作执行结果（可为 null，表示无动作配置）。
     * 前端据此显示自动分配反馈 Toast。
     */
    private ActionExecutionResult actionResult;

    public static TransitStatusResultVO of(Integer version, ActionExecutionResult actionResult) {
        return new TransitStatusResultVO(version, actionResult);
    }
}
