import request from './request'
import type { R, PageResult, CustomFieldDefinitionVO, CustomFieldUsageVO, AvailableColumnVO } from './types'

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
    isMulti?: boolean
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
    isMulti?: boolean
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
  delete(id: string, confirm = false) {
    return request.delete<any, R<void>>(`/admin/custom-fields/${id}`, { params: { confirm } })
  },

  /** 获取字段使用情况（删除前影响分析） */
  getUsage(id: string) {
    return request.get<any, R<CustomFieldUsageVO>>(`/admin/custom-fields/${id}/usage`)
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
  },

  // ========== 项目级字段管理 ==========

  /** 获取项目设置中的字段列表（含全局字段） */
  listProjectSettingsFields(projectId: string) {
    return request.get<any, R<CustomFieldDefinitionVO[]>>(`/projects/${projectId}/settings/custom-fields`)
  },

  /** 获取可附加到项目的字段列表 */
  listAvailableForProject(projectId: string) {
    return request.get<any, R<CustomFieldDefinitionVO[]>>(`/projects/${projectId}/settings/custom-fields/available`)
  },

  /** 附加字段到项目 */
  attachToProject(projectId: string, fieldId: string) {
    return request.post<any, R<void>>(`/projects/${projectId}/settings/custom-fields/${fieldId}`)
  },

  /** 从项目移除字段 */
  detachFromProject(projectId: string, fieldId: string) {
    return request.delete<any, R<void>>(`/projects/${projectId}/settings/custom-fields/${fieldId}`)
  },

  /** 调整字段在项目中的排序 */
  reorderProjectFields(projectId: string, fieldIds: string[]) {
    return request.put<any, R<void>>(`/projects/${projectId}/settings/custom-fields/reorder`, { fieldIds })
  }
}
