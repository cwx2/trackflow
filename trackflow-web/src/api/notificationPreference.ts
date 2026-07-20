import request from './request'
import type { R } from './types'

export interface NotificationPreferenceVO {
  id: string
  userId: string
  projectId: string | null
  onIssueAssigned: boolean
  onIssueStatusChanged: boolean
  onIssueCommented: boolean
  onMentioned: boolean
  onIssueResolved: boolean
  onIssueUpdated: boolean
  onSprintStarted: boolean
  onSprintCompleted: boolean
  onProjectMemberChanged: boolean
  onProjectLifecycle: boolean
  onDueDate: boolean
  onOverdue: boolean
  dueDateAdvanceDays: number
  notifyOwnChanges: boolean
  emailEnabled: boolean
  // Per-event 邮件渠道控制
  emailOnIssueAssigned: boolean
  emailOnIssueStatusChanged: boolean
  emailOnIssueCommented: boolean
  emailOnMentioned: boolean
  emailOnIssueResolved: boolean
  emailOnIssueUpdated: boolean
  emailOnSprintStarted: boolean
  emailOnSprintCompleted: boolean
  emailOnProjectMemberChanged: boolean
  emailOnProjectLifecycle: boolean
  emailOnDueDate: boolean
  emailOnOverdue: boolean
  emailOnWatchedUpdated: boolean
  quietHoursStart: string | null
  quietHoursEnd: string | null
  // Watched 通知开关
  onWatchedUpdated: boolean
  // 自动关注行为配置
  autoWatchOnCreate: boolean
  autoWatchOnComment: boolean
  autoWatchOnUpdate: boolean
  autoWatchOnAssign: boolean
}

export interface UpdateNotificationPreferenceDTO {
  onIssueAssigned?: boolean
  onIssueStatusChanged?: boolean
  onIssueCommented?: boolean
  onMentioned?: boolean
  onIssueResolved?: boolean
  onIssueUpdated?: boolean
  onSprintStarted?: boolean
  onSprintCompleted?: boolean
  onProjectMemberChanged?: boolean
  onProjectLifecycle?: boolean
  onDueDate?: boolean
  onOverdue?: boolean
  dueDateAdvanceDays?: number
  notifyOwnChanges?: boolean
  emailEnabled?: boolean
  // Per-event 邮件渠道控制
  emailOnIssueAssigned?: boolean
  emailOnIssueStatusChanged?: boolean
  emailOnIssueCommented?: boolean
  emailOnMentioned?: boolean
  emailOnIssueResolved?: boolean
  emailOnIssueUpdated?: boolean
  emailOnSprintStarted?: boolean
  emailOnSprintCompleted?: boolean
  emailOnProjectMemberChanged?: boolean
  emailOnProjectLifecycle?: boolean
  emailOnDueDate?: boolean
  emailOnOverdue?: boolean
  emailOnWatchedUpdated?: boolean
  quietHoursStart?: string | null
  quietHoursEnd?: string | null
  // Watched 通知开关
  onWatchedUpdated?: boolean
  // 自动关注行为配置
  autoWatchOnCreate?: boolean
  autoWatchOnComment?: boolean
  autoWatchOnUpdate?: boolean
  autoWatchOnAssign?: boolean
}

export interface EmailAvailabilityVO {
  available: boolean
  globalEnabled: boolean
  smtpConfigured: boolean
  reason: string | null
}

export const notificationPreferenceApi = {
  /** 获取当前用户的全局通知偏好 */
  get() {
    return request.get<any, R<NotificationPreferenceVO>>('/notification-preferences')
  },

  /** 查询全局邮件通知可用状态 */
  getEmailStatus() {
    return request.get<any, R<EmailAvailabilityVO>>('/notification-preferences/email-status')
  },

  /** 更新当前用户的全局通知偏好 */
  update(data: UpdateNotificationPreferenceDTO) {
    return request.put<any, R<NotificationPreferenceVO>>('/notification-preferences', data)
  },

  /** 列出用户已配置的所有项目级偏好 */
  listProjectPreferences() {
    return request.get<any, R<NotificationPreferenceVO[]>>('/notification-preferences/projects')
  },

  /** 获取指定项目的偏好（可能为 null 表示使用全局设置） */
  getProjectPreference(projectId: string) {
    return request.get<any, R<NotificationPreferenceVO | null>>(`/notification-preferences/projects/${projectId}`)
  },

  /** 设置/更新指定项目的通知偏好 */
  updateProjectPreference(projectId: string, data: UpdateNotificationPreferenceDTO) {
    return request.put<any, R<NotificationPreferenceVO>>(`/notification-preferences/projects/${projectId}`, data)
  },

  /** 删除指定项目的偏好（恢复使用全局设置） */
  deleteProjectPreference(projectId: string) {
    return request.delete<any, R<void>>(`/notification-preferences/projects/${projectId}`)
  }
}
