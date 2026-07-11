import { ref, type Ref } from 'vue'
import { authApi } from '@/api'
import type { IssueVO } from '@/api/types'

/**
 * 权限管理 composable
 * 缓存用户在各项目中的权限，提供 canEdit 判断
 */
export function usePermission(issues: Ref<IssueVO[]>) {
  // projectId -> permissions set
  const permissionCache = ref<Record<string, Set<string>>>({})
  const loading = ref(false)

  /**
   * 加载列表中所有项目的权限（去重后批量加载）
   */
  async function loadPermissions() {
    const projectIds = [...new Set(issues.value.map(i => i.projectId))]
    const uncached = projectIds.filter(pid => !permissionCache.value[pid])

    if (uncached.length === 0) return

    loading.value = true
    try {
      await Promise.all(uncached.map(async (pid) => {
        try {
          const res = await authApi.getMyPermissions(pid)
          permissionCache.value[pid] = new Set(res.data || [])
        } catch {
          // 权限加载失败时默认无权限
          permissionCache.value[pid] = new Set()
        }
      }))
    } finally {
      loading.value = false
    }
  }

  /**
   * 判断当前用户是否可以编辑指定项目的 Issue
   */
  function canEditProject(projectId: string): boolean {
    const perms = permissionCache.value[projectId]
    if (!perms) return true // 未加载时默认允许，后端兜底
    return perms.has('issue:edit') || perms.has('system:admin')
  }

  /**
   * 判断指定 Issue 是否可编辑
   */
  function canEditIssue(issue: IssueVO): boolean {
    return canEditProject(issue.projectId)
  }

  return {
    permissionCache,
    loading,
    loadPermissions,
    canEditProject,
    canEditIssue
  }
}
