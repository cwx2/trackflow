/**
 * 画布连接语义的唯一来源。
 *
 * 工作流同时存在“执行顺序”和“业务数据”两张图：前者决定节点何时运行，
 * 后者决定字段从哪里取值。这里集中定义其持久化标记与编辑器展示状态，
 * 防止节点、边和序列化层各自猜测 `__flow`。
 */
export const FLOW_PORT = '__flow'

export type ConnectionKind = 'control' | 'data'
export type ConnectionViewMode = 'all' | 'flow' | 'data'

export function isFlowPort(portName: string | null | undefined) {
  return portName === FLOW_PORT
}

export function resolveConnectionKind(
  sourcePortName: string | null | undefined,
  targetPortName?: string | null,
): ConnectionKind | null {
  const sourceIsFlow = isFlowPort(sourcePortName)
  const targetIsFlow = targetPortName === undefined ? sourceIsFlow : isFlowPort(targetPortName)
  if (sourceIsFlow || targetIsFlow) return sourceIsFlow && targetIsFlow ? 'control' : null
  if (!sourcePortName) return null
  if (targetPortName !== undefined && !targetPortName) return null
  return 'data'
}

export function isConnectionVisible(kind: ConnectionKind, viewMode: ConnectionViewMode) {
  return viewMode === 'all' || (viewMode === 'flow' && kind === 'control') || (viewMode === 'data' && kind === 'data')
}

export function connectionKindLabel(kind: ConnectionKind) {
  return kind === 'control' ? '流程线' : '数据线'
}
