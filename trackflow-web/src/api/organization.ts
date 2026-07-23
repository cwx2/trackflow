import request from './request'
import type { R, PageResult } from './types'

/** 组织 VO 类型 */
export interface OrgVO {
  id: string
  name: string
  code: string
  description?: string
  createdAt: string
}

/**
 * 组织管理模块 API
 */
export const organizationApi = {
  /** 分页查询组织列表 */
  list(params?: { keyword?: string; page?: number; pageSize?: number; sort?: string }) {
    return request.get<any, R<PageResult<OrgVO>>>('/organizations', { params })
  },

  /** 获取组织详情 */
  getById(id: string) {
    return request.get<any, R<OrgVO>>(`/organizations/${id}`)
  },

  /** 创建组织 */
  create(data: { name: string; code: string; description?: string }) {
    return request.post<any, R<OrgVO>>('/organizations', data)
  },

  /** 更新组织 */
  update(id: string, data: { name?: string; description?: string }) {
    return request.put<any, R<OrgVO>>(`/organizations/${id}`, data)
  },

  /** 删除组织 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/organizations/${id}`)
  }
}
