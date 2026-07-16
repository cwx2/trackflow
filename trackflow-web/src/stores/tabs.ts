import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface TabItem {
  id: string
  title: string
  path: string
  icon?: string
  closable: boolean
  /** 查询 ID（保存查询标签） */
  queryId?: string
  /** Issue ID（Issue 详情标签） */
  issueId?: string
}

export const useTabStore = defineStore('tabs', () => {
  const tabs = ref<TabItem[]>([
    { id: 'issues', title: '所有工单', path: '/issues', icon: '📋', closable: false }
  ])
  const activeTabId = ref('issues')

  const activeTab = computed(() => tabs.value.find(t => t.id === activeTabId.value))

  /**
   * 打开一个标签（已存在则激活，不存在则新建）
   */
  function openTab(tab: Omit<TabItem, 'id'> & { id?: string }) {
    const id = tab.id || generateId(tab)
    const existing = tabs.value.find(t => t.id === id)
    if (existing) {
      // 已存在，激活
      activeTabId.value = id
      // 更新标题（Issue 标题可能变化）
      if (tab.title) existing.title = tab.title
    } else {
      // 新建
      tabs.value.push({ ...tab, id, closable: tab.closable !== false })
      activeTabId.value = id
    }
  }

  /**
   * 关闭标签
   */
  function closeTab(id: string) {
    const tab = tabs.value.find(t => t.id === id)
    if (!tab || !tab.closable) return

    const idx = tabs.value.findIndex(t => t.id === id)
    tabs.value.splice(idx, 1)

    // 如果关闭的是当前激活标签，切换到相邻标签
    if (activeTabId.value === id) {
      const newIdx = Math.min(idx, tabs.value.length - 1)
      activeTabId.value = tabs.value[newIdx]?.id || 'issues'
    }
  }

  /**
   * 关闭其他标签
   */
  function closeOthers(keepId: string) {
    tabs.value = tabs.value.filter(t => !t.closable || t.id === keepId)
    if (!tabs.value.find(t => t.id === activeTabId.value)) {
      activeTabId.value = keepId
    }
  }

  /**
   * 关闭所有可关闭标签
   */
  function closeAll() {
    tabs.value = tabs.value.filter(t => !t.closable)
    activeTabId.value = tabs.value[0]?.id || 'issues'
  }

  /**
   * 设置激活标签
   */
  function setActive(id: string) {
    if (tabs.value.find(t => t.id === id)) {
      activeTabId.value = id
    }
  }

  return {
    tabs,
    activeTabId,
    activeTab,
    openTab,
    closeTab,
    closeOthers,
    closeAll,
    setActive
  }
})

function generateId(tab: { path: string; queryId?: string; issueId?: string }): string {
  if (tab.issueId) return `issue-${tab.issueId}`
  if (tab.queryId) return `query-${tab.queryId}`
  return `tab-${Date.now()}`
}
