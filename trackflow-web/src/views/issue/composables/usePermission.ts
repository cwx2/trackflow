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
   */
  function canEditIssue(issue: IssueVO): boolean {
    if (authStore.hasGlobalPermission('system:admin')) return true
    const perms = permissionCache.value[issue.projectId]
    if (!perms) return true // 未加载时默认允许，后端兜底
    return perms.has('issue:edit')
  }

  return {
    permissionCache,
    loading,
    loadPermissions,
    canEditIssue
  }
}
