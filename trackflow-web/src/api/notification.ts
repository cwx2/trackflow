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
  | 'issue_updated'
  | 'issue_voted'
  | 'issue_spent_time'
  | 'mention'
  | 'issue_moved'
  | 'member_added'
  | 'member_removed'
  | 'role_changed'
  | 'lead_changed'
  | 'project_archived'
  | 'project_restored'
  | 'project_deleted'
  | 'sprint_started'
  | 'sprint_completed'
  | 'due_date_alert'
  | 'overdue_alert'

/**
 * 通知分类（与后端 NotificationCategory 枚举同步）
 */
export type NotificationCategory = 'all' | 'mention' | 'subscription' | 'system'

/**
 * 通知接收原因（与后端 NotificationReason 枚举同步）
 */
export type NotificationReason =
  | 'assigned'
  | 'reporter'
  | 'commenter'
  | 'mentioned'
  | 'member'
  | 'watched'
  | 'subscription'
  | 'auto_assigned'
  | 'rule_triggered'

export interface NotificationVO {
  id: string
  userId: string
  actorId?: string
  actorName?: string
  actorAvatar?: string
  projectId?: string
  title: string
  content: string
  type: NotificationType
  reason?: NotificationReason
  reasonLabel?: string
  resourceType?: string
  resourceId?: string
  /** 资源直链路径（前端路由相对路径），如 /issues/DE4-123 */
  resourceUrl?: string
  isRead: boolean
  createdAt: string
  updatedAt?: string
  aggregationCount?: number
  /** 该通知对应的资源是否已被当前用户静音 */
  resourceMuted?: boolean
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
 * 已静音线程 VO
 */
export interface MutedThreadVO {
  id: string
  userId: string
  resourceType: string
  resourceId: string
  resourceTitle: string
  createdAt: string
}

/**
 * 通知模块 API
 */
export const notificationApi = {
  /** 获取通知列表 */
  list(params?: { unreadOnly?: boolean; category?: NotificationCategory; projectId?: string; reason?: NotificationReason; page?: number; pageSize?: number }) {
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

  /** 标记单条未读（恢复未读状态） */
  markUnread(id: string) {
    return request.put<any, R<void>>(`/notifications/${id}/unread`)
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
  },

  /** 静音指定资源的通知 */
  muteThread(resourceType: string, resourceId: string) {
    return request.post<any, R<void>>('/notifications/mute', null, { params: { resourceType, resourceId } })
  },

  /** 取消静音 */
  unmuteThread(resourceType: string, resourceId: string) {
    return request.delete<any, R<void>>('/notifications/mute', { params: { resourceType, resourceId } })
  },

  /** 检查指定资源是否已静音 */
  checkMuted(resourceType: string, resourceId: string) {
    return request.get<any, R<{ muted: boolean }>>('/notifications/mute/check', { params: { resourceType, resourceId } })
  },

  /** 获取已静音线程列表 */
  listMutedThreads() {
    return request.get<any, R<MutedThreadVO[]>>('/notifications/muted-threads')
  }
}
