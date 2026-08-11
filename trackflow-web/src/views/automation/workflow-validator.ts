import type { InputParameter, OutputPort, WorkflowDefinition } from '@/api/automation'
import { getNodeDefinition } from './node-definitions'
import { FLOW_PORT } from './graph/connection-semantics'

/** 与后端 WorkflowDefinitionValidator 对齐的运行前检查，避免用户等待一次 400 才知道画布无效。 */
export function validateExecutableWorkflow(definition: WorkflowDefinition): string | null {
  const nodes = definition.nodes || []
  const edges = definition.edges || []
  const byId = new Map(nodes.map(node => [node.id, node]))
  const starts = nodes.filter(node => node.type === 'start')
  if (starts.length !== 1) return '工作流必须且只能包含一个开始节点'
  if (!nodes.some(node => node.type === 'end')) return '工作流至少需要一个结束节点'

  for (const node of nodes) {
    const expected = getNodeDefinition(node.type)
    if (!expected) return `节点未注册：${node.type}`
    const actualInputs = new Map((node.inputs || []).map(port => [port.name, port]))
    const actualOutputs = new Map((node.outputs || []).map(port => [port.name, port]))
    if (!sameNames(expected.inputPorts, node.inputs || []) || !sameNames(expected.outputPorts, node.outputs || [])) {
      return `节点端口已过期或不完整：${node.nodeMeta?.title || node.id}`
    }
    for (const port of expected.inputPorts) {
      const actual = actualInputs.get(port.name)!
      if (actual.valueType !== port.valueType || actual.required !== port.required || Boolean(actual.optional) !== Boolean(port.optional)) {
        return `节点输入契约不一致：${node.nodeMeta?.title || node.id}.${port.label || port.name}`
      }
      if (port.required && actual.value == null) return `请配置必填参数：${node.nodeMeta?.title || node.id}.${port.label || port.name}`
    }
    for (const port of expected.outputPorts) {
      if (actualOutputs.get(port.name)?.valueType !== port.valueType) {
        return `节点输出契约不一致：${node.nodeMeta?.title || node.id}.${port.label || port.name}`
      }
    }
  }

  for (const edge of edges) {
    const source = byId.get(edge.sourceNodeId)
    const target = byId.get(edge.targetNodeId)
    if (!source || !target) return `连线 ${edge.id} 引用了不存在的节点`
    const isControlEdge = edge.sourcePortName === FLOW_PORT || edge.targetPortName === FLOW_PORT
    if (isControlEdge) {
      if (edge.sourcePortName !== FLOW_PORT || edge.targetPortName !== FLOW_PORT) {
        return `流程线必须从流程出口连接到流程入口：${edge.id}`
      }
      if (edge.flowBranch) {
        const branch = getNodeDefinition(source.type)?.outputPorts.find(port => port.name === edge.flowBranch)
        if (!branch || branch.valueType !== 'boolean') {
          return `流程分支不存在或不是布尔输出：${source.nodeMeta?.title || source.id}.${edge.flowBranch}`
        }
      }
      // 流程端口是画布运行时提供的虚拟端口，不属于节点的数据端口契约。
      continue
    }
    if (edge.flowBranch) return `只有流程线可以声明分支：${edge.id}`
    const sourcePort = getNodeDefinition(source.type)?.outputPorts.find(port => port.name === edge.sourcePortName)
    const targetPort = getNodeDefinition(target.type)?.inputPorts.find(port => port.name === edge.targetPortName)
    if (!sourcePort) return `连线来源端口不存在：${edge.sourcePortName}`
    if (!targetPort) return `连线目标端口不存在：${edge.targetPortName}`
    const sourceCardinality = sourcePort.cardinality || (sourcePort.valueType === 'array' ? 'collection' : 'single')
    const targetCardinality = targetPort.cardinality || (targetPort.valueType === 'array' ? 'collection' : 'single')
    if (sourceCardinality === 'collection' && targetCardinality === 'single'
      && edge.collectionBindingMode !== 'each') {
      return `集合连线必须启用逐项处理：${source.nodeMeta?.title || source.id} → ${target.nodeMeta?.title || target.id}`
    }
  }

  for (const node of nodes) {
    for (const input of node.inputs || []) {
      const ref = input.value
      if (!ref || ref.type !== 'ref') continue
      const source = byId.get(ref.nodeId)
      const sourcePort = source && getNodeDefinition(source.type)?.outputPorts.find(port => port.name === ref.outputName)
      if (!sourcePort) return `变量引用不存在：${ref.nodeId}.${ref.outputName}`
      const itemBinding = edges.some(edge => edge.sourceNodeId === ref.nodeId
        && edge.sourcePortName === ref.outputName
        && edge.targetNodeId === node.id
        && edge.targetPortName === input.name
        && edge.collectionBindingMode === 'each')
      if (!ref.path && !itemBinding && !compatible(sourcePort.valueType, input.valueType)
        && source.id === node.id) {
        const title = node.nodeMeta?.title || node.id
        const inputLabel = input.label || input.name
        const outputLabel = sourcePort.label || ref.outputName
        return `“${title}”的“${inputLabel}”错误地引用了自身输出“${outputLabel}”。请重新连接上游数据。`
      }
      if (!ref.path && !itemBinding && !compatible(sourcePort.valueType, input.valueType)) {
        return `变量类型不兼容：${ref.nodeId}.${ref.outputName} → ${node.id}.${input.name}`
      }
      if (ref.path && !['object', 'array', 'any'].includes(sourcePort.valueType)) {
        return `变量 path 只能用于对象、数组或任意类型：${ref.nodeId}.${ref.outputName}`
      }
    }
  }

  const reachable = walk(starts[0].id, edges, true)
  const endings = nodes.filter(node => node.type === 'end')
  const canReachEnd = new Set(endings.flatMap(node => [...walk(node.id, edges, false)]))
  for (const node of nodes) {
    if (!reachable.has(node.id)) return `存在无法从开始节点到达的节点：${node.nodeMeta?.title || node.id}`
    if (!canReachEnd.has(node.id)) return `存在无法结束的节点：${node.nodeMeta?.title || node.id}`
  }
  return null
}

function sameNames(expected: Array<{ name: string }>, actual: Array<InputParameter | OutputPort>) {
  return expected.length === actual.length && expected.every(port => actual.some(candidate => candidate.name === port.name))
}

function compatible(source: string, target: string) {
  return source === target || source === 'any' || target === 'any'
}

function walk(root: string, edges: WorkflowDefinition['edges'], forward: boolean) {
  const graph = new Map<string, string[]>()
  for (const edge of edges) {
    const from = forward ? edge.sourceNodeId : edge.targetNodeId
    const to = forward ? edge.targetNodeId : edge.sourceNodeId
    graph.set(from, [...(graph.get(from) || []), to])
  }
  const visited = new Set<string>()
  const queue = [root]
  while (queue.length) {
    const current = queue.shift()!
    if (visited.has(current)) continue
    visited.add(current)
    queue.push(...(graph.get(current) || []))
  }
  return visited
}
