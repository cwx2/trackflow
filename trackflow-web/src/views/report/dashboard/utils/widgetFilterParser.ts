/**
 * Widget 高级过滤查询解析器
 *
 * 支持的语法（与工单列表搜索框一致）：
 *   priority:Critical         → priority=Critical
 *   priority:Critical,High    → priority=Critical,High
 *   state:Blocked             → statusId 按名称匹配（传到后端作 keyword 搜索）
 *   assignee:me               → assigneeId=me
 *   type:Bug                  → issueType=Bug
 *   created:today             → createdAfter=今天零点
 *   created:week              → createdAfter=本周一
 *   overdue:true              → overdue=true
 *   dueSoon:true              → dueSoon=true
 *   sprint:none               → sprintId=none
 *   tag:xxx                   → tagId=xxx (文本形式，后端自行匹配)
 *   keyword:xxx               → keyword=xxx (全文搜索)
 *
 * 语法规则：
 * - 多个条件用空格分隔（AND 逻辑）
 * - 字段名大小写不敏感
 * - 值中包含空格需用引号包裹：assignee:"Zhang Wei"
 * - 未识别的格式会作为 keyword 传递
 */

/**
 * 解析 Widget filterQuery 字符串为 API 请求参数
 */
export function parseWidgetFilterQuery(query: string): Record<string, string> {
  if (!query || !query.trim()) return {}

  const params: Record<string, string> = {}
  const tokens = tokenize(query.trim())

  const unknownParts: string[] = []

  for (const token of tokens) {
    const colonIdx = token.indexOf(':')
    if (colonIdx <= 0) {
      // No colon or starts with colon — treat as keyword
      unknownParts.push(token)
      continue
    }

    const field = token.substring(0, colonIdx).toLowerCase()
    let value = token.substring(colonIdx + 1)

    // Strip surrounding quotes from value
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1)
    }

    if (!value) continue

    switch (field) {
      case 'priority':
        params.priority = value
        break
      case 'state':
      case 'status':
        // Status by name — store as _statusName for frontend resolution.
        // The widget will resolve names to numeric IDs via listStatuses() before calling the API.
        params._statusName = value
        break
      case 'assignee':
        params.assigneeId = value
        break
      case 'reporter':
        params.reporterId = value
        break
      case 'type':
      case 'issuetype':
        params.issueType = value
        break
      case 'sprint':
        params.sprintId = value
        break
      case 'tag':
        params.tagId = value
        break
      case 'created': {
        const dateRange = parseDateShortcut(value)
        if (dateRange.after) params.createdAfter = dateRange.after
        if (dateRange.before) params.createdBefore = dateRange.before
        break
      }
      case 'updated': {
        const dateRange = parseDateShortcut(value)
        if (dateRange.after) params.updatedAfter = dateRange.after
        if (dateRange.before) params.updatedBefore = dateRange.before
        break
      }
      case 'due': {
        const dateRange = parseDateShortcut(value)
        if (dateRange.after) params.dueAfter = dateRange.after
        if (dateRange.before) params.dueBefore = dateRange.before
        break
      }
      case 'overdue':
        if (value === 'true' || value === 'yes') params.overdue = 'true'
        break
      case 'duesoon':
        if (value === 'true' || value === 'yes') params.dueSoon = 'true'
        break
      case 'unresolved':
      case 'open':
        if (value === 'true' || value === 'yes') params.hideResolved = 'true'
        break
      case 'resolved':
      case 'closed':
        if (value === 'true' || value === 'yes') params.onlyResolved = 'true'
        break
      case 'keyword':
      case 'text':
        params.keyword = value
        break
      case 'sort':
        params.sort = value
        break
      default:
        // Unrecognized field:value pair — treat as keyword
        unknownParts.push(token)
    }
  }

  // Merge unknown parts as keyword search (AND with explicit keyword if any)
  if (unknownParts.length > 0) {
    const existingKeyword = params.keyword || ''
    const combined = [existingKeyword, ...unknownParts].filter(Boolean).join(' ')
    if (combined) params.keyword = combined
  }

  return params
}

/**
 * 根据 Widget config 构建跳转到工单列表页的 route query
 */
export function buildIssueListRoute(config: Record<string, any>): Record<string, string> {
  const query: Record<string, string> = {}

  // Map queryType to route params
  const queryType = config.queryType as string | undefined
  if (queryType === 'open') {
    query.hideResolved = 'true'
  } else if (queryType === 'closed') {
    query.onlyResolved = 'true'
  } else if (queryType === 'my_open') {
    query.hideResolved = 'true'
    query.assignedToMe = 'true'
  }

  if (config.projectId) query.projectId = config.projectId

  // Parse filterQuery into params and merge
  if (config.filterQuery) {
    const filterParams = parseWidgetFilterQuery(config.filterQuery)
    // _statusName is a frontend-internal marker; for navigation, pass the raw query text
    // so the issue list page can display it in its search bar.
    delete filterParams._statusName
    Object.assign(query, filterParams)
    // Also pass the raw filter query for the issue list page to parse/display
    query.filterQuery = config.filterQuery
  }

  return query
}

// ─── Internal helpers ─────────────────────────────────────

/**
 * Tokenize query string respecting quoted values
 * "priority:Critical assignee:me type:'Some Type'" → ["priority:Critical", "assignee:me", "type:'Some Type'"]
 */
function tokenize(input: string): string[] {
  const tokens: string[] = []
  let current = ''
  let inQuote: string | null = null

  for (let i = 0; i < input.length; i++) {
    const ch = input[i]

    if (inQuote) {
      current += ch
      if (ch === inQuote) {
        inQuote = null
      }
    } else if (ch === '"' || ch === "'") {
      inQuote = ch
      current += ch
    } else if (ch === ' ' || ch === '\t') {
      if (current) {
        tokens.push(current)
        current = ''
      }
    } else {
      current += ch
    }
  }

  if (current) tokens.push(current)
  return tokens
}

/**
 * Parse date shortcuts (today, yesterday, week, month) into ISO date strings
 */
function parseDateShortcut(value: string): { after?: string; before?: string } {
  const lower = value.toLowerCase()
  const now = new Date()

  switch (lower) {
    case 'today': {
      const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      return { after: start.toISOString().split('T')[0] }
    }
    case 'yesterday': {
      const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
      const end = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      return { after: start.toISOString().split('T')[0], before: end.toISOString().split('T')[0] }
    }
    case 'week':
    case 'thisweek': {
      const day = now.getDay()
      const diff = day === 0 ? 6 : day - 1 // Monday as start of week
      const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - diff)
      return { after: start.toISOString().split('T')[0] }
    }
    case 'month':
    case 'thismonth': {
      const start = new Date(now.getFullYear(), now.getMonth(), 1)
      return { after: start.toISOString().split('T')[0] }
    }
    case 'lastweek': {
      const day = now.getDay()
      const diff = day === 0 ? 6 : day - 1
      const thisWeekStart = new Date(now.getFullYear(), now.getMonth(), now.getDate() - diff)
      const lastWeekStart = new Date(thisWeekStart.getTime() - 7 * 24 * 60 * 60 * 1000)
      return {
        after: lastWeekStart.toISOString().split('T')[0],
        before: thisWeekStart.toISOString().split('T')[0]
      }
    }
    case 'lastmonth': {
      const start = new Date(now.getFullYear(), now.getMonth() - 1, 1)
      const end = new Date(now.getFullYear(), now.getMonth(), 1)
      return {
        after: start.toISOString().split('T')[0],
        before: end.toISOString().split('T')[0]
      }
    }
    default:
      // Try to use as a literal date (YYYY-MM-DD)
      if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
        return { after: value }
      }
      return {}
  }
}
