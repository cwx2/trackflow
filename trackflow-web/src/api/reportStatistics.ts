import request from './request'
import type { R } from './types'

// ─── 类型定义 ─────────────────────────────────────────

export interface DashboardParams {
  projectId?: string
  sprintId?: string
  startDate?: string  // ISO date string
  endDate?: string
}

export interface OverviewData {
  total: number
  open: number
  closed: number
  unassigned: number
  overdue: number
  completionRate: number
}

export interface StatusDistributionItem {
  name: string
  value: number
  color: string
  category: string
}

export interface StatusDistributionData {
  items: StatusDistributionItem[]
  total: number
}

export interface PriorityDistributionData {
  labels: string[]
  data: number[]
  colors: string[]
  total: number
}

export interface TypeDistributionItem {
  name: string
  value: number
  color: string
}

export interface TypeDistributionData {
  items: TypeDistributionItem[]
  total: number
}

export interface WorkloadItem {
  name: string
  value: number
  done: number
  inProgress: number
}

export interface WorkloadData {
  items: WorkloadItem[]
  total: number
}

export interface TrendData {
  dates: string[]
  created: number[]
  resolved: number[]
}

export interface BurndownData {
  dates: string[]
  ideal: number[]
  actual: number[]
  sprintName: string
  totalIssues: number
}

export interface DashboardData {
  statusDistribution: StatusDistributionData
  priorityDistribution: PriorityDistributionData
  typeDistribution: TypeDistributionData
  workload: WorkloadData
  trend: TrendData
  burndown?: BurndownData
  overview: OverviewData
}

// ─── API ──────────────────────────────────────────────

export const reportStatisticsApi = {
  /** 获取仪表盘全量数据 */
  dashboard(params: DashboardParams) {
    return request.get<any, R<DashboardData>>('/reports/statistics/dashboard', { params })
  },

  /** 工单状态分布 */
  statusDistribution(projectId: string, sprintId?: string) {
    return request.get<any, R<StatusDistributionData>>('/reports/statistics/status-distribution', {
      params: { projectId, sprintId }
    })
  },

  /** 优先级分布 */
  priorityDistribution(projectId: string, sprintId?: string) {
    return request.get<any, R<PriorityDistributionData>>('/reports/statistics/priority-distribution', {
      params: { projectId, sprintId }
    })
  },

  /** 工单趋势 */
  trend(projectId: string, startDate?: string, endDate?: string) {
    return request.get<any, R<TrendData>>('/reports/statistics/trend', {
      params: { projectId, startDate, endDate }
    })
  },

  /** 团队工作负载 */
  workload(projectId: string, sprintId?: string) {
    return request.get<any, R<WorkloadData>>('/reports/statistics/workload', {
      params: { projectId, sprintId }
    })
  },

  /** 工单类型分布 */
  typeDistribution(projectId: string, sprintId?: string) {
    return request.get<any, R<TypeDistributionData>>('/reports/statistics/type-distribution', {
      params: { projectId, sprintId }
    })
  },

  /** Sprint 燃尽图 */
  burndown(projectId: string, sprintId: string) {
    return request.get<any, R<BurndownData>>('/reports/statistics/burndown', {
      params: { projectId, sprintId }
    })
  }
}
