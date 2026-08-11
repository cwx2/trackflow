/**
 * 将后端执行事件映射为画布边的短暂运行态。
 *
 * 只有节点实际收到 `node_running` 事件时，才播放进入该节点的入边；这避免了
 * 条件节点的未选中分支、循环的未进入分支被错误地渲染为已执行。
 */
export type ExecutionFlowStatus = 'idle' | 'running' | 'done' | 'failed'

export type WorkflowCanvasEdge = {
  id: string
  sourceNodeId: string
  targetNodeId: string
}

type EdgePropertyWriter = (edgeId: string, properties: { flowStatus: ExecutionFlowStatus }) => void

const FLOW_DURATION_MS = 1_650

export class ExecutionFlowAnimator {
  private readonly settleTimers = new Map<string, number>()
  private generation = 0

  constructor(
    private readonly getEdges: () => WorkflowCanvasEdge[],
    private readonly setEdgeProperties: EdgePropertyWriter,
  ) {}

  /** 开始一次新执行前清空上一轮的瞬时状态和延迟任务。 */
  begin() {
    this.generation += 1
    this.clearTimers()
    this.setAll('idle')
  }

  /** 播放实际进入 targetNodeId 的数据流。 */
  flowIntoNode(targetNodeId: string) {
    const generation = this.generation
    for (const edge of this.getIncomingEdges(targetNodeId)) {
      this.clearTimer(edge.id)
      this.setEdgeProperties(edge.id, { flowStatus: 'running' })
      const timer = window.setTimeout(() => {
        if (this.generation === generation) {
          this.setEdgeProperties(edge.id, { flowStatus: 'done' })
        }
        this.settleTimers.delete(edge.id)
      }, FLOW_DURATION_MS)
      this.settleTimers.set(edge.id, timer)
    }
  }

  /** 节点失败时保留失败来源，便于定位实际中断的连线。 */
  failIntoNode(targetNodeId: string) {
    for (const edge of this.getIncomingEdges(targetNodeId)) {
      this.clearTimer(edge.id)
      this.setEdgeProperties(edge.id, { flowStatus: 'failed' })
    }
  }

  /** 整体执行结束后结算仍在流动的边，不影响已经完成或失败的路径。 */
  settle(outcome: 'success' | 'failed' | 'cancelled') {
    const status: ExecutionFlowStatus = outcome === 'success' ? 'done' : 'failed'
    for (const edgeId of this.settleTimers.keys()) {
      this.clearTimer(edgeId)
      this.setEdgeProperties(edgeId, { flowStatus: status })
    }
  }

  /** 加载已完成的执行详情时，恢复真实经过的路径，不播放装饰性动画。 */
  restoreCompletedPaths(nodeStatuses: Record<string, string>) {
    for (const edge of this.getEdges()) {
      const targetStatus = nodeStatuses[edge.targetNodeId]
      if (targetStatus === 'success') {
        this.setEdgeProperties(edge.id, { flowStatus: 'done' })
      } else if (targetStatus === 'failed' || targetStatus === 'cancelled') {
        this.setEdgeProperties(edge.id, { flowStatus: 'failed' })
      }
    }
  }

  dispose() {
    this.generation += 1
    this.clearTimers()
  }

  private getIncomingEdges(targetNodeId: string) {
    return this.getEdges().filter(edge => edge.targetNodeId === targetNodeId)
  }

  private setAll(status: ExecutionFlowStatus) {
    for (const edge of this.getEdges()) {
      this.setEdgeProperties(edge.id, { flowStatus: status })
    }
  }

  private clearTimers() {
    for (const edgeId of this.settleTimers.keys()) this.clearTimer(edgeId)
  }

  private clearTimer(edgeId: string) {
    const timer = this.settleTimers.get(edgeId)
    if (timer !== undefined) window.clearTimeout(timer)
    this.settleTimers.delete(edgeId)
  }
}
