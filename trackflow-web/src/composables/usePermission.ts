import { ref, computed, watch } from 'vue'
import { authApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

/**
 * 缓存 TTL（毫秒）：与后端 Redis 权限缓存 TTL 对齐
 */
const CACHE_TTL_MS = 5 * 60 * 1000 // 5 分钟

/**
 * 项目级权限缓存
 * key: projectId, value: { permissions, cachedAt }
 */
interface CacheEntry {
  permissions: Set<string>
  cachedAt: number
}
const projectPermissionsCache = new Map<string, CacheEntry>()
const loadingProjects = new Map<string, Promise<Set<string>>>()

/**
 * 检查缓存是否有效（未过期）
 */
function isCacheValid(entry: CacheEntry): boolean {
  return Date.now() - entry.cachedAt < CACHE_TTL_MS
}

/**
 * 加载指定项目的权限（带去重和缓存 + TTL）
 * 也供 Issue 列表版 composable 复用
 */
export async function loadProjectPermissions(projectId: string): Promise<Set<string>> {
  const cached = projectPermissionsCache.get(projectId)
  if (cached && isCacheValid(cached)) {
    return cached.permissions
  }

  // 去重：如果已有正在进行的请求，复用
  if (loadingProjects.has(projectId)) {
    return loadingProjects.get(projectId)!
  }

  const promise = (async () => {
    try {
      const res = await authApi.getMyPermissions(projectId)
      const perms = new Set<string>(res.data || [])
      projectPermissionsCache.set(projectId, { permissions: perms, cachedAt: Date.now() })
      return perms
    } catch (e) {
      console.warn('[usePermission] Failed to load permissions for project', projectId, e)
      return new Set<string>()
    } finally {
      loadingProjects.delete(projectId)
    }
  })()

  loadingProjects.set(projectId, promise)
  return promise
}

/**
 * 失效指定项目的权限缓存（角色变更后调用）
 */
export function invalidateProjectPermissions(projectId?: string) {
  if (projectId) {
    projectPermissionsCache.delete(projectId)
  } else {
    projectPermissionsCache.clear()
  }
}

/**
 * 项目级权限 composable
 * 
 * 用法:
 * ```ts
 * const { permissions, hasPermission, canEditIssue, canManageSprint, loading } = usePermission(projectId)
 * ```
 */
export function usePermission(projectIdRef: () => string | undefined) {
  const authStore = useAuthStore()
  const permissions = ref<Set<string>>(new Set())
  const loading = ref(false)

  // 监听 projectId 变化，重新加载权限
  watch(
    projectIdRef,
    async (projectId) => {
      if (!projectId) {
        permissions.value = new Set()
        return
      }
      loading.value = true
      try {
        permissions.value = await loadProjectPermissions(projectId)
      } finally {
        loading.value = false
      }
    },
    { immediate: true }
  )

  /**
   * 检查是否拥有指定权限
   * system:admin 自动拥有所有权限
   * 权限加载中时返回 true（乐观策略，后端兜底），避免按钮闪烁
   */
  function hasPermission(permission: string): boolean {
    if (authStore.hasGlobalPermission('system:admin')) return true
    if (loading.value) return true // 加载中不隐藏按钮，避免 UI 闪烁
    return permissions.value.has(permission)
  }

  // ===== 便捷 computed =====

  /** 是否可以创建 Issue */
  const canCreateIssue = computed(() => hasPermission('issue:create'))

  /** 是否可以编辑 Issue */
  const canEditIssue = computed(() => hasPermission('issue:edit'))

  /** 是否可以删除 Issue */
  const canDeleteIssue = computed(() => hasPermission('issue:delete'))

  /** 是否可以分配 Issue */
  const canAssignIssue = computed(() => hasPermission('issue:assign'))

  /** 是否可以变更 Issue 状态 */
  const canChangeStatus = computed(() => hasPermission('issue:change_status'))

  /** 是否可以评论 */
  const canComment = computed(() => hasPermission('issue:comment'))

  /** 是否可以创建 Sprint */
  const canCreateSprint = computed(() => hasPermission('sprint:create'))

  /** 是否可以编辑 Sprint */
  const canEditSprint = computed(() => hasPermission('sprint:edit'))

  /** 是否可以删除 Sprint */
  const canDeleteSprint = computed(() => hasPermission('sprint:delete'))

  /** 是否可以查看 Sprint */
  const canViewSprint = computed(() => hasPermission('sprint:view'))

  /** 是否可以管理工作流 */
  const canManageWorkflow = computed(() => hasPermission('project:manage_workflow'))

  /** 是否可以管理项目成员 */
  const canManageMembers = computed(() => hasPermission('project:manage_members'))

  /** 是否可以编辑项目 */
  const canEditProject = computed(() => hasPermission('project:edit'))

  return {
    permissions,
    loading,
    hasPermission,
    canCreateIssue,
    canEditIssue,
    canDeleteIssue,
    canAssignIssue,
    canChangeStatus,
    canComment,
    canCreateSprint,
    canEditSprint,
    canDeleteSprint,
    canViewSprint,
    canManageWorkflow,
    canManageMembers,
    canEditProject
  }
}
