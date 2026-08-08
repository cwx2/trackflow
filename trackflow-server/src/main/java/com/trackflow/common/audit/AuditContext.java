package com.trackflow.common.audit;

import java.util.HashMap;
import java.util.Map;

/**
 * 审计上下文 ThreadLocal 工具——在业务方法内向 {@code @AuditLog} 切面传递额外审计详情。
 * <p>
 * 适用场景：当审计详情需要方法内部计算的中间值（如旧值、影响行数等），
 * 仅靠注解 SpEL 无法获取时，在方法体内调用 {@code AuditContext.put(...)} 补充。
 * <p>
 * 切面在写入审计日志时自动合并 AuditContext 中的数据，并在 finally 中清理 ThreadLocal。
 * <p>
 * 用法示例：
 * <pre>{@code
 * @AuditLog(action = "update_group", targetType = "user_group", targetId = "#groupId")
 * public UserGroup update(Long groupId, UpdateGroupDTO dto) {
 *     UserGroup group = groupMapper.selectById(groupId);
 *     AuditContext.put("oldName", group.getName());
 *     AuditContext.put("newName", dto.getName());
 *     // ... 业务逻辑 ...
 * }
 * }</pre>
 *
 * @author TrackFlow
 * @since 1.0
 * @see com.trackflow.common.annotation.AuditLog
 * @see com.trackflow.common.aspect.AuditLogAdvisor
 */
public final class AuditContext {

    private static final ThreadLocal<Map<String, Object>> CONTEXT = new ThreadLocal<>();

    private AuditContext() {
    }

    /**
     * 向当前线程的审计上下文中添加一个键值对。
     */
    public static void put(String key, Object value) {
        Map<String, Object> map = CONTEXT.get();
        if (map == null) {
            map = new HashMap<>();
            CONTEXT.set(map);
        }
        map.put(key, value);
    }

    /**
     * 获取当前线程的审计上下文（可能为 null）。
     */
    public static Map<String, Object> get() {
        return CONTEXT.get();
    }

    /**
     * 清除当前线程的审计上下文。由切面在 finally 中调用，防止内存泄漏。
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
