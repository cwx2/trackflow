import { ref, computed } from 'vue'

/**
 * 最近浏览工单管理 — localStorage 存储，用户级别隔离
 *
 * 功能：
 * - 记录最近浏览的 issue（最多 20 条）
 * - Pin/Unpin 操作
 * - 移除单个项
 * - 关闭未固定项（批量清理）
 * - 隐藏/显示面板
 */

export interface RecentIssueItem {
  /** Issue ID */
  id: string
  /** Issue Key（如 DE4-101） */
  issueKey: string
  /** Issue 标题 */
  title: string
  /** 所属项目名 */
  projectName?: string
  /** 是否固定 */
  pinned: boolean
  /** 访问时间戳 */
  visitedAt: number
}

const STORAGE_KEY = 'tf_recent_issues'
const PANEL_VISIBLE_KEY = 'tf_recent_issues_visible'
const MAX_ITEMS = 20

function getUserStorageKey(key: string): string {
  // 从 localStorage 获取当前用户标识
  const userStr = localStorage.getItem('tf_user')
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      return `${key}_${user.id || user.username || 'default'}`
    } catch { /* JSON 解析容错，降级为默认 key */ }
  }
  return `${key}_default`
}

function loadItems(): RecentIssueItem[] {
  try {
    const raw = localStorage.getItem(getUserStorageKey(STORAGE_KEY))
    if (raw) return JSON.parse(raw)
  } catch { /* JSON 解析容错，降级为空列表 */ }
  return []
}

function saveItems(items: RecentIssueItem[]) {
  localStorage.setItem(getUserStorageKey(STORAGE_KEY), JSON.stringify(items))
}

function loadPanelVisible(): boolean {
  const raw = localStorage.getItem(getUserStorageKey(PANEL_VISIBLE_KEY))
  // 默认显示
  if (raw === null) return true
  return raw === 'true'
}

function savePanelVisible(visible: boolean) {
  localStorage.setItem(getUserStorageKey(PANEL_VISIBLE_KEY), String(visible))
}

// 全局响应式状态（跨组件共享）
const items = ref<RecentIssueItem[]>(loadItems())
const panelVisible = ref<boolean>(loadPanelVisible())

export function useRecentIssues() {
  /** 可见的最近浏览项（固定项排前，其余按时间倒序） */
  const visibleItems = computed(() => {
    const pinned = items.value.filter(i => i.pinned).sort((a, b) => b.visitedAt - a.visitedAt)
    const unpinned = items.value.filter(i => !i.pinned).sort((a, b) => b.visitedAt - a.visitedAt)
    return [...pinned, ...unpinned]
  })

  /** 固定项列表 */
  const pinnedItems = computed(() => items.value.filter(i => i.pinned))

  /** 未固定项列表 */
  const unpinnedItems = computed(() => items.value.filter(i => !i.pinned).sort((a, b) => b.visitedAt - a.visitedAt))

  /** 面板是否可见 */
  const isPanelVisible = computed(() => panelVisible.value && items.value.length > 0)

  /** 记录一次浏览 */
  function recordVisit(issue: { id: string; issueKey: string; title: string; projectName?: string }) {
    const idx = items.value.findIndex(i => i.id === issue.id)
    if (idx >= 0) {
      // 已存在则更新时间和信息
      items.value[idx].visitedAt = Date.now()
      items.value[idx].title = issue.title
      items.value[idx].issueKey = issue.issueKey
      if (issue.projectName) items.value[idx].projectName = issue.projectName
    } else {
      // 新增
      items.value.unshift({
        id: issue.id,
        issueKey: issue.issueKey,
        title: issue.title,
        projectName: issue.projectName,
        pinned: false,
        visitedAt: Date.now(),
      })
      // 超出限制时移除最老的未固定项
      if (items.value.length > MAX_ITEMS) {
        const unpinnedByTime = items.value
          .filter(i => !i.pinned)
          .sort((a, b) => a.visitedAt - b.visitedAt)
        if (unpinnedByTime.length > 0) {
          const removeId = unpinnedByTime[0].id
          items.value = items.value.filter(i => i.id !== removeId)
        }
      }
    }
    saveItems(items.value)
  }

  /** Pin 一个项目 */
  function pinItem(id: string) {
    const item = items.value.find(i => i.id === id)
    if (item) {
      item.pinned = true
      saveItems(items.value)
    }
  }

  /** Unpin 一个项目 */
  function unpinItem(id: string) {
    const item = items.value.find(i => i.id === id)
    if (item) {
      item.pinned = false
      saveItems(items.value)
    }
  }

  /** 移除单个项 */
  function removeItem(id: string) {
    items.value = items.value.filter(i => i.id !== id)
    saveItems(items.value)
  }

  /** 关闭所有未固定项 */
  function closeUnpinnedItems() {
    items.value = items.value.filter(i => i.pinned)
    saveItems(items.value)
  }

  /** 隐藏面板（并清除所有项） */
  function hideAll() {
    items.value = []
    panelVisible.value = false
    saveItems(items.value)
    savePanelVisible(false)
  }

  /** 显示面板 */
  function showPanel() {
    panelVisible.value = true
    savePanelVisible(true)
  }

  /** 刷新数据（从 localStorage 重新加载） */
  function refresh() {
    items.value = loadItems()
    panelVisible.value = loadPanelVisible()
  }

  return {
    items,
    visibleItems,
    pinnedItems,
    unpinnedItems,
    isPanelVisible,
    panelVisible,
    recordVisit,
    pinItem,
    unpinItem,
    removeItem,
    closeUnpinnedItems,
    hideAll,
    showPanel,
    refresh,
  }
}
