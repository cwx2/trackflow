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
  shareCount: number
  /** 当前用户是否收藏了该报表 */
  favorited: boolean
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

// ─── Share Types ───────────────────────────────────────

export interface ReportShareVO {
  id: string
  targetType: 'user' | 'group'
  targetId: string
  targetName: string
  permission: 'view' | 'edit'
  createdAt: string
}

export interface ReportShareTarget {
  targetType: 'user' | 'group'
  targetId: number
  permission: 'view' | 'edit'
}

export interface ShareReportParams {
  targets: ReportShareTarget[]
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
  /** 数据计算时间（ISO 格式） */
  calculatedAt?: string
  /** 自动刷新间隔（秒），null/0 表示不自动刷新 */
  refreshInterval?: number | null
}

/** 报表可用分组维度选项 */
export interface ReportGroupByOptionVO {
  /** 维度值（如 "status", "cf_2077320550062825474"） */
  value: string
  /** 显示标签（如 "按状态", "平台"） */
  label: string
  /** 维度类别: "builtin" 或 "custom_field" */
  category: 'builtin' | 'custom_field'
  /** 自定义字段格式（仅 category=custom_field 时有值） */
  fieldFormat?: string
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

  /** 获取可用的分组维度列表（内置 + 自定义字段） */
  getGroupByOptions(projectId?: string) {
    const params = projectId ? { projectId } : {}
    return request.get<any, R<ReportGroupByOptionVO[]>>('/reports/group-by-options', { params })
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
  },

  /** 克隆报表 */
  clone(id: string) {
    return request.post<any, R<ReportDefinitionVO>>(`/reports/${id}/clone`)
  },

  /** 切换报表收藏状态 */
  toggleFavorite(id: string) {
    return request.post<any, R<boolean>>(`/reports/${id}/favorite`)
  },

  /** 导出报表为 CSV（返回 Blob） */
  exportCsv(id: string) {
    return request.get<any, Blob>(`/reports/${id}/export`, {
      params: { format: 'csv' },
      responseType: 'blob'
    })
  },

  /** 导出报表为 Excel XLSX（返回 Blob） */
  exportExcel(id: string) {
    return request.get<any, Blob>(`/reports/${id}/export`, {
      params: { format: 'xlsx' },
      responseType: 'blob'
    })
  },

  // ─── Share API ─────────────────────────────────────────

  /** 获取报表共享列表 */
  getShares(id: string) {
    return request.get<any, R<ReportShareVO[]>>(`/reports/${id}/shares`)
  },

  /** 设置报表共享（覆盖模式） */
  setShares(id: string, data: ShareReportParams) {
    return request.put<any, R<ReportShareVO[]>>(`/reports/${id}/shares`, data)
  },

  /** 移除单条共享 */
  removeShare(id: string, shareId: string) {
    return request.delete<any, R<void>>(`/reports/${id}/shares/${shareId}`)
  }
}
