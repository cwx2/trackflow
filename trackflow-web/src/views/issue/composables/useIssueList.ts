import { ref, computed } from 'vue'
import { issueApi, queryApi } from '@/api'
import type { IssueVO } from '@/api/types'

export type SortDirection = 'asc' | 'desc' | null

export interface SortState {
  field: string | null
  direction: SortDirection
}

export interface IssueListFilters {
  projectId?: string | number
  statusId?: string
  priority?: string
  assigneeId?: string
  assigneeName?: string
  sprintId?: string
  issueType?: string
  keyword?: string
  queryId?: string | number
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
  // Special filters
  overdue?: string
  dueSoon?: string
  reportedByMe?: string
  hideResolved?: string
}

export function useIssueList() {
  const issues = ref<IssueVO[]>([])
  const totalIssues = ref(0)
  const currentPage = ref(1)
  const pageSize = 20
  const loading = ref(false)
  const sortState = ref<SortState>({ field: null, direction: null })

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
   */
  async function loadIssues(filters: IssueListFilters = {}) {
    loading.value = true
    try {
      const params: Record<string, any> = {
        page: currentPage.value,
        pageSize,
        sort: sortParam.value
      }

      // 合并筛选条件
      if (filters.projectId) params.projectId = filters.projectId
      if (filters.statusId) params.statusId = filters.statusId
      if (filters.priority) params.priority = filters.priority
      if (filters.assigneeId) params.assigneeId = filters.assigneeId
      if (filters.assigneeName) params.assigneeName = filters.assigneeName
      if (filters.sprintId) params.sprintId = filters.sprintId
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
      // Special filters
      if (filters.overdue) params.overdue = filters.overdue
      if (filters.dueSoon) params.dueSoon = filters.dueSoon
      if (filters.reportedByMe) params.reportedByMe = filters.reportedByMe
      if (filters.hideResolved) params.hideResolved = filters.hideResolved

      let res
      if (filters.queryId) {
        // 通过已保存查询执行
        res = await queryApi.executeById(String(filters.queryId), params)
      } else {
        res = await issueApi.list(params)
      }

      issues.value = res.data?.list || []
      totalIssues.value = res.data?.pagination?.total || 0
    } catch (e) {
      issues.value = []
      totalIssues.value = 0
    } finally {
      loading.value = false
    }
  }

  /**
   * 跳转到指定页
   */
  function goPage(page: number) {
    currentPage.value = page
  }

  /**
   * 更新本地单条 Issue 数据（内联编辑后刷新）
   */
  function updateLocalIssue(issueId: string, patch: Partial<IssueVO>) {
    const idx = issues.value.findIndex(i => i.id === issueId)
    if (idx !== -1) {
      issues.value[idx] = { ...issues.value[idx], ...patch }
    }
  }

  return {
    issues,
    totalIssues,
    currentPage,
    pageSize,
    loading,
    sortState,
    sortParam,
    toggleSort,
    loadIssues,
    goPage,
    updateLocalIssue
  }
}
