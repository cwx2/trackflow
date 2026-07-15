import request from './request'
import type { R } from './types'

export interface DashboardSummaryVO {
  assignedOpen: number
  assignedInProgress: number
  completedThisWeek: number
  dueSoon: number
  overdue: number
  reportedByMeOpen: number
  testingCount: number
  totalIssues: number
  activeProjects: number
  primaryRoleCode: string | null
  // 周对比数据
  lastWeekOpen: number
  lastWeekInProgress: number
  lastWeekCompleted: number
  lastWeekOverdue: number
}

export interface DashboardActivityVO {
  id: string
  issueId: string
  issueKey: string
  issueTitle: string
  userId: string
  userName: string
  action: string
  fieldName?: string
  oldValue?: string
  newValue?: string
  createdAt: string
}

// ─── 图表数据类型 ─────────────────────────────────────

export interface DashboardTrendData {
  dates: string[]
  created: number[]
  resolved: number[]
}

export interface DashboardStatusItem {
  name: string
  value: number
  color: string
  category: string
}

export interface DashboardStatusDistribution {
  items: DashboardStatusItem[]
  total: number
}

export interface DashboardWorkloadItem {
  name: string
  total: number
  done: number
  inProgress: number
}

export interface DashboardWorkloadData {
  items: DashboardWorkloadItem[]
  total: number
}

export interface DashboardChartsVO {
  trend: DashboardTrendData
  statusDistribution: DashboardStatusDistribution
  workload: DashboardWorkloadData
}

/**
 * Dashboard 模块 API
 */
export const dashboardApi = {
  /** 统计概览 */
  summary() {
    return request.get<any, R<DashboardSummaryVO>>('/dashboard/summary')
  },

  /** 图表数据（趋势、状态分布、工作负载） */
  charts() {
    return request.get<any, R<DashboardChartsVO>>('/dashboard/charts')
  },

  /** 分配给我的工单 */
  assignedToMe(limit = 10) {
    return request.get<any, R<any[]>>('/dashboard/assigned-to-me', { params: { limit } })
  },

  /** 即将到期 / 逾期工单 */
  overdue(days = 7, limit = 10) {
    return request.get<any, R<any[]>>('/dashboard/overdue', { params: { days, limit } })
  },

  /** 最近活动流 */
  activity(limit = 20) {
    return request.get<any, R<DashboardActivityVO[]>>('/dashboard/activity', { params: { limit } })
  }
}
