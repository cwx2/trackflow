import request from './request'
import type { R, BoardColumnVO, BoardColumnItem, BoardCardConfigVO, BoardSwimlaneConfigVO, BoardColumnMergeGroupVO, BoardGeneralConfigVO } from './types'

/**
 * 看板模块 API
 */
export const boardApi = {
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
  saveSwimlaneConfig(projectId: string, data: { groupByField: string }) {
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

  /** 获取项目看板基本设置（名称 + 访问权限） */
  getGeneralConfig(projectId: string) {
    return request.get<any, R<BoardGeneralConfigVO>>('/boards/general-config', {
      params: { projectId }
    })
  },

  /** 保存项目看板基本设置 */
  saveGeneralConfig(projectId: string, data: { name: string; canViewRoles: string[]; canEditRoles: string[]; filterMode: string; doneRetentionDays: number | null }) {
    return request.put<any, R<void>>('/boards/general-config', data, {
      params: { projectId }
    })
  }
}
