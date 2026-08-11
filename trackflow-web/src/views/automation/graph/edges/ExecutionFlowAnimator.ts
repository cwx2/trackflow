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

/** 与 FlowEdge 粒子时长保持一致，保证快速工作流也至少渲染一个完整流动周期。 */
const FLOW_DURATION_MS = 900

export class ExecutionFlowAnimator {
  private readonly settleTimers = new Map<string, number>()
  private readonly startTimers = new Map<string, number>()
  private readonly nodeArrivalTimes = new Map<string, number>()
  private generation = 0

  constructor(
    private readonly getEdges: () => WorkflowCanvasEdge[],
    private readonly setEdgeProperties: EdgePropertyWriter,
  ) {}

  /** 开始一次新执行前清空上一轮的瞬时状态和延迟任务。 */
  begin() {
    this.generation += 1
    this.clearTimers()
    this.nodeArrivalTimes.clear()
    this.setAll('idle')
  }

  /** 播放实际进入 targetNodeId 的数据流。 */
  flowIntoNode(targetNodeId: string) {
    const generation = this.generation
    const edges = this.getIncomingEdges(targetNodeId)
    if (edges.length === 0) {
      this.nodeArrivalTimes.set(targetNodeId, Date.now())
      return
    }

    // SSE 可能在同一个绘制帧内回放多个极快节点。以已知上游的可视抵达时间
    // 为下限，既不改变真实执行顺序，又给用户留下能看见的流动过渡。
    const startedAt = Math.max(
      Date.now(),
      ...edges.map(edge => this.nodeArrivalTimes.get(edge.sourceNodeId) || 0),
    )
    this.nodeArrivalTimes.set(targetNodeId, startedAt + FLOW_DURATION_MS)

    for (const edge of edges) {
      this.clearTimer(edge.id)
      const delay = Math.max(0, startedAt - Date.now())
      const start = () => {
        if (this.generation !== generation) return
        this.setEdgeProperties(edge.id, { flowStatus: 'running' })
        const settle = window.setTimeout(() => {
          if (this.generation === generation) {
            this.setEdgeProperties(edge.id, { flowStatus: 'done' })
          }
          this.settleTimers.delete(edge.id)
        }, FLOW_DURATION_MS)
        this.settleTimers.set(edge.id, settle)
      }
      if (delay === 0) {
        start()
      } else {
        const timer = window.setTimeout(() => {
          this.startTimers.delete(edge.id)
          start()
        }, delay)
        this.startTimers.set(edge.id, timer)
      }
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
    // 成功的快速流程仍让已排队的真实路径完整播放；实际执行状态已经结束，
    // 这里只保留一个不阻塞用户的视觉回放。
    if (outcome === 'success') return
    const status: ExecutionFlowStatus = 'failed'
    for (const edgeId of this.startTimers.keys()) {
      this.clearStartTimer(edgeId)
      this.setEdgeProperties(edgeId, { flowStatus: status })
    }
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
    for (const edgeId of this.startTimers.keys()) this.clearStartTimer(edgeId)
    for (const edgeId of this.settleTimers.keys()) this.clearTimer(edgeId)
  }

  private clearStartTimer(edgeId: string) {
    const timer = this.startTimers.get(edgeId)
    if (timer !== undefined) window.clearTimeout(timer)
    this.startTimers.delete(edgeId)
  }

  private clearTimer(edgeId: string) {
    const timer = this.settleTimers.get(edgeId)
    if (timer !== undefined) window.clearTimeout(timer)
    this.settleTimers.delete(edgeId)
  }
}
