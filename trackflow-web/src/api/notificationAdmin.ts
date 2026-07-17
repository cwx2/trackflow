import request from './request'
import type { R } from './types'

// === 通知管理设置 ===

export interface NotificationSettingsVO {
  inAppEnabled: boolean
  emailEnabled: boolean
  retentionDays: number
  defaultOnIssueAssigned: boolean
  defaultOnIssueStatusChanged: boolean
  defaultOnIssueCommented: boolean
  defaultOnMentioned: boolean
  defaultOnIssueResolved: boolean
  defaultOnSprintStarted: boolean
  defaultOnSprintCompleted: boolean
  defaultOnProjectMemberChanged: boolean
  defaultOnProjectLifecycle: boolean
  defaultEmailEnabled: boolean
}

export interface UpdateNotificationSettingsDTO {
  inAppEnabled?: boolean
  emailEnabled?: boolean
  retentionDays?: number
  defaultOnIssueAssigned?: boolean
  defaultOnIssueStatusChanged?: boolean
  defaultOnIssueCommented?: boolean
  defaultOnMentioned?: boolean
  defaultOnIssueResolved?: boolean
  defaultOnSprintStarted?: boolean
  defaultOnSprintCompleted?: boolean
  defaultOnProjectMemberChanged?: boolean
  defaultOnProjectLifecycle?: boolean
  defaultEmailEnabled?: boolean
}

export interface NotificationStatsVO {
  totalCount: number
  unreadCount: number
  readCount: number
  todayCount: number
  weekCount: number
  typeDistribution: Record<string, number>
}

export interface EmailConfigVO {
  host: string
  port: number
  protocol: string
  username: string
  password: string
  sslEnabled: boolean
  fromAddress: string
  replyToAddress: string
  configured: boolean
}

export interface UpdateEmailConfigDTO {
  host?: string
  port?: number
  protocol?: string
  username?: string
  password?: string
  sslEnabled?: boolean
  fromAddress?: string
  replyToAddress?: string
}

export const notificationAdminApi = {
  /** 获取全局通知设置 */
  getSettings() {
    return request.get<any, R<NotificationSettingsVO>>('/admin/notifications/settings')
  },

  /** 更新全局通知设置 */
  updateSettings(data: UpdateNotificationSettingsDTO) {
    return request.put<any, R<NotificationSettingsVO>>('/admin/notifications/settings', data)
  },

  /** 获取通知统计概览 */
  getStats() {
    return request.get<any, R<NotificationStatsVO>>('/admin/notifications/stats')
  },

  /** 获取邮件服务器配置 */
  getEmailConfig() {
    return request.get<any, R<EmailConfigVO>>('/admin/notifications/email-config')
  },

  /** 更新邮件服务器配置 */
  updateEmailConfig(data: UpdateEmailConfigDTO) {
    return request.put<any, R<EmailConfigVO>>('/admin/notifications/email-config', data)
  }
}
