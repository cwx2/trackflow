import { ref } from 'vue'
import { Message, Notification } from '@arco-design/web-vue'
import { issueApi } from '@/api'
import type { IssueVO } from '@/api/types'

export interface BatchResult {
  total: number
  succeeded: number
  failed: number
  failures: Array<{ issueId: string; issueKey: string; reason: string }>
}

const MAX_BATCH_SIZE = 50

export function useBatchOps() {
  const executing = ref(false)

  /**
   * 批量状态转换
   */
  async function batchTransitStatus(
    issues: IssueVO[],
    targetStatusId: string
  ): Promise<BatchResult> {
    return executeBatch(issues, async (issue) => {
      await issueApi.transitStatus(issue.id, targetStatusId)
    }, '状态变更')
  }

  /**
   * 批量分配
   */
  async function batchAssign(
    issues: IssueVO[],
    assigneeId: string
  ): Promise<BatchResult> {
    return executeBatch(issues, async (issue) => {
      await issueApi.assign(issue.id, assigneeId)
    }, '分配')
  }

  /**
   * 批量移动 Sprint
   */
  async function batchUpdateSprint(
    issues: IssueVO[],
    sprintId: string | null
  ): Promise<BatchResult> {
    return executeBatch(issues, async (issue) => {
      await issueApi.update(issue.id, { sprintId })
    }, 'Sprint 移动')
  }

  /**
   * 批量变更优先级
   */
  async function batchUpdatePriority(
    issues: IssueVO[],
    priority: string
  ): Promise<BatchResult> {
    return executeBatch(issues, async (issue) => {
      await issueApi.update(issue.id, { priority })
    }, '优先级变更')
  }

  /**
   * 通用批量执行引擎
   */
  async function executeBatch(
    issues: IssueVO[],
    action: (issue: IssueVO) => Promise<void>,
    operationName: string
  ): Promise<BatchResult> {
    const batch = issues.slice(0, MAX_BATCH_SIZE)
    executing.value = true

    try {
      const results = await Promise.allSettled(
        batch.map(issue => action(issue))
      )

      const result: BatchResult = {
        total: batch.length,
        succeeded: 0,
        failed: 0,
        failures: []
      }

      results.forEach((r, idx) => {
        if (r.status === 'fulfilled') {
          result.succeeded++
        } else {
          result.failed++
          const reason = (r.reason as any)?.response?.data?.message
            || (r.reason as any)?.message
            || '未知错误'
          result.failures.push({
            issueId: batch[idx].id,
            issueKey: batch[idx].issueKey,
            reason
          })
        }
      })

      // 显示结果通知
      showBatchResult(result, operationName)

      return result
    } finally {
      executing.value = false
    }
  }

  /**
   * 显示批量操作结果
   */
  function showBatchResult(result: BatchResult, operationName: string) {
    if (result.failed === 0) {
      Message.success({
        content: `${operationName}完成，共 ${result.succeeded} 个工单已更新`,
        duration: 3000
      })
    } else if (result.succeeded === 0) {
      Message.error({
        content: `${operationName}失败，${result.failed} 个工单未能更新`,
        duration: 5000
      })
    } else {
      Notification.warning({
        title: `${operationName}部分完成`,
        content: `成功 ${result.succeeded} 个，失败 ${result.failed} 个`,
        duration: 0, // 不自动关闭
        closable: true
      })
    }
  }

  return {
    executing,
    batchTransitStatus,
    batchAssign,
    batchUpdateSprint,
    batchUpdatePriority
  }
}
