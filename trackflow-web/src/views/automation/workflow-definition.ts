import type { GlobalVariable, NodeMeta, NodeType, WorkflowDefinition, WorkflowNode } from '@/api'
import { getNodeDefinition } from './node-definitions'

const CANVAS_OFFSET = { x: 100, y: 30 }
const RESERVED_NODE_PROPERTIES = new Set(['nodeType', 'nodeMeta', 'inputs', 'outputs', 'runStatus', 'label'])

/** 将历史存量定义升级为当前节点注册表的正式端口契约。 */
export function migrateWorkflowDefinition(raw: any): WorkflowDefinition {
  if (raw.globalVariables !== undefined) {
    return synchronizeDefinitionBindings({
      ...raw,
      nodes: (raw.nodes || []).map((node: any) => upgradeNodeContract(node)),
    } as WorkflowDefinition)
  }

  return synchronizeDefinitionBindings({
    globalVariables: Object.fromEntries(
      Object.entries(raw.variables || {}).map(([key, value]) => [key, { type: 'string' as const, defaultValue: value }]),
    ),
    nodes: (raw.nodes || []).map((node: any) => {
      const definition = getNodeDefinition(node.type)
      return {
        id: node.id,
        type: node.type,
        position: node.position,
        nodeMeta: {
          title: node.label || definition?.meta.title || node.type,
          icon: definition?.meta.icon || '⬡',
          description: definition?.meta.description || '',
          color: definition?.meta.color || '#6366f1',
        },
        inputs: (definition?.inputPorts || []).map(port => ({
          name: port.name,
          valueType: port.valueType,
          required: port.required,
          optional: port.optional,
          description: port.description,
          value: node.data?.[port.name] != null
            ? { type: 'literal' as const, value: node.data[port.name] }
            : null,
        })),
        outputs: definition?.outputPorts || [],
        config: node.data || {},
      }
    }),
    edges: (raw.edges || []).map((edge: any) => ({
      id: edge.id,
      sourceNodeId: edge.source || edge.sourceNodeId,
      sourcePortName: edge.sourceHandle || edge.sourcePortName || 'output',
      targetNodeId: edge.target || edge.targetNodeId,
      targetPortName: edge.targetHandle || edge.targetPortName || 'input',
    })),
  } as WorkflowDefinition)
}

/**
 * 空白工作流的系统骨架。
 *
 * 开始与结束不是可从节点库任意拖入的业务节点：一个工作流只能有一个开始节点，
 * 但可以在分支中拥有多个结束节点。新建和历史空画布都从这条可运行基线开始。
 */
export function createInitialWorkflowDefinition(): WorkflowDefinition {
  const start = upgradeNodeContract({
    id: 'start',
    type: 'start',
    position: { x: 260, y: 260 },
    nodeMeta: {},
    inputs: [],
    outputs: [],
    config: {},
  })
  const end = upgradeNodeContract({
    id: 'end',
    type: 'end',
    position: { x: 700, y: 260 },
    nodeMeta: {},
    inputs: [],
    outputs: [],
    config: {},
  })
  end.inputs = end.inputs.map((input: any) => input.name === 'result'
    ? { ...input, value: { type: 'ref', nodeId: 'start', outputName: 'trigger' } }
    : input)

  return {
    globalVariables: {},
    nodes: [start, end],
    edges: [{
      id: 'start-to-end',
      sourceNodeId: 'start',
      sourcePortName: 'trigger',
      targetNodeId: 'end',
      targetPortName: 'result',
    }],
  } as WorkflowDefinition
}

/** 使用节点注册表重建端口快照，杜绝模板把过期端口重新写回服务端。 */
export function upgradeNodeContract(node: any) {
  const definition = getNodeDefinition(node.type)
  if (!definition) return node

  const existingInputs = new Map<string, any>((node.inputs || []).map((input: any) => [input.name, input]))
  return {
    ...node,
    // category belongs to the editor palette, not the persisted NodeMeta contract.
    // Keep this boundary strict so every definition accepted by the editor is
    // accepted by the backend without relying on unknown-property tolerance.
    nodeMeta: createNodeMeta(definition.meta, node.nodeMeta),
    inputs: definition.inputPorts.map(port => {
      const existing = existingInputs.get(port.name)
      return {
        name: port.name,
        label: port.label,
        valueType: port.valueType,
        required: port.required === true,
        optional: port.optional === true,
        description: port.description,
        value: existing?.value ?? port.defaultValue ?? null,
      }
    }),
    outputs: definition.outputPorts,
  }
}

/** 将 LogicFlow 节点转换为可保存、可执行的工作流节点。 */
export function normalizeCanvasNode(canvasNode: any): WorkflowNode {
  const type = (canvasNode.properties?.nodeType || canvasNode.type) as NodeType
  return upgradeNodeContract({
    id: canvasNode.id,
    type,
    position: { x: canvasNode.x - CANVAS_OFFSET.x, y: canvasNode.y - CANVAS_OFFSET.y },
    nodeMeta: canvasNode.properties?.nodeMeta || {
      title: canvasNode.text?.value || canvasNode.text || getWorkflowNodeTitle(type),
      icon: '⬡', description: '', color: '#6366f1',
    },
    inputs: canvasNode.properties?.inputs || [],
    outputs: canvasNode.properties?.outputs || [],
    config: extractNodeConfig(canvasNode.properties || {}),
  }) as WorkflowNode
}

/** Convert editor metadata into the exact backend NodeMeta contract. */
function createNodeMeta(definitionMeta: NodeMeta & { category?: string }, savedMeta?: Partial<NodeMeta>): NodeMeta {
  return {
    title: savedMeta?.title || definitionMeta.title,
    icon: savedMeta?.icon || definitionMeta.icon,
    description: savedMeta?.description || definitionMeta.description || '',
    color: savedMeta?.color || definitionMeta.color,
  }
}

/** 统一从画布生成定义，保存和试运行使用同一份序列化规则。 */
export function buildWorkflowDefinition(
  globalVariables: Record<string, GlobalVariable>,
  graphData: { nodes: any[]; edges: any[] },
): WorkflowDefinition {
  return synchronizeDefinitionBindings({
    globalVariables,
    nodes: graphData.nodes.map(normalizeCanvasNode),
    edges: graphData.edges.map((edge: any) => ({
      id: edge.id,
      sourceNodeId: edge.sourceNodeId,
      sourcePortName: edge.properties?.sourcePortName || 'output',
      targetNodeId: edge.targetNodeId,
      targetPortName: edge.properties?.targetPortName || 'input',
    })),
  } as WorkflowDefinition)
}

/**
 * 兼容历史草稿的单向修复：具名端口边是数据绑定的可视表达。
 * 仅在源、目标端口类型可直接赋值时补齐缺失的 ref；对象取字段需要用户明确 path，
 * 因而绝不在这里猜测字段或覆盖用户已有的输入值。
 */
export function synchronizeDefinitionBindings(definition: WorkflowDefinition): WorkflowDefinition {
  const nodes = definition.nodes.map(node => ({ ...node, inputs: node.inputs.map(input => ({ ...input })) }))
  const nodesById = new Map(nodes.map(node => [node.id, node]))
  for (const edge of definition.edges || []) {
    const source = nodesById.get(edge.sourceNodeId)
    const target = nodesById.get(edge.targetNodeId)
    const sourcePort = source?.outputs.find(port => port.name === edge.sourcePortName)
    const targetInput = target?.inputs.find(input => input.name === edge.targetPortName)
    if (!sourcePort || !targetInput || targetInput.value != null || !isDirectlyCompatible(sourcePort.valueType, targetInput.valueType)) continue
    targetInput.value = { type: 'ref', nodeId: source!.id, outputName: sourcePort.name }
  }
  return { ...definition, nodes }
}

function isDirectlyCompatible(source: string, target: string) {
  return source === target || source === 'any' || target === 'any'
}

export function extractNodeConfig(properties: Record<string, any>) {
  const legacyConfig = Object.fromEntries(
    Object.entries(properties).filter(([key]) => !RESERVED_NODE_PROPERTIES.has(key) && key !== 'config'),
  )
  return { ...legacyConfig, ...(properties.config || {}) }
}

export function getWorkflowNodeTitle(type: string): string {
  const definition = getNodeDefinition(type)
  if (definition) return definition.meta.title
  const fallbackTitles: Record<string, string> = {
    'cli-agent': 'CLI Agent',
    variables: '变量设置',
    condition: '条件判断',
    loop: '重试循环',
    'file-input': '文件输入',
    delay: '延时等待',
  }
  return fallbackTitles[type] || '节点'
}
