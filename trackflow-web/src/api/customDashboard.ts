import request from './request'
import type { R } from './types'

// ─── Types ─────────────────────────────────────────────

export interface DashboardWidgetVO {
  id: string
  dashboardId: string
  widgetType: string
  title: string
  config: string
  reportId: string | null
  positionX: number
  positionY: number
  width: number
  height: number
  sortOrder: number
  createdAt: string
}

export interface DashboardDetailVO {
  id: string
  name: string
  description: string
  ownerId: string
  ownerName: string
  shared: boolean
  createdAt: string
  updatedAt: string
  widgets: DashboardWidgetVO[]
}

export interface DashboardListVO {
  id: string
  name: string
  description: string
  ownerId: string
  ownerName: string
  shared: boolean
  widgetCount: number
  createdAt: string
  updatedAt: string
}

export interface CreateDashboardParams {
  name: string
  description?: string
  shared?: boolean
}

export interface UpdateDashboardParams {
  name?: string
  description?: string
  shared?: boolean
}

export interface CreateWidgetParams {
  widgetType: string
  title?: string
  config?: string
  reportId?: string
  positionX?: number
  positionY?: number
  width?: number
  height?: number
}

export interface UpdateWidgetParams {
  title?: string
  config?: string
  reportId?: string
  positionX?: number
  positionY?: number
  width?: number
  height?: number
}

export interface LayoutItem {
  widgetId: string
  positionX: number
  positionY: number
  width: number
  height: number
}

// ─── API ───────────────────────────────────────────────

/**
 * 自定义仪表盘 API
 */
export const customDashboardApi = {
  /** 仪表盘列表 */
  list() {
    return request.get<any, R<DashboardListVO[]>>('/dashboards')
  },

  /** 仪表盘详情（含 Widgets） */
  getDetail(id: string) {
    return request.get<any, R<DashboardDetailVO>>(`/dashboards/${id}`)
  },

  /** 创建仪表盘 */
  create(data: CreateDashboardParams) {
    return request.post<any, R<DashboardDetailVO>>('/dashboards', data)
  },

  /** 更新仪表盘 */
  update(id: string, data: UpdateDashboardParams) {
    return request.put<any, R<DashboardDetailVO>>(`/dashboards/${id}`, data)
  },

  /** 删除仪表盘 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/dashboards/${id}`)
  },

  /** 添加 Widget */
  addWidget(dashboardId: string, data: CreateWidgetParams) {
    return request.post<any, R<DashboardWidgetVO>>(`/dashboards/${dashboardId}/widgets`, data)
  },

  /** 更新 Widget */
  updateWidget(dashboardId: string, widgetId: string, data: UpdateWidgetParams) {
    return request.put<any, R<DashboardWidgetVO>>(`/dashboards/${dashboardId}/widgets/${widgetId}`, data)
  },

  /** 删除 Widget */
  deleteWidget(dashboardId: string, widgetId: string) {
    return request.delete<any, R<void>>(`/dashboards/${dashboardId}/widgets/${widgetId}`)
  },

  /** 批量更新布局（拖拽后保存） */
  updateLayout(dashboardId: string, items: LayoutItem[]) {
    return request.put<any, R<void>>(`/dashboards/${dashboardId}/layout`, { items })
  }
}
