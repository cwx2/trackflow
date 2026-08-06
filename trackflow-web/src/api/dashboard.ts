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
  userAvatar?: string
  action: string
  fieldName?: string
  oldValue?: string
  newValue?: string
  createdAt: string
}

export interface ProjectTeamMemberVO {
  userId: string
  username: string
  displayName: string
  email?: string
  roleName: string
  openIssueCount: number
}

// ─── 图表数据已迁移至 reportStatisticsApi.dashboard() ─────────────────────────────────────

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
  },

  /** Widget 活动流（支持多维筛选） */
  activityFeed(params?: { projectIds?: string[]; actions?: string[]; userIds?: string[]; limit?: number }) {
    return request.get<any, R<DashboardActivityVO[]>>('/dashboard/activity-feed', { params })
  },

  /** 项目团队成员数据（用于 Project Team Widget） */
  projectTeam(params: { projectId: string; limit?: number }) {
    return request.get<any, R<ProjectTeamMemberVO[]>>('/dashboard/project-team', { params })
  }
}
