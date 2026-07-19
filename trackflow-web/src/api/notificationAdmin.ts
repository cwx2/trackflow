import request from './request'
import type { R, PageResult } from './types'

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
  defaultNotifyOwnChanges: boolean
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
  defaultNotifyOwnChanges?: boolean
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

export interface SendTestEmailDTO {
  toAddress: string
}

// === 通知发件箱（Outbox）类型 ===

export interface NotificationOutboxVO {
  id: string
  eventType: string
  payload: string
  status: 'pending' | 'failed' | 'completed'
  retryCount: number
  maxRetries: number
  nextRetryAt: string | null
  errorMessage: string | null
  createdAt: string
  completedAt: string | null
}

export interface OutboxStats {
  pending: number
  failed: number
  completed: number
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
  },

  /** 发送测试邮件 */
  sendTestEmail(data: SendTestEmailDTO) {
    return request.post<any, R<void>>('/admin/notifications/email-config/test', data)
  },

  // === 通知发件箱（Outbox）管理 ===

  /** 查看通知发件箱列表 */
  listOutbox(params?: { status?: string; page?: number; pageSize?: number }) {
    return request.get<any, R<PageResult<NotificationOutboxVO>>>('/admin/notifications/outbox', { params })
  },

  /** 获取发件箱统计 */
  getOutboxStats() {
    return request.get<any, R<OutboxStats>>('/admin/notifications/outbox/stats')
  },

  /** 手动重试一条失败的通知 */
  retryOutboxItem(id: string) {
    return request.post<any, R<void>>(`/admin/notifications/outbox/${id}/retry`)
  }
}
