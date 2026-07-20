/**
 * API 统一出口
 *
 * 使用方式:
 *   import { projectApi, issueApi } from '@/api'
 *   const res = await projectApi.list({ page: 1, pageSize: 20 })
 */
export { projectApi } from './project'
export { issueApi } from './issue'
export { userApi } from './user'
export { sprintApi } from './sprint'
export { queryApi } from './query'
export { tagApi } from './tag'
export { authApi } from './auth'
export { customFieldApi } from './customField'
export { dashboardApi } from './dashboard'
export { timeEntryApi } from './timeEntry'
export { workItemAttributeApi } from './timeEntry'
export { workflowApi } from './workflow'
export { boardApi } from './board'
export { reportApi } from './report'
export { customDashboardApi } from './customDashboard'
export { reportStatisticsApi } from './reportStatistics'
export { notificationPreferenceApi } from './notificationPreference'
export { notificationSubscriptionApi } from './notificationSubscription'
export { transitionActionApi } from './transitionAction'
export { notificationApi } from './notification'
export { auditLogApi } from './auditLog'
export { systemSettingApi } from './systemSetting'
export { notificationAdminApi } from './notificationAdmin'
export { groupApi } from './group'
export { quickActionApi } from './quickAction'
export { ruleApi } from './rule'
export { webhookApi } from './webhook'
export { integrationAdminApi } from './integrationAdmin'
export { workflowRuleApi } from './workflowRule'
export { workflowDefinitionApi } from './workflowDefinition'
export { issueTemplateApi } from './issueTemplate'
export { globalMemberApi } from './globalMember'
export { issueWatcherApi } from './issueWatcher'

// 类型导出
export type * from './types'

// 错误码常量
export { ERROR_CODES } from './error-codes'
export type { ErrorCodeValue } from './error-codes'
