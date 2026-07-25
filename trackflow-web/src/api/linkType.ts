import request from './request'
import type { R, IssueLinkTypeVO } from './types'

/**
 * 关联类型管理 API（管理员）
 * 对应后端：/api/v1/admin/link-types
 */
export const linkTypeApi = {
  /** 获取所有关联类型 */
  list() {
    return request.get<any, R<IssueLinkTypeVO[]>>('/admin/link-types')
  },

  /** 获取指定关联类型的使用数量（删除前预检） */
  getUsageCount(id: string) {
    return request.get<any, R<number>>(`/admin/link-types/${id}/usage-count`)
  },

  /** 创建关联类型 */
  create(data: { name: string; outwardName: string; inwardName: string; direction: string }) {
    return request.post<any, R<IssueLinkTypeVO>>('/admin/link-types', data)
  },

  /** 更新关联类型 */
  update(id: string, data: { outwardName: string; inwardName: string; direction: string }) {
    return request.put<any, R<IssueLinkTypeVO>>(`/admin/link-types/${id}`, data)
  },

  /** 删除关联类型（级联删除所有使用该类型的关联记录） */
  delete(id: string) {
    return request.delete<any, R<void>>(`/admin/link-types/${id}`)
  }
}
