import type { NodeDefinition } from './types'

export const variablesDefinition: NodeDefinition = {
  type: 'variables',
  meta: {
    title: '变量设置',
    icon: '📝',
    color: '#0ea5e9',
    description: '定义和设置变量，供下游节点引用',
    category: '数据处理',
  },
  inputPorts: [],
  outputPorts: [
    { name: 'vars', label: '变量集合', valueType: 'object', description: '所有已定义变量的集合' },
  ],
  configFields: [
    {
      key: 'vars',
      label: '变量列表',
      type: 'string',
      description: 'JSON 格式，如 {"workspace":"/project/YT","apiKey":"xxx"}',
      placeholder: '{"key": "value"}',
    },
  ],
}
