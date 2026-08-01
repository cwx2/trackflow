import type { NodeDefinition } from './types'

export const endDefinition: NodeDefinition = {
  type: 'end',
  meta: {
    title: '结束',
    icon: '⏹',
    color: '#ef4444',
    description: '工作流的终点，收集最终结果',
    category: '特殊节点',
  },
  inputPorts: [
    { name: 'result', valueType: 'object', required: false, description: '工作流最终输出' },
  ],
  outputPorts: [],  // 结束节点无输出
  configFields: [],
}
