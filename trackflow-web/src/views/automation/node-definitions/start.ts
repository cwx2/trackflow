import type { NodeDefinition } from './types'

export const startDefinition: NodeDefinition = {
  type: 'start',
  meta: {
    title: '开始',
    icon: '▶',
    color: '#10b981',
    description: '工作流的起点，定义触发参数',
    category: '特殊节点',
  },
  inputPorts: [],
  outputPorts: [
    { name: 'trigger', label: '触发参数', valueType: 'object', description: '触发参数（包含所有输入字段）' },
  ],
  configFields: [
    {
      key: 'triggerFields',
      label: '触发参数',
      type: 'string',
      description: '逗号分隔的参数名，如 keyword,projectId',
      placeholder: 'keyword,projectId',
    },
  ],
}
