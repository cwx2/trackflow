import request from './request'
import type { R, ApiKeyVO, ApiKeyCreatedVO } from './types'

export interface CreateApiKeyParams {
  name: string
  permissions?: string[]
  expiresAt?: string
}

/**
 * API Key 管理模块
 */
export const apiKeyApi = {
  /** 获取当前用户的 API Key 列表 */
  list() {
    return request.get<any, R<ApiKeyVO[]>>('/api-keys')
  },

  /** 创建 API Key */
  create(data: CreateApiKeyParams) {
    return request.post<any, R<ApiKeyCreatedVO>>('/api-keys', data)
  },

  /** 撤销（删除）API Key */
  revoke(id: string) {
    return request.delete<any, R<void>>(`/api-keys/${id}`)
  }
}
