import { nextTick, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import type { IssueStatusVO, SprintVO } from '@/api/types'
import { localizeStatusName } from '@/utils/fieldLabels'

export interface DashboardFilterOptions {
  activeQueryId: Ref<string | null>
  activeQueryObj: Ref<any>
  activeProjectId: Ref<string | null>
  filterProject: Ref<string | undefined>
  searchKeyword: Ref<string>
  globalFilterParams: Ref<Record<string, any>>
  initialFilterChips: Ref<any[]>
  activeQueryName: Ref<string>
  statusCache: Ref<IssueStatusVO[]>
  sprintOptionsCache: Record<string, SprintVO[]>
  projectList: Ref<any[]>
  filterBarRef: Ref<{ clearAll: () => void; setSearchKeyword: (kw: string) => void } | null>
  refreshList: () => void
}

export function useDashboardFilter(options: DashboardFilterOptions) {
  const route = useRoute()

  const {
    activeQueryId, activeQueryObj, activeProjectId, filterProject,
    searchKeyword, globalFilterParams, initialFilterChips, activeQueryName,
    statusCache, sprintOptionsCache, projectList, filterBarRef, refreshList
  } = options

  /**
   * Parse URL query params (from Dashboard/Sprint/Report drill-down) into filter state.
   * Supports: statusId, statusCode, statusCategory, statusName, status, overdue, dueSoon,
   *           sprint, reportedByMe, assignedToMe, priority, issueType, assigneeName,
   *           assignee, reporter, keyword, project/projectId, label
   */
  function applyDashboardFilter() {
    // 清除已选中的保存查询，防止 buildFilters() 中 queryId 覆盖 dashboard 过滤条件
    activeQueryId.value = null
    activeQueryObj.value = null
    filterProject.value = undefined
    searchKeyword.value = ''

    const filters: Record<string, any> = {}
    const chips: any[] = []

    if (route.query.statusId) {
      const statusIds = String(route.query.statusId).split(',')
      filters.statusId = String(route.query.statusId)

      const statusNames = statusIds.map(id => {
        const s = statusCache.value.find(st => st.id === id)
        return s?.name || id
      })
      chips.push({
        fieldKey: 'status',
        operator: 'any_of',
        values: statusIds,
        valueLabels: statusNames
      })
    }

    // statusCode: 单个状态代码（如 'testing'）
    if (route.query.statusCode) {
      const code = String(route.query.statusCode)
      const matchedStatus = statusCache.value.find(st => st.code === code)
      if (matchedStatus) {
        filters.statusId = matchedStatus.id
        chips.push({
          fieldKey: 'status',
          operator: 'any_of',
          values: [matchedStatus.id],
          valueLabels: [matchedStatus.name]
        })
      }
    }

    // statusName: 按状态英文名匹配（从报表图表下钻时使用）
    if (route.query.statusName) {
      const name = String(route.query.statusName)
      const matchedStatus = statusCache.value.find(st => st.name === name)
      if (matchedStatus) {
        filters.statusId = matchedStatus.id
        chips.push({
          fieldKey: 'status',
          operator: 'any_of',
          values: [matchedStatus.id],
          valueLabels: [matchedStatus.name]
        })
      }
    }

    // status: 通用状态筛选参数
    if (route.query.status) {
      const statusParam = String(route.query.status)
      const matchedStatus = statusCache.value.find(st =>
        st.name === statusParam ||
        localizeStatusName(st.name) === statusParam ||
        st.code === statusParam
      )
      if (matchedStatus) {
        filters.statusId = matchedStatus.id
        chips.push({
          fieldKey: 'status',
          operator: 'any_of',
          values: [matchedStatus.id],
          valueLabels: [localizeStatusName(matchedStatus.name)]
        })
      }
    }

    // statusCategory: 状态分类（如 'open', 'in_progress', 'done'）
    if (route.query.statusCategory) {
      const category = String(route.query.statusCategory)
      const matchedStatuses = statusCache.value.filter(st => st.category === category)
      if (matchedStatuses.length > 0) {
        const ids = matchedStatuses.map(s => s.id)
        filters.statusId = ids.join(',')
        chips.push({
          fieldKey: 'status',
          operator: 'any_of',
          values: ids,
          valueLabels: matchedStatuses.map(s => s.name)
        })
      }
    }

    // reportedByMe
    if (route.query.reportedByMe) {
      filters.reportedByMe = 'true'
      chips.push({
        fieldKey: 'reporter',
        operator: 'equals',
        values: ['me'],
        valueLabels: ['我']
      })
    }

    // assignedToMe
    if (route.query.assignedToMe) {
      filters.assignedToMe = 'true'
      chips.push({
        fieldKey: 'assignee',
        operator: 'equals',
        values: ['me'],
        valueLabels: ['我']
      })
    }

    if (route.query.overdue) {
      filters.overdue = 'true'
    }

    if (route.query.dueSoon) {
      filters.dueSoon = 'true'
    }

    if (route.query.sprint) {
      filters.sprintId = String(route.query.sprint)
      let sprintDisplayName = ''
      if (route.query.label && !route.query.statusCategory) {
        sprintDisplayName = String(route.query.label)
      }
      if (!sprintDisplayName) {
        const sprintId = String(route.query.sprint)
        for (const sprints of Object.values(sprintOptionsCache)) {
          const matched = sprints.find(s => s.id === sprintId)
          if (matched) { sprintDisplayName = matched.name; break }
        }
      }
      if (!sprintDisplayName) {
        sprintDisplayName = `Sprint #${route.query.sprint}`
      }
      chips.push({
        fieldKey: 'sprint',
        operator: 'equals',
        values: [String(route.query.sprint)],
        valueLabels: [sprintDisplayName]
      })
    }

    // sprintStatus: filter issues by sprint status (e.g. "completed" = lingering issues in completed sprints)
    if (route.query.sprintStatus) {
      const sprintStatusVal = String(route.query.sprintStatus)
      filters.sprintStatus = sprintStatusVal
      const statusLabelMap: Record<string, string> = {
        completed: '已完成迭代中的遗留工单',
        active: '进行中迭代的工单',
        planned: '计划中迭代的工单'
      }
      const displayLabel = statusLabelMap[sprintStatusVal] || `迭代状态: ${sprintStatusVal}`
      chips.push({
        fieldKey: 'sprint',
        operator: 'equals',
        values: [sprintStatusVal],
        valueLabels: [displayLabel]
      })
      // Set a descriptive query name for display
      if (!route.query.label) {
        activeQueryName.value = displayLabel
      }
    }

    // priority
    if (route.query.priority) {
      const priority = String(route.query.priority)
      filters.priority = priority
      chips.push({
        fieldKey: 'priority',
        operator: 'equals',
        values: [priority],
        valueLabels: [priority]
      })
    }

    // issueType
    if (route.query.issueType) {
      const issueType = String(route.query.issueType)
      filters.issueType = issueType
      chips.push({
        fieldKey: 'type',
        operator: 'equals',
        values: [issueType],
        valueLabels: [issueType]
      })
    }

    // assigneeName
    if (route.query.assigneeName) {
      const name = String(route.query.assigneeName)
      filters.assigneeName = name
      chips.push({
        fieldKey: 'assignee',
        operator: 'equals',
        values: [name],
        valueLabels: [name]
      })
    }

    // assignee=unassigned or assignee={userId}
    if (route.query.assignee === 'unassigned') {
      filters.assigneeId = 'none'
      chips.push({
        fieldKey: 'assignee',
        operator: 'equals',
        values: ['none'],
        valueLabels: ['未分配']
      })
    } else if (route.query.assignee) {
      const assigneeUserId = String(route.query.assignee)
      filters.assigneeId = assigneeUserId
      const assigneeLabel = route.query.assigneeName
        ? String(route.query.assigneeName)
        : assigneeUserId
      chips.push({
        fieldKey: 'assignee',
        operator: 'equals',
        values: [assigneeUserId],
        valueLabels: [assigneeLabel]
      })
    }

    // reporter={userId}
    if (route.query.reporter) {
      const reporterUserId = String(route.query.reporter)
      filters.reporterId = reporterUserId
      const reporterLabel = route.query.reporterName
        ? String(route.query.reporterName)
        : reporterUserId
      chips.push({
        fieldKey: 'reporter',
        operator: 'equals',
        values: [reporterUserId],
        valueLabels: [reporterLabel]
      })
    }

    // keyword
    if (route.query.keyword) {
      const keyword = String(route.query.keyword)
      searchKeyword.value = keyword
      nextTick(() => {
        filterBarRef.value?.setSearchKeyword(keyword)
      })
    }

    // project (key) or projectId
    const projectParam = route.query.project || route.query.projectId
    if (projectParam) {
      const queryProject = String(projectParam)
      const matched = projectList.value.find(p => p.key === queryProject || p.id === queryProject)
      if (matched) {
        filterProject.value = matched.id
        activeProjectId.value = matched.id
      }
    }

    // Set display label
    if (route.query.label) {
      activeQueryName.value = String(route.query.label)
    }

    initialFilterChips.value = chips
    globalFilterParams.value = filters
    refreshList()
  }

  /** Check whether the current route has dashboard filter params */
  function hasDashboardFilterParams(): boolean {
    return !!(
      route.query.statusId || route.query.statusCode || route.query.statusCategory ||
      route.query.statusName || route.query.status || route.query.overdue ||
      route.query.dueSoon || route.query.sprint || route.query.sprintStatus ||
      route.query.reportedByMe || route.query.assignedToMe || route.query.priority ||
      route.query.issueType || route.query.assigneeName || route.query.assignee ||
      route.query.reporter || route.query.projectId || route.query.keyword
    )
  }

  return {
    applyDashboardFilter,
    hasDashboardFilterParams
  }
}
