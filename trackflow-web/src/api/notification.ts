import request from './request'
import type { R, PageResult } from './types'

export interface NotificationVO {
  id: string
  userId: string
  actorId?: string
  actorName?: string
  actorAvatar?: string
  title: string
  content: string
  type: string
  resourceType?: string
  resourceId?: string
  isRead: boolean
  createdAt: string
}

/**
 * 通知模块 API
 */
export const notificationApi = {
  /** 获取通知列表 */
  list(params?: { unreadOnly?: boolean; page?: number; pageSize?: number }) {
    return request.get<any, R<PageResult<NotificationVO>>>('/notifications', { params })
  },

  /** 获取未读数量 */
  unreadCount() {
    return request.get<any, R<{ count: number }>>('/notifications/unread-count')
  },

  /** 标记单条已读 */
  markRead(id: string) {
    return request.put<any, R<void>>(`/notifications/${id}/read`)
  },

  /** 全部标记已读 */
  markAllRead() {
    return request.put<any, R<void>>('/notifications/read-all')
  },

  /** 删除单条通知 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/notifications/${id}`)
  },

  /** 清除所有已读通知 */
  deleteAllRead() {
    return request.delete<any, R<{ deleted: number }>>('/notifications/read')
  }
}
