import { ref, shallowRef, computed, onUnmounted } from 'vue'
import axios from 'axios'
import { issueApi, queryApi } from '@/api'
import type { IssueVO, SavedQueryFilter } from '@/api/types'

export type SortDirection = 'asc' | 'desc' | null

export interface SortState {
  field: string | null
  direction: SortDirection
}

/** FilterCondition 格式（与 SavedQueryFilter 兼容，用于 QueryExecutor 路径） */
export interface FilterCondition {
  field: string
  operator: string
  value?: string[]
}

export interface IssueListFilters {
  projectId?: string | number
  statusId?: string
  priority?: string
  assigneeId?: string
  assigneeName?: string
  reporterId?: string
  sprintId?: string
  sprintStatus?: string
  issueType?: string
  keyword?: string
  queryId?: string | number
  /** FilterCondition[] 条件（走 QueryExecutor 路径），优先于 URL 参数 */
  filterConditions?: FilterCondition[]
  // Negative filters
  statusIdNot?: string
  priorityNot?: string
  assigneeIdNot?: string
  sprintIdNot?: string
  issueTypeNot?: string
  // Tag & parent filters
  tagId?: string
  parentId?: string
  hasParent?: string
  // Date range filters
  createdAfter?: string
  createdBefore?: string
  updatedAfter?: string
  updatedBefore?: string
  resolvedAfter?: string
  resolvedBefore?: string
  dueAfter?: string
  dueBefore?: string
  // Special filters
  overdue?: string
  dueSoon?: string
  reportedByMe?: string
  assignedToMe?: string
  hideResolved?: string
}

/**
 * 将 FilterCondition[] 转换为 SavedQueryFilter[] 格式（QueryExecutor 接受的格式）
 * 同时注入额外的上下文条件（如 projectId, hideResolved）
 */
function buildAdhocFilters(conditions: FilterCondition[], context: IssueListFilters): SavedQueryFilter[] {
  const filters: SavedQueryFilter[] = []

  // 注入项目 ID（如果不在 conditions 中已存在）
  if (context.projectId) {
    const hasProject = conditions.some(c => c.field === 'project')
    if (!hasProject) {
      filters.push({ field: 'project', operator: 'in', value: [String(context.projectId)] })
    }
  }

  // 转换 FilterCondition → SavedQueryFilter
  for (const cond of conditions) {
    const filter: SavedQueryFilter = {
      field: cond.field,
      operator: cond.operator,
      value: cond.value || []
    }
    filters.push(filter)
  }

  // 注入关键词搜索
  if (context.keyword) {
    filters.push({ field: 'keyword', operator: 'contains', value: [context.keyword] })
  }

  return filters
}

export function useIssueList() {
  // issues 用 shallowRef：每次 loadIssues 替换整个数组引用，Vue 无需递归代理每条记录
  const issues = shallowRef<IssueVO[]>([])
  const totalIssues = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)
  const loadError = ref(false)
  const sortState = ref<SortState>({ field: null, direction: null })

  // 请求取消控制器：用于取消前一个请求，避免竞态条件
  let abortController: AbortController | null = null

  /**
   * 三态排序切换: null → asc → desc → null
   */
  function toggleSort(field: string) {
    if (sortState.value.field !== field) {
      // 切换到新字段，从 asc 开始
      sortState.value = { field, direction: 'asc' }
    } else if (sortState.value.direction === 'asc') {
      sortState.value = { field, direction: 'desc' }
    } else {
      // desc → 清除排序
      sortState.value = { field: null, direction: null }
    }
    // 排序变更时重置为第一页
    currentPage.value = 1
  }

  /**
   * 构建后端 sort 参数
   * 格式: -fieldName (desc) / fieldName (asc)
   */
  const sortParam = computed<string | undefined>(() => {
    if (!sortState.value.field) return undefined
    return sortState.value.direction === 'desc'
      ? `-${sortState.value.field}`
      : sortState.value.field
  })

  /**
   * 加载 Issue 列表
   * 支持请求取消：当快速切换筛选时，取消前一个请求，避免竞态条件导致计数与列表不一致
   */
  async function loadIssues(filters: IssueListFilters = {}) {
    // 取消前一个正在进行的请求
    if (abortController) {
      abortController.abort()
    }
    // 创建新的取消控制器
    abortController = new AbortController()
    const currentAbortController = abortController

    loading.value = true
    loadError.value = false
    try {
      const params: Record<string, any> = {
        page: currentPage.value,
        pageSize: pageSize.value,
        sort: sortParam.value
      }

      // 合并筛选条件
      if (filters.projectId) params.projectId = filters.projectId
      if (filters.statusId) params.statusId = filters.statusId
      if (filters.priority) params.priority = filters.priority
      if (filters.assigneeId) params.assigneeId = filters.assigneeId
      if (filters.assigneeName) params.assigneeName = filters.assigneeName
      if (filters.reporterId) params.reporterId = filters.reporterId
      if (filters.sprintId) params.sprintId = filters.sprintId
      if (filters.sprintStatus) params.sprintStatus = filters.sprintStatus
      if (filters.issueType) params.issueType = filters.issueType
      if (filters.keyword) params.keyword = filters.keyword
      // Negative filters
      if (filters.statusIdNot) params.statusIdNot = filters.statusIdNot
      if (filters.priorityNot) params.priorityNot = filters.priorityNot
      if (filters.assigneeIdNot) params.assigneeIdNot = filters.assigneeIdNot
      if (filters.sprintIdNot) params.sprintIdNot = filters.sprintIdNot
      if (filters.issueTypeNot) params.issueTypeNot = filters.issueTypeNot
      // Tag & parent filters
      if (filters.tagId) params.tagId = filters.tagId
      if (filters.parentId) params.parentId = filters.parentId
      if (filters.hasParent) params.hasParent = filters.hasParent
      // Date range filters
      if (filters.createdAfter) params.createdAfter = filters.createdAfter
      if (filters.createdBefore) params.createdBefore = filters.createdBefore
      if (filters.updatedAfter) params.updatedAfter = filters.updatedAfter
      if (filters.updatedBefore) params.updatedBefore = filters.updatedBefore
      if (filters.resolvedAfter) params.resolvedAfter = filters.resolvedAfter
      if (filters.resolvedBefore) params.resolvedBefore = filters.resolvedBefore
      if (filters.dueAfter) params.dueAfter = filters.dueAfter
      if (filters.dueBefore) params.dueBefore = filters.dueBefore
      // Special filters
      if (filters.overdue) params.overdue = filters.overdue
      if (filters.dueSoon) params.dueSoon = filters.dueSoon
      if (filters.reportedByMe) params.reportedByMe = filters.reportedByMe
      if (filters.assignedToMe) params.assignedToMe = filters.assignedToMe
      if (filters.hideResolved) params.hideResolved = filters.hideResolved

      let res
      if (filters.queryId) {
        // 通过已保存查询执行
        res = await queryApi.executeById(String(filters.queryId), params, currentAbortController.signal)
      } else if (filters.filterConditions && filters.filterConditions.length > 0) {
        // 通过 FilterCondition[] 走 QueryExecutor 路径（统一筛选器）
        const adhocFilters = buildAdhocFilters(filters.filterConditions, filters)
        res = await queryApi.executeAdhoc({
          filters: adhocFilters,
          page: currentPage.value,
          pageSize: pageSize.value
        })
      } else {
        res = await issueApi.list(params, currentAbortController.signal)
      }

      // 检查是否被取消（可能在等待响应期间发起了新请求）
      if (currentAbortController.signal.aborted) {
        return
      }

      issues.value = res.data?.list || []
      totalIssues.value = res.data?.pagination?.total || 0
    } catch (e) {
      // 请求被取消（无论是 AbortController 还是会话过期），静默处理
      if (axios.isCancel(e) || (e instanceof DOMException && e.name === 'AbortError')) {
        return
      }

      // 只有当前请求未被取消时才更新状态，避免覆盖新请求的结果
      if (!currentAbortController.signal.aborted) {
        issues.value = []
        totalIssues.value = 0
        loadError.value = true
      }
    } finally {
      // 只有当前请求未被取消时才取消 loading 状态
      if (!currentAbortController.signal.aborted) {
        loading.value = false
      }
    }
  }

  /**
   * 跳转到指定页
   */
  function goPage(page: number) {
    currentPage.value = page
  }

  function changePageSize(size: number) {
    pageSize.value = size
    currentPage.value = 1
  }

  /**
   * 更新本地单条 Issue 数据（内联编辑后刷新）
   * shallowRef 下需替换整个数组引用才能触发视图更新
   */
  function updateLocalIssue(issueId: string, patch: Partial<IssueVO>) {
    const idx = issues.value.findIndex(i => i.id === issueId)
    if (idx !== -1) {
      const updated = [...issues.value]
      updated[idx] = { ...updated[idx], ...patch }
      issues.value = updated
    }
  }

  /**
   * 从本地列表中移除工单（用于筛选视图中编辑后不满足条件的情况）
   * shallowRef 下用 filter 返回新数组替换引用
   */
  function removeLocalIssue(issueId: string) {
    const idx = issues.value.findIndex(i => i.id === issueId)
    if (idx !== -1) {
      issues.value = issues.value.filter(i => i.id !== issueId)
      totalIssues.value = Math.max(0, totalIssues.value - 1)
    }
  }

  /**
   * 清理：取消正在进行的请求
   * 组件销毁时应调用此函数
   */
  function cleanup() {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
  }

  // 组件卸载时自动清理
  onUnmounted(() => {
    cleanup()
  })

  return {
    issues,
    totalIssues,
    currentPage,
    pageSize,
    loading,
    loadError,
    sortState,
    sortParam,
    toggleSort,
    loadIssues,
    goPage,
    changePageSize,
    updateLocalIssue,
    removeLocalIssue
  }
}
