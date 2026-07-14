import { ref, computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { dashboardApi } from '@/api'

/**
 * 导航栏 Badge 数据 composable
 * 
 * 为导航菜单提供角色相关的 Badge 数量显示：
 * - 测试人员：显示"待测试"工单数量
 * - 所有用户：根据权限判断是否显示 Sprint 只读标识
 * 
 * 数据通过 Dashboard Summary API 获取，定时轮询刷新。
 */

const REFRESH_INTERVAL = 3 * 60 * 1000 // 3 分钟轮询

// 模块级状态（单例，多个组件共享）
const testingCount = ref(0)
const primaryRoleCode = ref<string | null>(null)
const loaded = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null
let isLoading = false

export function useNavBadge() {
  const authStore = useAuthStore()

  const isTester = computed(() => primaryRoleCode.value === 'tester')

  /** 是否有 Sprint 管理权限（创建/编辑） */
  const canManageSprint = computed(() => {
    if (authStore.hasGlobalPermission('system:admin')) return true
    if (authStore.permissionsLoaded) {
      return authStore.hasGlobalPermission('nav:sprint_manage')
    }
    // 权限未加载时乐观显示
    return true
  })

  /** 问题菜单 Badge（仅测试人员显示） */
  const issueBadgeCount = computed(() => {
    return isTester.value ? testingCount.value : 0
  })

  async function loadBadgeData() {
    if (!authStore.isAuthenticated || isLoading) return
    isLoading = true
    try {
      const res = await dashboardApi.summary()
      if (res.code === 0 && res.data) {
        testingCount.value = res.data.testingCount || 0
        primaryRoleCode.value = res.data.primaryRoleCode || null
        loaded.value = true
      }
    } catch {
      // 静默失败，不影响导航显示
    } finally {
      isLoading = false
    }
  }

  function startPolling() {
    stopPolling()
    refreshTimer = setInterval(() => {
      if (authStore.isAuthenticated) {
        loadBadgeData()
      }
    }, REFRESH_INTERVAL)
  }

  function stopPolling() {
    if (refreshTimer !== null) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  }

  /** 初始化（在 AppLayout onMounted 中调用一次） */
  function init() {
    if (authStore.isAuthenticated) {
      loadBadgeData()
      startPolling()
    }

    // 认证状态变化时切换
    watch(() => authStore.isAuthenticated, (authenticated) => {
      if (authenticated) {
        loadBadgeData()
        startPolling()
      } else {
        stopPolling()
        testingCount.value = 0
        primaryRoleCode.value = null
        loaded.value = false
      }
    })
  }

  /** 外部手动触发刷新（如从 Testing 状态变更后） */
  function refresh() {
    loadBadgeData()
  }

  return {
    testingCount,
    primaryRoleCode,
    isTester,
    canManageSprint,
    issueBadgeCount,
    loaded,
    init,
    refresh
  }
}
