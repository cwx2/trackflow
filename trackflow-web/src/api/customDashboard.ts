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
  layoutVersion: number
  shareCount: number
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
  shareCount: number
  favorited: boolean
  isDefault: boolean
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
  clearReportId?: boolean
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

// ─── Share Types ───────────────────────────────────────

export interface DashboardShareVO {
  id: string
  targetType: 'user' | 'group'
  targetId: string
  targetName: string
  permission: 'view' | 'edit'
  createdAt: string
}

export interface ShareTarget {
  targetType: 'user' | 'group'
  targetId: number
  permission: 'view' | 'edit'
}

export interface ShareDashboardParams {
  targets: ShareTarget[]
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

  // ─── 收藏 & 默认 ──────────────────────────────────────

  /** 切换收藏状态，返回 true=已收藏，false=已取消 */
  toggleFavorite(dashboardId: string) {
    return request.post<any, R<boolean>>(`/dashboards/${dashboardId}/favorite`)
  },

  /** 设为默认仪表盘 */
  setDefault(dashboardId: string) {
    return request.put<any, R<void>>(`/dashboards/${dashboardId}/default`)
  },

  /** 取消默认仪表盘 */
  unsetDefault() {
    return request.delete<any, R<void>>('/dashboards/default')
  },

  /** 获取当前用户的默认仪表盘 ID */
  getDefault() {
    return request.get<any, R<string | null>>('/dashboards/default')
  },

  // ─── Widget ────────────────────────────────────────────

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

  /** 移动 Widget 到另一个仪表盘 */
  moveWidget(dashboardId: string, widgetId: string, targetDashboardId: string) {
    return request.post<any, R<DashboardWidgetVO>>(`/dashboards/${dashboardId}/widgets/${widgetId}/move`, null, {
      params: { targetDashboardId }
    })
  },

  /** 批量更新布局（拖拽后保存） */
  updateLayout(dashboardId: string, items: LayoutItem[], version: number) {
    return request.put<any, R<void>>(`/dashboards/${dashboardId}/layout`, { items, version })
  },

  // ─── Share API ─────────────────────────────────────────

  /** 获取仪表盘共享列表 */
  getShares(dashboardId: string) {
    return request.get<any, R<DashboardShareVO[]>>(`/dashboards/${dashboardId}/shares`)
  },

  /** 设置仪表盘共享（覆盖模式） */
  setShares(dashboardId: string, data: ShareDashboardParams) {
    return request.put<any, R<DashboardShareVO[]>>(`/dashboards/${dashboardId}/shares`, data)
  },

  /** 移除单条共享 */
  removeShare(dashboardId: string, shareId: string) {
    return request.delete<any, R<void>>(`/dashboards/${dashboardId}/shares/${shareId}`)
  }
}
