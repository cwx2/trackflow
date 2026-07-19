import request from './request'
import type { R } from './types'

export interface ReportDefinitionVO {
  id: string
  name: string
  projectId: string
  type: string
  config: string
  shared: boolean
  isSystem: boolean
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface CreateReportParams {
  name: string
  projectId: string
  type: string
  config?: string
  shared?: boolean
}

export interface UpdateReportParams {
  name?: string
  type?: string
  config?: string
  shared?: boolean
}

export interface ReportDataVO {
  title: string
  /** 图表类型（bar_horizontal/bar_vertical/pie/line/number_card） */
  chartType: string
  type: string
  groupBy: string
  /** 第二分组依据（双维度交叉时使用） */
  secondGroupBy?: string
  labels: string[]
  data: number[]
  /** 第二维度标签列表（双维度模式） */
  secondLabels?: string[]
  /** 矩阵数据（双维度模式） matrix[i][j] = 主维度第i项 × 第二维度第j项 */
  matrix?: number[][]
  total: number
  /** 应用的筛选条件摘要 */
  appliedFilters?: Record<string, any>
}

/**
 * 报表模块 API
 */
export const reportApi = {
  /** 报表列表（可按项目过滤） */
  list(projectId?: string) {
    const params = projectId ? { projectId } : {}
    return request.get<any, R<ReportDefinitionVO[]>>('/reports', { params })
  },

  /** 创建报表 */
  create(data: CreateReportParams) {
    return request.post<any, R<ReportDefinitionVO>>('/reports', data)
  },

  /** 更新报表 */
  update(id: string, data: UpdateReportParams) {
    return request.put<any, R<ReportDefinitionVO>>(`/reports/${id}`, data)
  },

  /** 删除报表 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/reports/${id}`)
  },

  /** 执行报表（获取数据） */
  execute(id: string) {
    return request.get<any, R<ReportDataVO>>(`/reports/${id}/data`)
  }
}
