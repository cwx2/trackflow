import request from './request'
import type { R, PageResult } from './types'

export interface IntegrationAdapterVO {
  adapterType: string
  displayName: string
  enabled: boolean
  supportedEvents: string[]
  maxRetries: number
  retryIntervalSeconds: number
  lastActivityAt: string | null
  successCount: number
  failedCount: number
}

export interface IntegrationConfigVO {
  adapterType: string
  displayName: string
  enabled: boolean
  config: Record<string, string>
}

export interface IntegrationLogVO {
  id: string
  adapterType: string
  eventType: string
  direction: string
  referenceId: string
  payload: string
  status: string
  errorMessage: string | null
  retryCount: number
  nextRetryAt: string | null
  createdAt: string
  updatedAt: string
}

export interface IntegrationLogQuery {
  adapterType?: string
  status?: string
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

export const integrationAdminApi = {
  /** 列出所有已注册的适配器 */
  listAdapters() {
    return request.get<any, R<IntegrationAdapterVO[]>>('/admin/integrations')
  },

  /** 启用/禁用适配器 */
  toggleAdapter(adapterType: string, enabled: boolean) {
    return request.put<any, R<void>>(`/admin/integrations/${adapterType}/toggle`, { enabled })
  },

  /** 获取适配器配置 */
  getConfig(adapterType: string) {
    return request.get<any, R<IntegrationConfigVO>>(`/admin/integrations/${adapterType}/config`)
  },

  /** 更新适配器配置 */
  updateConfig(adapterType: string, config: Record<string, string>) {
    return request.put<any, R<IntegrationConfigVO>>(`/admin/integrations/${adapterType}/config`, { config })
  },

  /** 查询集成日志 */
  queryLogs(params: IntegrationLogQuery) {
    return request.get<any, R<PageResult<IntegrationLogVO>>>('/admin/integrations/logs', { params })
  },

  /** 手动重试失败事件 */
  retryEvent(logId: string) {
    return request.post<any, R<IntegrationLogVO>>(`/admin/integrations/logs/${logId}/retry`)
  }
}
