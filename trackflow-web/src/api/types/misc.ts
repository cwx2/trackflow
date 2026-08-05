/**
 * 杂项类型定义 — 工单模板、手动排序、API Key 等
 */

// ========== 工单模板 ==========
export interface IssueTemplateVO {
  id: string
  projectId: string
  name: string
  description?: string
  issueType?: string
  priority?: string
  defaultTags?: string
  isSystem: boolean
  sortOrder: number
  createdBy?: string
}

// ========== 手动排序 ==========

export interface ManualOrderVO {
  contextType: string
  contextId: string
  ownerOrder: boolean
  issueIds: string[]
}

export interface GroupSimpleVO {
  id: string
  name: string
}

// ========== API Key / Permanent Token ==========

/** API Key 列表项 */
export interface ApiKeyVO {
  id: string
  userId: string
  name: string
  /** Key 前缀（如 "tf_abc1"），用于识别 */
  prefix: string
  /** 权限范围（逗号分隔或描述文本） */
  permissions: string
  expiresAt?: string
  lastUsedAt?: string
  createdAt: string
}

/** API Key 创建结果（仅返回一次，包含明文 Key） */
export interface ApiKeyCreatedVO {
  id: string
  name: string
  /** 完整的 API Key 明文，仅此一次展示 */
  key: string
  prefix: string
  expiresAt?: string
  createdAt: string
}
