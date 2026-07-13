import request from './request'
import type { R, BoardColumnVO, BoardColumnItem } from './types'

/**
 * 看板模块 API
 */
export const boardApi = {
  /** 获取项目看板列配置 */
  getColumns(projectId: string) {
    return request.get<any, R<BoardColumnVO[]>>('/boards/columns', {
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
