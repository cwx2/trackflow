import request from './request'
import type { R } from './types'

export interface ReportDefinitionVO {
  id: string
  name: string
  projectId: string
  type: string
  config: string
  shared: boolean
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
  type: string
  groupBy: string
  labels: string[]
  data: number[]
  total: number
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
