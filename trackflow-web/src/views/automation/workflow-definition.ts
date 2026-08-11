import type { GlobalVariable, NodeType, WorkflowDefinition, WorkflowNode } from '@/api'
import { getNodeDefinition } from './node-definitions'

const CANVAS_OFFSET = { x: 100, y: 30 }
const RESERVED_NODE_PROPERTIES = new Set(['nodeType', 'nodeMeta', 'inputs', 'outputs', 'runStatus', 'label'])

/** 将历史存量定义升级为当前节点注册表的正式端口契约。 */
export function migrateWorkflowDefinition(raw: any): WorkflowDefinition {
  if (raw.globalVariables !== undefined) {
    return {
      ...raw,
      nodes: (raw.nodes || []).map((node: any) => upgradeNodeContract(node)),
    } as WorkflowDefinition
  }

  return {
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
  }
}

/** 使用节点注册表重建端口快照，杜绝模板把过期端口重新写回服务端。 */
export function upgradeNodeContract(node: any) {
  const definition = getNodeDefinition(node.type)
  if (!definition) return node

  const existingInputs = new Map<string, any>((node.inputs || []).map((input: any) => [input.name, input]))
  return {
    ...node,
    nodeMeta: { ...definition.meta, ...(node.nodeMeta || {}), category: definition.meta.category },
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

/** 统一从画布生成定义，保存和试运行使用同一份序列化规则。 */
export function buildWorkflowDefinition(
  globalVariables: Record<string, GlobalVariable>,
  graphData: { nodes: any[]; edges: any[] },
): WorkflowDefinition {
  return {
    globalVariables,
    nodes: graphData.nodes.map(normalizeCanvasNode),
    edges: graphData.edges.map((edge: any) => ({
      id: edge.id,
      sourceNodeId: edge.sourceNodeId,
      sourcePortName: edge.properties?.sourcePortName || 'output',
      targetNodeId: edge.targetNodeId,
      targetPortName: edge.properties?.targetPortName || 'input',
    })),
  }
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
