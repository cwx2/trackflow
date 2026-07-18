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

export interface ProjectComparisonItem {
  name: string
  key: string
  total: number
  open: number
  closed: number
  completionRate: number
  overdue: number
}

export interface ProjectComparisonData {
  items: ProjectComparisonItem[]
}

export interface CumulativeFlowSeries {
  name: string
  color: string
  data: number[]
}

export interface CumulativeFlowData {
  dates: string[]
  series: CumulativeFlowSeries[]
}

export interface ResolutionTimeGroupDetail {
  name: string
  avgHours: number
  medianHours: number
  count: number
}

export interface ResolutionTimeData {
  dates: string[]
  avgHours: (number | null)[]
  medianHours: (number | null)[]
  p90Hours: (number | null)[]
  resolvedCount: number[]
  groupDetails: ResolutionTimeGroupDetail[]
}

// ─── 时间报表类型 ──────────────────────────────────────

export interface TimeReportGroupItem {
  name: string
  minutes: number
  percentage: number
}

export interface TimeReportCrossItem {
  projectName: string
  userName: string
  minutes: number
}

export interface TimeReportData {
  totalMinutes: number
  byUser: TimeReportGroupItem[]
  byProject: TimeReportGroupItem[]
  byWorkType: TimeReportGroupItem[]
  trendDates: string[]
  trendMinutes: number[]
  crossProjectUser: TimeReportCrossItem[]
}

export interface EstimationIssueItem {
  issueId: string
  issueKey: string
  issueTitle: string
  projectName: string
  assigneeName: string
  estimatedHours: number
  spentHours: number
  deviationRate: number
  deviation: 'over' | 'under' | 'on_track'
}

export interface EstimationProjectItem {
  projectName: string
  estimatedHours: number
  spentHours: number
  deviationRate: number
  issueCount: number
}

export interface EstimationReportData {
  totalEstimatedHours: number
  totalSpentHours: number
  overallDeviationRate: number
  items: EstimationIssueItem[]
  pagination: {
    page: number
    pageSize: number
    total: number
    totalPages: number
  }
  byProject: EstimationProjectItem[]
}

export interface DashboardData {
  statusDistribution: StatusDistributionData
  priorityDistribution: PriorityDistributionData
  typeDistribution: TypeDistributionData
  workload: WorkloadData
  trend: TrendData
  burndown?: BurndownData
  overview: OverviewData
  projectComparison?: ProjectComparisonData
  cumulativeFlow?: CumulativeFlowData
  resolutionTime?: ResolutionTimeData
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
  },

  /** 累积流图 */
  cumulativeFlow(projectId: string, startDate?: string, endDate?: string) {
    return request.get<any, R<CumulativeFlowData>>('/reports/statistics/cumulative-flow', {
      params: { projectId, startDate, endDate }
    })
  },

  /** 解决时间分析 */
  resolutionTime(projectId: string, startDate?: string, endDate?: string, groupBy?: string) {
    return request.get<any, R<ResolutionTimeData>>('/reports/statistics/resolution-time', {
      params: { projectId, startDate, endDate, groupBy }
    })
  },

  /** 时间报表（按人员/项目/工作类型汇总工时） */
  timeReport(params: { projectId?: string; startDate?: string; endDate?: string }) {
    return request.get<any, R<TimeReportData>>('/reports/statistics/time-report', { params })
  },

  /** 预估对比报表 */
  estimationReport(projectId?: string, page?: number, pageSize?: number) {
    return request.get<any, R<EstimationReportData>>('/reports/statistics/estimation-report', {
      params: { projectId, page: page || 1, pageSize: pageSize || 50 }
    })
  }
}
