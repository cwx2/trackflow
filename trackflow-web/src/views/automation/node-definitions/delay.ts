import type { NodeDefinition } from './types'

export const delayDefinition: NodeDefinition = {
  type: 'delay',
  meta: {
    title: '延时等待',
    icon: '⏱',
    color: '#64748b',
    description: '暂停指定时长后继续执行',
    category: '控制流',
  },
  inputPorts: [
    { name: 'duration', label: '等待秒数', valueType: 'number', required: false, description: '等待秒数，覆盖配置项' },
  ],
  outputPorts: [
    { name: 'done', label: '完成', valueType: 'boolean', description: '等待完成，值恒为 true' },
  ],
  configFields: [
    { key: 'seconds', label: '等待秒数', type: 'number', defaultValue: 5 },
  ],
}
