import { ref } from 'vue'
import { Message, Modal, Notification } from '@arco-design/web-vue'
import { issueApi } from '@/api'
import { ERROR_CODES } from '@/api/error-codes'
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
    targetStatusId: string,
    comment?: string
  ): Promise<BatchResult> {
    const versions = buildVersionMap(issues)
    const batch = issues.slice(0, MAX_BATCH_SIZE)
    executing.value = true

    try {
      const res = await issueApi.batch({
        operation: 'status',
        issueIds: batch.map(i => i.id),
        statusId: targetStatusId,
        comment,
        versions
      })

      // WIP 限制超出 — 弹确认对话框
      if (res.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) {
        executing.value = false
        return new Promise<BatchResult>((resolve) => {
          Modal.warning({
            title: 'WIP 限制',
            content: res.message,
            okText: '继续变更',
            cancelText: '取消',
            hideCancel: false,
            onOk: async () => {
              // 用户确认后重试，带 forceWip=true
              const result = await executeBatchApi(issues, 'status', {
                statusId: targetStatusId, comment, versions, forceWip: true
              }, '状态变更')
              resolve(result)
            },
            onCancel: () => {
              resolve({ total: batch.length, succeeded: 0, failed: 0, failures: [] })
            }
          })
        })
      }

      // 正常响应处理
      const data = res.data
      const result: BatchResult = {
        total: data?.total ?? batch.length,
        succeeded: data?.succeeded ?? 0,
        failed: data?.failed ?? 0,
        failures: data?.failures ?? []
      }
      showBatchResult(result, '状态变更')
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
   * 批量添加标签
   */
  async function batchTagAdd(
    issues: IssueVO[],
    tagId: string
  ): Promise<BatchResult> {
    return executeBatchApi(issues, 'tag_add', { tagId }, '添加标签')
  }

  /**
   * 批量移除标签
   */
  async function batchTagRemove(
    issues: IssueVO[],
    tagId: string
  ): Promise<BatchResult> {
    return executeBatchApi(issues, 'tag_remove', { tagId }, '移除标签')
  }

  /**
   * 批量添加关联（逐个调用单条 API）
   */
  async function batchAddLink(
    issues: IssueVO[],
    linkType: string,
    targetIssueId: string
  ): Promise<BatchResult> {
    const batch = issues.slice(0, MAX_BATCH_SIZE)
    executing.value = true
    const failures: Array<{ issueId: string; issueKey: string; reason: string }> = []
    let succeeded = 0

    try {
      for (const issue of batch) {
        try {
          await issueApi.createLink(issue.id, { linkType, targetIssueId })
          succeeded++
        } catch (e: any) {
          const reason = e.response?.data?.message || '创建关联失败'
          failures.push({ issueId: issue.id, issueKey: issue.issueKey, reason })
        }
      }

      const result: BatchResult = {
        total: batch.length,
        succeeded,
        failed: failures.length,
        failures
      }
      showBatchResult(result, '添加关联')
      return result
    } finally {
      executing.value = false
    }
  }

  /**
   * 批量删除（带撤销 toast）
   */
  async function batchDelete(
    issues: IssueVO[]
  ): Promise<BatchResult> {
    const result = await executeBatchApi(issues, 'delete', {}, '删除', true)
    // 若有删除成功的工单，显示带"撤销"按钮的 Notification
    if (result.succeeded > 0) {
      const deletedIds = issues.slice(0, MAX_BATCH_SIZE)
        .map(i => i.id)
        .filter(id => !result.failures.some(f => f.issueId === id))
      showUndoNotification(deletedIds, result.succeeded)
    }
    return result
  }

  /**
   * 显示带"撤销"按钮的删除通知（5 秒超时）
   */
  function showUndoNotification(deletedIds: string[], count: number) {
    let undone = false
    const key = `undo-delete-${Date.now()}`
    Notification.info({
      id: key,
      title: `已删除 ${count} 个工单`,
      content: '已移至回收站，可随时恢复',
      duration: 5000,
      closable: true,
      footer: () => {
        const btn = document.createElement('button')
        btn.textContent = '撤销'
        btn.className = 'arco-btn arco-btn-text arco-btn-size-mini'
        btn.style.cssText = 'color: var(--tf-accent); font-weight: 500; margin-top: 4px;'
        btn.onclick = async () => {
          if (undone) return
          undone = true
          try {
            await Promise.all(deletedIds.map(id => issueApi.restore(id)))
            Message.success(`已撤销删除，${count} 个工单已恢复`)
            // Trigger refresh via custom event
            window.dispatchEvent(new CustomEvent('trackflow:issues-restored'))
          } catch {
            Message.error('撤销失败，请到回收站手动恢复')
          }
          Notification.remove(key)
        }
        return btn
      }
    })
  }

  /**
   * 从选中的工单列表中构建乐观锁版本映射（id → version）
   */
  function buildVersionMap(issues: IssueVO[]): Record<string, number> {
    const map: Record<string, number> = {}
    for (const issue of issues.slice(0, MAX_BATCH_SIZE)) {
      if (issue.version != null) {
        map[issue.id] = issue.version
      }
    }
    return map
  }

  /**
   * 调用后端批量 API
   */
  async function executeBatchApi(
    issues: IssueVO[],
    operation: string,
    params: Record<string, any>,
    operationName: string,
    suppressSuccessMessage = false
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

      if (!suppressSuccessMessage) {
        showBatchResult(result, operationName)
      } else if (result.failed > 0 && result.succeeded > 0) {
        // 部分失败仍然显示
        Notification.warning({
          title: `${operationName}部分完成`,
          content: `成功 ${result.succeeded} 个，失败 ${result.failed} 个`,
          duration: 0,
          closable: true
        })
      } else if (result.succeeded === 0 && result.failed > 0) {
        Message.error({
          content: `${operationName}失败，${result.failed} 个工单未能更新`,
          duration: 5000
        })
      }
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
        duration: 0,
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
    batchTagAdd,
    batchTagRemove,
    batchAddLink,
    batchDelete
  }
}
