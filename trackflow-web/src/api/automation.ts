import request from './request'
import type { R } from './types'

// ====== 工作流类型定义 ======

export interface WorkflowVO {
  id: string
  name: string
  description?: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface WorkflowDetailVO extends WorkflowVO {
  definition: string // JSON string
}

export interface CreateWorkflowDTO {
  name: string
  description?: string
}

export interface UpdateAutomationDTO {
  name?: string
  description?: string
  definition?: string // JSON string
}

// ====== 工作流画布数据结构（前端解析 definition） ======

export interface WorkflowNode {
  id: string
  type: 'cli-agent' | 'variables' | 'condition' | 'loop' | 'file-input' | 'delay'
  label: string
  position: { x: number; y: number }
  data: Record<string, unknown>
}

export interface WorkflowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
}

export interface WorkflowDefinition {
  variables: Record<string, string>
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
}

// CLI Agent 节点配置
export interface CliAgentNodeData {
  command: string // kiro-cli / codex / claude / custom
  args: string // 固定参数
  prompt_template: string // 支持 {变量名} 占位符
  model?: string // claude-opus-4.5 / claude-sonnet-4.6
  timeout: number // 秒
  work_dir: string // 工作目录
  output_var: string // 输出变量名
}

// 变量设置节点配置
export interface VariablesNodeData {
  vars: Array<{ key: string; value: string }>
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
  }
}
