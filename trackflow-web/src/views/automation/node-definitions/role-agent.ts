import type { NodeDefinition } from './types'

export const roleAgentDefinition: NodeDefinition = {
  type: 'role-agent',
  meta: { title: '角色 Agent', icon: '🧠', color: '#7c3aed', description: '按角色策略执行开发、测试或分析任务', category: 'Agent' },
  inputPorts: [
    { name: 'roleId', label: '角色 ID', valueType: 'number', required: true },
    { name: 'task', label: '任务', valueType: 'string', required: true },
    { name: 'context', label: '上下文', valueType: 'object', required: false },
    { name: 'workDir', label: '工作目录', valueType: 'string', required: false },
  ],
  outputPorts: [
    { name: 'output', label: '原始输出', valueType: 'string' },
    { name: 'structuredOutput', label: '结构化输出', valueType: 'object' },
    { name: 'success', label: '成功', valueType: 'boolean' },
  ],
  configFields: [],
}
