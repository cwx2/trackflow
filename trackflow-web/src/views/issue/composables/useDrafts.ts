import { ref, computed } from 'vue'

/**
 * Issue 草稿管理 — localStorage 存储，用户级别隔离
 *
 * 功能：
 * - 创建/更新草稿（取消创建时自动保存）
 * - 列出所有草稿
 * - 删除单个草稿
 * - 删除所有草稿
 * - 恢复草稿到创建表单
 *
 * 参考 YouTrack：
 * - 侧边栏 Drafts 区域列出未提交的工单草稿
 * - 点击草稿恢复编辑
 * - 创建成功后自动移除草稿
 * - "New issue" 弹窗快速创建
 */

export interface IssueDraft {
  /** 草稿唯一 ID */
  id: string
  /** 标题 */
  title: string
  /** 描述（Markdown/HTML） */
  description: string
  /** 项目 ID */
  projectId: string
  /** 工单类型 */
  issueType: string
  /** 优先级 */
  priority: string
  /** Sprint ID */
  sprintId: string
  /** 负责人 ID */
  assigneeId: string
  /** 截止日期 */
  dueDate: string
  /** 预估工时 */
  estimatedHours: number | null
  /** 自定义字段值 */
  customFieldValues: Record<string, string>
  /** 创建时间戳 */
  createdAt: number
  /** 最后更新时间戳 */
  updatedAt: number
}

/** 创建/更新草稿时的表单数据 */
export interface DraftFormData {
  title?: string
  description?: string
  projectId?: string
  issueType?: string
  priority?: string
  sprintId?: string
  assigneeId?: string
  dueDate?: string
  estimatedHours?: number | null
  customFieldValues?: Record<string, string>
}

const STORAGE_KEY = 'tf_issue_drafts'
const MAX_DRAFTS = 50

function getUserStorageKey(): string {
  const userStr = localStorage.getItem('tf_user')
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      return `${STORAGE_KEY}_${user.id || user.username || 'default'}`
    } catch { /* ignore */ }
  }
  return `${STORAGE_KEY}_default`
}

function loadDrafts(): IssueDraft[] {
  try {
    const raw = localStorage.getItem(getUserStorageKey())
    if (raw) return JSON.parse(raw)
  } catch { /* ignore */ }
  return []
}

function saveDrafts(data: IssueDraft[]) {
  localStorage.setItem(getUserStorageKey(), JSON.stringify(data))
}

function generateId(): string {
  return `draft_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

// 全局响应式状态（跨组件共享）
const drafts = ref<IssueDraft[]>(loadDrafts())

export function useDrafts() {
  /** 草稿列表（按更新时间倒序） */
  const draftList = computed(() => {
    return [...drafts.value].sort((a, b) => b.updatedAt - a.updatedAt)
  })

  /** 草稿数量 */
  const draftCount = computed(() => drafts.value.length)

  /** 是否有草稿 */
  const hasDrafts = computed(() => drafts.value.length > 0)

  /**
   * 判断表单数据是否有实质内容（标题或描述非空）
   */
  function hasContent(form: DraftFormData): boolean {
    return !!(form.title?.trim() || form.description?.trim())
  }

  /**
   * 保存草稿（新建或更新）
   * @param form 表单数据
   * @param existingDraftId 如果是从草稿恢复编辑，传入草稿 ID
   * @returns 保存的草稿 ID，如果没有内容则返回 null
   */
  function saveDraft(form: DraftFormData, existingDraftId?: string): string | null {
    if (!hasContent(form)) return null

    const now = Date.now()

    if (existingDraftId) {
      // 更新已有草稿
      const index = drafts.value.findIndex(d => d.id === existingDraftId)
      if (index >= 0) {
        drafts.value[index] = {
          ...drafts.value[index],
          title: form.title || '',
          description: form.description || '',
          projectId: form.projectId || '',
          issueType: form.issueType || 'Task',
          priority: form.priority || 'Normal',
          sprintId: form.sprintId || '',
          assigneeId: form.assigneeId || '',
          dueDate: form.dueDate || '',
          estimatedHours: form.estimatedHours ?? null,
          customFieldValues: form.customFieldValues || {},
          updatedAt: now,
        }
        persistDrafts()
        return existingDraftId
      }
    }

    // 新建草稿
    const draft: IssueDraft = {
      id: generateId(),
      title: form.title || '',
      description: form.description || '',
      projectId: form.projectId || '',
      issueType: form.issueType || 'Task',
      priority: form.priority || 'Normal',
      sprintId: form.sprintId || '',
      assigneeId: form.assigneeId || '',
      dueDate: form.dueDate || '',
      estimatedHours: form.estimatedHours ?? null,
      customFieldValues: form.customFieldValues || {},
      createdAt: now,
      updatedAt: now,
    }

    drafts.value.unshift(draft)

    // 超过最大数量时移除最旧的
    if (drafts.value.length > MAX_DRAFTS) {
      drafts.value = drafts.value.slice(0, MAX_DRAFTS)
    }

    persistDrafts()
    return draft.id
  }

  /**
   * 删除单个草稿
   */
  function deleteDraft(draftId: string) {
    drafts.value = drafts.value.filter(d => d.id !== draftId)
    persistDrafts()
  }

  /**
   * 删除所有草稿
   */
  function deleteAllDrafts() {
    drafts.value = []
    persistDrafts()
  }

  /**
   * 获取单个草稿
   */
  function getDraft(draftId: string): IssueDraft | undefined {
    return drafts.value.find(d => d.id === draftId)
  }

  /**
   * 刷新（从 localStorage 重新读取）
   */
  function refresh() {
    drafts.value = loadDrafts()
  }

  /** 持久化到 localStorage */
  function persistDrafts() {
    saveDrafts(drafts.value)
  }

  return {
    draftList,
    draftCount,
    hasDrafts,
    hasContent,
    saveDraft,
    deleteDraft,
    deleteAllDrafts,
    getDraft,
    refresh,
  }
}
