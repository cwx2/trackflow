import request from './request'
import type { R } from './types'

// ==================== Types ====================

export interface SubscriptionEventsVO {
  onCreated: boolean
  onUpdated: boolean
  onResolved: boolean
  onCommented: boolean
  onTagAdded: boolean
  onTagRemoved: boolean
}

export interface NotificationSubscriptionVO {
  id: string
  userId: string
  name: string
  sourceType: 'tag' | 'saved_query' | 'builtin' | 'project'
  sourceId: string | null
  sourceName: string
  builtinKey: string | null
  isDefault: boolean
  events: SubscriptionEventsVO
  createdAt: string
  updatedAt: string
}

export interface CreateSubscriptionDTO {
  sourceType: 'tag' | 'saved_query' | 'project'
  sourceId: string
  events?: Partial<SubscriptionEventsVO>
}

export interface UpdateSubscriptionEventsDTO {
  events: Partial<SubscriptionEventsVO>
}

// ==================== API ====================

export const notificationSubscriptionApi = {
  /** 获取当前用户的所有订阅 */
  list() {
    return request.get<any, R<NotificationSubscriptionVO[]>>('/notification-subscriptions')
  },

  /** 创建自定义订阅 */
  create(data: CreateSubscriptionDTO) {
    return request.post<any, R<NotificationSubscriptionVO>>('/notification-subscriptions', data)
  },

  /** 更新订阅事件配置 */
  update(id: string, data: UpdateSubscriptionEventsDTO) {
    return request.put<any, R<NotificationSubscriptionVO>>(`/notification-subscriptions/${id}`, data)
  },

  /** 删除订阅 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/notification-subscriptions/${id}`)
  }
}
