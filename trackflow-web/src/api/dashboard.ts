import request from './request'
import type { R } from './types'

export interface DashboardSummaryVO {
  assignedOpen: number
  assignedInProgress: number
  completedThisWeek: number
  dueSoon: number
  overdue: number
  reportedByMeOpen: number
  totalIssues: number
  activeProjects: number
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

/**
 * Dashboard 模块 API
 */
export const dashboardApi = {
  /** 统计概览 */
  summary() {
    return request.get<any, R<DashboardSummaryVO>>('/dashboard/summary')
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
