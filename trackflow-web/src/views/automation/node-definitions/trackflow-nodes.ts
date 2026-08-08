import type { NodeDefinition } from './types'

export const issueGetDefinition: NodeDefinition = {
  type: 'trackflow-issue-get',
  meta: { title: '获取需求', icon: '📋', color: '#2563eb', description: '按 ID 或编号读取需求（含评论、标签、自定义字段）', category: 'TrackFlow' },
  inputPorts: [{ name: 'issue', label: '工单 ID / 编号', valueType: 'string', required: true }],
  outputPorts: [{ name: 'issue', label: '工单（完整）', valueType: 'object' }], configFields: [],
}

export const issueSearchDefinition: NodeDefinition = {
  type: 'trackflow-issue-search',
  meta: { title: '查找待办需求', icon: '🔎', color: '#0ea5e9', description: '按项目、状态、优先级、类型和关键词筛选', category: 'TrackFlow' },
  inputPorts: [
    { name: 'projectId', label: '项目 ID', valueType: 'number', required: false },
    { name: 'statusIds', label: '状态 ID', valueType: 'string', required: false },
    { name: 'priority', label: '优先级', valueType: 'string', required: false },
    { name: 'issueType', label: '工单类型', valueType: 'string', required: false },
    { name: 'tagIds', label: '标签 ID', valueType: 'string', required: false },
    { name: 'keyword', label: '关键词', valueType: 'string', required: false, optional: true },
    { name: 'assignedToMe', label: '仅分配给执行身份', valueType: 'boolean', required: false, optional: true },
    { name: 'sort', label: '排序', valueType: 'string', required: false, optional: true },
    { name: 'limit', label: '数量上限', valueType: 'number', required: false, optional: true, defaultValue: { type: 'literal', value: 20 } },
  ],
  outputPorts: [
    { name: 'issues', label: '需求列表', valueType: 'array' },
    { name: 'count', label: '数量', valueType: 'number' },
    { name: 'hasWork', label: '有待办', valueType: 'boolean' },
  ], configFields: [],
}

export const issueContextDefinition: NodeDefinition = {
  type: 'trackflow-issue-context',
  meta: { title: '准备工单上下文', icon: '📝', color: '#8b5cf6', description: '将工单聚合为 Markdown 文本供 Agent 读取', category: 'TrackFlow' },
  inputPorts: [
    { name: 'issue', label: '工单对象', valueType: 'object', required: true },
    { name: 'includeComments', label: '包含评论', valueType: 'boolean', required: false },
    { name: 'includeCustomFields', label: '包含自定义字段', valueType: 'boolean', required: false },
    { name: 'maxLength', label: '最大字符数', valueType: 'number', required: false, defaultValue: { type: 'literal', value: 4000 } },
  ],
  outputPorts: [
    { name: 'context', label: 'Markdown 上下文', valueType: 'string' },
    { name: 'summary', label: '简短摘要', valueType: 'string' },
  ], configFields: [],
}

export const issueTransitionDefinition: NodeDefinition = {
  type: 'trackflow-issue-transition',
  meta: { title: '变更需求状态', icon: '🔁', color: '#f59e0b', description: '按状态机安全流转需求', category: 'TrackFlow' },
  inputPorts: [
    { name: 'issueId', label: '工单 ID', valueType: 'number', required: true },
    { name: 'statusId', label: '目标状态 ID', valueType: 'number', required: true },
    { name: 'comment', label: '流转说明', valueType: 'string', required: false, optional: true },
    { name: 'version', label: '工单版本', valueType: 'number', required: false, optional: true },
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

export const issueUpdateDefinition: NodeDefinition = {
  type: 'trackflow-issue-update',
  meta: { title: '更新工单字段', icon: '✏️', color: '#ec4899', description: '更新优先级、负责人、标签或自定义字段', category: 'TrackFlow' },
  inputPorts: [
    { name: 'issueId', label: '工单 ID', valueType: 'number', required: true },
    { name: 'priority', label: '优先级', valueType: 'string', required: false },
    { name: 'assigneeId', label: '负责人 ID', valueType: 'number', required: false },
    { name: 'tagIds', label: '标签 ID', valueType: 'string', required: false },
    { name: 'customFields', label: '自定义字段 JSON', valueType: 'string', required: false },
  ],
  outputPorts: [
    { name: 'issue', label: '更新后工单', valueType: 'object' },
    { name: 'success', label: '成功', valueType: 'boolean' },
  ], configFields: [],
}
