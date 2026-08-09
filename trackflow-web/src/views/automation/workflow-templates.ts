/**
 * 内置工作流模板。
 *
 * 模板不是手写的端口副本：节点快照从 node-definitions 注册表生成，避免节点升级后
 * 模板仍携带过期端口。模板只保留真实可执行的配置和值绑定。
 */
import type { InputValue, NodeType, WorkflowDefinition, WorkflowNode, WorkflowTemplateVO } from '@/api/automation'
import { getNodeDefinition } from './node-definitions'
import { validateExecutableWorkflow } from './workflow-validator'

type Position = { x: number; y: number }

function node(
  id: string,
  type: NodeType,
  position: Position,
  values: Record<string, InputValue> = {},
  config: Record<string, unknown> = {},
): WorkflowNode {
  const definition = getNodeDefinition(type)
  if (!definition) throw new Error(`内置模板引用了未注册节点：${type}`)

  return {
    id,
    type,
    position,
    nodeMeta: { ...definition.meta },
    inputs: definition.inputPorts.map(port => ({
      name: port.name,
      label: port.label,
      valueType: port.valueType,
      required: port.required,
      description: port.description,
      optional: port.optional,
      value: values[port.name] ?? port.defaultValue ?? null,
    })),
    outputs: definition.outputPorts.map(port => ({ ...port })),
    config,
  }
}

function definition(value: WorkflowDefinition): string {
  return JSON.stringify(value)
}

/** 无外部依赖；空触发参数也能完整试运行。 */
const PENDING_WORK_CHECK: WorkflowDefinition = {
  globalVariables: {},
  nodes: [
    node('start', 'start', { x: 220, y: 180 }),
    node('search', 'trackflow-issue-search', { x: 520, y: 180 }, {
      // projectId 不传时按当前执行身份可见范围检索，传入时则精确限定项目。
      projectId: { type: 'ref', nodeId: 'start', outputName: 'trigger', path: 'projectId' },
    }, { limit: 20 }),
    node('has-work', 'condition', { x: 820, y: 180 }, {
      value: { type: 'ref', nodeId: 'search', outputName: 'hasWork' },
    }, { operator: 'equals', compareValue: 'true' }),
    node('work-found', 'end', { x: 1120, y: 100 }, {
      result: { type: 'ref', nodeId: 'has-work', outputName: 'true' },
    }),
    node('no-work', 'end', { x: 1120, y: 260 }, {
      result: { type: 'ref', nodeId: 'has-work', outputName: 'false' },
    }),
  ],
  edges: [
    { id: 'start-search', sourceNodeId: 'start', sourcePortName: 'trigger', targetNodeId: 'search', targetPortName: 'projectId' },
    { id: 'search-condition', sourceNodeId: 'search', sourcePortName: 'hasWork', targetNodeId: 'has-work', targetPortName: 'value' },
    { id: 'condition-work', sourceNodeId: 'has-work', sourcePortName: 'true', targetNodeId: 'work-found', targetPortName: 'result' },
    { id: 'condition-empty', sourceNodeId: 'has-work', sourcePortName: 'false', targetNodeId: 'no-work', targetPortName: 'result' },
  ],
}

/** 审批节点的两个分支都有明确终点，可直接用于验证暂停、审批与恢复链路。 */
const APPROVAL_CHECK: WorkflowDefinition = {
  globalVariables: {},
  nodes: [
    node('start', 'start', { x: 220, y: 180 }),
    node('approval', 'approval', { x: 560, y: 180 }, {
      title: { type: 'literal', value: '确认执行自动化操作' },
      description: { type: 'literal', value: '请确认本次自动化请求的输入和影响范围。' },
      payload: { type: 'ref', nodeId: 'start', outputName: 'trigger' },
    }, { riskLevel: 'medium', expiryHours: 24 }),
    node('approved-end', 'end', { x: 900, y: 100 }, {
      result: { type: 'ref', nodeId: 'approval', outputName: 'approved' },
    }),
    node('rejected-end', 'end', { x: 900, y: 260 }, {
      result: { type: 'ref', nodeId: 'approval', outputName: 'rejected' },
    }),
  ],
  edges: [
    { id: 'start-approval', sourceNodeId: 'start', sourcePortName: 'trigger', targetNodeId: 'approval', targetPortName: 'payload' },
    { id: 'approval-approved', sourceNodeId: 'approval', sourcePortName: 'approved', targetNodeId: 'approved-end', targetPortName: 'result' },
    { id: 'approval-rejected', sourceNodeId: 'approval', sourcePortName: 'rejected', targetNodeId: 'rejected-end', targetPortName: 'result' },
  ],
}

/** 固定五秒的延时流程，验证暂停、恢复和终止链路，不依赖伪造的 HTTP 服务。 */
const DELAY_CHECK: WorkflowDefinition = {
  globalVariables: {},
  nodes: [
    node('start', 'start', { x: 220, y: 180 }),
    node('delay', 'delay', { x: 560, y: 180 }, {
      duration: { type: 'literal', value: 5 },
    }),
    node('end', 'end', { x: 900, y: 180 }, {
      result: { type: 'ref', nodeId: 'delay', outputName: 'done' },
    }),
  ],
  edges: [
    { id: 'start-delay', sourceNodeId: 'start', sourcePortName: 'trigger', targetNodeId: 'delay', targetPortName: 'duration' },
    { id: 'delay-end', sourceNodeId: 'delay', sourcePortName: 'done', targetNodeId: 'end', targetPortName: 'result' },
  ],
}

export const BUILTIN_WORKFLOW_TEMPLATES: WorkflowTemplateVO[] = [
  {
    id: 'builtin-pending-work-check',
    name: '待办工单检测',
    description: '在当前执行身份可访问的范围内检查待办工单，并输出是否存在待办。',
    category: 'issue_management',
    icon: '🔎',
    isBuiltin: true,
    definition: definition(PENDING_WORK_CHECK),
  },
  {
    id: 'builtin-approval-check',
    name: '人工审批流程',
    description: '演示审批挂起、批准/拒绝分支与恢复执行，适合验证审批链路。',
    category: 'general',
    icon: '🛡️',
    isBuiltin: true,
    definition: definition(APPROVAL_CHECK),
  },
  {
    id: 'builtin-delay-check',
    name: '延时执行',
    description: '等待五秒后结束，用于验证延时任务的持久化恢复链路。',
    category: 'general',
    icon: '⏱',
    isBuiltin: true,
    definition: definition(DELAY_CHECK),
  },
]

// 模板和用户工作流共用同一校验器；任何人修改模板端口后，应用会立即暴露错误，
// 不再把不可执行的模板交给用户创建副本。
for (const template of BUILTIN_WORKFLOW_TEMPLATES) {
  const validationError = validateExecutableWorkflow(JSON.parse(template.definition) as WorkflowDefinition)
  if (validationError) throw new Error(`内置工作流模板无效：${template.name}：${validationError}`)
}
