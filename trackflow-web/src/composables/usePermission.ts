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
 * 强制重新加载指定项目的权限（忽略 TTL 缓存）
 * 用于 403 响应后触发、或外部组件需要确保获取最新权限时调用
 * @since REQ-18
 */
export async function forceRefreshProjectPermissions(projectId: string): Promise<Set<string>> {
  // 先清除缓存
  projectPermissionsCache.delete(projectId)
  // 再重新加载
  return loadProjectPermissions(projectId)
}

/**
 * 项目级权限 composable
 * 
 * 用法:
 * ```ts
 * const { permissions, hasPermission, canEditIssue, canManageSprint, loading } = usePermission(projectId)
 * ```
 * 
 * @param projectIdRef - 获取当前 projectId 的 getter
 * @param options - 可选配置
 * @param options.isProjectArchived - 项目是否归档的 getter，归档时所有写权限返回 false
 */
export function usePermission(
  projectIdRef: () => string | undefined,
  options?: { isProjectArchived?: () => boolean }
) {
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
      // 先同步检查缓存——如果缓存命中，无需设置 loading 状态，避免按钮闪烁
      const cached = projectPermissionsCache.get(projectId)
      if (cached && isCacheValid(cached)) {
        permissions.value = cached.permissions
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
   * 权限加载中时返回 false（悲观策略），避免无权用户看到闪烁的操作按钮
   */
  function hasPermission(permission: string): boolean {
    if (authStore.hasGlobalPermission('system:admin')) return true
    if (loading.value) return false // 加载中隐藏操作按钮，避免无权用户看到闪烁
    return permissions.value.has(permission)
  }

  /**
   * 检查写权限：归档项目自动返回 false
   */
  function hasWritePermission(permission: string): boolean {
    if (options?.isProjectArchived?.()) return false
    return hasPermission(permission)
  }

  // ===== 便捷 computed =====

  /** 是否可以创建 Issue */
  const canCreateIssue = computed(() => hasWritePermission('issue:create'))

  /** 是否可以编辑 Issue */
  const canEditIssue = computed(() => hasWritePermission('issue:edit'))

  /** 是否可以删除 Issue */
  const canDeleteIssue = computed(() => hasWritePermission('issue:delete'))

  /** 是否可以分配 Issue */
  const canAssignIssue = computed(() => hasWritePermission('issue:assign'))

  /** 是否可以变更 Issue 状态 */
  const canChangeStatus = computed(() => hasWritePermission('issue:change_status'))

  /** 是否可以评论 */
  const canComment = computed(() => hasWritePermission('issue:comment'))

  /** 是否可以创建 Sprint */
  const canCreateSprint = computed(() => hasWritePermission('sprint:create'))

  /** 是否可以编辑 Sprint */
  const canEditSprint = computed(() => hasWritePermission('sprint:edit'))

  /** 是否可以删除 Sprint */
  const canDeleteSprint = computed(() => hasWritePermission('sprint:delete'))

  /** 是否可以查看 Sprint */
  const canViewSprint = computed(() => hasPermission('sprint:view'))

  /** 是否可以管理工作流 */
  const canManageWorkflow = computed(() => hasWritePermission('project:manage_workflow'))

  /** 是否可以管理项目成员 */
  const canManageMembers = computed(() => hasWritePermission('project:manage_members'))

  /** 是否可以编辑项目 */
  const canEditProject = computed(() => hasWritePermission('project:edit'))

  /** 是否可以记录工时 */
  const canLogTime = computed(() => hasWritePermission('time:log'))

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
    canEditProject,
    canLogTime
  }
}

// ===== 基于项目 ID 的同步权限检查（依赖缓存） =====

/**
 * 同步检查指定项目的权限（基于缓存）
 * 如果缓存未命中，返回 false（悲观策略）
 * 
 * @param projectId - 项目 ID
 * @param permission - 权限标识（如 'sprint:edit'）
 * @returns 是否具有该权限
 */
export function checkPermissionSync(projectId: string | undefined, permission: string): boolean {
  if (!projectId) return false
  
  // system:admin 自动拥有所有权限
  const authStore = useAuthStore()
  if (authStore.hasGlobalPermission('system:admin')) return true
  
  const cached = projectPermissionsCache.get(projectId)
  if (!cached || !isCacheValid(cached)) {
    // 缓存未命中，触发异步加载，但本次返回 false
    loadProjectPermissions(projectId)
    return false
  }
  return cached.permissions.has(permission)
}

/**
 * 预加载多个项目的权限到缓存
 * 用于批量场景，如 Sprint 列表渲染前预加载所有涉及项目的权限
 * 
 * @param projectIds - 项目 ID 数组
 * @returns Promise，所有权限加载完成后 resolve
 */
export async function preloadPermissions(projectIds: string[]): Promise<void> {
  const uniqueIds = [...new Set(projectIds.filter(id => !!id))]
  await Promise.all(uniqueIds.map(id => loadProjectPermissions(id)))
}

/**
 * 基于项目 ID 同步检查 Sprint 编辑权限
 */
export function canEditSprintSync(projectId: string | undefined): boolean {
  return checkPermissionSync(projectId, 'sprint:edit')
}

/**
 * 基于项目 ID 同步检查 Sprint 删除权限
 */
export function canDeleteSprintSync(projectId: string | undefined): boolean {
  return checkPermissionSync(projectId, 'sprint:delete')
}

/**
 * 基于项目 ID 同步检查 Sprint 创建权限
 */
export function canCreateSprintSync(projectId: string | undefined): boolean {
  return checkPermissionSync(projectId, 'sprint:create')
}
