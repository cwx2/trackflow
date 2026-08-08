import type { NodeDefinition } from './types'

export const variablesDefinition: NodeDefinition = {
  type: 'variables',
  meta: {
    title: '变量设置',
    icon: '📝',
    color: '#0ea5e9',
    description: '定义和设置变量，每个变量独立输出供下游节点引用',
    category: '数据处理',
  },
  inputPorts: [],
  outputPorts: [
    { name: 'vars', label: '变量集合', valueType: 'object', description: '所有已定义变量的集合（向后兼容）' },
  ],
  configFields: [
    {
      key: 'vars',
      label: '变量列表（旧格式）',
      type: 'string',
      description: 'JSON 格式（向后兼容），新增变量请使用输出端口的 [+] 按钮',
      placeholder: '{"key": "value"}',
    },
  ],
}
