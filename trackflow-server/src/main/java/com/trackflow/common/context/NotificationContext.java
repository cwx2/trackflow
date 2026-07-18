package com.trackflow.common.context;

/**
 * 通知上下文（线程局部变量）。
 * 用于 "Apply without notice" 场景：批量操作时跳过通知发送。
 *
 * 使用方式：
 * <pre>
 * NotificationContext.setSilent(true);
 * try {
 *     // ... 执行操作，内部的事件发布将被监听器忽略
 * } finally {
 *     NotificationContext.clear();
 * }
 * </pre>
 */
public final class NotificationContext {

    private static final ThreadLocal<Boolean> SILENT = ThreadLocal.withInitial(() -> false);

    private NotificationContext() {}

    public static boolean isSilent() {
        return Boolean.TRUE.equals(SILENT.get());
    }

    public static void setSilent(boolean silent) {
        SILENT.set(silent);
    }

    public static void clear() {
        SILENT.remove();
    }
}
