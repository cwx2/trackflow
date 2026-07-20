import { ref, type Ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { loadProjectPermissions } from '@/composables/usePermission'
import type { IssueVO } from '@/api/types'

/**
 * Issue 列表专用的权限 composable
 * 
 * 与全局 src/composables/usePermission.ts 不同：
 * - 这个版本处理多项目场景（列表中的 issue 可能属于不同项目）
 * - 按 projectId 批量加载和缓存权限
 * - 提供 canEditIssue(issue) 方法对单个 issue 进行权限判断
 * 
 * 复用全局 composable 的 loadProjectPermissions 函数，共享缓存和 TTL 机制
 */
export function usePermission(issues: Ref<IssueVO[]>) {
  const authStore = useAuthStore()
  // projectId -> permissions set (本地引用，从全局缓存加载)
  const permissionCache = ref<Record<string, Set<string>>>({})
  const loading = ref(false)

  /**
   * 加载列表中所有项目的权限（去重后批量加载）
   * 复用全局 loadProjectPermissions，自动获得 TTL + 请求去重
   */
  async function loadPermissions() {
    if (authStore.hasGlobalPermission('system:admin')) return // admin 无需加载

    const projectIds = [...new Set(issues.value.map(i => i.projectId))]
    const uncached = projectIds.filter(pid => !permissionCache.value[pid])

    if (uncached.length === 0) return

    loading.value = true
    try {
      await Promise.all(uncached.map(async (pid) => {
        const perms = await loadProjectPermissions(pid)
        permissionCache.value[pid] = perms
      }))
    } finally {
      loading.value = false
    }
  }

  /**
   * 判断当前用户是否可以编辑指定 Issue
   * system:admin 直接返回 true
   * 未加载权限时返回 true（乐观策略，后端兜底）
   * 
   * 检查逻辑（与后端 PermissionService.hasIssuePermission 一致）：
   * 1. 项目级 issue:edit 权限 → 允许
   * 2. 是 reporter 且有 issue:edit_own → 允许
   * 3. 是 assignee 且有 issue:edit_assigned → 允许
   */
  function canEditIssue(issue: IssueVO): boolean {
    if (authStore.hasGlobalPermission('system:admin')) return true
    const perms = permissionCache.value[issue.projectId]
    if (!perms) return true // 未加载时默认允许，后端兜底
    // 1. 项目级 issue:edit
    if (perms.has('issue:edit')) return true
    // 2. 资源级：reporter + issue:edit_own
    const userId = authStore.user?.userId
    if (userId && userId === issue.reporterId && perms.has('issue:edit_own')) return true
    // 3. 资源级：assignee + issue:edit_assigned
    if (userId && userId === issue.assigneeId && perms.has('issue:edit_assigned')) return true
    return false
  }

  /**
   * 判断当前用户是否可以删除工单（任意已加载项目中具有 issue:delete 权限）
   * system:admin 直接返回 true
   */
  function canDeleteIssue(): boolean {
    if (authStore.hasGlobalPermission('system:admin')) return true
    // 只要任一已加载项目有 issue:delete 权限即可显示删除按钮
    // 后端对每个工单仍会做精确校验
    return Object.values(permissionCache.value).some(perms => perms.has('issue:delete'))
  }

  return {
    permissionCache,
    loading,
    loadPermissions,
    canEditIssue,
    canDeleteIssue
  }
}
