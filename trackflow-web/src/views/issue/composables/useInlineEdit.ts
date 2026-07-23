import { ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import type { IssueVO } from '@/api/types'
import { extractVersion, showActionFeedback } from '@/utils/transition'

export interface CellEditState {
  issueId: string
  field: string
  loading: boolean
  previousValue: any
}

const FIELD_LABELS: Record<string, string> = {
  statusId: '状态',
  assigneeId: '负责人',
  assigneeName: '负责人',
  sprintId: 'Sprint',
  priority: '优先级'
}

const TIMEOUT_MS = 10_000

export function useInlineEdit(issues: { value: IssueVO[] }) {
  const editingCell = ref<CellEditState | null>(null)

  /**
   * 判断某个单元格是否正在编辑中
   */
  function isCellEditing(issueId: string, field: string): boolean {
    return editingCell.value?.issueId === issueId && editingCell.value?.field === field
  }

  /**
   * 执行内联编辑（带乐观更新和回滚）
   *
   * @param issueId Issue ID
   * @param field 字段名（用于标记和回滚）
   * @param newValue 新值（用于乐观更新）
   * @param apiCall API 调用函数
   * @param patchFn 可选的自定义 patch 函数（用于更新多个字段）
   */
  async function executeEdit(
    issueId: string,
    field: string,
    newValue: any,
    apiCall: (signal: AbortSignal) => Promise<any>,
    patchFn?: (issue: IssueVO) => Partial<IssueVO>
  ) {
    // 防止同一单元格重复编辑
    if (isCellEditing(issueId, field)) return

    const issue = issues.value.find(i => i.id === issueId)
    if (!issue) return

    const previousValue = (issue as any)[field]

    // 乐观更新
    if (patchFn) {
      const patch = patchFn(issue)
      Object.assign(issue, patch)
    } else {
      ;(issue as any)[field] = newValue
    }

    // 标记编辑状态
    editingCell.value = { issueId, field, loading: true, previousValue }

    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), TIMEOUT_MS)

    try {
      const res = await apiCall(controller.signal)
      // 成功：同步版本号
      if (res?.data != null) {
        const version = extractVersion(res.data)
        if (version != null) {
          issue.version = version
        } else if (typeof res.data === 'object' && 'version' in res.data && typeof res.data.version === 'number') {
          // update 返回完整 IssueDetailVO，包含 version 字段
          issue.version = res.data.version
        } else {
          // 其他情况本地递增
          issue.version = (issue.version || 0) + 1
        }
        // 显示自动分配反馈（仅对 TransitStatusResultVO 响应有效）
        if (typeof res.data === 'object' && 'actionResult' in res.data) {
          showActionFeedback(res.data)
        }
      } else {
        issue.version = (issue.version || 0) + 1
      }
      // 处理字段级警告（部分字段权限不足被跳过）
      if (res?.warnings?.length) {
        // 有警告意味着当前字段被跳过——回滚乐观更新
        ;(issue as any)[field] = previousValue
        res.warnings.forEach((w: string) => Message.warning({ content: w, duration: 5000 }))
      }
      editingCell.value = null
    } catch (e: any) {
      // 回滚
      if (patchFn) {
        // 回滚需要恢复原始值
        ;(issue as any)[field] = previousValue
      } else {
        ;(issue as any)[field] = previousValue
      }
      editingCell.value = null

      const label = FIELD_LABELS[field] || field
      if (e.name === 'AbortError' || controller.signal.aborted) {
        Message.error({ content: `${label} 更新超时，请重试`, duration: 3000 })
      } else if (e.response?.status === 403) {
        Message.error({ content: `无权限修改${label}`, duration: 5000 })
      } else {
        const msg = e.response?.data?.message || `${label} 更新失败`
        Message.error({ content: msg, duration: 3000 })
      }
    } finally {
      clearTimeout(timeoutId)
    }
  }

  return {
    editingCell,
    isCellEditing,
    executeEdit
  }
}
