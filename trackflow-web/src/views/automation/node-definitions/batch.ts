import type { NodeDefinition } from './types'

/**
 * Explicit child-workflow fan-out. This is deliberately different from an
 * `each` data binding: collection-to-single connections already execute the
 * downstream node once per item and do not need this node.
 */
export const batchDefinition: NodeDefinition = {
  type: 'batch',
  meta: {
    title: '批量调用子流程',
    icon: '↻',
    color: '#7c3aed',
    description: '将列表逐项交给已发布子流程，用于触发可复用的独立处理流程',
    category: '控制流',
  },
  inputPorts: [
    {
      name: 'items', label: '待处理列表', valueType: 'array', required: true,
      description: '集合中的每一项会独立触发一次子工作流',
      cardinality: 'collection',
    },
  ],
  outputPorts: [
    { name: 'successItems', label: '成功项', valueType: 'array', description: '已成功处理的项与子流程输出', cardinality: 'collection' },
    { name: 'failedItems', label: '失败项', valueType: 'array', description: '处理失败的项与错误信息', cardinality: 'collection' },
    { name: 'skippedItems', label: '跳过项', valueType: 'array', description: '受数量上限或失败策略影响而跳过的项', cardinality: 'collection' },
    { name: 'summary', label: '执行汇总', valueType: 'object', description: '总数、成功数、失败数和跳过数', cardinality: 'single' },
  ],
  configFields: [
    { key: 'workflowId', label: '处理子工作流', type: 'string', defaultValue: '', placeholder: '在右侧配置中选择已发布的工作流' },
    { key: 'itemInputKey', label: '子流程输入字段', type: 'string', defaultValue: 'item', placeholder: 'item' },
    { key: 'maxItems', label: '单次最多处理', type: 'number', defaultValue: 100, description: '范围 1-1000，超出的项目将记录为跳过' },
    {
      key: 'onItemFailure', label: '单项失败策略', type: 'select', defaultValue: 'continue',
      options: [
        { label: '继续处理其余项目', value: 'continue' },
        { label: '立即停止并标记其余项目为跳过', value: 'stop' },
      ],
    },
  ],
}
