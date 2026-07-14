import request from './request'
import type { R } from './types'

export interface NotificationPreferenceVO {
  id: string
  userId: string
  onIssueAssigned: boolean
  onIssueStatusChanged: boolean
  onIssueCommented: boolean
  onMentioned: boolean
  onIssueResolved: boolean
  onSprintStarted: boolean
  onSprintCompleted: boolean
  emailEnabled: boolean
  quietHoursStart: string | null
  quietHoursEnd: string | null
}

export interface UpdateNotificationPreferenceDTO {
  onIssueAssigned?: boolean
  onIssueStatusChanged?: boolean
  onIssueCommented?: boolean
  onMentioned?: boolean
  onIssueResolved?: boolean
  onSprintStarted?: boolean
  onSprintCompleted?: boolean
  emailEnabled?: boolean
  quietHoursStart?: string | null
  quietHoursEnd?: string | null
}

export const notificationPreferenceApi = {
  /** 获取当前用户的通知偏好 */
  get() {
    return request.get<any, R<NotificationPreferenceVO>>('/notification-preferences')
  },

  /** 更新当前用户的通知偏好 */
  update(data: UpdateNotificationPreferenceDTO) {
    return request.put<any, R<NotificationPreferenceVO>>('/notification-preferences', data)
  }
}
