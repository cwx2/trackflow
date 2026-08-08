import type { IssueVO, BoardCardVO } from '@/api/types'

/** 看板中使用的工单类型 — 可以是精简卡片 VO（聚合 API）或完整 IssueVO（Legacy fallback） */
export type BoardIssue = IssueVO | BoardCardVO

export type SwimlaneGroupBy = 'none' | 'assignee' | 'priority' | 'type' | 'sprint' | 'tag' | 'parent' | 'dueDate'
export type CardSize = 'S' | 'M' | 'L' | 'XL'

export interface EffectiveColumn {
  id: string
  name: string
  color: string
  category?: string
  statusIds: string[]
  isMerged: boolean
  sortOrder?: number
}

export interface SwimlaneRow {
  key: string
  label: string
  issues: BoardIssue[]
}
