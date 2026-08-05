/**
 * 公共类型定义 — 后端统一响应结构、分页、认证用户
 */

/**
 * 后端统一响应结构 R<T>
 */
export interface R<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
  traceId?: string
  /** 字段级警告信息（部分字段因权限不足被跳过时出现） */
  warnings?: string[]
}

/**
 * /api/v1/auth/me 返回的当前用户信息 VO
 * 对应后端 UserInfoVO，字段来自 Keycloak JWT + 数据库
 */
export interface UserInfoVO {
  /** 数据库用户 ID（用于资源级权限判断） */
  userId?: string
  /** Keycloak subject ID */
  keycloakId: string
  /** 登录用户名 */
  username: string
  /** 显示名称 */
  displayName: string
  /** 邮箱 */
  email: string
}

/**
 * 从 JWT Token 解析出的当前认证用户信息
 * 存储在 auth store 的 user 字段中，持久化到 localStorage
 */
export interface AuthUser {
  /** Keycloak subject ID */
  id: string
  /** 数据库用户 ID（用于资源级权限判断，与 Issue 的 reporterId/assigneeId 对比） */
  userId?: string
  /** 登录用户名 */
  username: string
  /** 显示名称（CJK 姓+名，西方名+姓） */
  displayName: string
  /** 邮箱 */
  email: string
  /** Keycloak realm_access.roles（如 tf_admin, tf_user） */
  roles: string[]
}

/**
 * 分页结果
 */
export interface PageResult<T = any> {
  list: T[]
  pagination: {
    page: number
    pageSize: number
    total: number
    totalPages: number
  }
}

/**
 * 分页查询参数
 */
export interface PageQuery {
  page?: number
  pageSize?: number
  sort?: string
}
