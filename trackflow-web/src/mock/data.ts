/**
 * Mock 数据中心
 * 所有页面共享的模拟数据，支持 CRUD 操作
 * 后续接真实 API 时，只需要替换 api/ 层的调用即可
 */
import { reactive } from 'vue'

// ========== 用户 ==========
export const mockUsers = reactive([
  { id: '1', username: 'system', displayName: 'System', email: 'system@trackflow.dev', avatar: '' },
  { id: '2', username: 'vance', displayName: 'VanceChang', email: 'vance@company.com', avatar: '' },
  { id: '3', username: 'cici', displayName: 'CiciCheng', email: 'cici@company.com', avatar: '' },
  { id: '4', username: 'mike', displayName: 'MikeWang', email: 'mike@company.com', avatar: '' },
  { id: '5', username: 'sarah', displayName: 'SarahLiu', email: 'sarah@company.com', avatar: '' },
  { id: '6', username: 'tom', displayName: 'TomZhang', email: 'tom@company.com', avatar: '' },
])

// ========== 项目 ==========
export const mockProjects = reactive([
  { id: '1', key: 'DE4', name: '后端开发', description: 'TrackFlow 后端 Java 服务', status: 'active', issueSequence: 1392 },
  { id: '2', key: 'FE', name: '前端开发', description: 'TrackFlow Vue 前端', status: 'active', issueSequence: 87 },
  { id: '3', key: 'OPS', name: '运维部署', description: 'CI/CD 和基础设施', status: 'active', issueSequence: 45 },
])

// ========== 标签 ==========
export const mockTags = reactive([
  { id: '1', projectId: '1', name: 'Wording', color: '#7c3aed' },
  { id: '2', projectId: '1', name: 'P1', color: '#ef4444' },
  { id: '3', projectId: '1', name: 'SUG', color: '#0891b2' },
  { id: '4', projectId: '1', name: 'Mobile', color: '#16a34a' },
  { id: '5', projectId: '1', name: 'API', color: '#f59e0b' },
  { id: '6', projectId: '1', name: 'Backend', color: '#6366f1' },
  { id: '7', projectId: '1', name: 'Frontend', color: '#ec4899' },
  { id: '8', projectId: '1', name: 'UX', color: '#14b8a6' },
  { id: '9', projectId: '1', name: 'Performance', color: '#8b5cf6' },
  { id: '10', projectId: '1', name: 'Security', color: '#dc2626' },
])

// ========== 状态 ==========
export const mockStatuses = reactive([
  { id: '1', name: 'Open', code: 'open', color: '#4caf50', category: 'open', isDefault: true, isClosed: false },
  { id: '2', name: 'In Progress', code: 'in_progress', color: '#2196f3', category: 'in_progress', isDefault: false, isClosed: false },
  { id: '3', name: 'Code Review', code: 'code_review', color: '#9c27b0', category: 'in_progress', isDefault: false, isClosed: false },
  { id: '4', name: 'Testing', code: 'testing', color: '#ff9800', category: 'in_progress', isDefault: false, isClosed: false },
  { id: '5', name: 'Done', code: 'done', color: '#607d8b', category: 'done', isDefault: false, isClosed: true },
  { id: '6', name: 'Cancelled', code: 'cancelled', color: '#9e9e9e', category: 'cancelled', isDefault: false, isClosed: true },
  { id: '7', name: 'Reopened', code: 'reopened', color: '#f44336', category: 'open', isDefault: false, isClosed: false },
  { id: '8', name: 'Todo', code: 'todo', color: '#42a5f5', category: 'open', isDefault: false, isClosed: false },
  { id: '9', name: 'UI Todo', code: 'ui_todo', color: '#ab47bc', category: 'open', isDefault: false, isClosed: false },
  { id: '10', name: 'Done (Local Env)', code: 'done_local', color: '#66bb6a', category: 'in_progress', isDefault: false, isClosed: false },
  { id: '11', name: 'No Test', code: 'no_test', color: '#78909c', category: 'in_progress', isDefault: false, isClosed: false },
  { id: '12', name: 'Pending Code Review', code: 'pending_code_review', color: '#7e57c2', category: 'in_progress', isDefault: false, isClosed: false },
  { id: '13', name: 'Pending Publish', code: 'pending_publish', color: '#ffa726', category: 'in_progress', isDefault: false, isClosed: false },
  { id: '14', name: 'Online', code: 'online', color: '#26a69a', category: 'done', isDefault: false, isClosed: true },
  { id: '15', name: 'Solved', code: 'solved', color: '#43a047', category: 'done', isDefault: false, isClosed: true },
  { id: '16', name: 'Closed', code: 'closed', color: '#546e7a', category: 'done', isDefault: false, isClosed: true },
  { id: '17', name: 'Pending Cancel', code: 'pending_cancel', color: '#ef5350', category: 'open', isDefault: false, isClosed: false },
  { id: '18', name: 'Pending Extension', code: 'pending_extension', color: '#ffca28', category: 'open', isDefault: false, isClosed: false },
])

// ========== Sprint ==========
export const mockSprints = reactive([
  { id: '1', projectId: '1', name: 'Sprint 22 (6/17 - 6/30)', status: 'completed', startDate: '2026-06-17', endDate: '2026-06-30' },
  { id: '2', projectId: '1', name: 'Sprint 23 (7/1 - 7/14)', status: 'active', startDate: '2026-07-01', endDate: '2026-07-14' },
  { id: '3', projectId: '1', name: 'Sprint 24 (7/15 - 7/28)', status: 'planned', startDate: '2026-07-15', endDate: '2026-07-28' },
])

// ========== Issues ==========
let issueIdCounter = 1392

export const mockIssues = reactive([
  {
    id: '1389', projectId: '1', issueKey: 'DE4-1389', title: '[API](7/9)SUG-Wording: 用户反馈页面文案错误导致退款率上升',
    description: `## 问题描述\n\n用户在 Reviews 页面看到的文案 "Your subscription will be automatically renewed" 与实际行为不符。当用户已取消订阅时，该文案仍然显示，导致用户误以为会被重复收费，引发大量客服投诉和退款请求。\n\n### 影响范围\n\n- 影响平台：SSG, ESG\n- 影响用户群：所有已取消订阅的活跃用户\n- 退款率上升约 **12%**（对比上月同期）\n\n### 复现步骤\n\n1. 使用测试账号登录 SSG\n2. 进入 Account > Subscription 页面\n3. 取消订阅\n4. 返回 Reviews 页面\n5. 观察页面底部文案 — 仍显示 "auto-renewed"\n\n### 期望行为\n\n取消订阅后，文案应变更为：\n> "Your subscription has been cancelled. You can continue using the service until the end of your billing period."\n\n### 技术方案\n\n\`\`\`javascript\nconst subscriptionText = computed(() => {\n  if (user.subscription.status === 'cancelled') {\n    return i18n.t('subscription.cancelled_notice')\n  }\n  return i18n.t('subscription.auto_renew_notice')\n})\n\`\`\``,
    issueType: 'Bug', statusId: '2', priority: 'Critical',
    assigneeId: '2', reporterId: '1', sprintId: '2',
    parentId: null, dueDate: '2026-07-13', estimatedHours: 4, spentHours: 2,
    tags: ['1', '2', '3', '4'],
    createdAt: '2026-07-09T10:00:00Z', updatedAt: '2026-07-11T09:20:00Z',
  },
  {
    id: '1390', projectId: '1', issueKey: 'DE4-1390', title: 'SSG 端文案修改',
    description: '修改 SSG 平台 Reviews 页面和 Subscription 页面的订阅状态文案。',
    issueType: 'Task', statusId: '1', priority: 'High',
    assigneeId: '2', reporterId: '2', sprintId: '2',
    parentId: '1389', dueDate: '2026-07-12', estimatedHours: 2, spentHours: 0,
    tags: ['1', '4'],
    createdAt: '2026-07-09T10:30:00Z', updatedAt: '2026-07-09T10:30:00Z',
  },
  {
    id: '1391', projectId: '1', issueKey: 'DE4-1391', title: 'ESG 端文案修改',
    description: '修改 ESG 平台对应页面的订阅状态文案，与 SSG 保持一致。',
    issueType: 'Task', statusId: '1', priority: 'High',
    assigneeId: '4', reporterId: '2', sprintId: '2',
    parentId: '1389', dueDate: '2026-07-12', estimatedHours: 2, spentHours: 0,
    tags: ['1'],
    createdAt: '2026-07-09T10:35:00Z', updatedAt: '2026-07-09T10:35:00Z',
  },
  {
    id: '1245', projectId: '1', issueKey: 'DE4-1245', title: '退款流程自动化处理',
    description: '当用户申请退款时，系统自动验证条件并执行退款，减少人工处理。',
    issueType: 'Feature', statusId: '2', priority: 'Normal',
    assigneeId: '4', reporterId: '3', sprintId: '2',
    parentId: null, dueDate: '2026-07-20', estimatedHours: 16, spentHours: 8,
    tags: ['5', '6'],
    createdAt: '2026-06-20T09:00:00Z', updatedAt: '2026-07-10T14:00:00Z',
  },
  {
    id: '1102', projectId: '1', issueKey: 'DE4-1102', title: 'Wording 审核流程优化',
    description: '优化文案审核的工作流，增加 AI 预审环节，减少人工审核量。',
    issueType: 'Feature', statusId: '5', priority: 'Normal',
    assigneeId: '3', reporterId: '2', sprintId: '1',
    parentId: null, dueDate: null, estimatedHours: 8, spentHours: 10,
    tags: ['1'],
    createdAt: '2026-06-01T09:00:00Z', updatedAt: '2026-06-28T16:00:00Z',
  },
])

// ========== 关联 ==========
export const mockLinks = reactive([
  { id: '1', sourceIssueId: '1389', targetIssueId: '1245', linkType: 'blocks' },
  { id: '2', sourceIssueId: '1389', targetIssueId: '1102', linkType: 'relates_to' },
  { id: '3', sourceIssueId: '1389', targetIssueId: '1390', linkType: 'parent_of' },
  { id: '4', sourceIssueId: '1389', targetIssueId: '1391', linkType: 'parent_of' },
])

// ========== 评论 ==========
export const mockComments = reactive([
  { id: '1', issueId: '1389', userId: '1', content: '**AI Review Result**\n\n✅ Duplicate Check: PASS\nNo similar approved suggestions found.\n\n❌ Quality Check: REJECTED\n\nReason: The suggestion lacks a "Before" screenshot, which is mandatory for comparison.', createdAt: '2026-07-09T14:00:00Z' },
  { id: '2', issueId: '1389', userId: '3', content: 'Please check this suggestion.\n\nI\'ve verified the wording issue on both SSG and ESG platforms. The incorrect text appears in 3 locations:\n1. `/reviews` page footer\n2. `/account/subscription` sidebar\n3. Email notification template\n\nPriority should be raised to P1 due to revenue impact.', createdAt: '2026-07-09T18:00:00Z' },
  { id: '3', issueId: '1389', userId: '1', content: '{"response": "The suggestion lacks evidence on how the proposed change won\'t unfairly target users from specific countries."}\n\nComment by AI', createdAt: '2026-07-10T04:00:00Z' },
  { id: '4', issueId: '1389', userId: '2', content: '已经提交了 Before/After screenshot，并更新了技术方案。\n\n修改涉及以下文件：\n- `src/pages/ReviewsPage.vue`\n- `src/i18n/en.json`\n- `src/i18n/zh.json`\n\n预计今天提交 PR，请 @CiciCheng review。', createdAt: '2026-07-11T07:00:00Z' },
  { id: '5', issueId: '1389', userId: '3', content: 'PR 已经 review 通过。\n\n有一个小建议：`cancelled_notice` 的 key 名建议改为 `subscription.status.cancelled.notice`，和其他 i18n key 保持命名一致性。\n\n其他没有问题，LGTM 👍', createdAt: '2026-07-11T08:00:00Z' },
  { id: '6', issueId: '1389', userId: '2', content: '好的，已经按建议修改了 key 命名。新的 commit 已 push。', createdAt: '2026-07-11T08:30:00Z' },
])

// ========== 活动 ==========
export const mockActivities = reactive([
  { id: '1', issueId: '1389', userId: '1', action: 'created', fieldName: null, oldValue: null, newValue: null, createdAt: '2026-07-09T10:00:00Z' },
  { id: '2', issueId: '1389', userId: '1', action: 'updated', fieldName: '优先级', oldValue: 'Normal', newValue: 'High', createdAt: '2026-07-09T11:00:00Z' },
  { id: '3', issueId: '1389', userId: '3', action: 'updated', fieldName: '优先级', oldValue: 'High', newValue: 'Critical', createdAt: '2026-07-09T18:00:00Z' },
  { id: '4', issueId: '1389', userId: '2', action: 'updated', fieldName: '状态', oldValue: 'Open', newValue: 'In Progress', createdAt: '2026-07-10T08:00:00Z' },
  { id: '5', issueId: '1389', userId: '1', action: 'updated', fieldName: '负责人', oldValue: null, newValue: 'VanceChang', createdAt: '2026-07-10T08:00:00Z' },
  { id: '6', issueId: '1389', userId: '2', action: 'updated', fieldName: '预估工时', oldValue: null, newValue: '4h', createdAt: '2026-07-10T08:05:00Z' },
  { id: '7', issueId: '1389', userId: '2', action: 'attachment_added', fieldName: '附件', oldValue: null, newValue: 'before-screenshot.png', createdAt: '2026-07-10T12:00:00Z' },
  { id: '8', issueId: '1389', userId: '2', action: 'attachment_added', fieldName: '附件', oldValue: null, newValue: 'after-mockup.png', createdAt: '2026-07-10T12:05:00Z' },
  { id: '9', issueId: '1389', userId: '3', action: 'updated', fieldName: 'Sprint', oldValue: null, newValue: 'Sprint 23', createdAt: '2026-07-10T16:00:00Z' },
  { id: '10', issueId: '1389', userId: '2', action: 'updated', fieldName: '截止日期', oldValue: null, newValue: '2026-07-13', createdAt: '2026-07-10T16:30:00Z' },
])

// ========== 附件 ==========
export const mockAttachments = reactive([
  { id: '1', issueId: '1389', fileName: 'before-screenshot.png', fileSize: 350208, contentType: 'image/png', uploadedBy: '2', createdAt: '2026-07-10T12:00:00Z' },
  { id: '2', issueId: '1389', fileName: 'after-mockup.png', fileSize: 293888, contentType: 'image/png', uploadedBy: '2', createdAt: '2026-07-10T12:05:00Z' },
  { id: '3', issueId: '1389', fileName: 'refund-rate-report.xlsx', fileSize: 1887436, contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', uploadedBy: '3', createdAt: '2026-07-09T18:30:00Z' },
  { id: '4', issueId: '1389', fileName: 'wording-diff.patch', fileSize: 4301, contentType: 'text/x-patch', uploadedBy: '2', createdAt: '2026-07-11T07:30:00Z' },
])

// ========== Helper Functions ==========

export function getUser(id: string | null) {
  return mockUsers.find(u => u.id === id) || null
}

export function getStatus(id: string) {
  return mockStatuses.find(s => s.id === id) || null
}

export function getIssue(id: string) {
  return mockIssues.find(i => i.id === id) || null
}

export function getIssueTags(issueId: string) {
  const issue = getIssue(issueId)
  if (!issue) return []
  return mockTags.filter(t => issue.tags.includes(t.id))
}

export function getIssueLinks(issueId: string) {
  const links: Array<{ id: string; linkType: string; issueId: string; issueKey: string; issueTitle: string; statusName: string; statusColor: string }> = []

  for (const link of mockLinks) {
    if (link.sourceIssueId === issueId) {
      const target = getIssue(link.targetIssueId)
      if (target) {
        const status = getStatus(target.statusId)
        links.push({ id: link.id, linkType: linkTypeLabel(link.linkType), issueId: target.id, issueKey: target.issueKey, issueTitle: target.title, statusName: status?.name || '', statusColor: status?.color || '' })
      }
    }
    if (link.targetIssueId === issueId) {
      const source = getIssue(link.sourceIssueId)
      if (source) {
        const status = getStatus(source.statusId)
        links.push({ id: link.id, linkType: linkTypeLabel(reverseLinkType(link.linkType)), issueId: source.id, issueKey: source.issueKey, issueTitle: source.title, statusName: status?.name || '', statusColor: status?.color || '' })
      }
    }
  }
  return links
}

export function getIssueComments(issueId: string) {
  return mockComments
    .filter(c => c.issueId === issueId)
    .map(c => ({ ...c, userName: getUser(c.userId)?.displayName || '未知' }))
}

export function getIssueActivities(issueId: string) {
  return mockActivities
    .filter(a => a.issueId === issueId)
    .map(a => ({ ...a, userName: getUser(a.userId)?.displayName || '未知' }))
}

export function getIssueAttachments(issueId: string) {
  return mockAttachments.filter(a => a.issueId === issueId)
}

export function addComment(issueId: string, userId: string, content: string) {
  const comment = {
    id: String(Date.now()),
    issueId,
    userId,
    content,
    createdAt: new Date().toISOString(),
  }
  mockComments.push(comment)
  return comment
}

export function addIssueTag(issueId: string, tagId: string) {
  const issue = getIssue(issueId)
  if (issue && !issue.tags.includes(tagId)) {
    issue.tags.push(tagId)
  }
}

export function removeIssueTag(issueId: string, tagId: string) {
  const issue = getIssue(issueId)
  if (issue) {
    issue.tags = issue.tags.filter((t: string) => t !== tagId)
  }
}

export function createTag(projectId: string, name: string, color?: string) {
  const id = String(Date.now())
  const tag = { id, projectId, name, color: color || randomColor() }
  mockTags.push(tag)
  return tag
}

export function updateIssueField(issueId: string, field: string, value: any) {
  const issue = getIssue(issueId)
  if (issue) {
    (issue as any)[field] = value
    issue.updatedAt = new Date().toISOString()
    // 记录活动
    mockActivities.push({
      id: String(Date.now()),
      issueId, userId: '2', action: 'updated',
      fieldName: field, oldValue: null, newValue: String(value),
      createdAt: new Date().toISOString(),
    })
  }
}

export function transitStatus(issueId: string, newStatusId: string) {
  const issue = getIssue(issueId)
  if (issue) {
    const oldStatus = getStatus(issue.statusId)
    const newStatus = getStatus(newStatusId)
    issue.statusId = newStatusId
    issue.updatedAt = new Date().toISOString()
    mockActivities.push({
      id: String(Date.now()),
      issueId, userId: '2', action: 'updated',
      fieldName: '状态', oldValue: oldStatus?.name || '', newValue: newStatus?.name || '',
      createdAt: new Date().toISOString(),
    })
  }
}

/**
 * 获取可用状态转换（模拟后端 WorkflowService.getAvailableTransitions）
 * 实际后端逻辑：查 workflow_transition 表 WHERE old_status_id AND issue_type AND role_id IN (用户角色)
 */
export function getAvailableTransitions(currentStatusId: string, _issueType: string) {
  // 工作流转换矩阵（模拟数据库 workflow_transition 表内容）
  const rules: Record<string, string[]> = {
    '1': ['2', '8', '9', '6'],                    // Open → In Progress, Todo, UI Todo, Cancelled
    '2': ['3', '4', '10', '11', '6'],             // In Progress → Code Review, Testing, Done(Local), No Test, Cancelled
    '3': ['2', '4', '10'],                         // Code Review → In Progress(退回), Testing, Done(Local)
    '4': ['5', '2', '13'],                         // Testing → Done, In Progress(退回), Pending Publish
    '5': ['7', '14'],                              // Done → Reopened, Online
    '6': ['7'],                                    // Cancelled → Reopened
    '7': ['2', '8'],                               // Reopened → In Progress, Todo
    '8': ['2', '9', '6'],                          // Todo → In Progress, UI Todo, Cancelled
    '9': ['2', '8'],                               // UI Todo → In Progress, Todo
    '10': ['12', '4', '11'],                       // Done(Local) → Pending Code Review, Testing, No Test
    '11': ['13', '5'],                             // No Test → Pending Publish, Done
    '12': ['2', '4', '10'],                        // Pending Code Review → In Progress(退回), Testing, Done(Local)
    '13': ['14', '2'],                             // Pending Publish → Online, In Progress(退回)
    '14': ['16'],                                  // Online → Closed
    '15': ['7', '16'],                             // Solved → Reopened, Closed
    '16': ['7'],                                   // Closed → Reopened
    '17': ['6', '2'],                              // Pending Cancel → Cancelled, In Progress
    '18': ['2', '8'],                              // Pending Extension → In Progress, Todo
  }
  const allowedIds = rules[currentStatusId] || []
  return mockStatuses.filter(s => allowedIds.includes(s.id))
}

export function nextIssueId(projectId: string) {
  const project = mockProjects.find(p => p.id === projectId)
  if (project) {
    project.issueSequence++
    return `${project.key}-${project.issueSequence}`
  }
  return `UNK-${++issueIdCounter}`
}

// ========== 内部工具 ==========

function linkTypeLabel(type: string) {
  const map: Record<string, string> = {
    relates_to: '关联', blocks: '阻塞', blocked_by: '被阻塞',
    parent_of: '子级', child_of: '父级', duplicates: '重复', duplicated_by: '被重复'
  }
  return map[type] || type
}

function reverseLinkType(type: string) {
  const map: Record<string, string> = {
    blocks: 'blocked_by', blocked_by: 'blocks',
    parent_of: 'child_of', child_of: 'parent_of',
    duplicates: 'duplicated_by', duplicated_by: 'duplicates',
    relates_to: 'relates_to'
  }
  return map[type] || type
}

function randomColor() {
  const colors = ['#7c3aed', '#ef4444', '#0891b2', '#16a34a', '#f59e0b', '#6366f1', '#ec4899', '#14b8a6', '#8b5cf6', '#dc2626']
  return colors[Math.floor(Math.random() * colors.length)]
}
