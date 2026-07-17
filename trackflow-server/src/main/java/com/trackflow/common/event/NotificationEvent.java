package com.trackflow.common.event;

/**
 * 通知事件基类（sealed 接口）。
 * <p>
 * 所有通知事件都实现此接口，使用 @TransactionalEventListener(phase = AFTER_COMMIT)
 * 监听，确保只有在事务成功提交后才会触发通知发送。
 * <p>
 * 设计理由：
 * - 解决 @Async 在 @Transactional 方法内部调用时事务未提交即触发通知的问题
 * - 事务回滚时事件不会被消费，保证数据一致性
 * - 参考 OpenProject 的 after_commit + ActiveJob 模式
 */
public sealed interface NotificationEvent
        permits IssueNotificationEvent, SprintNotificationEvent, ProjectNotificationEvent {
}
