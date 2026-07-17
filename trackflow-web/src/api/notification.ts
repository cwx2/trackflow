import request from './request'
import type { R, PageResult } from './types'

/**
 * 通知类型（与后端 NotificationType 枚举同步）
 */
export type NotificationType =
  | 'issue_assigned'
  | 'issue_auto_assigned'
  | 'issue_commented'
  | 'issue_status_changed'
  | 'mention'
  | 'member_added'
  | 'member_removed'
  | 'role_changed'
  | 'lead_changed'
  | 'project_archived'
  | 'project_restored'
  | 'project_deleted'
  | 'sprint_started'
  | 'sprint_completed'

/**
 * 通知分类（与后端 NotificationCategory 枚举同步）
 */
export type NotificationCategory = 'all' | 'mention' | 'subscription' | 'system'

export interface NotificationVO {
  id: string
  userId: string
  actorId?: string
  actorName?: string
  actorAvatar?: string
  title: string
  content: string
  type: NotificationType
  resourceType?: string
  resourceId?: string
  isRead: boolean
  createdAt: string
  updatedAt?: string
  aggregationCount?: number
}

/**
 * 各分类未读计数
 */
export interface CategoryUnreadCounts {
  all: number
  mention: number
  subscription: number
  system: number
}

/**
 * 通知模块 API
 */
export const notificationApi = {
  /** 获取通知列表 */
  list(params?: { unreadOnly?: boolean; category?: NotificationCategory; page?: number; pageSize?: number }) {
    return request.get<any, R<PageResult<NotificationVO>>>('/notifications', { params })
  },

  /** 获取未读数量 */
  unreadCount() {
    return request.get<any, R<{ count: number }>>('/notifications/unread-count')
  },

  /** 获取各分类的未读计数 */
  unreadCountByCategory() {
    return request.get<any, R<CategoryUnreadCounts>>('/notifications/unread-count-by-category')
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
