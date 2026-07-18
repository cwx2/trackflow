import { ref, type Ref } from 'vue'
import type { IssueVO, IssueStatusVO, ProjectMemberVO, SprintVO, IssueTagVO } from '@/api/types'
import { issueApi, projectApi, sprintApi, tagApi } from '@/api'
import { localizeStatusName, localizePriority, statusLabelMap } from '@/utils/fieldLabels'
import type { BatchResult } from './useBatchOps'
import { useBatchOps } from './useBatchOps'

/**
 * 命令类型定义
 */
export interface ParsedCommand {
  field: string          // 原始字段标识
  fieldLabel: string     // 中文字段名（显示用）
  value: string          // 用户输入的原始值
  resolvedValue?: string // 解析后的值（ID 或标准枚举值）
  operation?: 'set' | 'add' | 'remove'  // 操作类型（标签用）
  error?: string         // 解析错误信息
}

/**
 * 自动补全建议
 */
export interface CommandSuggestion {
  text: string           // 要插入的文本
  label: string          // 显示标签
  description?: string   // 描述
  type: 'field' | 'value'
}

/**
 * 命令执行结果
 */
export interface CommandExecutionResult {
  total: number
  succeeded: number
  failed: number
  details: Array<{ field: string; result: BatchResult }>
}

// 字段别名映射（中文/英文 → 标准字段名）
const FIELD_ALIASES: Record<string, string> = {
  // 状态
  '状态': 'status',
  'state': 'status',
  'status': 'status',
  // 负责人
  '负责人': 'assignee',
  '分配': 'assignee',
  'assignee': 'assignee',
  'assigned': 'assignee',
  'for': 'assignee',
  // 优先级
  '优先级': 'priority',
  'priority': 'priority',
  // Sprint
  'sprint': 'sprint',
  '迭代': 'sprint',
  // 标签
  '标签': 'tag_add',
  'tag': 'tag_add',
  '添加标签': 'tag_add',
  'add tag': 'tag_add',
  // 移除标签
  '移除标签': 'tag_remove',
  'remove tag': 'tag_remove',
  '删除标签': 'tag_remove',
}

// 优先级值别名
const PRIORITY_ALIASES: Record<string, string> = {
  '紧急': 'Critical',
  '高': 'High',
  '普通': 'Normal',
  '低': 'Low',
  'critical': 'Critical',
  'high': 'High',
  'normal': 'Normal',
  'low': 'Low',
  'major': 'High',
  'minor': 'Low',
}

export function useApplyCommand(selectedIssues: Ref<IssueVO[]>) {
  const { batchTransitStatus, batchAssign, batchUpdateSprint, batchUpdatePriority } = useBatchOps()

  // Loaded context data
  const statuses = ref<IssueStatusVO[]>([])
  const members = ref<ProjectMemberVO[]>([])
  const sprints = ref<SprintVO[]>([])
  const tags = ref<IssueTagVO[]>([])
  const contextLoading = ref(false)
  const contextLoaded = ref(false)

  /**
   * 加载命令对话框所需的上下文数据（状态、成员、Sprint、标签）
   */
  async function loadContext() {
    if (contextLoaded.value) return
    contextLoading.value = true
    try {
      const projectIds = [...new Set(selectedIssues.value.map(i => i.projectId))]
      if (projectIds.length === 0) return

      // 并行加载所有上下文数据
      const [statusRes, ...otherResults] = await Promise.all([
        issueApi.listStatuses(),
        ...projectIds.map(pid => projectApi.listMembers(pid)),
        ...projectIds.map(pid => sprintApi.listByProject(pid)),
        ...projectIds.map(pid => tagApi.listProjectTags(pid)),
      ])

      statuses.value = statusRes.data || []

      // 合并成员（去重）
      const allMembers: ProjectMemberVO[] = []
      const memberResults = otherResults.slice(0, projectIds.length)
      for (const res of memberResults) {
        allMembers.push(...((res as any).data || []))
      }
      const seenUsers = new Set<string>()
      members.value = allMembers.filter(m => {
        if (seenUsers.has(m.userId)) return false
        seenUsers.add(m.userId)
        return true
      })

      // 合并 Sprint
      const allSprints: SprintVO[] = []
      const sprintResults = otherResults.slice(projectIds.length, projectIds.length * 2)
      for (const res of sprintResults) {
        allSprints.push(...((res as any).data || []))
      }
      sprints.value = allSprints

      // 合并标签（去重）
      const allTags: IssueTagVO[] = []
      const tagResults = otherResults.slice(projectIds.length * 2)
      for (const res of tagResults) {
        allTags.push(...((res as any).data || []))
      }
      const seenTags = new Set<string>()
      tags.value = allTags.filter(t => {
        if (seenTags.has(t.id)) return false
        seenTags.add(t.id)
        return true
      })

      contextLoaded.value = true
    } catch (e) {
      console.error('Failed to load command context', e)
    } finally {
      contextLoading.value = false
    }
  }

  /**
   * 解析命令文本为命令列表
   * 支持格式：字段名 值 [字段名 值 ...]
   * 示例："状态 进行中 负责人 张伟 优先级 高"
   */
  function parseCommand(input: string): ParsedCommand[] {
    const commands: ParsedCommand[] = []
    if (!input.trim()) return commands

    // 将输入拆分为 token 列表
    const tokens = tokenize(input.trim())
    let i = 0

    while (i < tokens.length) {
      const token = tokens[i]

      // 尝试匹配双词字段名（如 "移除标签"、"remove tag"）
      let fieldKey: string | undefined
      let consumedTokens = 0

      if (i + 1 < tokens.length) {
        const twoWord = (token + ' ' + tokens[i + 1]).toLowerCase()
        if (FIELD_ALIASES[twoWord]) {
          fieldKey = FIELD_ALIASES[twoWord]
          consumedTokens = 2
        }
      }

      if (!fieldKey) {
        const oneWord = token.toLowerCase()
        if (FIELD_ALIASES[oneWord]) {
          fieldKey = FIELD_ALIASES[oneWord]
          consumedTokens = 1
        }
      }

      if (!fieldKey) {
        // 不是已知字段名，跳过（可能是多词值的一部分被错误分割了）
        i++
        continue
      }

      i += consumedTokens

      // 收集值（直到下一个字段名或输入结束）
      const valueTokens: string[] = []
      while (i < tokens.length) {
        const nextToken = tokens[i]
        // 检查是否是下一个字段
        let isField = false
        if (i + 1 < tokens.length) {
          const twoWord = (nextToken + ' ' + tokens[i + 1]).toLowerCase()
          if (FIELD_ALIASES[twoWord]) { isField = true }
        }
        if (!isField && FIELD_ALIASES[nextToken.toLowerCase()]) {
          isField = true
        }
        if (isField) break
        valueTokens.push(nextToken)
        i++
      }

      const rawValue = valueTokens.join(' ')
      const cmd = resolveCommand(fieldKey, rawValue)
      commands.push(cmd)
    }

    return commands
  }

  /**
   * 将字段+值解析为具体命令
   */
  function resolveCommand(field: string, rawValue: string): ParsedCommand {
    const cmd: ParsedCommand = {
      field,
      fieldLabel: getFieldLabel(field),
      value: rawValue,
      operation: field.startsWith('tag_') ? (field === 'tag_add' ? 'add' : 'remove') : 'set',
    }

    if (!rawValue) {
      cmd.error = '缺少值'
      return cmd
    }

    switch (field) {
      case 'status': {
        const status = findStatus(rawValue)
        if (status) {
          cmd.resolvedValue = status.id
          cmd.value = localizeStatusName(status.name)
        } else {
          cmd.error = `未找到状态「${rawValue}」`
        }
        break
      }
      case 'assignee': {
        if (rawValue === 'me' || rawValue === '我') {
          // 分配给自己 — 用当前用户
          cmd.resolvedValue = 'me'
          cmd.value = '我'
        } else {
          const member = findMember(rawValue)
          if (member) {
            cmd.resolvedValue = member.userId
            cmd.value = member.displayName || rawValue
          } else {
            cmd.error = `未找到成员「${rawValue}」`
          }
        }
        break
      }
      case 'priority': {
        const resolved = PRIORITY_ALIASES[rawValue.toLowerCase()] || PRIORITY_ALIASES[rawValue]
        if (resolved) {
          cmd.resolvedValue = resolved
          cmd.value = localizePriority(resolved)
        } else {
          cmd.error = `未知优先级「${rawValue}」，可选：紧急/高/普通/低`
        }
        break
      }
      case 'sprint': {
        if (rawValue === '无' || rawValue === 'none' || rawValue === 'backlog') {
          cmd.resolvedValue = '0'
          cmd.value = '无 Sprint'
        } else {
          const sprint = findSprint(rawValue)
          if (sprint) {
            cmd.resolvedValue = sprint.id
            cmd.value = sprint.name
          } else {
            cmd.error = `未找到 Sprint「${rawValue}」`
          }
        }
        break
      }
      case 'tag_add':
      case 'tag_remove': {
        const tag = findTag(rawValue)
        if (tag) {
          cmd.resolvedValue = tag.id
          cmd.value = tag.name
        } else {
          cmd.error = `未找到标签「${rawValue}」`
        }
        break
      }
    }

    return cmd
  }

  /**
   * 获取自动补全建议
   */
  function getSuggestions(input: string): CommandSuggestion[] {
    const suggestions: CommandSuggestion[] = []
    if (!input.trim()) {
      // 空输入时显示所有可用字段
      return getFieldSuggestions('')
    }

    const tokens = tokenize(input.trim())
    const lastToken = tokens[tokens.length - 1] || ''

    // 检查当前正在输入的是字段还是值
    // 逻辑：从后往前找最近的字段名，如果 lastToken 紧跟在字段名后面，则补全值
    let currentField: string | undefined
    let valuePrefix = ''

    // 从 token 列表中找到最后一个被识别的字段
    for (let i = tokens.length - 1; i >= 0; i--) {
      const t = tokens[i].toLowerCase()
      // 双词字段
      if (i > 0) {
        const twoWord = (tokens[i - 1] + ' ' + tokens[i]).toLowerCase()
        if (FIELD_ALIASES[twoWord]) {
          currentField = FIELD_ALIASES[twoWord]
          valuePrefix = tokens.slice(i + 1).join(' ')
          break
        }
      }
      if (FIELD_ALIASES[t]) {
        currentField = FIELD_ALIASES[t]
        valuePrefix = tokens.slice(i + 1).join(' ')
        break
      }
    }

    if (!currentField || valuePrefix === '') {
      // 还在输入字段名，提供字段补全
      return getFieldSuggestions(lastToken)
    }

    // 提供值补全
    return getValueSuggestions(currentField, valuePrefix)
  }

  function getFieldSuggestions(prefix: string): CommandSuggestion[] {
    const fields = [
      { text: '状态 ', label: '状态', description: '变更工单状态' },
      { text: '负责人 ', label: '负责人', description: '分配给成员' },
      { text: '优先级 ', label: '优先级', description: '设置优先级' },
      { text: 'Sprint ', label: 'Sprint', description: '移至迭代' },
      { text: '标签 ', label: '标签', description: '添加标签' },
      { text: '移除标签 ', label: '移除标签', description: '移除标签' },
    ]
    const lowerPrefix = prefix.toLowerCase()
    return fields
      .filter(f => !lowerPrefix || f.label.toLowerCase().includes(lowerPrefix) || f.text.toLowerCase().includes(lowerPrefix))
      .map(f => ({ ...f, type: 'field' as const }))
  }

  function getValueSuggestions(field: string, prefix: string): CommandSuggestion[] {
    const lowerPrefix = prefix.toLowerCase()
    switch (field) {
      case 'status':
        return statuses.value
          .filter(s => {
            const localName = localizeStatusName(s.name)
            return localName.toLowerCase().includes(lowerPrefix) || s.name.toLowerCase().includes(lowerPrefix)
          })
          .map(s => ({
            text: localizeStatusName(s.name),
            label: localizeStatusName(s.name),
            description: s.category,
            type: 'value' as const,
          }))
      case 'assignee':
        const memberSuggestions: CommandSuggestion[] = [
          { text: '我', label: '我', description: '分配给自己', type: 'value' },
        ]
        return [
          ...memberSuggestions.filter(s => !lowerPrefix || s.label.includes(lowerPrefix)),
          ...members.value
            .filter(m => m.displayName?.toLowerCase().includes(lowerPrefix))
            .map(m => ({
              text: m.displayName || '',
              label: m.displayName || '',
              type: 'value' as const,
            }))
        ]
      case 'priority':
        return [
          { text: '紧急', label: '紧急', description: 'Critical', type: 'value' as const },
          { text: '高', label: '高', description: 'High', type: 'value' as const },
          { text: '普通', label: '普通', description: 'Normal', type: 'value' as const },
          { text: '低', label: '低', description: 'Low', type: 'value' as const },
        ].filter(s => !lowerPrefix || s.label.includes(lowerPrefix))
      case 'sprint':
        return [
          { text: '无', label: '无 Sprint', description: '移出迭代', type: 'value' as const },
          ...sprints.value
            .filter(s => s.status !== 'completed' && s.name.toLowerCase().includes(lowerPrefix))
            .map(s => ({
              text: s.name,
              label: s.name,
              description: s.status === 'active' ? '进行中' : '计划中',
              type: 'value' as const,
            }))
        ]
      case 'tag_add':
      case 'tag_remove':
        return tags.value
          .filter(t => t.name.toLowerCase().includes(lowerPrefix))
          .map(t => ({
            text: t.name,
            label: t.name,
            type: 'value' as const,
          }))
      default:
        return []
    }
  }

  /**
   * 执行已解析的命令列表
   */
  async function executeCommands(
    commands: ParsedCommand[],
    silent: boolean
  ): Promise<CommandExecutionResult> {
    const issues = selectedIssues.value
    const issueIds = issues.map(i => i.id)
    const result: CommandExecutionResult = {
      total: commands.length,
      succeeded: 0,
      failed: 0,
      details: [],
    }

    for (const cmd of commands) {
      if (cmd.error || !cmd.resolvedValue) {
        result.failed++
        result.details.push({
          field: cmd.fieldLabel,
          result: { total: 0, succeeded: 0, failed: 1, failures: [{ issueId: '', issueKey: '', reason: cmd.error || '无法解析' }] }
        })
        continue
      }

      try {
        let batchResult: BatchResult
        switch (cmd.field) {
          case 'status': {
            const versions: Record<string, number> = {}
            for (const issue of issues) {
              if (issue.version != null) versions[issue.id] = issue.version
            }
            const res = await issueApi.batch({
              operation: 'status',
              issueIds,
              statusId: cmd.resolvedValue,
              versions,
              silent,
            })
            batchResult = res.data || { total: issueIds.length, succeeded: 0, failed: 0, failures: [] }
            break
          }
          case 'assignee': {
            let assigneeId = cmd.resolvedValue
            if (assigneeId === 'me') {
              // 获取当前用户 ID
              const userJson = localStorage.getItem('tf_user')
              if (userJson) {
                const user = JSON.parse(userJson)
                assigneeId = user.userId || user.id
              }
            }
            const res = await issueApi.batch({
              operation: 'assign',
              issueIds,
              assigneeId: assigneeId || '0',
              silent,
            })
            batchResult = res.data || { total: issueIds.length, succeeded: 0, failed: 0, failures: [] }
            break
          }
          case 'priority': {
            const res = await issueApi.batch({
              operation: 'priority',
              issueIds,
              priority: cmd.resolvedValue,
              silent,
            })
            batchResult = res.data || { total: issueIds.length, succeeded: 0, failed: 0, failures: [] }
            break
          }
          case 'sprint': {
            const res = await issueApi.batch({
              operation: 'sprint',
              issueIds,
              sprintId: cmd.resolvedValue,
              silent,
            })
            batchResult = res.data || { total: issueIds.length, succeeded: 0, failed: 0, failures: [] }
            break
          }
          case 'tag_add': {
            const res = await issueApi.batch({
              operation: 'tag_add',
              issueIds,
              tagId: cmd.resolvedValue,
              silent,
            })
            batchResult = res.data || { total: issueIds.length, succeeded: 0, failed: 0, failures: [] }
            break
          }
          case 'tag_remove': {
            const res = await issueApi.batch({
              operation: 'tag_remove',
              issueIds,
              tagId: cmd.resolvedValue,
              silent,
            })
            batchResult = res.data || { total: issueIds.length, succeeded: 0, failed: 0, failures: [] }
            break
          }
          default:
            batchResult = { total: 0, succeeded: 0, failed: 1, failures: [] }
        }

        if (batchResult.failed === 0) {
          result.succeeded++
        } else {
          result.failed++
        }
        result.details.push({ field: cmd.fieldLabel, result: batchResult })
      } catch (e: any) {
        result.failed++
        result.details.push({
          field: cmd.fieldLabel,
          result: { total: issueIds.length, succeeded: 0, failed: issueIds.length, failures: [{ issueId: '', issueKey: '', reason: e.response?.data?.message || '执行失败' }] }
        })
      }
    }

    return result
  }

  // ========== Helper functions ==========

  function tokenize(input: string): string[] {
    // 简单按空格拆分，保留引号内的多词值
    const tokens: string[] = []
    let current = ''
    let inQuote = false
    let quoteChar = ''

    for (const ch of input) {
      if ((ch === '"' || ch === '\'' || ch === '「' || ch === '」') && !inQuote) {
        inQuote = true
        quoteChar = ch === '「' ? '」' : ch
        continue
      }
      if (inQuote && ch === quoteChar) {
        inQuote = false
        if (current) tokens.push(current)
        current = ''
        continue
      }
      if (ch === ' ' && !inQuote) {
        if (current) tokens.push(current)
        current = ''
      } else {
        current += ch
      }
    }
    if (current) tokens.push(current)
    return tokens
  }

  function getFieldLabel(field: string): string {
    switch (field) {
      case 'status': return '状态'
      case 'assignee': return '负责人'
      case 'priority': return '优先级'
      case 'sprint': return 'Sprint'
      case 'tag_add': return '添加标签'
      case 'tag_remove': return '移除标签'
      default: return field
    }
  }

  function findStatus(query: string): IssueStatusVO | undefined {
    const lower = query.toLowerCase()
    // 先尝试中文名匹配
    for (const [name, localName] of Object.entries(statusLabelMap)) {
      if (localName === query || localName.toLowerCase() === lower) {
        return statuses.value.find(s => s.name === name)
      }
    }
    // 再尝试英文名匹配
    return statuses.value.find(s => s.name.toLowerCase() === lower)
  }

  function findMember(query: string): ProjectMemberVO | undefined {
    const lower = query.toLowerCase()
    return members.value.find(m =>
      m.displayName?.toLowerCase() === lower ||
      m.displayName?.toLowerCase().includes(lower)
    )
  }

  function findSprint(query: string): SprintVO | undefined {
    const lower = query.toLowerCase()
    return sprints.value.find(s =>
      s.status !== 'completed' && (
        s.name.toLowerCase() === lower ||
        s.name.toLowerCase().includes(lower)
      )
    )
  }

  function findTag(query: string): IssueTagVO | undefined {
    const lower = query.toLowerCase()
    return tags.value.find(t =>
      t.name.toLowerCase() === lower ||
      t.name.toLowerCase().includes(lower)
    )
  }

  return {
    contextLoading,
    contextLoaded,
    loadContext,
    parseCommand,
    getSuggestions,
    executeCommands,
    statuses,
    members,
    sprints,
    tags,
  }
}
