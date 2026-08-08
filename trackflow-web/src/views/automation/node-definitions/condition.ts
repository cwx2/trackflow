import type { NodeDefinition } from './types'

export const conditionDefinition: NodeDefinition = {
  type: 'condition',
  meta: {
    title: '条件判断',
    icon: '🔀',
    color: '#f59e0b',
    description: '根据条件表达式决定分支流转',
    category: '控制流',
  },
  inputPorts: [
    { name: 'value', label: '判断值', valueType: 'any', required: true, description: '待判断的值' },
  ],
  outputPorts: [
    { name: 'true',  label: '成立',   valueType: 'boolean', description: '条件成立时流转此路径' },
    { name: 'false', label: '不成立', valueType: 'boolean', description: '条件不成立时流转此路径' },
  ],
  configFields: [
    {
      key: 'operator',
      label: '判断条件',
      type: 'select',
      defaultValue: 'contains',
      options: [
        { label: '包含',   value: 'contains' },
        { label: '不包含', value: 'not_contains' },
        { label: '等于',   value: 'equals' },
        { label: '不等于', value: 'not_equals' },
        { label: '为空',   value: 'is_empty' },
        { label: '不为空', value: 'is_not_empty' },
      ],
    },
    { key: 'compareValue', label: '比较值', type: 'string', placeholder: '要比较的值' },
  ],
}
