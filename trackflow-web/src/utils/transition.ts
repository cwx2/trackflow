import { Message } from '@arco-design/web-vue'
import type { TransitStatusResultVO } from '@/api/types'

/**
 * 从状态转换响应中提取版本号（兼容新旧格式）。
 *
 * 旧格式：data 直接是 number（版本号）
 * 新格式：data 是 TransitStatusResultVO { version, actionResult }
 */
export function extractVersion(data: any): number | null {
  if (data == null) return null
  if (typeof data === 'number') return data
  if (typeof data === 'object' && 'version' in data) return data.version
  return null
}

/**
 * 显示自动化动作执行反馈 Toast。
 * 仅在自动分配成功或策略失败时显示。
 */
export function showActionFeedback(result: TransitStatusResultVO | null | undefined): void {
  if (!result?.actionResult) return

  const { outcome, newAssigneeName } = result.actionResult

  switch (outcome) {
    case 'ASSIGNED':
      Message.info({
        content: `已自动分配给 ${newAssigneeName || '相关人员'}`,
        duration: 3000
      })
      break
    case 'STRATEGY_FAILED':
      Message.warning({
        content: '自动分配失败，请手动指定负责人',
        duration: 5000
      })
      break
    case 'EXECUTION_ERROR':
      Message.warning({
        content: '自动分配执行异常，请检查负责人',
        duration: 5000
      })
      break
    // MANUAL_OVERRIDE / NO_ACTIONS — 不需要额外提示
  }
}
