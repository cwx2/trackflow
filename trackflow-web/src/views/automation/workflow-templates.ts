/**
 * 内置工作流模板
 *
 * 所有模板在此文件硬编码管理，通过 git 版本控制。
 * 节点结构必须与 node-definitions/ 中的端口定义保持一致。
 *
 * 注意：修改此文件后同步更新后端 BuiltinWorkflowTemplates.java
 */

export interface WorkflowTemplateVO {
  id: string
  name: string
  description?: string
  category?: string
  icon?: string
  isBuiltin: boolean
  definition: string
}

// ─── 模板1：定时巡检并 AI 处理待办工单 ───────────────────────────────────────

const TEMPLATE_AI_PATROL = {
  globalVariables: {},
  nodes: [
    {
      id: 'start-1', type: 'start',
      position: { x: 250, y: 50 },
      nodeMeta: { title: '开始', icon: '▶️', description: '定时触发或手动触发', color: '#52c41a' },
      inputs: [], outputs: [], config: {},
    },
    {
      id: 'search-1', type: 'trackflow-issue-search',
      position: { x: 250, y: 180 },
      nodeMeta: { title: '查找待办需求', icon: '🔎', description: '按项目、状态、优先级、类型和关键词筛选', color: '#0ea5e9' },
      inputs: [
        { name: 'projectId',    label: '项目 ID',         valueType: 'number',  required: false, value: null },
        { name: 'statusIds',    label: '状态 ID',         valueType: 'string',  required: false, value: null },
        { name: 'priority',     label: '优先级',          valueType: 'string',  required: false, value: null },
        { name: 'issueType',    label: '工单类型',        valueType: 'string',  required: false, value: null },
        { name: 'tagIds',       label: '标签 ID',         valueType: 'string',  required: false, value: null },
        { name: 'keyword',      label: '关键词',          valueType: 'string',  required: false, optional: true, value: null },
        { name: 'assignedToMe', label: '仅分配给执行身份', valueType: 'boolean', required: false, optional: true, value: null },
        { name: 'sort',         label: '排序',            valueType: 'string',  required: false, optional: true, value: null },
        { name: 'limit',        label: '数量上限',        valueType: 'number',  required: false, optional: true, value: { type: 'literal', value: 20 } },
      ],
      outputs: [
        { name: 'issues',   label: '需求列表', valueType: 'array' },
        { name: 'count',    label: '数量',     valueType: 'number' },
        { name: 'hasWork',  label: '有待办',   valueType: 'boolean' },
      ],
      config: {},
    },
    {
      id: 'condition-1', type: 'condition',
      position: { x: 250, y: 340 },
      nodeMeta: { title: '有待处理工单？', icon: '❓', description: '判断是否有搜索结果', color: '#faad14' },
      inputs: [
        { name: 'expression', valueType: 'string', required: true, value: { type: 'literal', value: '{output}' } },
      ],
      outputs: [
        { name: 'result', valueType: 'boolean' },
      ],
      config: { variable: '{output}', operator: 'is_not_empty', value: '' },
    },
    {
      id: 'loop-1', type: 'loop',
      position: { x: 250, y: 490 },
      nodeMeta: { title: '遍历工单', icon: '🔁', description: '逐个处理搜索到的工单', color: '#722ed1' },
      inputs: [
        { name: 'items', valueType: 'array', required: true, value: { type: 'ref', nodeId: 'search-1', outputName: 'issues' } },
      ],
      outputs: [
        { name: 'currentItem', valueType: 'object' },
      ],
      config: { maxRetries: 50, interval: 0, exitOperator: 'is_empty', exitVariable: '{currentItem}' },
    },
    {
      id: 'agent-1', type: 'role-agent',
      position: { x: 250, y: 640 },
      nodeMeta: { title: 'AI 分析工单', icon: '🤖', description: 'AI Agent 分析工单内容并给出处理建议', color: '#eb2f96' },
      inputs: [
        { name: 'roleId',   label: 'Agent 角色',   valueType: 'number', required: false, value: null },
        { name: 'task',     label: '任务描述',     valueType: 'string', required: true,  value: { type: 'literal', value: '分析此工单，给出处理建议、优先级评估和可能的解决方案' } },
        { name: 'context',  label: '上下文（可选）', valueType: 'object', required: false, value: { type: 'ref', nodeId: 'loop-1', outputName: 'currentItem' } },
        { name: 'workDir',  label: '工作目录',     valueType: 'string', required: false, value: null },
      ],
      outputs: [
        { name: 'response', label: 'Agent 响应', valueType: 'string' },
      ],
      config: {},
    },
    {
      id: 'comment-1', type: 'trackflow-issue-comment',
      position: { x: 250, y: 790 },
      nodeMeta: { title: '添加 AI 评论', icon: '💬', description: '将 AI 分析结果作为评论添加到工单', color: '#13c2c2' },
      inputs: [
        { name: 'issueId', label: '工单 ID',   valueType: 'number', required: true, value: null },
        { name: 'content', label: '评论内容', valueType: 'string', required: true, value: { type: 'ref', nodeId: 'agent-1', outputName: 'response' } },
      ],
      outputs: [
        { name: 'comment', label: '评论', valueType: 'object' },
      ],
      config: {},
    },
    {
      id: 'end-1', type: 'end',
      position: { x: 250, y: 940 },
      nodeMeta: { title: '结束', icon: '⏹️', description: '工作流结束', color: '#f5222d' },
      inputs: [], outputs: [], config: {},
    },
  ],
  edges: [
    { id: 'e1', sourceNodeId: 'start-1',     sourcePortName: 'next',         targetNodeId: 'search-1',    targetPortName: 'input' },
    { id: 'e2', sourceNodeId: 'search-1',    sourcePortName: 'issues',       targetNodeId: 'condition-1', targetPortName: 'expression' },
    { id: 'e3', sourceNodeId: 'condition-1', sourcePortName: 'true',         targetNodeId: 'loop-1',      targetPortName: 'items' },
    { id: 'e4', sourceNodeId: 'loop-1',      sourcePortName: 'currentItem',  targetNodeId: 'agent-1',     targetPortName: 'context' },
    { id: 'e5', sourceNodeId: 'agent-1',     sourcePortName: 'response',     targetNodeId: 'comment-1',   targetPortName: 'content' },
    { id: 'e6', sourceNodeId: 'comment-1',   sourcePortName: 'comment',      targetNodeId: 'end-1',       targetPortName: 'input' },
    { id: 'e7', sourceNodeId: 'condition-1', sourcePortName: 'false',        targetNodeId: 'end-1',       targetPortName: 'input' },
  ],
}

// ─── 模板2：新工单自动 AI 分析并评论建议 ──────────────────────────────────────

const TEMPLATE_AI_NEW_ISSUE = {
  globalVariables: {},
  nodes: [
    {
      id: 'start-1', type: 'start',
      position: { x: 250, y: 50 },
      nodeMeta: { title: '开始', icon: '▶️', description: '新工单创建时触发', color: '#52c41a' },
      inputs: [], outputs: [], config: {},
    },
    {
      id: 'get-1', type: 'trackflow-issue-get',
      position: { x: 250, y: 180 },
      nodeMeta: { title: '获取需求', icon: '📋', description: '按 ID 或编号读取需求（含评论、标签、自定义字段）', color: '#2563eb' },
      inputs: [
        { name: 'issue', label: '工单 ID / 编号', valueType: 'string', required: true, value: null },
      ],
      outputs: [
        { name: 'issue', label: '工单（完整）', valueType: 'object' },
      ],
      config: {},
    },
    {
      id: 'agent-1', type: 'role-agent',
      position: { x: 250, y: 330 },
      nodeMeta: { title: 'AI 分析', icon: '🤖', description: '分析工单内容，给出处理建议', color: '#eb2f96' },
      inputs: [
        { name: 'roleId',  label: 'Agent 角色',    valueType: 'number', required: false, value: null },
        { name: 'task',    label: '任务描述',      valueType: 'string', required: true,  value: { type: 'literal', value: '分析此工单，给出处理建议、优先级评估和可能的解决方案' } },
        { name: 'context', label: '上下文（可选）', valueType: 'object', required: false, value: { type: 'ref', nodeId: 'get-1', outputName: 'issue' } },
        { name: 'workDir', label: '工作目录',      valueType: 'string', required: false, value: null },
      ],
      outputs: [
        { name: 'response', label: 'Agent 响应', valueType: 'string' },
      ],
      config: {},
    },
    {
      id: 'comment-1', type: 'trackflow-issue-comment',
      position: { x: 250, y: 490 },
      nodeMeta: { title: '添加建议评论', icon: '💬', description: '将 AI 建议作为评论发布', color: '#13c2c2' },
      inputs: [
        { name: 'issueId', label: '工单 ID',   valueType: 'number', required: true, value: null },
        { name: 'content', label: '评论内容', valueType: 'string', required: true, value: { type: 'ref', nodeId: 'agent-1', outputName: 'response' } },
      ],
      outputs: [
        { name: 'comment', label: '评论', valueType: 'object' },
      ],
      config: {},
    },
    {
      id: 'end-1', type: 'end',
      position: { x: 250, y: 640 },
      nodeMeta: { title: '结束', icon: '⏹️', description: '工作流结束', color: '#f5222d' },
      inputs: [], outputs: [], config: {},
    },
  ],
  edges: [
    { id: 'e1', sourceNodeId: 'start-1',   sourcePortName: 'next',     targetNodeId: 'get-1',     targetPortName: 'input' },
    { id: 'e2', sourceNodeId: 'get-1',     sourcePortName: 'issue',    targetNodeId: 'agent-1',   targetPortName: 'context' },
    { id: 'e3', sourceNodeId: 'agent-1',   sourcePortName: 'response', targetNodeId: 'comment-1', targetPortName: 'content' },
    { id: 'e4', sourceNodeId: 'comment-1', sourcePortName: 'comment',  targetNodeId: 'end-1',     targetPortName: 'input' },
  ],
}

// ─── 模板3：工单状态变更时自动通知负责人 ──────────────────────────────────────

const TEMPLATE_STATUS_NOTIFY = {
  globalVariables: {},
  nodes: [
    {
      id: 'start-1', type: 'start',
      position: { x: 250, y: 50 },
      nodeMeta: { title: '开始', icon: '▶️', description: '工单状态变更时触发', color: '#52c41a' },
      inputs: [], outputs: [], config: {},
    },
    {
      id: 'get-1', type: 'trackflow-issue-get',
      position: { x: 250, y: 180 },
      nodeMeta: { title: '获取需求', icon: '📋', description: '按 ID 或编号读取需求（含评论、标签、自定义字段）', color: '#2563eb' },
      inputs: [
        { name: 'issue', label: '工单 ID / 编号', valueType: 'string', required: true, value: null },
      ],
      outputs: [
        { name: 'issue', label: '工单（完整）', valueType: 'object' },
      ],
      config: {},
    },
    {
      id: 'condition-1', type: 'condition',
      position: { x: 250, y: 330 },
      nodeMeta: { title: '有负责人？', icon: '❓', description: '判断工单是否已分配负责人', color: '#faad14' },
      inputs: [
        { name: 'expression', valueType: 'string', required: true, value: { type: 'literal', value: '{output}' } },
      ],
      outputs: [
        { name: 'result', valueType: 'boolean' },
      ],
      config: { variable: '{output}', operator: 'is_not_empty', value: '' },
    },
    {
      id: 'http-1', type: 'http-request',
      position: { x: 250, y: 480 },
      nodeMeta: { title: '发送通知', icon: '📤', description: '通过 HTTP 接口发送状态变更通知', color: '#fa8c16' },
      inputs: [
        { name: 'url',     label: 'URL',      valueType: 'string', required: true,  value: { type: 'literal', value: 'http://localhost:8090/api/v1/notifications/send' } },
        { name: 'method',  label: 'HTTP 方法', valueType: 'string', required: true,  value: { type: 'literal', value: 'POST' } },
        { name: 'body',    label: '请求体',   valueType: 'object', required: false, value: null },
        { name: 'headers', label: 'Headers',  valueType: 'object', required: false, value: null },
        { name: 'timeout', label: '超时（秒）', valueType: 'number', required: false, value: null },
      ],
      outputs: [
        { name: 'response',   label: 'HTTP 响应', valueType: 'object' },
        { name: 'statusCode', label: '状态码',    valueType: 'number' },
        { name: 'success',    label: '是否成功',  valueType: 'boolean' },
      ],
      config: {},
    },
    {
      id: 'end-1', type: 'end',
      position: { x: 250, y: 630 },
      nodeMeta: { title: '结束', icon: '⏹️', description: '工作流结束', color: '#f5222d' },
      inputs: [], outputs: [], config: {},
    },
  ],
  edges: [
    { id: 'e1', sourceNodeId: 'start-1',     sourcePortName: 'next',    targetNodeId: 'get-1',       targetPortName: 'input' },
    { id: 'e2', sourceNodeId: 'get-1',       sourcePortName: 'issue',   targetNodeId: 'condition-1', targetPortName: 'expression' },
    { id: 'e3', sourceNodeId: 'condition-1', sourcePortName: 'true',    targetNodeId: 'http-1',      targetPortName: 'url' },
    { id: 'e4', sourceNodeId: 'http-1',      sourcePortName: 'success', targetNodeId: 'end-1',       targetPortName: 'input' },
    { id: 'e5', sourceNodeId: 'condition-1', sourcePortName: 'false',   targetNodeId: 'end-1',       targetPortName: 'input' },
  ],
}

// ─── 导出 ─────────────────────────────────────────────────────────────────────

export const BUILTIN_WORKFLOW_TEMPLATES: WorkflowTemplateVO[] = [
  {
    id: 'builtin-1',
    name: '定时巡检并 AI 处理待办工单',
    description: '定时搜索待办工单，逐个调用 AI Agent 分析并自动添加处理建议评论',
    category: 'ai_task',
    icon: '🤖',
    isBuiltin: true,
    definition: JSON.stringify(TEMPLATE_AI_PATROL),
  },
  {
    id: 'builtin-2',
    name: '新工单自动 AI 分析并评论建议',
    description: '工单创建时触发，AI 自动分析内容并发布处理建议评论',
    category: 'ai_task',
    icon: '✨',
    isBuiltin: true,
    definition: JSON.stringify(TEMPLATE_AI_NEW_ISSUE),
  },
  {
    id: 'builtin-3',
    name: '工单状态变更时自动通知负责人',
    description: '工单状态变更时检查是否有负责人，有则通过 HTTP 接口发送通知',
    category: 'notification',
    icon: '🔔',
    isBuiltin: true,
    definition: JSON.stringify(TEMPLATE_STATUS_NOTIFY),
  },
]
