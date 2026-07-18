package com.trackflow.quickaction.vo;

import lombok.Data;

/**
 * 快捷动作执行结果 VO
 */
@Data
public class QuickActionExecutionResultVO {

    private boolean success;

    /** 生成的评论 ID */
    private String commentId;

    /** 是否已发送邮件 */
    private boolean mailSent;

    /** 邮件发送错误信息（如有） */
    private String mailError;

    /** 状态变更前 */
    private String statusBefore;

    /** 状态变更后（如有） */
    private String statusAfter;

    /** 执行日志 ID */
    private String logId;

    public static QuickActionExecutionResultVO success(String commentId, boolean mailSent, String logId) {
        QuickActionExecutionResultVO vo = new QuickActionExecutionResultVO();
        vo.setSuccess(true);
        vo.setCommentId(commentId);
        vo.setMailSent(mailSent);
        vo.setLogId(logId);
        return vo;
    }

    public static QuickActionExecutionResultVO successWithTransition(
            String commentId, boolean mailSent, String logId,
            String statusBefore, String statusAfter) {
        QuickActionExecutionResultVO vo = success(commentId, mailSent, logId);
        vo.setStatusBefore(statusBefore);
        vo.setStatusAfter(statusAfter);
        return vo;
    }
}
