/**
 * useIssueDetailData — 工单详情页数据加载、状态管理、WebSocket 实时更新
 *
 * 从 IssueDetailView.vue 提取，负责：
 * - 工单主数据加载（issue, comments, activities, attachments, links, members, sprints, customFieldDefs）
 * - 权限计算（综合项目级+资源级权限）
 * - WebSocket 实时订阅
 * - 活动流分页
 * - 选项（priority/issueType）动态加载
 */
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { issueApi, projectApi, sprintApi, tagApi, customFieldApi, timeEntryApi } from '@/api'
import { workItemAttributeApi } from '@/api/timeEntry'
import type { WorkItemAttributeVO } from '@/api/timeEntry'
import { usePermission, loadProjectPermissions } from '@/composables/usePermission'
import { useIssueDetailSubscription } from '@/composables/useWebSocket'
import type { IssueRealtimeEvent } from '@/composables/useWebSocket'
import { useAuthStore } from '@/stores/auth'
import { useTabStore } from '@/stores/tabs'
import { useRecentIssues } from './useRecentIssues'
import { loadPriorityOptions } from './usePriorityOptions'
import { loadIssueTypeOptions } from './useIssueTypeOptions'
import type {
  IssueDetailVO, IssueStatusVO, IssueCommentVO, IssueActivityVO,
  IssueAttachmentVO, IssueLinkVO, IssueTagVO, ProjectMemberVO,
  SprintVO, CustomFieldDefinitionVO
} from '@/api/types'

export function useIssueDetailData() {
  const route = useRoute()
  const router = useRouter()
  const authStore = useAuthStore()
  const tabStore = useTabStore()
  const { recordVisit: recordRecentVisit } = useRecentIssues()

  // ============ Core state ============
  const loading = ref(false)
  const loadError = ref<string | null>(null)
  const issue = ref<IssueDetailVO | null>(null)

  const transitions = ref<IssueStatusVO[]>([])
  const comments = ref<IssueCommentVO[]>([])
  const activities = ref<IssueActivityVO[]>([])
  const activityPage = ref(1)
  const activityTotal = ref(0)
  const activityHasMore = ref(false)
  const activityLoadingMore = ref(false)
  const attachments = ref<IssueAttachmentVO[]>([])
  const links = ref<IssueLinkVO[]>([])
  const projectTagList = ref<IssueTagVO[]>([])
  const members = ref<ProjectMemberVO[]>([])
  const allProjectMembers = ref<ProjectMemberVO[]>([])
  const sprints = ref<SprintVO[]>([])
  const customFieldDefs = ref<CustomFieldDefinitionVO[]>([])

  // Options loaded dynamically
  const dynamicPriorityOptions = ref<Array<{ value: string; label: string; color: string }>>([
    { value: 'Show-stopper', label: '阻塞', color: '#b91c1c' },
    { value: 'Critical', label: '紧急', color: '#ef4444' },
    { value: 'High', label: '高', color: '#f59e0b' },
    { value: 'Normal', label: '普通', color: '#6366f1' },
    { value: 'Low', label: '低', color: '#64748b' },
  ])

  const dynamicIssueTypeOptions = ref<Array<{ value: string; label: string; color: string }>>([
    { value: 'Bug', label: '缺陷', color: '#ef4444' },
    { value: 'Task', label: '任务', color: '#6366f1' },
    { value: 'Feature', label: '需求', color: '#22c55e' },
    { value: 'Epic', label: '史诗', color: '#a855f7' },
    { value: 'Story', label: '故事', color: '#3b82f6' },
  ])

  // Time tracking
  const projectTimeTrackingEnabled = ref(true)
  const issueProjectAttributes = ref<WorkItemAttributeVO[]>([])
  const timeFormCanLogForOthers = ref(false)
  const timeFormProjectMembers = ref<{ userId: string; username?: string; displayName: string }[]>([])

  const issueWorkTypeValues = computed(() => {
    const wt = issueProjectAttributes.value.find(a => a.name === 'Work type' || a.isBuiltin)
    return wt?.values || [
      { id: 'Development', name: '开发', color: '#58a6ff' },
      { id: 'Testing', name: '测试', color: '#3fb950' },
      { id: 'Documentation', name: '文档', color: '#d29922' },
      { id: 'Design', name: '设计', color: '#a371f7' },
      { id: 'Review', name: '代码审查', color: '#f0883e' },
      { id: 'Meeting', name: '会议', color: '#8b949e' },
      { id: 'Other', name: '其他', color: '#6e7681' }
    ]
  })

  const issueExtraAttributes = computed(() => {
    return issueProjectAttributes.value.filter(a => !a.isBuiltin && a.name !== 'Work type')
  })

  // ============ Computed ============
  const issueId = computed(() => route.params.id as string || '')
  const isProjectArchived = computed(() => issue.value?.projectStatus === 'archived')
  const currentUserId = computed(() => authStore.user?.userId || '')

  // ============ Permission ============
  const { canCreateIssue, canEditIssue, canDeleteIssue, canChangeStatus, canComment, canAssignIssue, canEditSprint, canLogTime, hasPermission: hasProjectPermission } = usePermission(
    () => issue.value?.projectId,
    { isProjectArchived: () => isProjectArchived.value }
  )

  const canManageComments = computed(() => hasProjectPermission('issue:manage_comments'))
  const canManageCustomFieldsComputed = computed(() => hasProjectPermission('project:manage_custom_fields'))

  const isReporter = computed(() => {
    const dbUserId = authStore.user?.userId
    if (!dbUserId || !issue.value) return false
    return dbUserId === issue.value.reporterId
  })

  const isAssignee = computed(() => {
    const dbUserId = authStore.user?.userId
    if (!dbUserId || !issue.value) return false
    return dbUserId === issue.value.assigneeId
  })

  const canEditIssueEffective = computed(() => {
    if (isProjectArchived.value) return false
    if (canEditIssue.value) return true
    if (isReporter.value && hasProjectPermission('issue:edit_own')) return true
    if (isAssignee.value && hasProjectPermission('issue:edit_assigned')) return true
    return false
  })

  const canChangeStatusEffective = computed(() => {
    if (isProjectArchived.value) return false
    if (canChangeStatus.value) return true
    if (isAssignee.value && hasProjectPermission('issue:edit_assigned')) return true
    return false
  })

  const canCommentEffective = computed(() => {
    if (isProjectArchived.value) return false
    if (canComment.value) return true
    if (isReporter.value) return true
    return false
  })

  const canMoveIssue = computed(() => {
    if (isProjectArchived.value) return false
    return hasProjectPermission('issue:move')
  })

  // ============ WebSocket 实时更新 ============
  const activityStreamRef = ref<HTMLElement | null>(null)
  const realtimeUpdateBanner = ref<{ visible: boolean; message: string }>({ visible: false, message: '' })
  let inactiveUpdateCount = 0
  const originalTitle = ref('')

  function isActivityStreamVisible(): boolean {
    const el = activityStreamRef.value
    if (!el) return true
    const rect = el.getBoundingClientRect()
    const viewHeight = window.innerHeight || document.documentElement.clientHeight
    return rect.top < viewHeight && rect.bottom > 0
  }

  function scrollToActivity() {
    realtimeUpdateBanner.value.visible = false
    const el = activityStreamRef.value
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }

  function showRealtimeNotification(message: string, isActivityUpdate: boolean) {
    if (document.hidden) {
      inactiveUpdateCount++
      document.title = `(${inactiveUpdateCount}) ${originalTitle.value || document.title.replace(/^\(\d+\)\s*/, '')}`
      return
    }
    if (isActivityUpdate && !isActivityStreamVisible()) {
      realtimeUpdateBanner.value = { visible: true, message }
      setTimeout(() => { realtimeUpdateBanner.value.visible = false }, 10000)
    } else {
      Message.info({ content: message, duration: 3000 })
    }
  }

  function onVisibilityChange() {
    if (!document.hidden && inactiveUpdateCount > 0) {
      inactiveUpdateCount = 0
      document.title = originalTitle.value || document.title.replace(/^\(\d+\)\s*/, '')
    }
  }

  useIssueDetailSubscription(
    () => issue.value?.id,
    (event: IssueRealtimeEvent) => {
      const myUserId = authStore.user?.userId
      if (myUserId && String(event.operatorId) === String(myUserId)) return

      if (event.action === 'FIELD_UPDATED' && issue.value) {
        for (const [key, value] of Object.entries(event.changes)) {
          ;(issue.value as any)[key] = value
        }
        if ('statusId' in event.changes) { loadTransitions() }
        showRealtimeNotification(`${event.operatorName || '其他用户'} 更新了此工单`, false)
      } else if (event.action === 'COMMENT_ADDED') {
        loadCommentsAndActivities()
        showRealtimeNotification(`${event.operatorName || '其他用户'} 添加了新评论`, true)
      } else if (event.action === 'ATTACHMENT_CHANGED') {
        loadAttachments()
        showRealtimeNotification(`${event.operatorName || '其他用户'} 更新了附件`, true)
      } else if (event.action === 'LINK_CHANGED') {
        loadLinks()
        showRealtimeNotification(`${event.operatorName || '其他用户'} 更新了关联工单`, true)
      } else if (event.action === 'DELETED') {
        Message.warning({ content: '此工单已被删除', duration: 5000 })
        router.push({ name: 'issues' })
      }
    }
  )

  // ============ Data loading ============
  async function loadTransitions() {
    if (!issue.value) return
    try {
      const res = await issueApi.getAvailableTransitions(issue.value.id)
      if (res.code === 0) transitions.value = res.data || []
    } catch { /* ignore */ }
  }

  async function loadCommentsAndActivities() {
    if (!issue.value) return
    try {
      const [commRes, actRes] = await Promise.all([
        issueApi.listComments(issue.value.id),
        issueApi.listActivities(issue.value.id, { page: 1, pageSize: 20 })
      ])
      if (commRes.code === 0) comments.value = commRes.data || []
      if (actRes.code === 0) {
        const pageData = actRes.data
        activities.value = pageData.list || []
        activityPage.value = 1
        activityTotal.value = pageData.pagination.total
        activityHasMore.value = pageData.pagination.page < pageData.pagination.totalPages
      }
    } catch { /* ignore */ }
  }

  async function loadMoreActivities() {
    if (!issue.value || !activityHasMore.value || activityLoadingMore.value) return
    activityLoadingMore.value = true
    try {
      const nextPage = activityPage.value + 1
      const res = await issueApi.listActivities(issue.value.id, { page: nextPage, pageSize: 20 })
      if (res.code === 0) {
        const pageData = res.data
        activities.value = [...activities.value, ...(pageData.list || [])]
        activityPage.value = nextPage
        activityHasMore.value = pageData.pagination.page < pageData.pagination.totalPages
      }
    } catch { /* ignore */ }
    finally { activityLoadingMore.value = false }
  }

  async function loadAttachments() {
    if (!issue.value) return
    try {
      const res = await issueApi.listAttachments(issue.value.id)
      if (res.code === 0) attachments.value = res.data || []
    } catch { /* ignore */ }
  }

  async function loadLinks() {
    if (!issue.value) return
    try {
      const res = await issueApi.listLinks(issue.value.id)
      if (res.code === 0) links.value = res.data || []
    } catch { /* ignore */ }
  }

  async function loadAll() {
    const id = issueId.value
    if (!id) { loadError.value = '缺少工单 ID'; return }

    loading.value = true
    loadError.value = null

    try {
      const isKey = id.includes('-') && !/^\d+$/.test(id)
      const res = isKey ? await issueApi.getByKey(id) : await issueApi.getById(id)
      if (res.code === 0 && res.data) {
        issue.value = res.data
        recordRecentVisit({ id: res.data.id, issueKey: res.data.issueKey, title: res.data.title })
        tabStore.openTab({
          id: `issue-${id}`,
          title: res.data.issueKey,
          path: route.fullPath,
          closable: true,
          issueId: id
        })
        await loadRelatedData()
      } else {
        loadError.value = res.message || '加载工单失败'
      }
    } catch (e: any) {
      const status = e.response?.status
      if (status === 404) { loadError.value = `工单 ${id} 不存在` }
      else if (status === 403) { loadError.value = '无权访问此工单' }
      else { loadError.value = e.response?.data?.message || '网络错误，请稍后重试' }
      console.warn('[IssueDetail] Failed to load issue:', e)
    } finally {
      loading.value = false
    }
  }

  async function loadRelatedData() {
    if (!issue.value) return
    const id = issue.value.id
    const pid = issue.value.projectId

    loadPriorityOptions(pid).then(opts => {
      dynamicPriorityOptions.value = opts.map(o => ({ value: o.value, label: o.label, color: o.color || '#6366f1' }))
    })

    loadIssueTypeOptions(pid).then(opts => {
      dynamicIssueTypeOptions.value = opts.map(o => ({ value: o.value, label: o.label, color: o.color || '#6366f1' }))
    })

    const perms = await loadProjectPermissions(pid)
    const isAdmin = authStore.hasGlobalPermission('system:admin')
    const needTransitions = isAdmin || perms.has('issue:change_status')
    const needSprintOptions = isAdmin || perms.has('sprint:edit')
    const needMemberOptions = isAdmin || perms.has('issue:assign')

    try {
      const ttRes = await projectApi.getTimeTrackingSettings(pid, { _silent403: true })
      if (ttRes.code === 0 && ttRes.data) { projectTimeTrackingEnabled.value = ttRes.data.enabled }
    } catch { projectTimeTrackingEnabled.value = false }

    if (!needTransitions) { transitions.value = [] }

    try {
      const promises: Promise<any>[] = [
        issueApi.listComments(id),
        issueApi.listActivities(id, { page: 1, pageSize: 20 }),
        issueApi.listAttachments(id),
        issueApi.listLinks(id),
        tagApi.listProjectTags(pid, { _silent403: true }),
        customFieldApi.listByProject(pid, issue.value!.issueType),
      ]
      if (needTransitions) promises.push(issueApi.getAvailableTransitions(id))
      if (needMemberOptions) promises.push(projectApi.listAssignableMembers(pid))
      if (needSprintOptions) promises.push(sprintApi.listByProject(pid))
      promises.push(projectApi.listMembers(pid))

      const results = await Promise.allSettled(promises)

      let idx = 0
      if (results[idx].status === 'fulfilled') comments.value = (results[idx] as any).value.data || []
      idx++
      if (results[idx].status === 'fulfilled') {
        const pageData = (results[idx] as any).value.data
        activities.value = pageData?.list || []
        activityPage.value = 1
        activityTotal.value = pageData?.pagination?.total || 0
        activityHasMore.value = (pageData?.pagination?.page || 0) < (pageData?.pagination?.totalPages || 0)
      }
      idx++
      if (results[idx].status === 'fulfilled') attachments.value = (results[idx] as any).value.data || []
      idx++
      if (results[idx].status === 'fulfilled') links.value = (results[idx] as any).value.data || []
      idx++
      if (results[idx].status === 'fulfilled') projectTagList.value = (results[idx] as any).value.data || []
      idx++
      if (results[idx].status === 'fulfilled') customFieldDefs.value = (results[idx] as any).value.data || []
      idx++
      if (needTransitions) {
        if (results[idx].status === 'fulfilled') transitions.value = (results[idx] as any).value.data || []
        idx++
      }
      if (needMemberOptions) {
        if (results[idx].status === 'fulfilled') members.value = (results[idx] as any).value.data || []
        idx++
      }
      if (needSprintOptions) {
        if (results[idx].status === 'fulfilled') sprints.value = (results[idx] as any).value.data?.list || []
        idx++
      }
      if (results[idx].status === 'fulfilled') allProjectMembers.value = (results[idx] as any).value.data || []
    } catch { /* ignore partial failures */ }
  }

  async function loadIssueProjectAttributes(projectId: string) {
    try {
      const res = await workItemAttributeApi.listByProject(projectId)
      if (res.code === 0 && res.data) { issueProjectAttributes.value = res.data }
    } catch { /* silent */ }
  }

  async function loadTimeFormPermissions(projectId: string) {
    try {
      const res = await timeEntryApi.canLogForOthers()
      timeFormCanLogForOthers.value = res.code === 0 ? (res.data ?? false) : false
    } catch { timeFormCanLogForOthers.value = false }
    if (timeFormCanLogForOthers.value) {
      try {
        const res = await projectApi.listAssignableMembers(projectId)
        if (res.code === 0 && res.data) {
          timeFormProjectMembers.value = res.data.map((m: ProjectMemberVO) => ({
            userId: m.userId,
            username: m.username || m.displayName,
            displayName: m.displayName || m.username || ''
          }))
        }
      } catch { /* silent */ }
    }
  }

  // ============ Lifecycle ============
  onMounted(() => {
    originalTitle.value = document.title.replace(/^\(\d+\)\s*/, '')
    document.addEventListener('visibilitychange', onVisibilityChange)
  })

  onUnmounted(() => {
    document.removeEventListener('visibilitychange', onVisibilityChange)
    if (inactiveUpdateCount > 0) {
      document.title = originalTitle.value || document.title.replace(/^\(\d+\)\s*/, '')
    }
  })

  watch(() => route.params.id, () => loadAll())

  return {
    // State
    loading, loadError, issue,
    transitions, comments, activities, activityPage, activityTotal, activityHasMore, activityLoadingMore,
    attachments, links, projectTagList, members, allProjectMembers, sprints, customFieldDefs,
    dynamicPriorityOptions, dynamicIssueTypeOptions,
    projectTimeTrackingEnabled, issueProjectAttributes, issueWorkTypeValues, issueExtraAttributes,
    timeFormCanLogForOthers, timeFormProjectMembers,

    // Computed
    issueId, isProjectArchived, currentUserId,

    // Permissions
    canCreateIssue, canEditIssue, canDeleteIssue, canChangeStatus, canComment,
    canAssignIssue, canEditSprint, canLogTime, hasProjectPermission,
    canManageComments, canManageCustomFieldsComputed,
    isReporter, isAssignee,
    canEditIssueEffective, canChangeStatusEffective, canCommentEffective, canMoveIssue,

    // WebSocket / realtime
    activityStreamRef, realtimeUpdateBanner, scrollToActivity,

    // Data loading
    loadAll, loadTransitions, loadCommentsAndActivities, loadMoreActivities,
    loadAttachments, loadLinks, loadRelatedData,
    loadIssueProjectAttributes, loadTimeFormPermissions,
  }
}
