/**
 * AdminDataTable 配置驱动列定义
 *
 * 用法示例：
 * ```ts
 * const columns: ColumnDef[] = [
 *   { type: 'index', title: 'ID', width: 56 },
 *   { type: 'user', title: '用户', key: 'displayName', subKey: 'username' },
 *   { type: 'code', title: '编码', key: 'code', width: 130 },
 *   { type: 'status', title: '状态', key: 'status', width: 90 },
 *   { type: 'date', title: '创建时间', key: 'createdAt', width: 160 },
 *   {
 *     type: 'actions',
 *     width: 160,
 *     actions: (record) => [
 *       { label: '编辑', onClick: (r) => edit(r) },
 *       { label: '删除', danger: true, onClick: (r) => del(r) },
 *     ]
 *   },
 * ]
 * ```
 */

// ===== 操作按钮定义 =====

export interface ActionItem {
  /** 按钮文字 */
  label: string
  /** danger 样式（红色） */
  danger?: boolean
  /** 是否禁用 */
  disabled?: boolean | ((record: any) => boolean)
  /** 是否隐藏（不渲染） */
  hidden?: boolean | ((record: any) => boolean)
  /** 点击回调 */
  onClick: (record: any) => void
}

// ===== 列类型 =====

/** 序号列（1, 2, 3...） */
export interface IndexColumnDef {
  type: 'index'
  title?: string
  width?: number
  align?: 'left' | 'center' | 'right'
}

/** 普通文本列 */
export interface TextColumnDef {
  type: 'text'
  title: string
  key: string
  width?: number
  ellipsis?: boolean
  align?: 'left' | 'center' | 'right'
  /** 自定义格式化，返回字符串 */
  format?: (value: any, record: any) => string
  /** 空值占位符，默认 '—' */
  empty?: string
}

/** 代码标签列（`<code>` 样式）*/
export interface CodeColumnDef {
  type: 'code'
  title: string
  key: string
  width?: number
  align?: 'left' | 'center' | 'right'
}

/** 类型/分类 badge 列（彩色圆角标签）*/
export interface BadgeColumnDef {
  type: 'badge'
  title: string
  key: string
  width?: number
  align?: 'left' | 'center' | 'right'
  /** 值 → 显示文字的映射，不传则直接显示原值 */
  labelMap?: Record<string, string>
  /** 值 → 颜色 class 的映射（如 { global: 'blue', project: 'green' }） */
  colorMap?: Record<string, string>
}

/** 状态列（● 启用 / ● 禁用，用 a-tag） */
export interface StatusColumnDef {
  type: 'status'
  title: string
  key: string
  width?: number
  align?: 'left' | 'center' | 'right'
  /**
   * 决定显示为"启用"还是"禁用"的值
   * - 字符串：record[key] === activeValue 时显示启用
   * - 函数：返回 true 表示启用
   * 默认：record[key] 为 true / 'active' / 'enabled' 时启用
   */
  activeValue?: string | boolean | ((record: any) => boolean)
  /** 启用时的文字，默认 '启用' */
  activeLabel?: string
  /** 禁用时的文字，默认 '禁用' */
  inactiveLabel?: string
}

/** 数字/计数列（带单位） */
export interface CountColumnDef {
  type: 'count'
  title: string
  key: string
  width?: number
  align?: 'left' | 'center' | 'right'
  unit?: string
  /** 点击回调（用于"5 人"点击弹窗等场景） */
  onClick?: (record: any) => void
}

/** 日期时间列（自动格式化） */
export interface DateColumnDef {
  type: 'date'
  title: string
  key: string
  width?: number
  align?: 'left' | 'center' | 'right'
  /** 'date' = YYYY-MM-DD，'datetime' = YYYY-MM-DD HH:mm，默认 'datetime' */
  format?: 'date' | 'datetime' | 'relative'
}

/** 用户列（头像 + 显示名 + 副标题，需引入 UserAvatar） */
export interface UserColumnDef {
  type: 'user'
  title: string
  /** 主标签字段（如 displayName） */
  key: string
  width?: number
  /** 副标题字段（如 username → @xxx）*/
  subKey?: string
  /** 头像尺寸，默认 28 */
  avatarSize?: number
  /** 点击用户名的链接，传入函数 */
  href?: (record: any) => string
}

/** 布尔值列（是/否 tag） */
export interface BooleanColumnDef {
  type: 'boolean'
  title: string
  key: string
  width?: number
  align?: 'left' | 'center' | 'right'
  trueLabel?: string
  falseLabel?: string
  trueColor?: string
  falseColor?: string
}

/** Switch 开关列（a-switch，点击直接触发） */
export interface SwitchColumnDef {
  type: 'switch'
  title: string
  key: string
  width?: number
  align?: 'left' | 'center' | 'right'
  onChange: (record: any, value: boolean) => void
  /** 某些行禁止操作 */
  disabled?: (record: any) => boolean
}

/** 操作按钮列（最后列，自动加左竖线） */
export interface ActionsColumnDef {
  type: 'actions'
  title?: string
  width?: number
  align?: 'left' | 'center' | 'right'
  /** 返回该行的操作按钮列表 */
  actions: (record: any) => ActionItem[]
}

/** 自定义渲染列（使用 h() 或 VNode） */
export interface RenderColumnDef {
  type: 'render'
  title: string
  key?: string
  width?: number
  ellipsis?: boolean
  align?: 'left' | 'center' | 'right'
  /** 自定义渲染函数，返回 VNode 或字符串 */
  render: (record: any, index: number) => any
}

// ===== 联合类型 =====

export type ColumnDef =
  | IndexColumnDef
  | TextColumnDef
  | CodeColumnDef
  | BadgeColumnDef
  | StatusColumnDef
  | CountColumnDef
  | DateColumnDef
  | UserColumnDef
  | BooleanColumnDef
  | SwitchColumnDef
  | ActionsColumnDef
  | RenderColumnDef
