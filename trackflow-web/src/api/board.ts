import request from './request'
import type { R, BoardColumnVO, BoardColumnItem, BoardCardConfigVO, BoardChartConfigVO, BoardSwimlaneConfigVO, BoardColumnMergeGroupVO, BoardGeneralConfigVO, BoardDataVO, BoardListItemVO } from './types'

/**
 * 看板模块 API
 */
export const boardApi = {
  /** 获取看板聚合数据（按列分组，单次请求替代循环分页） */
  getBoardData(params: {
    projectId: string
    sprintId?: string
    assigneeId?: string
    keyword?: string
    excludeDoneBefore?: string
    columnLimit?: number
    collapsedStatusIds?: string
  }) {
    return request.get<any, R<BoardDataVO>>('/boards/data', { params })
  },

  /** 获取项目看板列配置（纯读取，不触发写操作） */
  getColumns(projectId: string) {
    return request.get<any, R<BoardColumnVO[]>>('/boards/columns', {
      params: { projectId }
    })
  },

  /**
   * 显式初始化项目看板列配置（幂等）。
   * 仅在用户首次打开看板设置面板时调用。
   * 如果项目已有配置则直接返回现有配置。
   */
  initializeColumns(projectId: string) {
    return request.post<any, R<BoardColumnVO[]>>('/boards/columns/init', null, {
      params: { projectId }
    })
  },

  /** 保存项目看板列配置 */
  saveColumns(projectId: string, columns: BoardColumnItem[]) {
    return request.put<any, R<void>>('/boards/columns', {
      columns
    }, {
      params: { projectId }
    })
  },

  /** 获取项目看板卡片配置（显示字段 + 颜色方案） */
  getCardConfig(projectId: string) {
    return request.get<any, R<BoardCardConfigVO>>('/boards/card-config', {
      params: { projectId }
    })
  },

  /** 保存项目看板卡片配置 */
  saveCardConfig(projectId: string, data: { visibleFields: string[]; colorScheme: string }) {
    return request.put<any, R<void>>('/boards/card-config', data, {
      params: { projectId }
    })
  },

  /** 获取项目看板泳道配置 */
  getSwimlaneConfig(projectId: string) {
    return request.get<any, R<BoardSwimlaneConfigVO>>('/boards/swimlane-config', {
      params: { projectId }
    })
  },

  /** 保存项目看板泳道配置 */
  saveSwimlaneConfig(projectId: string, data: { groupByField: string; selectedValues?: string[] | null; showUncategorized?: boolean; uncategorizedPosition?: 'top' | 'bottom' }) {
    return request.put<any, R<void>>('/boards/swimlane-config', data, {
      params: { projectId }
    })
  },

  /** 获取项目看板列合并配置 */
  getColumnMerges(projectId: string) {
    return request.get<any, R<BoardColumnMergeGroupVO[]>>('/boards/column-merges', {
      params: { projectId }
    })
  },

  /** 保存项目看板列合并配置（全量替换） */
  saveColumnMerges(projectId: string, data: { mergeGroups: Array<{ mergeGroupId: string; mergeTitle: string; statusIds: number[] }> }) {
    return request.put<any, R<void>>('/boards/column-merges', data, {
      params: { projectId }
    })
  },

  /** 获取项目看板图表配置（图表类型 + 计算方式） */
  getChartConfig(projectId: string) {
    return request.get<any, R<BoardChartConfigVO>>('/boards/chart-config', {
      params: { projectId }
    })
  },

  /** 保存项目看板图表配置 */
  saveChartConfig(projectId: string, data: {
    chartType: string
    burndownCalculation: string
    issueFilterMode: string
    issueFilterQuery?: string | null
    estimationFieldId?: number | null
    originalEstimationFieldId?: number | null
  }) {
    return request.put<any, R<void>>('/boards/chart-config', data, {
      params: { projectId }
    })
  },

  /** 获取项目看板基本设置（名称 + 访问权限） */
  getGeneralConfig(projectId: string) {
    return request.get<any, R<BoardGeneralConfigVO>>('/boards/general-config', {
      params: { projectId }
    })
  },

  /** 保存项目看板基本设置 */
  saveGeneralConfig(projectId: string, data: { name: string; canViewRoles: string[]; canEditRoles: string[]; filterMode: string; filterQuery?: string | null; doneRetentionDays: number | null; configVersion?: number }) {
    return request.put<any, R<void>>('/boards/general-config', data, {
      params: { projectId }
    })
  },

  /**
   * 批量保存所有看板设置（推荐）。
   * 将列设置、卡片、泳道、列合并、基本设置合并为一次请求，
   * 仅做一次版本检查，确保并发安全。
   */
  saveBoardSettings(projectId: string, data: {
    configVersion: number | null
    columns: { columns: Array<{ statusId: number; visible: boolean; sortOrder: number; collapsed?: boolean; wipMin?: number | null; wipMax?: number | null }> }
    cardConfig: { visibleFields: string[]; colorScheme: string }
    swimlaneConfig: { groupByField: string; selectedValues?: string[] | null; showUncategorized?: boolean; uncategorizedPosition?: 'top' | 'bottom' }
    columnMerges: { mergeGroups: Array<{ mergeGroupId: string; mergeTitle: string; statusIds: number[] }> }
    generalConfig: { name: string; canViewRoles: string[]; canEditRoles: string[]; filterMode: string; filterQuery?: string | null; doneRetentionDays: number | null }
    chartConfig?: { chartType: string; burndownCalculation: string; issueFilterMode: string; issueFilterQuery?: string | null; estimationFieldId?: number | null; originalEstimationFieldId?: number | null }
  }) {
    return request.put<any, R<void>>('/boards/settings', data, {
      params: { projectId }
    })
  },

  // ========== 看板列表 & 收藏（Board Selector） ==========

  /** 获取看板列表（Board Selector 用） */
  listBoards() {
    return request.get<any, R<BoardListItemVO[]>>('/boards/list')
  },

  /** 收藏看板 */
  addFavorite(projectId: string) {
    return request.post<any, R<void>>('/boards/favorite', null, {
      params: { projectId }
    })
  },

  /** 取消收藏看板 */
  removeFavorite(projectId: string) {
    return request.delete<any, R<void>>('/boards/favorite', {
      params: { projectId }
    })
  }
}
