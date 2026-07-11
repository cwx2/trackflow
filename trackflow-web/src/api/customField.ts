import request from './request'
import type { R, PageResult, CustomFieldDefinitionVO, AvailableColumnVO } from './types'

/**
 * 自定义字段模块 API
 */
export const customFieldApi = {
  // ========== Admin CRUD ==========

  /** 字段定义列表（管理员） */
  list(params?: { page?: number; pageSize?: number; fieldFormat?: string; keyword?: string }) {
    return request.get<any, R<PageResult<CustomFieldDefinitionVO>>>('/admin/custom-fields', { params })
  },

  /** 创建自定义字段 */
  create(data: {
    name: string
    fieldFormat: string
    isRequired?: boolean
    isForAll?: boolean
    defaultValue?: string
    minLength?: number
    maxLength?: number
    regexp?: string
    options?: Array<{ value: string; isDefault?: boolean }>
    projectIds?: string[]
    issueTypes?: string[]
  }) {
    return request.post<any, R<CustomFieldDefinitionVO>>('/admin/custom-fields', data)
  },

  /** 更新自定义字段 */
  update(id: string, data: {
    name?: string
    isRequired?: boolean
    isForAll?: boolean
    defaultValue?: string
    minLength?: number
    maxLength?: number
    regexp?: string
    options?: Array<{ id?: string; value: string; isDefault?: boolean }>
    projectIds?: string[]
    issueTypes?: string[]
  }) {
    return request.put<any, R<CustomFieldDefinitionVO>>(`/admin/custom-fields/${id}`, data)
  },

  /** 删除自定义字段 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/admin/custom-fields/${id}`)
  },

  /** 排序自定义字段 */
  reorder(ids: string[]) {
    return request.put<any, R<void>>('/admin/custom-fields/reorder', { ids })
  },

  // ========== 项目级读取 ==========

  /** 获取项目可用的自定义字段 */
  listByProject(projectId: string, issueType?: string) {
    const params: Record<string, string> = {}
    if (issueType) params.issueType = issueType
    return request.get<any, R<CustomFieldDefinitionVO[]>>(`/projects/${projectId}/custom-fields`, { params })
  },

  /** 获取项目可用的列配置（固定列 + 自定义字段列） */
  availableColumns(projectId?: string) {
    if (projectId) {
      return request.get<any, R<AvailableColumnVO[]>>(`/projects/${projectId}/available-columns`)
    }
    return request.get<any, R<AvailableColumnVO[]>>('/available-columns')
  }
}
