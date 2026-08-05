package com.trackflow.customfield.handler;

/**
 * 校验上下文 — 携带校验所需的项目信息。
 *
 * @author TrackFlow
 * @since 1.0
 */
public record ValidationContext(
        /** 工单所属项目 ID（用于 user 类型的成员校验），可为 null */
        Long projectId
) {

    public static final ValidationContext EMPTY = new ValidationContext(null);

    public static ValidationContext of(Long projectId) {
        return new ValidationContext(projectId);
    }
}
