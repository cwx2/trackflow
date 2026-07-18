import request from './request'
import type { R, PageResult } from './types'

// === Webhook 类型定义 ===

export interface WebhookVO {
  id: string
  projectId: string
  name: string
  url: string
  events: string
  active: boolean
  createdAt: string
}

export interface WebhookLogVO {
  id: string
  webhookId: string
  event: string
  responseStatus: number | null
  responseBody: string | null
  success: boolean
  createdAt: string
}

export interface CreateWebhookDTO {
  projectId: string
  name: string
  url: string
  secret?: string
  events: string
  active?: boolean
}

export interface UpdateWebhookDTO {
  name?: string
  url?: string
  secret?: string
  events?: string
  active?: boolean
}

// === Webhook API ===

export const webhookApi = {
  /** 获取项目 Webhook 列表 */
  list(projectId: string) {
    return request.get<any, R<WebhookVO[]>>('/webhooks', { params: { projectId } })
  },

  /** 创建 Webhook */
  create(data: CreateWebhookDTO) {
    return request.post<any, R<WebhookVO>>('/webhooks', data)
  },

  /** 更新 Webhook */
  update(id: string, data: UpdateWebhookDTO) {
    return request.put<any, R<WebhookVO>>(`/webhooks/${id}`, data)
  },

  /** 删除 Webhook */
  delete(id: string) {
    return request.delete<any, R<void>>(`/webhooks/${id}`)
  },

  /** 手动测试触发 */
  testTrigger(id: string) {
    return request.post<any, R<WebhookLogVO>>(`/webhooks/${id}/test`)
  },

  /** 获取投递日志 */
  getDeliveryLogs(id: string, page = 1, pageSize = 20) {
    return request.get<any, R<PageResult<WebhookLogVO>>>(`/webhooks/${id}/logs`, { params: { page, pageSize } })
  }
}
