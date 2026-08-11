import type { GlobalVariable, NodeMeta, NodeType, WorkflowDefinition, WorkflowNode } from '@/api'
import { getNodeDefinition } from './node-definitions'

const CANVAS_OFFSET = { x: 100, y: 30 }
const RESERVED_NODE_PROPERTIES = new Set([
  'nodeType', 'nodeMeta', 'inputs', 'outputs', 'runStatus', 'label',
  // 仅用于画布呈现与拖拽反馈，绝不持久化到节点 config。
  'connectionViewMode', 'connectionDragKind',
])

/** 将历史存量定义升级为当前节点注册表的正式端口契约。 */
export function migrateWorkflowDefinition(raw: any): WorkflowDefinition {
  if (raw.globalVariables !== undefined) {
    return normalizeLegacyInlineBatches(normalizeFlowEdges(synchronizeDefinitionBindings({
      ...raw,
      nodes: (raw.nodes || []).map((node: any) => upgradeNodeContract(node)),
    } as WorkflowDefinition)))
  }

  return normalizeLegacyInlineBatches(normalizeFlowEdges(synchronizeDefinitionBindings({
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
          cardinality: port.cardinality || (port.valueType === 'array' ? 'collection' : 'single'),
          semanticType: port.semanticType,
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
      collectionBindingMode: edge.collectionBindingMode,
    })),
  } as WorkflowDefinition)))
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
      sourcePortName: '__flow',
      targetNodeId: 'end',
      targetPortName: '__flow',
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
        cardinality: port.cardinality || (port.valueType === 'array' ? 'collection' : 'single'),
        semanticType: port.semanticType,
        value: existing?.value ?? port.defaultValue ?? null,
      }
    }),
    outputs: definition.outputPorts.map(port => ({
      ...port,
      cardinality: port.cardinality || (port.valueType === 'array' ? 'collection' : 'single'),
    })),
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
  return normalizeLegacyInlineBatches(normalizeFlowEdges(synchronizeDefinitionBindings({
    globalVariables,
    nodes: graphData.nodes.map(normalizeCanvasNode),
    edges: graphData.edges.map((edge: any) => ({
      id: edge.id,
      sourceNodeId: edge.sourceNodeId,
      sourcePortName: edge.properties?.sourcePortName || 'output',
      targetNodeId: edge.targetNodeId,
      targetPortName: edge.properties?.targetPortName || 'input',
      collectionBindingMode: edge.properties?.collectionBindingMode,
    })),
  } as WorkflowDefinition)))
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
    if (edge.sourcePortName === '__flow' || edge.targetPortName === '__flow') continue
    const source = nodesById.get(edge.sourceNodeId)
    const target = nodesById.get(edge.targetNodeId)
    const sourcePort = source?.outputs.find(port => port.name === edge.sourcePortName)
    const targetInput = target?.inputs.find(input => input.name === edge.targetPortName)
    const sourceCardinality = sourcePort?.cardinality || (sourcePort?.valueType === 'array' ? 'collection' : 'single')
    const targetCardinality = targetInput?.cardinality || (targetInput?.valueType === 'array' ? 'collection' : 'single')
    const isEachBinding = edge.collectionBindingMode === 'each'
      && sourceCardinality === 'collection' && targetCardinality === 'single'
    if (!sourcePort || !targetInput || targetInput.value != null
      || (!isEachBinding && !isDirectlyCompatible(sourcePort.valueType, targetInput.valueType))) continue
    targetInput.value = { type: 'ref', nodeId: source!.id, outputName: sourcePort.name }
  }
  return { ...definition, nodes }
}

/**
 * 历史画布曾借用 start.trigger / end.result 表示执行顺序。现在统一收敛为正式流程端口，
 * 但保留已经建立的参数引用，避免用户打开既有工作流后丢失数据配置。
 */
function normalizeFlowEdges(definition: WorkflowDefinition): WorkflowDefinition {
  const nodesById = new Map(definition.nodes.map(node => [node.id, node]))
  const edges = (definition.edges || []).map(edge => {
    const sourceType = nodesById.get(edge.sourceNodeId)?.type
    const targetType = nodesById.get(edge.targetNodeId)?.type
    const isLegacyStart = sourceType === 'start' && edge.sourcePortName === 'trigger'
    const isLegacyEnd = targetType === 'end' && edge.targetPortName === 'result'
    return isLegacyStart || isLegacyEnd
      ? { ...edge, sourcePortName: '__flow', targetPortName: '__flow' }
      : edge
  })
  return { ...definition, edges }
}

/**
 * The first batch-node implementation invoked an unrelated child workflow,
 * while the canvas presented it as an inline iterator. A blank legacy batch
 * therefore means the author intended a collection-to-item data binding.
 * Convert only that unambiguous shape; configured child-workflow batches keep
 * their original behaviour.
 */
function normalizeLegacyInlineBatches(definition: WorkflowDefinition): WorkflowDefinition {
  let nodes = definition.nodes
  let edges = definition.edges || []
  for (const batch of definition.nodes.filter(node => node.type === 'batch'
    && !String(node.config?.workflowId || '').trim())) {
    const dataInput = edges.find(edge => edge.targetNodeId === batch.id
      && edge.sourcePortName !== '__flow' && edge.targetPortName !== '__flow')
    const flowInput = edges.find(edge => edge.targetNodeId === batch.id
      && edge.sourcePortName === '__flow' && edge.targetPortName === '__flow')
    const flowOutput = edges.find(edge => edge.sourceNodeId === batch.id
      && edge.sourcePortName === '__flow' && edge.targetPortName === '__flow')
    if (!dataInput || !flowInput || !flowOutput) continue

    const source = nodes.find(node => node.id === dataInput.sourceNodeId)
    const target = nodes.find(node => node.id === flowOutput.targetNodeId)
    const sourcePort = source?.outputs.find(port => port.name === dataInput.sourcePortName)
    const targetInput = target?.inputs.find(input => input.cardinality === 'single'
      && (!sourcePort?.semanticType || !input.semanticType || input.semanticType === sourcePort.semanticType))
    if (!source || !target || !sourcePort || !targetInput) continue

    nodes = nodes.filter(node => node.id !== batch.id)
    edges = edges
      .filter(edge => edge.sourceNodeId !== batch.id && edge.targetNodeId !== batch.id)
      .concat([
        {
          ...dataInput,
          targetNodeId: target.id,
          targetPortName: targetInput.name,
          collectionBindingMode: 'each',
        },
        {
          ...flowInput,
          id: `${batch.id}-inline-flow`,
          targetNodeId: target.id,
          targetPortName: '__flow',
        },
      ])
  }
  return synchronizeDefinitionBindings({ ...definition, nodes, edges })
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
