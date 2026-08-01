import request from './request'
import type { R, PageResult } from './types'

// ====== 工作流 VO / DTO ======

export interface WorkflowVO {
  id: string
  name: string
  description?: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface WorkflowDetailVO extends WorkflowVO {
  definition: string // JSON string of WorkflowDefinition
}

export interface CreateWorkflowDTO {
  name: string
  description?: string
}

export interface UpdateAutomationDTO {
  name?: string
  description?: string
  definition?: string // JSON string of WorkflowDefinition
}

// ====== 新 Schema：变量类型系统 ======

/** 变量数据类型 */
export type ValueType = 'string' | 'number' | 'boolean' | 'array' | 'object'

/** 字面值（直接填写的固定值） */
export interface LiteralValue {
  type: 'literal'
  value: string | number | boolean
}

/** 变量引用（来自某个节点的某个输出端口） */
export interface VariableRef {
  type: 'ref'
  nodeId: string       // 来源节点 ID
  outputName: string   // 来源输出端口名称
}

/** 输入参数的值：字面值、变量引用、或未填写 */
export type InputValue = LiteralValue | VariableRef | null

/** 输入参数定义（节点配置时的一个输入槽） */
export interface InputParameter {
  name: string
  valueType: ValueType
  required: boolean
  description?: string
  value: InputValue
}

/** 输出端口声明（该节点执行后会产出什么） */
export interface OutputPort {
  name: string
  valueType: ValueType
  description?: string
}

/** 节点元数据（展示信息） */
export interface NodeMeta {
  title: string
  icon: string
  description: string
  color: string
}

// ====== 新 Schema：节点和连线 ======

/** 所有支持的节点类型 */
export type NodeType =
  | 'start'
  | 'end'
  | 'cli-agent'
  | 'variables'
  | 'condition'
  | 'loop'
  | 'file-input'
  | 'delay'

/** 工作流节点（新结构，替代旧的 data: Record<string,any>） */
export interface WorkflowNode {
  id: string
  type: NodeType
  position: { x: number; y: number }
  /** 节点展示信息 */
  nodeMeta: NodeMeta
  /** 结构化输入参数列表 */
  inputs: InputParameter[]
  /** 声明会输出的端口列表（前端变量选择器用） */
  outputs: OutputPort[]
  /** 节点特有配置（不参与数据流，只是执行参数，如 timeout/command/args） */
  config: Record<string, unknown>
}

/** 有向边（端口到端口连线） */
export interface WorkflowEdge {
  id: string
  sourceNodeId: string
  sourcePortName: string   // 从哪个输出端口出发
  targetNodeId: string
  targetPortName: string   // 连到哪个输入端口
}

/** 全局变量定义 */
export interface GlobalVariable {
  type: ValueType
  defaultValue?: unknown
}

/** 工作流完整定义（新结构） */
export interface WorkflowDefinition {
  /** 全局变量（整个工作流可见） */
  globalVariables: Record<string, GlobalVariable>
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
}

// ====== 执行记录 VO ======

export interface ExecutionVO {
  id: string
  automationId: string
  status: 'running' | 'success' | 'failed' | 'cancelled'
  startedAt: string
  finishedAt?: string
  durationMs?: number
  createdBy?: string
}

export interface NodeExecutionVO {
  id: string
  nodeId: string
  nodeType: string
  nodeName?: string
  status: 'running' | 'success' | 'failed' | 'skipped'
  input?: unknown
  output?: unknown
  errorInfo?: string
  startedAt: string
  finishedAt?: string
  durationMs?: number
}

export interface ExecutionDetailVO extends ExecutionVO {
  input?: unknown
  output?: unknown
  errorMessage?: string
  nodeExecutions: NodeExecutionVO[]
}

// ====== API 函数 ======

export const automationApi = {
  /**
   * 获取工作流列表
   */
  list() {
    return request.get<any, R<WorkflowVO[]>>('/automation/workflows')
  },

  /**
   * 获取工作流详情
   */
  getById(id: string) {
    return request.get<any, R<WorkflowDetailVO>>(`/automation/workflows/${id}`)
  },

  /**
   * 创建工作流
   */
  create(data: CreateWorkflowDTO) {
    return request.post<any, R<WorkflowDetailVO>>('/automation/workflows', data)
  },

  /**
   * 更新工作流（包括保存画布）
   */
  update(id: string, data: UpdateAutomationDTO) {
    return request.put<any, R<WorkflowDetailVO>>(`/automation/workflows/${id}`, data)
  },

  /**
   * 删除工作流
   */
  delete(id: string) {
    return request.delete<any, R<void>>(`/automation/workflows/${id}`)
  },

  /**
   * 触发执行工作流，返回 executionId
   */
  execute(id: string, inputs: Record<string, unknown>) {
    return request.post<any, R<{ executionId: string }>>(`/automation/workflows/${id}/execute`, { inputs })
  },

  /**
   * 查询执行历史列表
   */
  listExecutions(id: string, params?: { page?: number; pageSize?: number }) {
    return request.get<any, R<PageResult<ExecutionVO>>>(`/automation/workflows/${id}/executions`, { params })
  },

  /**
   * 查询执行详情（含节点执行记录）
   */
  getExecution(executionId: string) {
    return request.get<any, R<ExecutionDetailVO>>(`/automation/executions/${executionId}`)
  }
}
