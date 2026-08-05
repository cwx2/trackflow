import { ref, computed, onUnmounted } from 'vue'
import type { Ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import type { BoardIssue } from './useKanbanBoard'

/**
 * 看板搜索/过滤逻辑。
 *
 * 管理关键词搜索、防抖加载、清除搜索等。
 */
export function useBoardFilter(options: {
  keyword: Ref<string>
  selectedProject: Ref<string | undefined>
  issues: Ref<BoardIssue[]>
  loading: Ref<boolean>
  loadIssues: () => Promise<void>
}) {
  const { keyword, selectedProject, issues, loading, loadIssues } = options

  let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

  const isSearchActive = computed(() => keyword.value.trim().length > 0)

  const showNoSearchResults = computed(() =>
    isSearchActive.value && selectedProject.value && issues.value.length === 0 && !loading.value
  )

  function onSearchInput() {
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
    searchDebounceTimer = setTimeout(() => {
      loadIssuesWithLoading()
    }, 350)
  }

  function onSearchClear() {
    keyword.value = ''
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
    loadIssuesWithLoading()
  }

  function clearSearch() {
    keyword.value = ''
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
    loadIssuesWithLoading()
  }

  /** 带 loading 状态的工单刷新 */
  async function loadIssuesWithLoading() {
    if (!selectedProject.value) return
    loading.value = true
    try {
      await loadIssues()
    } catch (e: any) {
      // 会话过期（axios.Cancel from request.ts）时不显示"搜索失败"——已有过期提示和跳转
      const isSessionExpired = e?.message === '会话已过期' || e?.code === 'ERR_CANCELED'
      if (isSessionExpired) return
      issues.value = []
      Message.error('搜索失败')
    } finally {
      loading.value = false
    }
  }

  function clearDebounceTimer() {
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  }

  onUnmounted(() => {
    clearDebounceTimer()
  })

  return {
    isSearchActive,
    showNoSearchResults,
    onSearchInput,
    onSearchClear,
    clearSearch,
    loadIssuesWithLoading,
    clearDebounceTimer
  }
}
