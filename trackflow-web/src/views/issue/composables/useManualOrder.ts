import { ref, computed, watch } from 'vue'
import { issueApi } from '@/api'
import type { IssueVO, ManualOrderVO } from '@/api/types'
import { Message } from '@arco-design/web-vue'

export interface ManualOrderContext {
  type: 'project' | 'query'
  id: string
}

/**
 * 手动排序 composable
 * 管理拖拽排序状态、API 交互、列表重排
 */
export function useManualOrder() {
  const manualOrderData = ref<ManualOrderVO | null>(null)
  const isManualSorted = ref(false)
  const isOwnerOrder = ref(false)
  const loading = ref(false)
  const context = ref<ManualOrderContext | null>(null)

  /**
   * 加载指定上下文的手动排序数据
   */
  async function loadManualOrder(ctx: ManualOrderContext) {
    context.value = ctx
    try {
      const res = await issueApi.getManualOrder(ctx.type, ctx.id)
      manualOrderData.value = res.data || null
      isManualSorted.value = !!(res.data?.issueIds?.length)
      isOwnerOrder.value = res.data?.ownerOrder || false
    } catch {
      manualOrderData.value = null
      isManualSorted.value = false
      isOwnerOrder.value = false
    }
  }

  /**
   * 根据手动排序对 issues 列表重排序
   * 返回：{ sorted: 手动排序的 issues, rest: 未排序的 issues }
   */
  function applyManualOrder(issues: IssueVO[]): { sorted: IssueVO[]; rest: IssueVO[] } {
    if (!isManualSorted.value || !manualOrderData.value?.issueIds?.length) {
      return { sorted: [], rest: issues }
    }

    const orderIds = manualOrderData.value.issueIds
    const orderMap = new Map<string, number>()
    orderIds.forEach((id, idx) => orderMap.set(id, idx))

    const sorted: IssueVO[] = []
    const rest: IssueVO[] = []

    for (const issue of issues) {
      if (orderMap.has(issue.id)) {
        sorted.push(issue)
      } else {
        rest.push(issue)
      }
    }

    // 按手动排序的 position 排列
    sorted.sort((a, b) => (orderMap.get(a.id) ?? 0) - (orderMap.get(b.id) ?? 0))

    return { sorted, rest }
  }

  /**
   * 保存拖拽后的完整排序列表
   */
  async function saveOrder(issueIds: string[]) {
    if (!context.value) return

    loading.value = true
    try {
      const res = await issueApi.saveManualOrder({
        contextType: context.value.type,
        contextId: Number(context.value.id),
        issueIds: issueIds.map(Number)
      })
      manualOrderData.value = res.data || null
      isManualSorted.value = true
      isOwnerOrder.value = res.data?.ownerOrder || false
    } catch (e: any) {
      Message.error(e.response?.data?.message || '保存排序失败')
    } finally {
      loading.value = false
    }
  }

  /**
   * 丢弃手动排序（恢复默认）
   */
  async function discardOrder() {
    if (!context.value) return

    loading.value = true
    try {
      await issueApi.discardManualOrder(context.value.type, context.value.id)
      manualOrderData.value = null
      isManualSorted.value = false
      isOwnerOrder.value = false
      Message.success('已恢复默认排序')
    } catch (e: any) {
      Message.error(e.response?.data?.message || '操作失败')
    } finally {
      loading.value = false
    }
  }

  /**
   * 清除状态（切换上下文时调用）
   */
  function reset() {
    manualOrderData.value = null
    isManualSorted.value = false
    isOwnerOrder.value = false
    context.value = null
  }

  return {
    manualOrderData,
    isManualSorted,
    isOwnerOrder,
    loading,
    context,
    loadManualOrder,
    applyManualOrder,
    saveOrder,
    discardOrder,
    reset
  }
}
