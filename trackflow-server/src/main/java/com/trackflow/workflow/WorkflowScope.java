package com.trackflow.workflow;

/**
 * 工作流作用域工具类 —— 统一"全局工作流"的语义表达。
 * <p>
 * 系统中"全局工作流"的表达方式约定：
 * <ul>
 *   <li><b>前端/URL 层</b>：projectId = "0" 或 /projects/0/workflows</li>
 *   <li><b>Service/DB 层</b>：projectId = null（数据库 project_id IS NULL）</li>
 * </ul>
 * <p>
 * 所有 Controller 入口必须调用 {@link #fromApi(Long)} 将 API 层的 0 转为 null，
 * 所有需要向 API 层返回 projectId 的地方使用 {@link #toApi(Long)} 将 null 转为 0。
 * <p>
 * <b>禁止</b>在 Controller/Service 代码中直接写 {@code projectId == 0L} 判断。
 */
public final class WorkflowScope {

    private WorkflowScope() {
        // 工具类不允许实例化
    }

    /**
     * API 层表示"全局"的约定值。前端传 0 表示全局工作流。
     */
    public static final long API_GLOBAL = 0L;

    /**
     * 从 API 入参转为 Service/DB 层使用的值。
     * <p>
     * 转换规则：{@code null} 或 {@code 0L} → {@code null}（表示全局），其他值原样返回。
     *
     * @param apiProjectId 前端/Controller 层接收的 projectId
     * @return Service 层使用的 projectId（null 表示全局）
     */
    public static Long fromApi(Long apiProjectId) {
        return (apiProjectId == null || apiProjectId == API_GLOBAL) ? null : apiProjectId;
    }

    /**
     * 从 DB/Service 层的值转为 API 输出值。
     * <p>
     * 转换规则：{@code null} → {@code 0L}（API 层全局标识），其他值原样返回。
     *
     * @param dbProjectId 数据库或 Service 层的 projectId
     * @return API 层输出的 projectId（0L 表示全局）
     */
    public static Long toApi(Long dbProjectId) {
        return dbProjectId == null ? API_GLOBAL : dbProjectId;
    }

    /**
     * 判断是否为全局作用域。
     * <p>
     * 兼容 API 层（0L）和 Service 层（null）两种表达方式。
     *
     * @param projectId 任何层级的 projectId
     * @return true 表示全局作用域
     */
    public static boolean isGlobal(Long projectId) {
        return projectId == null || projectId == API_GLOBAL;
    }
}
