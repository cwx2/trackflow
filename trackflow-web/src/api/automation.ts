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
  status?: 'draft' | 'published' | 'disabled'
  version?: number
  triggerType?: string
  runtimeEnabled?: boolean
  activatedAt?: string
  activatedBy?: string
}

export interface WorkflowDetailVO extends WorkflowVO {
  definition: string // JSON string of WorkflowDefinition
  projectId?: number
  publishedDefinition?: string
  triggerConfig?: string
  concurrencyMode?: 'queue' | 'skip' | 'parallel'
  maxConcurrent?: number
  actorUserId?: number
  publishedAt?: string
}

export interface CreateWorkflowDTO {
  name: string
  description?: string
  projectId?: number
  definition?: string // 从模板克隆时传入初始画布数据
}

export interface UpdateAutomationDTO {
  name?: string
  description?: string
  definition?: string // JSON string of WorkflowDefinition
  version?: number
  projectId?: number
  triggerType?: string
  triggerConfig?: string
  concurrencyMode?: string
  maxConcurrent?: number
  actorUserId?: number
}

// ====== 新 Schema：变量类型系统 ======

/** 变量数据类型 */
export type ValueType = 'string' | 'number' | 'boolean' | 'array' | 'object' | 'any'

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
  /** 从 object/array 输出中显式读取字段，例如 trigger 的 issueId */
  path?: string
}

/** 模板表达式值 — 混合文本和 {{nodeId.portName}} 变量引用 */
export interface TemplateValue {
  type: 'template'
  template: string     // 模板字符串，如 "审核: {{node1.context}}"
}

/** 输入参数的值：字面值、变量引用、模板表达式、或未填写 */
export type InputValue = LiteralValue | VariableRef | TemplateValue | null

/** 输入参数定义（节点配置时的一个输入槽） */
export interface InputParameter {
  name: string
  /** 画布展示名称，不参与运行时变量解析 */
  label?: string
  valueType: ValueType
  required: boolean
  optional?: boolean
  description?: string
  value: InputValue
}

/** 输出端口声明（该节点执行后会产出什么） */
export interface OutputPort {
  name: string
  /** 画布展示名称，不参与运行时变量解析 */
  label?: string
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
  | 'code'
  | 'http-request'
  | 'sub-workflow'
  | 'role-agent'
  | 'approval'
  | 'trackflow-issue-get'
  | 'trackflow-issue-search'
  | 'trackflow-issue-transition'
  | 'trackflow-issue-comment'

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
  status: 'queued' | 'running' | 'waiting_timer' | 'waiting_event' | 'waiting_approval'
    | 'retrying' | 'paused' | 'success' | 'failed' | 'cancelled'
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
  status: 'pending' | 'ready' | 'running' | 'waiting' | 'retrying'
    | 'success' | 'failed' | 'skipped' | 'cancelled'
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

// ====== 模板 VO ======

export interface WorkflowTemplateVO {
  id: string
  name: string
  description?: string
  category?: string
  icon?: string
  definition: string
  sortOrder?: number
  isBuiltin?: boolean
  createdBy?: string
  createdAt?: string
}

export interface CreateTemplateFromWorkflowDTO {
  workflowId: string
  name: string
  description?: string
  category?: string
  icon?: string
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

  publish(id: string) {
    return request.post<any, R<WorkflowDetailVO>>(`/automation/workflows/${id}/publish`)
  },

  start(id: string) {
    return request.post<any, R<WorkflowDetailVO>>(`/automation/workflows/${id}/start`)
  },

  stop(id: string) {
    return request.post<any, R<WorkflowDetailVO>>(`/automation/workflows/${id}/stop`)
  },

  disable(id: string) {
    return request.post<any, R<WorkflowDetailVO>>(`/automation/workflows/${id}/disable`)
  },

  listRoles() {
    return request.get<any, R<AutomationRoleProfile[]>>('/automation/roles')
  },

  createRole(data: AutomationRoleProfileDTO) {
    return request.post<any, R<AutomationRoleProfile>>('/automation/roles', data)
  },

  updateRole(id: string, data: AutomationRoleProfileDTO) {
    return request.put<any, R<AutomationRoleProfile>>(`/automation/roles/${id}`, data)
  },

  deleteRole(id: string) {
    return request.delete<any, R<void>>(`/automation/roles/${id}`)
  },

  listApprovals() {
    return request.get<any, R<AutomationApproval[]>>('/automation/approvals')
  },

  decideApproval(id: string, decision: 'approved' | 'rejected', comment?: string) {
    return request.post<any, R<AutomationApproval>>(`/automation/approvals/${id}/decision`, { decision, comment })
  },

  listWorkItems(state?: string) {
    return request.get<any, R<AutomationWorkItem[]>>('/automation/work-items', { params: { state, limit: 200 } })
  },

  retryWorkItem(id: string) {
    return request.post<any, R<void>>(`/automation/work-items/${id}/retry`)
  },

  cancelWorkItem(id: string) {
    return request.post<any, R<void>>(`/automation/work-items/${id}/cancel`)
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
  },

  /** 取消正在运行或等待中的工作流 */
  cancelExecution(executionId: string) {
    return request.post<any, R<void>>(`/automation/executions/${executionId}/cancel`)
  },

  // ====== 模板相关 ======

  /** 获取所有工作流模板（内置 + 当前用户自定义） */
  listTemplates() {
    return request.get<any, R<WorkflowTemplateVO[]>>('/automation/templates')
  },

  /** 将已有工作流保存为自定义模板 */
  saveAsTemplate(data: CreateTemplateFromWorkflowDTO) {
    return request.post<any, R<WorkflowTemplateVO>>('/automation/templates', data)
  },

  /** 从模板克隆工作流 */
  cloneFromTemplate(templateId: string) {
    return request.post<any, R<WorkflowDetailVO>>(`/automation/templates/${templateId}/clone`)
  },

  /** 删除模板（内置模板返回 403） */
  deleteTemplate(templateId: string) {
    return request.delete<any, R<void>>(`/automation/templates/${templateId}`)
  }
}

export interface AutomationRoleProfile {
  id: string
  name: string
  description?: string
  providerType: 'cli' | 'http' | 'openai_compatible'
  model?: string
  enabled: boolean
  systemPrompt?: string
  toolPolicy?: string
  outputSchema?: string
  workspacePolicy?: string
}

export interface AutomationRoleProfileDTO {
  name: string
  description?: string
  providerType: 'cli' | 'http' | 'openai_compatible'
  model?: string
  systemPrompt?: string
  toolPolicy?: string
  outputSchema?: string
  workspacePolicy?: string
  enabled?: boolean
}

export interface AutomationApproval {
  id: string
  executionId: string
  nodeId: string
  title: string
  description?: string
  riskLevel: 'low' | 'medium' | 'high' | 'critical'
  requestPayload?: string
  status: 'pending' | 'approved' | 'rejected' | 'expired' | 'cancelled'
  createdAt: string
  expiresAt?: string
}

export interface AutomationWorkItem {
  id: string
  automationId: string
  issueId?: string
  correlationId: string
  state: 'queued' | 'leased' | 'running' | 'completed' | 'failed' | 'dead_letter' | 'cancelled'
  attempt: number
  maxAttempts: number
  executionId?: string
  lastError?: string
  createdAt: string
}
