import type { NodeDefinition } from './types'

export const approvalDefinition: NodeDefinition = {
  type: 'approval',
  meta: { title: '人工审批', icon: '🛡️', color: '#dc2626', description: '高风险操作前暂停并等待审批', category: '控制流' },
  inputPorts: [
    { name: 'title', label: '审批标题', valueType: 'string', required: true },
    { name: 'description', label: '风险说明', valueType: 'string', required: false },
    { name: 'payload', label: '审批数据', valueType: 'object', required: false },
  ],
  outputPorts: [
    { name: 'approved', label: '批准', valueType: 'boolean' },
    { name: 'rejected', label: '拒绝', valueType: 'boolean' },
    { name: 'comment', label: '审批意见', valueType: 'string' },
  ],
  configFields: [
    { key: 'riskLevel', label: '风险等级', type: 'select', defaultValue: 'medium', options: [
      { label: '低', value: 'low' }, { label: '中', value: 'medium' },
      { label: '高', value: 'high' }, { label: '关键', value: 'critical' },
    ] },
    { key: 'expiryHours', label: '审批有效期（小时）', type: 'number', defaultValue: 24 },
  ],
}
