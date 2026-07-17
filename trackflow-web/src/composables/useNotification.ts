import { ref, computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { notificationApi, type NotificationVO } from '@/api/notification'

/**
 * 通知中心 composable
 *
 * 管理通知状态：未读计数、通知列表、轮询刷新。
 * 模块级单例，多个组件共享同一状态。
 */

const POLL_INTERVAL = 30 * 1000 // 30 秒轮询未读数

// 模块级状态（单例）
const unreadCount = ref(0)
const notifications = ref<NotificationVO[]>([])
const loading = ref(false)
const unreadOnly = ref(false)
const panelVisible = ref(false)
const totalCount = ref(0)

let pollTimer: ReturnType<typeof setInterval> | null = null
let initialized = false

export function useNotification() {
  const authStore = useAuthStore()

  /** 是否有未读通知 */
  const hasUnread = computed(() => unreadCount.value > 0)

  /** 获取未读数量 */
  async function fetchUnreadCount() {
    if (!authStore.isAuthenticated) return
    try {
      const res = await notificationApi.unreadCount()
      if (res.code === 0 && res.data) {
        unreadCount.value = res.data.count
      }
    } catch {
      // 静默失败
    }
  }

  /** 获取通知列表 */
  async function fetchNotifications() {
    if (!authStore.isAuthenticated) return
    loading.value = true
    try {
      const res = await notificationApi.list({ unreadOnly: unreadOnly.value, page: 1, pageSize: 50 })
      if (res.code === 0 && res.data) {
        notifications.value = res.data.list
        totalCount.value = res.data.pagination.total
      }
    } catch {
      // 静默失败
    } finally {
      loading.value = false
    }
  }

  /** 标记单条已读 */
  async function markRead(id: string) {
    try {
      const res = await notificationApi.markRead(id)
      if (res.code === 0) {
        // 本地更新状态
        const item = notifications.value.find(n => n.id === id)
        if (item && !item.isRead) {
          item.isRead = true
          unreadCount.value = Math.max(0, unreadCount.value - 1)
        }
      }
    } catch {
      // 静默失败
    }
  }

  /** 全部标记已读 */
  async function markAllRead() {
    try {
      const res = await notificationApi.markAllRead()
      if (res.code === 0) {
        notifications.value.forEach(n => { n.isRead = true })
        unreadCount.value = 0
      }
    } catch {
      // 静默失败
    }
  }

  /** 切换仅显示未读 */
  function toggleUnreadOnly() {
    unreadOnly.value = !unreadOnly.value
    fetchNotifications()
  }

  /** 打开面板 */
  function openPanel() {
    panelVisible.value = true
    fetchNotifications()
  }

  /** 关闭面板 */
  function closePanel() {
    panelVisible.value = false
  }

  /** 切换面板 */
  function togglePanel() {
    if (panelVisible.value) {
      closePanel()
    } else {
      openPanel()
    }
  }

  /** 开始轮询 */
  function startPolling() {
    stopPolling()
    pollTimer = setInterval(fetchUnreadCount, POLL_INTERVAL)
  }

  /** 停止轮询 */
  function stopPolling() {
    if (pollTimer !== null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  /** 初始化（AppLayout onMounted 调用一次） */
  function init() {
    if (initialized) return
    initialized = true

    if (authStore.isAuthenticated) {
      fetchUnreadCount()
      startPolling()
    }

    watch(() => authStore.isAuthenticated, (authenticated) => {
      if (authenticated) {
        fetchUnreadCount()
        startPolling()
      } else {
        stopPolling()
        unreadCount.value = 0
        notifications.value = []
        panelVisible.value = false
      }
    })
  }

  /** 外部手动刷新 */
  function refresh() {
    fetchUnreadCount()
    if (panelVisible.value) {
      fetchNotifications()
    }
  }

  return {
    // State
    unreadCount,
    notifications,
    loading,
    unreadOnly,
    panelVisible,
    totalCount,
    hasUnread,
    // Actions
    fetchUnreadCount,
    fetchNotifications,
    markRead,
    markAllRead,
    toggleUnreadOnly,
    openPanel,
    closePanel,
    togglePanel,
    init,
    refresh
  }
}
