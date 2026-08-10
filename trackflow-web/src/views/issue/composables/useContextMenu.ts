import { ref, reactive, computed } from 'vue'
import { Message } from '@arco-design/web-vue'
import { issueApi, sprintApi } from '@/api'
import type { IssueVO, IssueStatusVO, SprintVO } from '@/api/types'
import { localizeStatusName } from '@/utils/fieldLabels'

export interface ContextMenuState {
  visible: boolean
  x: number
  y: number
  issue: IssueVO | null
}

export interface ContextMenuOptions {
  refreshList: () => void
  updateLocalIssue: (id: string, patch: Partial<IssueVO>) => void
  sprintOptionsCache: Record<string, SprintVO[]>
  canEditIssue: (issue: IssueVO) => boolean
}

export function useContextMenu(options: ContextMenuOptions) {
  const { refreshList, sprintOptionsCache } = options

  const contextMenu = reactive<ContextMenuState>({
    visible: false,
    x: 0,
    y: 0,
    issue: null
  })

  const ctxTransitions = ref<IssueStatusVO[]>([])
  const ctxTransitionsLoading = ref(false)
  const ctxSprintsLoading = ref(false)
  const ctxSprintSubVisible = ref(false)
  const ctxSprintGroupsData = ref<{ label: string; items: SprintVO[] }[]>([])
  const ctxSprintGroups = computed(() => ctxSprintGroupsData.value)

  function openContextMenu(issue: IssueVO, event: MouseEvent) {
    contextMenu.issue = issue
    contextMenu.visible = true
    // Calculate position to keep menu inside viewport
    const menuWidth = 220
    const menuHeight = 300
    const vw = window.innerWidth
    const vh = window.innerHeight
    contextMenu.x = event.clientX + menuWidth > vw ? event.clientX - menuWidth : event.clientX
    contextMenu.y = event.clientY + menuHeight > vh ? event.clientY - menuHeight : event.clientY
    ctxTransitions.value = []
    ctxTransitionsLoading.value = false
    ctxSprintSubVisible.value = false
    // Preload transitions
    loadCtxTransitions(issue)
  }

  function closeContextMenu() {
    contextMenu.visible = false
    contextMenu.issue = null
    ctxSprintSubVisible.value = false
  }

  async function loadCtxTransitions(issue: IssueVO) {
    ctxTransitionsLoading.value = true
    try {
      const res = await issueApi.getAvailableTransitions(issue.id)
      if (res.code === 0) {
        ctxTransitions.value = (res.data || []).filter((t: IssueStatusVO) => t.id !== issue.statusId)
      }
    } catch (e) {
      console.error('[IssueList] 加载右键菜单可用转换失败:', e)
    } finally {
      ctxTransitionsLoading.value = false
    }
  }

  // List layout context menu handler
  function onListItemContextMenu({ issue, event }: { issue: IssueVO; event: MouseEvent }) {
    openContextMenu(issue, event)
  }

  // Table row-contextmenu event from Arco Design
  function onTableRowContextMenu(record: any, event: Event) {
    event.preventDefault()
    openContextMenu(record, event as MouseEvent)
  }

  function ctxCopyIssueKey() {
    if (!contextMenu.issue) return
    navigator.clipboard.writeText(contextMenu.issue.issueKey || '')
    Message.success(`已复制工单 ID: ${contextMenu.issue.issueKey}`)
    closeContextMenu()
  }

  function ctxCopyLink() {
    if (!contextMenu.issue) return
    const url = `${window.location.origin}/issues/${contextMenu.issue.issueKey}`
    navigator.clipboard.writeText(url)
    Message.success('已复制工单链接')
    closeContextMenu()
  }

  function ctxOpenNewTab() {
    if (!contextMenu.issue) return
    window.open(`/issues/${contextMenu.issue.issueKey}`, '_blank')
    closeContextMenu()
  }

  async function ctxSetStatus(st: IssueStatusVO) {
    if (!contextMenu.issue) return
    const issue = contextMenu.issue
    closeContextMenu()
    try {
      await issueApi.transitStatus(issue.id, st.id, undefined, issue.version)
      Message.success(`状态已更新为 ${localizeStatusName(st.name)}`)
      await refreshList()
    } catch (e: any) {
      Message.error(e?.response?.data?.message || '状态变更失败')
    }
  }

  async function ctxLoadSprints() {
    if (!contextMenu.issue) return
    ctxSprintSubVisible.value = true
    if (ctxSprintGroupsData.value.length > 0) return // already loaded
    ctxSprintsLoading.value = true
    try {
      const projectId = contextMenu.issue.projectId
      if (!sprintOptionsCache[projectId]) {
        const res = await sprintApi.listByProject(projectId)
        if (res.code === 0) {
          sprintOptionsCache[projectId] = res.data?.list || []
        }
      }
      const sprints = sprintOptionsCache[projectId] || []
      const active = sprints.filter((s: SprintVO) => s.status === 'active')
      const planned = sprints.filter((s: SprintVO) => s.status === 'planned')
      const groups: { label: string; items: SprintVO[] }[] = []
      if (active.length) groups.push({ label: '进行中', items: active })
      if (planned.length) groups.push({ label: '计划中', items: planned })
      ctxSprintGroupsData.value = groups
    } catch (e) {
      console.error('[IssueList] 加载右键菜单 Sprint 列表失败:', e)
    } finally {
      ctxSprintsLoading.value = false
    }
  }

  function ctxMoveSprint() {
    ctxSprintSubVisible.value = !ctxSprintSubVisible.value
    if (ctxSprintSubVisible.value) ctxLoadSprints()
  }

  async function ctxSelectSprint(sprint: SprintVO | null) {
    if (!contextMenu.issue) return
    const issue = contextMenu.issue
    closeContextMenu()
    try {
      await issueApi.update(issue.id, { sprintId: sprint?.id || null, version: issue.version })
      Message.success(sprint ? `已移至 Sprint: ${sprint.name}` : '已移出 Sprint')
      await refreshList()
    } catch (e: any) {
      Message.error(e?.response?.data?.message || 'Sprint 更新失败')
    }
  }

  // Close context menu on escape key
  function onGlobalKeydownCtx(e: KeyboardEvent) {
    if (e.key === 'Escape') closeContextMenu()
  }

  return {
    contextMenu,
    ctxTransitions,
    ctxTransitionsLoading,
    ctxSprintsLoading,
    ctxSprintSubVisible,
    ctxSprintGroups,
    openContextMenu,
    closeContextMenu,
    onListItemContextMenu,
    onTableRowContextMenu,
    ctxCopyIssueKey,
    ctxCopyLink,
    ctxOpenNewTab,
    ctxSetStatus,
    ctxLoadSprints,
    ctxMoveSprint,
    ctxSelectSprint,
    onGlobalKeydownCtx
  }
}
