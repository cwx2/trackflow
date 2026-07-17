import { ref, computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { notificationApi, type NotificationVO, type NotificationCategory, type CategoryUnreadCounts } from '@/api/notification'

/**
 * 通知中心 composable
 *
 * 管理通知状态：未读计数、通知列表、分类标签页、轮询刷新。
 * 模块级单例，多个组件共享同一状态。
 */

const POLL_INTERVAL = 30 * 1000 // 30 秒轮询未读数
const CATEGORY_STORAGE_KEY = 'tf_notification_category'

// 模块级状态（单例）
const unreadCount = ref(0)
const categoryUnreadCounts = ref<CategoryUnreadCounts>({ all: 0, mention: 0, subscription: 0, system: 0 })
const notifications = ref<NotificationVO[]>([])
const loading = ref(false)
const unreadOnly = ref(false)
const panelVisible = ref(false)
const totalCount = ref(0)
const activeCategory = ref<NotificationCategory>(restoreCategory())

let pollTimer: ReturnType<typeof setInterval> | null = null
let initialized = false

/**
 * 从 localStorage 恢复上次选择的标签页
 */
function restoreCategory(): NotificationCategory {
  try {
    const saved = localStorage.getItem(CATEGORY_STORAGE_KEY)
    if (saved && ['all', 'mention', 'subscription', 'system'].includes(saved)) {
      return saved as NotificationCategory
    }
  } catch {
    // localStorage 不可用时忽略
  }
  return 'all'
}

/**
 * 保存标签页选择到 localStorage
 */
function persistCategory(category: NotificationCategory) {
  try {
    localStorage.setItem(CATEGORY_STORAGE_KEY, category)
  } catch {
    // 静默失败
  }
}

export function useNotification() {
  const authStore = useAuthStore()

  /** 是否有未读通知 */
  const hasUnread = computed(() => unreadCount.value > 0)

  /** 是否有已读通知 */
  const hasRead = computed(() => notifications.value.some(n => n.isRead))

  /** 是否是系统管理员（用于控制"系统"标签页可见性） */
  const isSystemAdmin = computed(() => authStore.hasGlobalPermission('system:admin'))

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

  /** 获取各分类未读计数 */
  async function fetchCategoryUnreadCounts() {
    if (!authStore.isAuthenticated) return
    try {
      const res = await notificationApi.unreadCountByCategory()
      if (res.code === 0 && res.data) {
        categoryUnreadCounts.value = res.data
      }
    } catch {
      // 静默失败
    }
  }

  /** 获取通知列表（含分类过滤） */
  async function fetchNotifications() {
    if (!authStore.isAuthenticated) return
    loading.value = true
    try {
      const params: { unreadOnly?: boolean; category?: NotificationCategory; page?: number; pageSize?: number } = {
        unreadOnly: unreadOnly.value,
        page: 1,
        pageSize: 50
      }
      // 只在非"全部"时传 category 参数
      if (activeCategory.value !== 'all') {
        params.category = activeCategory.value
      }
      const res = await notificationApi.list(params)
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

  /** 切换分类标签页 */
  function setCategory(category: NotificationCategory) {
    activeCategory.value = category
    persistCategory(category)
    fetchNotifications()
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
          // 更新分类未读计数
          decrementCategoryCount(item.type)
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
        categoryUnreadCounts.value = { all: 0, mention: 0, subscription: 0, system: 0 }
      }
    } catch {
      // 静默失败
    }
  }

  /** 删除单条通知 */
  async function deleteNotification(id: string) {
    try {
      const res = await notificationApi.delete(id)
      if (res.code === 0) {
        const item = notifications.value.find(n => n.id === id)
        if (item && !item.isRead) {
          unreadCount.value = Math.max(0, unreadCount.value - 1)
          decrementCategoryCount(item.type)
        }
        notifications.value = notifications.value.filter(n => n.id !== id)
        totalCount.value = Math.max(0, totalCount.value - 1)
      }
    } catch {
      // 静默失败
    }
  }

  /** 清除所有已读通知 */
  async function deleteAllRead() {
    try {
      const res = await notificationApi.deleteAllRead()
      if (res.code === 0) {
        notifications.value = notifications.value.filter(n => !n.isRead)
        // 重新拉取以更新 totalCount
        await fetchNotifications()
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
    fetchCategoryUnreadCounts()
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
    pollTimer = setInterval(() => {
      fetchUnreadCount()
      // 面板打开时同时更新分类计数
      if (panelVisible.value) {
        fetchCategoryUnreadCounts()
      }
    }, POLL_INTERVAL)
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
        categoryUnreadCounts.value = { all: 0, mention: 0, subscription: 0, system: 0 }
        notifications.value = []
        panelVisible.value = false
      }
    })
  }

  /** 外部手动刷新 */
  function refresh() {
    fetchUnreadCount()
    fetchCategoryUnreadCounts()
    if (panelVisible.value) {
      fetchNotifications()
    }
  }

  /**
   * 根据通知类型递减对应分类的未读计数
   */
  function decrementCategoryCount(type: string) {
    const counts = categoryUnreadCounts.value
    counts.all = Math.max(0, counts.all - 1)

    if (type === 'mention') {
      counts.mention = Math.max(0, counts.mention - 1)
    } else if (['issue_assigned', 'issue_auto_assigned', 'issue_commented', 'issue_status_changed'].includes(type)) {
      counts.subscription = Math.max(0, counts.subscription - 1)
    } else {
      counts.system = Math.max(0, counts.system - 1)
    }
  }

  return {
    // State
    unreadCount,
    categoryUnreadCounts,
    notifications,
    loading,
    unreadOnly,
    panelVisible,
    totalCount,
    hasUnread,
    hasRead,
    activeCategory,
    isSystemAdmin,
    // Actions
    fetchUnreadCount,
    fetchCategoryUnreadCounts,
    fetchNotifications,
    setCategory,
    markRead,
    markAllRead,
    deleteNotification,
    deleteAllRead,
    toggleUnreadOnly,
    openPanel,
    closePanel,
    togglePanel,
    init,
    refresh
  }
}
