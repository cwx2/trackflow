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
    return executeBatchApi(issues, 'status', { statusId: targetStatusId }, '状态变更')
  }

  /**
   * 批量分配
   */
  async function batchAssign(
    issues: IssueVO[],
    assigneeId: string
  ): Promise<BatchResult> {
    return executeBatchApi(issues, 'assign', { assigneeId: assigneeId || '0' }, '分配')
  }

  /**
   * 批量移动 Sprint
   */
  async function batchUpdateSprint(
    issues: IssueVO[],
    sprintId: string | null
  ): Promise<BatchResult> {
    return executeBatchApi(issues, 'sprint', { sprintId: sprintId || '0' }, 'Sprint 移动')
  }

  /**
   * 批量变更优先级
   */
  async function batchUpdatePriority(
    issues: IssueVO[],
    priority: string
  ): Promise<BatchResult> {
    return executeBatchApi(issues, 'priority', { priority }, '优先级变更')
  }

  /**
   * 批量删除
   */
  async function batchDelete(
    issues: IssueVO[]
  ): Promise<BatchResult> {
    return executeBatchApi(issues, 'delete', {}, '删除')
  }

  /**
   * 调用后端批量 API
   */
  async function executeBatchApi(
    issues: IssueVO[],
    operation: string,
    params: Record<string, any>,
    operationName: string
  ): Promise<BatchResult> {
    const batch = issues.slice(0, MAX_BATCH_SIZE)
    executing.value = true

    try {
      const res = await issueApi.batch({
        operation,
        issueIds: batch.map(i => i.id),
        ...params
      })

      const data = res.data
      const result: BatchResult = {
        total: data?.total ?? batch.length,
        succeeded: data?.succeeded ?? 0,
        failed: data?.failed ?? 0,
        failures: data?.failures ?? []
      }

      showBatchResult(result, operationName)
      return result
    } catch (e: any) {
      const errorMsg = e.response?.data?.message || '批量操作失败'
      Message.error({ content: errorMsg, duration: 5000 })
      return {
        total: batch.length,
        succeeded: 0,
        failed: batch.length,
        failures: batch.map(i => ({
          issueId: i.id,
          issueKey: i.issueKey,
          reason: errorMsg
        }))
      }
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
    batchUpdatePriority,
    batchDelete
  }
}
