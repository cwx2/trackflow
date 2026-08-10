import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { authApi } from '@/api'
import { useAuthStore } from './auth'

/**
 * 全局权限 Store
 *
 * 从 auth.ts 分离出来的权限管理逻辑，负责：
 * - 全局权限的加载和缓存
 * - 定期轮询刷新权限（每 5 分钟）
 * - 页面可见性恢复时刷新权限
 * - 权限检查方法
 */

/** 权限定期刷新间隔（5 分钟） */
const PERMISSION_REFRESH_INTERVAL = 5 * 60 * 1000

export const usePermissionStore = defineStore('permission', () => {
  const authStore = useAuthStore()

  const globalPermissions = ref<Set<string>>(new Set())
  const permissionsLoaded = ref(false)

  // 去重用 Promise
  let _permissionLoadPromise: Promise<void> | null = null
  // 定期刷新定时器
  let permissionRefreshTimer: ReturnType<typeof setInterval> | null = null

  /**
   * 加载当前用户的全局权限
   * 支持并发调用去重：多次调用只发起一次请求
   *
   * 注意：加载失败时 permissionsLoaded 保持 false，确保路由守卫下次导航时重试，
   * 避免因后端短暂不可用导致用户被永久锁定在无权限状态。
   */
  async function loadGlobalPermissions(): Promise<void> {
    if (!authStore.isAuthenticated) return
    if (permissionsLoaded.value) return

    // 去重：如果已有正在进行的请求，复用同一个 Promise
    if (_permissionLoadPromise) return _permissionLoadPromise

    _permissionLoadPromise = (async () => {
      try {
        const res = await authApi.getMyGlobalPermissions()
        globalPermissions.value = new Set(res.data || [])
        permissionsLoaded.value = true
      } catch (e) {
        console.warn('[permission] Failed to load global permissions, will retry on next navigation', e)
        // 不设置 permissionsLoaded = true，确保下次导航时重试
      } finally {
        _permissionLoadPromise = null
      }
    })()

    return _permissionLoadPromise
  }

  /**
   * 强制刷新全局权限（忽略 permissionsLoaded 标志）
   * 用于：403 响应触发、定期轮询、页面可见性恢复
   */
  async function refreshGlobalPermissions(): Promise<void> {
    if (!authStore.isAuthenticated) return

    // 去重：复用正在进行的请求
    if (_permissionLoadPromise) return _permissionLoadPromise

    _permissionLoadPromise = (async () => {
      try {
        const res = await authApi.getMyGlobalPermissions()
        globalPermissions.value = new Set(res.data || [])
      } catch (e) {
        console.warn('[permission] Failed to refresh global permissions', e)
      } finally {
        permissionsLoaded.value = true
        _permissionLoadPromise = null
      }
    })()

    return _permissionLoadPromise
  }

  /**
   * 检查用户是否拥有指定的全局权限
   */
  function hasGlobalPermission(permission: string): boolean {
    if (globalPermissions.value.has('system:admin')) return true
    return globalPermissions.value.has(permission)
  }

  /**
   * 当前用户是否可以创建工单（全局级判断）
   * system:admin 或在任意项目中拥有 issue:create 权限
   */
  const canCreateIssue = computed(() => {
    return hasGlobalPermission('nav:create_issue')
  })

  // ===== 定期轮询刷新全局权限 =====
  function startPermissionRefresh() {
    stopPermissionRefresh()
    permissionRefreshTimer = setInterval(() => {
      if (authStore.isAuthenticated && permissionsLoaded.value) {
        refreshGlobalPermissions()
      }
    }, PERMISSION_REFRESH_INTERVAL)
  }

  function stopPermissionRefresh() {
    if (permissionRefreshTimer !== null) {
      clearInterval(permissionRefreshTimer)
      permissionRefreshTimer = null
    }
  }

  /**
   * 清除权限状态（登出时由 auth store 调用）
   */
  function clearPermissions() {
    globalPermissions.value = new Set()
    permissionsLoaded.value = false
    stopPermissionRefresh()
  }

  // 页面可见性变化时刷新权限（用户从后台切回前台）
  let lastVisibilityRefresh = 0
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible' && authStore.isAuthenticated) {
        const now = Date.now()
        if (now - lastVisibilityRefresh < 30000) return
        lastVisibilityRefresh = now

        if (permissionsLoaded.value) {
          refreshGlobalPermissions()
          // 同时清除项目级权限缓存，下次访问时重新加载
          import('@/composables/usePermission').then(({ invalidateProjectPermissions }) => {
            invalidateProjectPermissions()
          }).catch(() => { /* ignore */ })
        }
      }
    })
  }

  // 认证状态变化时启动/停止定期刷新
  watch(() => authStore.isAuthenticated, (authenticated) => {
    if (authenticated) {
      startPermissionRefresh()
    } else {
      clearPermissions()
    }
  }, { immediate: true })

  return {
    globalPermissions,
    permissionsLoaded,
    canCreateIssue,
    loadGlobalPermissions,
    refreshGlobalPermissions,
    hasGlobalPermission,
    clearPermissions
  }
})
