import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** 是否需要认证（未登录跳转登录页） */
    requiresAuth?: boolean
    /** 是否需要管理员权限 */
    requiresAdmin?: boolean
    /** 是否需要工作流管理权限 */
    requiresWorkflow?: boolean
    /** 是否需要报表查看权限 */
    requiresReport?: boolean
    /** 是否需要项目编辑权限 */
    requiresProjectEdit?: boolean
    /** 是否需要 Sprint 管理权限 */
    requiresSprintManage?: boolean
    /** 是否需要创建工单权限 */
    requiresCreateIssue?: boolean
    /** 细粒度权限标识（如 system:manage_users） */
    requiredPermission?: string
    /** 页面标题（用于 document.title） */
    title?: string
  }
}
