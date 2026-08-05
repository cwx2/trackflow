import request from './request'
import type { R, PageResult } from './types'

// ==================== Types ====================

export interface RuleDefinitionVO {
  id: string
  name: string
  description: string | null
  projectId: string | null
  projectName: string | null
  enabled: boolean
  triggerType: 'scheduled' | 'event' | 'manual'
  triggerConfig: string
  scoreFormula: 'linear_daily' | 'fixed' | 'cumulative_increment' | 'custom'
  scoreConfig: string
  targetField: 'assignee' | 'reporter' | 'created_by'
  scheduleCron: string | null
  dedupStrategy: 'daily' | 'once_per_issue' | 'no_dedup'
  actions: string | null
  createdAt: string
  updatedAt: string
  createdByName: string | null
  lastExecutedAt: string | null
  executionCount: number
}

export interface SaveRuleDefinitionDTO {
  name: string
  description?: string
  projectId?: number | null
  triggerType: string
  triggerConfig: string
  scoreFormula: string
  scoreConfig: string
  targetField?: string
  scheduleCron?: string
  dedupStrategy?: string
  actions?: string
}

export interface RuleExecutionLogVO {
  id: string
  ruleId: string
  ruleName: string
  issueId: string
  issueKey: string
  issueTitle: string
  targetUserId: string
  targetUserName: string
  score: number
  scoreDetail: string | null
  executedAt: string
  executionDate: string
  note: string | null
}

export interface CreateExecutionLogDTO {
  ruleId: number
  issueId: number
  targetUserId: number
  score: number
  scoreDetail?: string
  note?: string
}

export interface RuleStatisticsVO {
  ranking: Array<{
    userId: string
    displayName: string
    totalScore: number
    executionCount: number
  }>
  trend: Array<{
    date: string
    dailyScore: number
    dailyCount: number
  }>
  summaryByRule: Array<{
    ruleId: string
    ruleName: string
    totalScore: number
    executionCount: number
  }>
}

// ==================== API ====================

export const scoreRuleApi = {
  // 规则定义管理
  listRules(params?: { projectId?: number }) {
    return request.get<any, R<RuleDefinitionVO[]>>('/rules', { params })
  },

  getRuleDetail(id: string) {
    return request.get<any, R<RuleDefinitionVO>>(`/rules/${id}`)
  },

  createRule(data: SaveRuleDefinitionDTO) {
    return request.post<any, R<RuleDefinitionVO>>('/rules', data)
  },

  updateRule(id: string, data: SaveRuleDefinitionDTO) {
    return request.put<any, R<RuleDefinitionVO>>(`/rules/${id}`, data)
  },

  deleteRule(id: string) {
    return request.delete<any, R<void>>(`/rules/${id}`)
  },

  toggleRule(id: string) {
    return request.post<any, R<void>>(`/rules/${id}/toggle`)
  },

  executeNow(id: string) {
    return request.post<any, R<{ executedCount: number }>>(`/rules/${id}/execute-now`)
  },

  // 执行记录
  listLogs(params?: {
    ruleId?: number
    issueId?: number
    targetUserId?: number
    startDate?: string
    endDate?: string
    page?: number
    pageSize?: number
  }) {
    return request.get<any, R<PageResult<RuleExecutionLogVO>>>('/rules/logs', { params })
  },

  createLog(data: CreateExecutionLogDTO) {
    return request.post<any, R<RuleExecutionLogVO>>('/rules/logs', data)
  },

  deleteLog(id: string) {
    return request.delete<any, R<void>>(`/rules/logs/${id}`)
  },

  // 统计
  getStatistics(params?: {
    ruleId?: number
    startDate?: string
    endDate?: string
    rankLimit?: number
  }) {
    return request.get<any, R<RuleStatisticsVO>>('/rules/statistics', { params })
  },

  // Issue 关联
  getIssueRuleLogs(issueId: string) {
    return request.get<any, R<RuleExecutionLogVO[]>>(`/rules/issues/${issueId}/logs`)
  },

  getIssueTotalScore(issueId: string) {
    return request.get<any, R<{ totalScore: number }>>(`/rules/issues/${issueId}/total-score`)
  }
}
