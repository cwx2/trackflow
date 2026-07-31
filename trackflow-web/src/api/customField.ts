import request from './request'
import type { R, PageResult, CustomFieldDefinitionVO, CustomFieldUsageVO, AvailableColumnVO, CustomFieldOptionVO, ProjectFieldsVO, OptionUsageItemVO, OptionSetStatusVO } from './types'

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
    isHiddenInList?: boolean
    defaultValue?: string
    minLength?: number
    maxLength?: number
    regexp?: string
    options?: Array<{ value: string; isDefault?: boolean; color?: string }>
    copyOptionsFromFieldId?: string
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
    isHiddenInList?: boolean
    defaultValue?: string
    minLength?: number
    maxLength?: number
    regexp?: string
    options?: Array<{ id?: string; value: string; isDefault?: boolean; color?: string }>
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

  /** 获取枚举字段逐选项使用统计 */
  getOptionUsage(id: string) {
    return request.get<any, R<OptionUsageItemVO[]>>(`/admin/custom-fields/${id}/option-usage`)
  },

  /** 获取单个字段详情 */
  getDetail(id: string) {
    return request.get<any, R<CustomFieldDefinitionVO>>(`/admin/custom-fields/${id}`)
  },

  /** 获取 Fields in Projects 矩阵数据 */
  fieldsInProjects() {
    return request.get<any, R<ProjectFieldsVO[]>>('/admin/custom-fields/fields-in-projects')
  },

  /** 获取所有枚举类型字段列表（用于"从已有字段复制选项"下拉） */
  listEnumFields() {
    return request.get<any, R<CustomFieldDefinitionVO[]>>('/admin/custom-fields/enum-fields')
  },

  /** 排序自定义字段 */
  reorder(ids: string[]) {
    return request.put<any, R<void>>('/admin/custom-fields/reorder', { ids })
  },

  /** 批量更新字段属性（isForAll / isHiddenInList） */
  batchUpdate(data: { ids: string[]; field: string; value: boolean }) {
    return request.put<any, R<void>>('/admin/custom-fields/batch-update', data)
  },

  /** 批量删除自定义字段 */
  batchDelete(ids: string[]) {
    return request.delete<any, R<void>>('/admin/custom-fields/batch-delete', {
      params: { ids: ids.join(',') }
    })
  },

  /** 启用/禁用字段的 Auto-attach 功能（自动附加到新项目） */
  setAutoAttach(id: string, enabled: boolean) {
    return request.put<any, R<void>>(`/admin/custom-fields/${id}/auto-attach`, null, {
      params: { enabled }
    })
  },

  /** 排序枚举字段的选项值 */
  reorderOptions(fieldId: string, optionIds: string[]) {
    return request.put<any, R<void>>(`/admin/custom-fields/${fieldId}/options/reorder`, { optionIds })
  },

  /** 归档/取消归档枚举字段的选项值 */
  archiveOption(fieldId: string, optionId: string, archived: boolean) {
    return request.put<any, R<void>>(`/admin/custom-fields/${fieldId}/options/${optionId}/archive`, null, {
      params: { archived }
    })
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
  },

  // ========== 条件显示 ==========

  /** 设置字段条件显示规则（项目级） */
  setFieldCondition(projectId: string, fieldId: string, data: {
    conditionFieldId: string | null
    conditionValues: string[] | null
  }) {
    return request.put<any, R<void>>(`/projects/${projectId}/settings/custom-fields/${fieldId}/condition`, data)
  },

  /** 清除项目中字段被条件隐藏的 issue 值 */
  clearHiddenValues(projectId: string, fieldId: string) {
    return request.post<any, R<number>>(`/projects/${projectId}/settings/custom-fields/${fieldId}/clear-hidden-values`)
  },

  // ========== 字段可见性/可编辑性 ==========

  /** 设置字段的可见性和编辑权限（项目级，基于角色） */
  setFieldVisibility(projectId: string, fieldId: string, data: {
    visibleToRoles: number[] | null
    updatableByRoles: number[] | null
  }) {
    return request.put<any, R<void>>(`/projects/${projectId}/settings/custom-fields/${fieldId}/visibility`, data)
  },

  /** 设置字段的项目级覆盖（必填性 + 默认值 + 是否允许为空）
   * @param canBeEmpty 是否允许为空。false + 无默认值 = YouTrack "No value (required)" 模式
   */
  setFieldProjectOverride(projectId: string, fieldId: string, data: {
    isRequired?: boolean | null
    defaultValue?: string | null
    canBeEmpty?: boolean | null
  }) {
    return request.put<any, R<void>>(`/projects/${projectId}/settings/custom-fields/${fieldId}/override`, data)
  },

  // ========== 值过滤规则（Filter values based on）==========

  /** 设置字段的值过滤规则（项目级） */
  setFieldFilterRules(projectId: string, fieldId: string, data: {
    filterFieldId: string | null
    rules: Array<{ whenValue: string; showOnly: string[] }> | null
  }) {
    return request.put<any, R<void>>(`/projects/${projectId}/settings/custom-fields/${fieldId}/filter-rules`, data)
  },

  // ========== 内联添加选项值 ==========

  /** 内联添加枚举字段选项值（工单详情页/创建表单快捷入口） */
  addOption(projectId: string, fieldId: string, data: { value: string; color?: string }) {
    return request.post<any, R<CustomFieldOptionVO>>(`/projects/${projectId}/custom-fields/${fieldId}/options`, data)
  },

  // ========== 项目级独立选项集管理（Make Independent Copy）==========

  /** 获取字段在项目中的选项集状态（共享/独立） */
  getOptionSetStatus(projectId: string, fieldId: string) {
    return request.get<any, R<OptionSetStatusVO>>(`/projects/${projectId}/settings/custom-fields/${fieldId}/option-set-status`)
  },

  /** 创建项目级独立选项副本（Make Independent Copy） */
  makeIndependentCopy(projectId: string, fieldId: string, emptyOptions = false) {
    return request.post<any, R<CustomFieldOptionVO[]>>(
      `/projects/${projectId}/settings/custom-fields/${fieldId}/make-independent`,
      { emptyOptions }
    )
  },

  /** 恢复为全局共享选项集 */
  revertToShared(projectId: string, fieldId: string, confirm = false) {
    return request.post<any, R<void>>(
      `/projects/${projectId}/settings/custom-fields/${fieldId}/revert-to-shared`,
      null,
      { params: { confirm } }
    )
  },

  /** 获取项目中字段的有效选项列表（支持独立/共享回退） */
  getProjectFieldOptions(projectId: string, fieldId: string) {
    return request.get<any, R<CustomFieldOptionVO[]>>(`/projects/${projectId}/custom-fields/${fieldId}/options`)
  },

  /** 更新项目独立选项集 */
  updateProjectOptions(projectId: string, fieldId: string, options: Array<{
    id?: string
    value: string
    isDefault?: boolean
    color?: string
    description?: string
  }>) {
    return request.put<any, R<CustomFieldOptionVO[]>>(
      `/projects/${projectId}/settings/custom-fields/${fieldId}/options`,
      { options }
    )
  }
}
