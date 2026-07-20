import request from './request'
import type { R, PageResult } from './types'

/**
 * 审计日志 VO
 */
export interface AuditLogVO {
  id: string
  operatorId: string
  operatorName: string
  action: string
  targetType: string
  targetId: string
  targetName: string
  details: string | null
  ipAddress: string | null
  createdAt: string
}

/**
 * 审计日志查询参数
 */
export interface AuditLogQuery {
  action?: string
  targetType?: string
  operatorId?: string
  targetId?: string
  startDate?: string
  endDate?: string
  page?: number
  pageSize?: number
}

/**
 * 审计日志模块 API
 */
export const auditLogApi = {
  /** 分页查询审计日志（强制时间范围不超过 365 天） */
  list(params?: AuditLogQuery) {
    return request.get<any, R<PageResult<AuditLogVO>>>('/admin/audit-logs', { params })
  },

  /**
   * 导出审计日志（CSV 格式）
   * 返回 Blob 对象，前端可触发下载
   *
   * @param startDate 开始日期 YYYY-MM-DD
   * @param endDate 结束日期 YYYY-MM-DD
   */
  export(startDate: string, endDate: string) {
    return request.get('/admin/audit-logs/export', {
      params: { startDate, endDate },
      responseType: 'blob'
    })
  }
}
