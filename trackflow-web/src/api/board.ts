import request from './request'
import type { R, BoardColumnVO, BoardColumnItem } from './types'

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
  }
}
