import type { NodeDefinition } from './types'

export const subWorkflowDefinition: NodeDefinition = {
  type: 'sub-workflow',
  meta: {
    title: '子工作流',
    icon: '🔗',
    color: '#d97706',
    description: '调用另一个工作流作为子流程，实现工作流复用',
    category: '业务逻辑',
  },
  inputPorts: [
    { name: 'input', label: '输入参数', valueType: 'object', required: false, description: '传递给子工作流的参数' },
  ],
  outputPorts: [
    { name: 'output', label: '输出结果', valueType: 'object', description: '子工作流的输出结果' },
    { name: 'status', label: '执行状态', valueType: 'string', description: 'success 或 failed' },
  ],
  configFields: [
    {
      key: 'workflowId',
      label: '选择工作流',
      type: 'string',
      defaultValue: '',
      placeholder: '输入工作流 ID 或在配置面板选择',
    },
  ],
}
