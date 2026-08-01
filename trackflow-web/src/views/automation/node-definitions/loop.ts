import type { NodeDefinition } from './types'

export const loopDefinition: NodeDefinition = {
  type: 'loop',
  meta: {
    title: '重试循环',
    icon: '🔄',
    color: '#10b981',
    description: '循环重试，直到满足退出条件',
    category: '控制流',
  },
  inputPorts: [
    { name: 'input', valueType: 'string', required: false, description: '循环输入值' },
  ],
  outputPorts: [
    { name: 'output',    valueType: 'string', description: '循环完成后的最终输出' },
    { name: 'iteration', valueType: 'number', description: '实际执行的循环次数' },
  ],
  configFields: [
    { key: 'maxRetries',   label: '最大重试次数', type: 'number', defaultValue: 3 },
    { key: 'interval',     label: '重试间隔（秒）', type: 'number', defaultValue: 5 },
    {
      key: 'exitOperator',
      label: '退出条件',
      type: 'select',
      defaultValue: 'contains',
      options: [
        { label: '输出包含',   value: 'contains' },
        { label: '输出不包含', value: 'not_contains' },
        { label: 'exitCode=0', value: 'exit_zero' },
      ],
    },
    { key: 'exitValue', label: '退出判断值', type: 'string', placeholder: 'PASS', defaultValue: 'PASS' },
  ],
}
