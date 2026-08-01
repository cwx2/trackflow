import type { NodeDefinition } from './types'

export const issueGetDefinition: NodeDefinition = {
  type: 'trackflow-issue-get',
  meta: { title: '获取需求', icon: '📋', color: '#2563eb', description: '按 ID 或编号读取需求', category: 'TrackFlow' },
  inputPorts: [{ name: 'issue', label: '工单 ID / 编号', valueType: 'string', required: true }],
  outputPorts: [{ name: 'issue', label: '工单', valueType: 'object' }], configFields: [],
}

export const issueSearchDefinition: NodeDefinition = {
  type: 'trackflow-issue-search',
  meta: { title: '查找待办需求', icon: '🔎', color: '#0ea5e9', description: '按项目、状态和关键词筛选', category: 'TrackFlow' },
  inputPorts: [
    { name: 'projectId', label: '项目 ID', valueType: 'number', required: false },
    { name: 'statusIds', label: '状态 ID', valueType: 'string', required: false },
    { name: 'keyword', label: '关键词', valueType: 'string', required: false },
    { name: 'assignedToMe', label: '仅分配给执行身份', valueType: 'boolean', required: false },
    { name: 'limit', label: '数量上限', valueType: 'number', required: false, defaultValue: { type: 'literal', value: 20 } },
  ],
  outputPorts: [
    { name: 'issues', label: '需求列表', valueType: 'array' },
    { name: 'count', label: '数量', valueType: 'number' },
    { name: 'hasWork', label: '有待办', valueType: 'boolean' },
  ], configFields: [],
}

export const issueTransitionDefinition: NodeDefinition = {
  type: 'trackflow-issue-transition',
  meta: { title: '变更需求状态', icon: '🔁', color: '#f59e0b', description: '按状态机安全流转需求', category: 'TrackFlow' },
  inputPorts: [
    { name: 'issueId', label: '工单 ID', valueType: 'number', required: true },
    { name: 'statusId', label: '目标状态 ID', valueType: 'number', required: true },
    { name: 'comment', label: '流转说明', valueType: 'string', required: false },
    { name: 'version', label: '工单版本', valueType: 'number', required: false },
  ],
  outputPorts: [
    { name: 'issue', label: '更新后工单', valueType: 'object' },
    { name: 'success', label: '成功', valueType: 'boolean' },
  ], configFields: [],
}

export const issueCommentDefinition: NodeDefinition = {
  type: 'trackflow-issue-comment',
  meta: { title: '记录处理结果', icon: '💬', color: '#14b8a6', description: '向需求写入计划、进度或测试结果', category: 'TrackFlow' },
  inputPorts: [
    { name: 'issueId', label: '工单 ID', valueType: 'number', required: true },
    { name: 'content', label: '评论内容', valueType: 'string', required: true },
  ],
  outputPorts: [{ name: 'comment', label: '评论', valueType: 'object' }], configFields: [],
}
