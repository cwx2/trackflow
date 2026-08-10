import { ref, computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useWebSocket } from '@/composables/useWebSocket'
import type { StompSubscription } from '@stomp/stompjs'
import { notificationApi, type NotificationVO, type NotificationCategory, type CategoryUnreadCounts } from '@/api/notification'

/**
 * 通知中心 composable
 *
 * 管理通知状态：未读计数、通知列表、分类标签页。
 * 实时推送优先（WebSocket STOMP），轮询作为降级方案。
 * 模块级单例，多个组件共享同一状态。
 */

const POLL_INTERVAL = 30 * 1000 // 30 秒轮询（WebSocket 断开时的降级方案）
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
const activeProjectId = ref<string | null>(null)

let pollTimer: ReturnType<typeof setInterval> | null = null
let initialized = false
let wsSubscription: StompSubscription | null = null
let wsConnected = false
let wsConnectCalled = false
let wsDisconnectFn: (() => void) | null = null

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

  /** 获取通知列表（含分类过滤和项目过滤） */
  async function fetchNotifications(page?: number, size?: number) {
    if (!authStore.isAuthenticated) return
    loading.value = true
    try {
      const params: { unreadOnly?: boolean; category?: NotificationCategory; projectId?: string; page?: number; pageSize?: number } = {
        unreadOnly: unreadOnly.value,
        page: page || 1,
        pageSize: size || 50
      }
      // 只在非"全部"时传 category 参数
      if (activeCategory.value !== 'all') {
        params.category = activeCategory.value
      }
      // 项目过滤
      if (activeProjectId.value) {
        params.projectId = activeProjectId.value
      }
      const res = await notificationApi.list(params)
      if (res.code === 0 && res.data) {
        // 未读优先排序：未读在前，已读在后，每组内部保持后端返回顺序（时间倒序）
        const list = res.data.list
        const unread = list.filter(n => !n.isRead)
        const read = list.filter(n => n.isRead)
        notifications.value = [...unread, ...read]
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

  /** 设置项目过滤（传 null 清除过滤） */
  function setProjectFilter(projectId: string | null) {
    activeProjectId.value = projectId
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
          // 重新排序：未读优先
          const unread = notifications.value.filter(n => !n.isRead)
          const read = notifications.value.filter(n => n.isRead)
          notifications.value = [...unread, ...read]
        }
      }
    } catch {
      // 静默失败
    }
  }

  /** 标记单条未读（恢复未读状态） */
  async function markUnread(id: string) {
    try {
      const res = await notificationApi.markUnread(id)
      if (res.code === 0) {
        const item = notifications.value.find(n => n.id === id)
        if (item && item.isRead) {
          item.isRead = false
          unreadCount.value += 1
          // 更新分类未读计数
          incrementCategoryCount(item.type)
          // 重新排序：未读优先
          const unread = notifications.value.filter(n => !n.isRead)
          const read = notifications.value.filter(n => n.isRead)
          notifications.value = [...unread, ...read]
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

  /** 开始轮询（WebSocket 断开时的降级方案） */
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

  /**
   * 订阅用户级通知 WebSocket 队列。
   * 收到推送时立即更新未读计数和通知列表（无需等待轮询周期）。
   * WebSocket 连接成功后停止 HTTP 轮询；断开后自动恢复轮询。
   *
   * 使用 wsConnectCalled flag 确保 connect() 最多调用一次，防止引用计数泄漏。
   */
  function subscribeNotifications() {
    const { connect, disconnect, getClient, status } = useWebSocket()

    if (!wsConnectCalled) {
      wsConnectCalled = true
      wsDisconnectFn = disconnect
      connect()
    }

    const doSubscribe = () => {
      unsubscribeNotifications()
      const client = getClient()
      if (!client?.connected) return

      // 使用 /user/queue/notifications — Spring 按 Principal 自动路由到当前用户
      // 无需在路径中暴露 userId，其他用户无法订阅此队列
      wsSubscription = client.subscribe(`/user/queue/notifications`, (message) => {
        try {
          const event = JSON.parse(message.body)
          if (event.event === 'NEW_NOTIFICATION') {
            // 收到实时推送 — 立即刷新未读计数
            fetchUnreadCount()
            fetchCategoryUnreadCounts()
            // 面板打开时追加新通知到列表
            if (panelVisible.value) {
              fetchNotifications()
            }
          }
        } catch (e) {
          console.warn('[Notification] Failed to parse WebSocket message:', e)
        }
      })

      // WebSocket 连接成功 — 停止轮询，立即补拉一次（修复断线期间可能错过的通知）
      wsConnected = true
      stopPolling()
      fetchUnreadCount()
      fetchCategoryUnreadCounts()
      if (panelVisible.value) {
        fetchNotifications()
      }
      console.debug('[Notification] WebSocket subscribed, polling stopped')
    }

    // 监听连接状态变化
    watch(status, (newStatus) => {
      if (newStatus === 'connected') {
        doSubscribe()
      } else if (newStatus === 'disconnected' || newStatus === 'error') {
        // WebSocket 断开 — 恢复轮询作为降级方案
        if (wsConnected) {
          wsConnected = false
          startPolling()
          console.debug('[Notification] WebSocket disconnected, polling resumed')
        }
      }
    })

    // 如果已经连接，立即订阅
    if (status.value === 'connected') {
      doSubscribe()
    } else {
      // 尚未连接 — 先启动轮询兜底
      startPolling()
    }
  }

  /** 取消 WebSocket 通知订阅 */
  function unsubscribeNotifications() {
    if (wsSubscription) {
      wsSubscription.unsubscribe()
      wsSubscription = null
    }
  }

  /**
   * 完整清理 WebSocket：取消订阅 + 调用 disconnect() 使引用计数归零。
   * 退出登录时调用，确保 globalClient 被 deactivate。
   */
  function cleanupWebSocket() {
    unsubscribeNotifications()
    wsConnected = false
    if (wsConnectCalled && wsDisconnectFn) {
      wsDisconnectFn()
      wsConnectCalled = false
      wsDisconnectFn = null
    }
  }

  /** 初始化（AppLayout onMounted 调用一次） */
  function init() {
    if (initialized) return
    initialized = true

    if (authStore.isAuthenticated) {
      fetchUnreadCount()
      subscribeNotifications()
    }

    watch(() => authStore.isAuthenticated, (authenticated) => {
      if (authenticated) {
        fetchUnreadCount()
        subscribeNotifications()
      } else {
        cleanupWebSocket()
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

  /** 静音指定工单的通知 */
  async function muteThread(resourceType: string, resourceId: string) {
    try {
      const res = await notificationApi.muteThread(resourceType, resourceId)
      if (res.code === 0) {
        // 更新本地通知列表中该资源的 resourceMuted 状态
        notifications.value.forEach(n => {
          if (n.resourceType === resourceType && n.resourceId === resourceId) {
            n.resourceMuted = true
          }
        })
      }
    } catch {
      // 静默失败
    }
  }

  /** 取消静音 */
  async function unmuteThread(resourceType: string, resourceId: string) {
    try {
      const res = await notificationApi.unmuteThread(resourceType, resourceId)
      if (res.code === 0) {
        notifications.value.forEach(n => {
          if (n.resourceType === resourceType && n.resourceId === resourceId) {
            n.resourceMuted = false
          }
        })
      }
    } catch {
      // 静默失败
    }
  }

  /**
   * 属于 subscription 分类的通知类型列表（与后端 NotificationCategory.subscription 枚举保持同步）
   * YouTrack 标准：分配、评论、状态变更、更新、移动、截止日期提醒、逾期提醒、投票、工时记录均属于 Subscriptions
   */
  const SUBSCRIPTION_TYPES = [
    'issue_assigned',
    'issue_auto_assigned',
    'issue_commented',
    'issue_status_changed',
    'issue_updated',
    'issue_moved',
    'due_date_alert',
    'overdue_alert',
    'issue_voted',
    'issue_spent_time'
  ]

  /**
   * 根据通知类型递减对应分类的未读计数
   */
  function decrementCategoryCount(type: string) {
    const counts = categoryUnreadCounts.value
    counts.all = Math.max(0, counts.all - 1)

    if (type === 'mention') {
      counts.mention = Math.max(0, counts.mention - 1)
    } else if (SUBSCRIPTION_TYPES.includes(type)) {
      counts.subscription = Math.max(0, counts.subscription - 1)
    } else {
      counts.system = Math.max(0, counts.system - 1)
    }
  }

  /**
   * 根据通知类型递增对应分类的未读计数
   */
  function incrementCategoryCount(type: string) {
    const counts = categoryUnreadCounts.value
    counts.all += 1

    if (type === 'mention') {
      counts.mention += 1
    } else if (SUBSCRIPTION_TYPES.includes(type)) {
      counts.subscription += 1
    } else {
      counts.system += 1
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
    activeProjectId,
    isSystemAdmin,
    // Actions
    fetchUnreadCount,
    fetchCategoryUnreadCounts,
    fetchNotifications,
    setCategory,
    setProjectFilter,
    markRead,
    markUnread,
    markAllRead,
    deleteNotification,
    deleteAllRead,
    toggleUnreadOnly,
    openPanel,
    closePanel,
    togglePanel,
    init,
    refresh,
    muteThread,
    unmuteThread
  }
}
