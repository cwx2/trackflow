import { ref, reactive, computed, type Ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { queryApi, tagApi, projectApi } from '@/api'
import type { IssueStatusVO, QueryPanelItemVO, SavedQueryFilter, UpdateSavedQueryDTO } from '@/api/types'
import type { TagPanelItemVO, AvailableTagVO } from '@/api/tag'
import { useAuthStore } from '@/stores/auth'
import {
  localizeStatusName, priorityLabelMap, priorityReverseLabelMap,
  queryFieldKeyToLabel, queryFieldLabelToKey
} from '@/utils/fieldLabels'
import axios from 'axios'

export interface QueryPanelOptions {
  statusCache: Ref<IssueStatusVO[]>
  projectList: Ref<Array<{ id: string; name: string; key: string; favorited?: boolean }>>
  issueTypeOptions: Ref<{ value: string; label: string; color: string }[]>
  priorityOptions: Ref<{ value: string; label: string; color: string }[]>
  activeProjectId: Ref<string | null>
  hideResolved: Ref<boolean>
  refreshList: () => void
  getIssueTypeLabelForRecord: (v: string) => string
}

export function useQueryPanel(options: QueryPanelOptions) {
  const {
    statusCache, projectList, issueTypeOptions,
    activeProjectId, hideResolved, refreshList, getIssueTypeLabelForRecord
  } = options

  const authStore = useAuthStore()

  // Panel state
  const savedQueries = ref<QueryPanelItemVO[]>([])
  const activeQueryId = ref<string | null>(null)
  const activeQueryName = ref('所有工单')
  const activeQueryObj = ref<QueryPanelItemVO | null>(null)
  const expandedGroups = reactive(new Set<string>(['saved', 'projects', 'drafts', 'tags']))
  const panelSearch = ref('')
  const panelLoadFailed = ref(false)

  // Tags panel state
  const favoriteTags = ref<TagPanelItemVO[]>([])
  const activeTagId = ref<string | null>(null)
  const showManageTagsModal = ref(false)
  const availableTags = ref<AvailableTagVO[]>([])

  // Projects favorite panel state
  const favoriteProjects = computed(() => projectList.value.filter(p => p.favorited))
  const showManageProjectsModal = ref(false)
  const manageProjectSearch = ref('')
  const manageProjectsLoading = ref(false)
  const allProjectsForManage = ref<Array<{ id: string; name: string; key: string; favorited?: boolean }>>([])

  const filteredManageProjects = computed(() => {
    const list = allProjectsForManage.value
    if (!manageProjectSearch.value) return list
    const kw = manageProjectSearch.value.toLowerCase()
    return list.filter((p) => p.name.toLowerCase().includes(kw) || p.key.toLowerCase().includes(kw))
  })

  // Panel width
  const PANEL_WIDTH_KEY = 'trackflow:panel-width'
  const panelWidth = ref(loadPanelWidth())
  const panelWidthBeforeCollapse = ref(280)

  function loadPanelWidth(): number {
    try {
      const stored = localStorage.getItem(PANEL_WIDTH_KEY)
      if (stored) return Math.max(200, Math.min(500, Number(stored)))
    } catch { /* localStorage 读取容错 */ }
    return 280
  }

  function startPanelResize(e: MouseEvent) {
    e.preventDefault()
    const startX = e.clientX
    const startWidth = panelWidth.value

    function onMove(ev: MouseEvent) {
      const delta = ev.clientX - startX
      panelWidth.value = Math.max(200, Math.min(500, startWidth + delta))
    }
    function onUp() {
      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onUp)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
      localStorage.setItem(PANEL_WIDTH_KEY, String(panelWidth.value))
    }
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
  }

  function togglePanelCollapse() {
    if (panelWidth.value <= 200) {
      panelWidth.value = panelWidthBeforeCollapse.value
    } else {
      panelWidthBeforeCollapse.value = panelWidth.value
      panelWidth.value = 200
    }
    localStorage.setItem(PANEL_WIDTH_KEY, String(panelWidth.value))
  }

  // Filtered queries
  const filteredQueries = computed(() => {
    if (!panelSearch.value) return savedQueries.value
    const kw = panelSearch.value.toLowerCase()
    return savedQueries.value.filter((q: QueryPanelItemVO) => q.name.toLowerCase().includes(kw))
  })

  function toggleGroup(group: string) {
    if (expandedGroups.has(group)) expandedGroups.delete(group)
    else expandedGroups.add(group)
  }

  // Query ownership check
  function isOwnQuery(q: QueryPanelItemVO | null): boolean {
    if (!q) return false
    const currentUserId = authStore.user?.userId || authStore.user?.id || ''
    return q.userId === String(currentUserId) && !q.shared
  }
  function isOwnQueryById(userId: string): boolean {
    const currentUserId = authStore.user?.userId || authStore.user?.id || ''
    return userId === String(currentUserId)
  }

  // Create query modal state
  const showCreateQueryModal = ref(false)
  const createQueryLoading = ref(false)
  const queryIconOptions = ['🧪', '🐛', '🚀', '⚡', '📋', '🎯', '🔥', '💡', '⭐', '🏷️', '📌', '🔍', '✅', '⏳', '🎨', '🛡️']
  const createQueryForm = reactive({
    name: '',
    pinned: true,
    shared: false,
    icon: '',
    queryText: ''
  })

  function openCreateQueryModal(buildCurrentFilters: () => SavedQueryFilter[]) {
    createQueryForm.name = ''
    createQueryForm.pinned = true
    createQueryForm.shared = false
    createQueryForm.icon = ''
    let preFilters: SavedQueryFilter[] = []
    if (activeQueryObj.value && activeQueryObj.value.filters) {
      try {
        const raw = activeQueryObj.value.filters
        preFilters = typeof raw === 'string' ? JSON.parse(raw) : (Array.isArray(raw) ? raw : [])
      } catch {
        preFilters = []
      }
    } else {
      preFilters = buildCurrentFilters()
    }
    createQueryForm.queryText = filtersToQueryText(preFilters)
    showCreateQueryModal.value = true
  }

  async function handleCreateQuery() {
    if (!createQueryForm.name.trim()) {
      Message.warning('请输入查询名称')
      return
    }
    createQueryLoading.value = true
    try {
      const filters = queryTextToFilters(createQueryForm.queryText)
      await queryApi.create({
        name: createQueryForm.name.trim(),
        filters: filters,
        pinned: createQueryForm.pinned,
        shared: createQueryForm.shared,
        icon: createQueryForm.icon || undefined
      })
      Message.success('查询已保存')
      showCreateQueryModal.value = false
      loadPanel()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      Message.error(err.response?.data?.message || '保存查询失败')
    } finally {
      createQueryLoading.value = false
    }
  }

  async function confirmDeleteQuery(q: QueryPanelItemVO) {
    const { confirmDelete } = useConfirmDelete()
    confirmDelete({
      itemName: `查询「${q.name}」`,
      onConfirm: async () => {
        try {
          await queryApi.delete(q.id)
          Message.success('查询已删除')
          if (activeQueryId.value === q.id) {
            activeQueryId.value = null
            activeQueryName.value = '所有工单'
            activeQueryObj.value = null
            refreshList()
          }
          loadPanel()
        } catch (e: unknown) {
          const err = e as { response?: { data?: { message?: string } } }
          Message.error(err.response?.data?.message || '删除失败')
        }
      }
    })
  }

  // Edit query
  const showEditQueryModal = ref(false)
  const editQueryLoading = ref(false)
  const editQueryForm = reactive({
    id: '',
    name: '',
    icon: '',
    pinned: false,
    shared: false,
    filters: [] as SavedQueryFilter[],
    queryText: ''
  })

  function openEditQueryModal(q: QueryPanelItemVO) {
    editQueryForm.id = q.id
    editQueryForm.name = q.name || ''
    editQueryForm.icon = q.icon || ''
    editQueryForm.pinned = q.pinned || false
    editQueryForm.shared = q.shared || false
    try {
      editQueryForm.filters = q.filters ? JSON.parse(q.filters) : []
    } catch {
      editQueryForm.filters = []
    }
    editQueryForm.queryText = filtersToQueryText(editQueryForm.filters)
    showEditQueryModal.value = true
  }

  async function handleEditQuery() {
    if (!editQueryForm.name.trim()) {
      Message.warning('请输入查询名称')
      return
    }
    editQueryLoading.value = true
    try {
      const newFilters = queryTextToFilters(editQueryForm.queryText)
      const originalQueryText = filtersToQueryText(editQueryForm.filters)
      const filtersChanged = editQueryForm.queryText.trim() !== originalQueryText.trim()

      const updateData: UpdateSavedQueryDTO = {
        name: editQueryForm.name.trim(),
        icon: editQueryForm.icon || '',
        pinned: editQueryForm.pinned,
        shared: editQueryForm.shared
      }
      if (filtersChanged) {
        updateData.filters = newFilters
      }
      await queryApi.update(editQueryForm.id, updateData)
      Message.success('查询已更新')
      showEditQueryModal.value = false

      if (activeQueryId.value === editQueryForm.id) {
        activeQueryName.value = editQueryForm.name.trim()
        if (activeQueryObj.value) {
          activeQueryObj.value = {
            ...activeQueryObj.value,
            name: editQueryForm.name.trim(),
            icon: editQueryForm.icon || '',
            pinned: editQueryForm.pinned,
            shared: editQueryForm.shared,
            ...(filtersChanged ? { filters: JSON.stringify(newFilters) } : {})
          }
        }
        if (filtersChanged) {
          refreshList()
        }
      }
      loadPanel()
    } catch (e: any) {
      Message.error(e.response?.data?.message || '更新失败')
    } finally {
      editQueryLoading.value = false
    }
  }

  // Rename query
  const showRenameQueryModal = ref(false)
  const renameQueryLoading = ref(false)
  const renameQueryForm = reactive({ id: '', name: '' })

  function openRenameQueryModal(q: QueryPanelItemVO) {
    renameQueryForm.id = q.id
    renameQueryForm.name = q.name || ''
    showRenameQueryModal.value = true
  }

  async function handleRenameQuery() {
    if (!renameQueryForm.name.trim()) {
      Message.warning('请输入查询名称')
      return
    }
    renameQueryLoading.value = true
    try {
      await queryApi.update(renameQueryForm.id, { name: renameQueryForm.name.trim() })
      Message.success('重命名成功')
      showRenameQueryModal.value = false
      if (activeQueryId.value === renameQueryForm.id) {
        activeQueryName.value = renameQueryForm.name.trim()
      }
      loadPanel()
    } catch (e: any) {
      Message.error(e.response?.data?.message || '重命名失败')
    } finally {
      renameQueryLoading.value = false
    }
  }

  // Toggle shared/pinned
  async function toggleQueryShared(q: QueryPanelItemVO) {
    try {
      await queryApi.update(q.id, { shared: !q.shared })
      Message.success(q.shared ? '已设为私有' : '已设为共享')
      loadPanel()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      Message.error(err.response?.data?.message || '操作失败')
    }
  }

  async function toggleQueryPinned(q: QueryPanelItemVO) {
    try {
      await queryApi.update(q.id, { pinned: !q.pinned })
      Message.success(q.pinned ? '已取消置顶' : '已置顶')
      loadPanel()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      Message.error(err.response?.data?.message || '操作失败')
    }
  }

  // Manage query favorites
  const showManageQueriesModal = ref(false)
  const manageQuerySearch = ref('')
  const availableQueries = ref<QueryPanelItemVO[]>([])

  const filteredManageQueries = computed(() => {
    if (!manageQuerySearch.value) return availableQueries.value
    const kw = manageQuerySearch.value.toLowerCase()
    return availableQueries.value.filter((q: QueryPanelItemVO) => q.name.toLowerCase().includes(kw))
  })

  async function openManageQueriesModal() {
    showManageQueriesModal.value = true
    manageQuerySearch.value = ''
    try {
      const res = await queryApi.getAvailableQueries(activeProjectId.value || undefined)
      availableQueries.value = res.data || []
    } catch {
      Message.error('加载可用查询失败')
      availableQueries.value = []
    }
  }

  async function toggleFavorite(q: QueryPanelItemVO) {
    try {
      if (q.favorited) {
        await queryApi.removeFavorite(q.id)
        q.favorited = false
        Message.success(`已从面板移除「${q.name}」`)
      } else {
        await queryApi.addFavorite(q.id)
        q.favorited = true
        Message.success(`已添加「${q.name}」到面板`)
      }
      loadPanel()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      Message.error(err.response?.data?.message || '操作失败')
    }
  }

  async function handleRemoveFavorite(q: QueryPanelItemVO) {
    try {
      await queryApi.removeFavorite(q.id)
      Message.success(`已从面板移除「${q.name}」`)
      loadPanel()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      Message.error(err.response?.data?.message || '操作失败')
    }
  }

  // Context menu trigger for ⋯ button
  function triggerContextMenu(event: MouseEvent, _q: QueryPanelItemVO) {
    const target = (event.target as HTMLElement).closest('.query-item')
    if (target) {
      const contextMenuEvent = new MouseEvent('contextmenu', {
        bubbles: true,
        cancelable: true,
        clientX: event.clientX,
        clientY: event.clientY
      })
      target.dispatchEvent(contextMenuEvent)
    }
  }

  // Projects management
  async function openManageProjectsModal() {
    showManageProjectsModal.value = true
    manageProjectSearch.value = ''
    manageProjectsLoading.value = true
    try {
      const res = await projectApi.list({ pageSize: 100 })
      const projects = res.data?.list || []
      const favoriteIds = new Set(projectList.value.filter(p => p.favorited).map(p => p.id))
      allProjectsForManage.value = projects.map((p) => ({
        ...p,
        favorited: favoriteIds.has(p.id)
      }))
    } catch {
      allProjectsForManage.value = []
    } finally {
      manageProjectsLoading.value = false
    }
  }

  async function toggleProjectFavorite(p: { id: string; favorited?: boolean }) {
    try {
      const res = await projectApi.toggleFavorite(p.id)
      const newFavorited = res.data?.favorited ?? !p.favorited
      p.favorited = newFavorited
      const inList = projectList.value.find(pr => pr.id === p.id)
      if (inList) {
        inList.favorited = newFavorited
      }
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      Message.error(err.response?.data?.message || '操作失败')
    }
  }

  // Tags management
  async function loadTags() {
    try {
      const res = await tagApi.getFavoritePanel(activeProjectId.value || undefined)
      favoriteTags.value = res.data || []
    } catch (e) {
      if (axios.isCancel(e)) return
      favoriteTags.value = []
    }
  }

  function selectTag(tag: TagPanelItemVO, globalFilterParams: Ref<Record<string, string>>, currentPage: Ref<number>) {
    if (activeTagId.value === tag.id) {
      activeTagId.value = null
      activeQueryId.value = null
      activeQueryName.value = '所有工单'
      activeQueryObj.value = null
      globalFilterParams.value = {}
      currentPage.value = 1
      refreshList()
      return
    }
    activeTagId.value = tag.id
    activeQueryId.value = null
    activeQueryName.value = tag.name
    activeQueryObj.value = null
    activeProjectId.value = null
    currentPage.value = 1
    globalFilterParams.value = { tagId: tag.id }
    refreshList()
  }

  async function openManageTagsModal() {
    showManageTagsModal.value = true
    try {
      const res = await tagApi.listAvailableTags(activeProjectId.value || undefined)
      availableTags.value = res.data || []
    } catch {
      availableTags.value = []
    }
  }

  async function toggleTagFavorite(tag: AvailableTagVO) {
    try {
      if (tag.favorited) {
        await tagApi.removeFavorite(tag.id)
        tag.favorited = false
      } else {
        await tagApi.addFavorite(tag.id)
        tag.favorited = true
      }
      await loadTags()
    } catch (e: any) {
      Message.error(e.response?.data?.message || '操作失败')
    }
  }

  // Load panel data
  async function loadPanel() {
    try {
      panelLoadFailed.value = false
      const res = await queryApi.getPanel(activeProjectId.value || undefined, hideResolved.value)
      const data = res.data || {}
      savedQueries.value = [...(data.pinned || []), ...(data.queries || [])]
    } catch (error: any) {
      if (axios.isCancel(error)) return
      panelLoadFailed.value = true
      if (savedQueries.value.length === 0 || savedQueries.value[0]?.id === '1') {
        savedQueries.value = []
      }
      if (error?.response?.status === 429) {
        const retryAfter = parseInt(error.response.headers?.['retry-after'] || '60', 10)
        setTimeout(() => loadPanel(), Math.min(retryAfter, 120) * 1000)
      }
    }
  }

  // ===== Query text ↔ Filters JSON conversion =====

  function filtersToQueryText(filters: SavedQueryFilter[]): string {
    if (!filters || filters.length === 0) return ''
    const parts: string[] = []
    for (const f of filters) {
      let fieldLabel: string
      if (f.field.startsWith('cf.') || f.field.startsWith('customField.')) {
        fieldLabel = f.displayName || f.field
      } else {
        fieldLabel = queryFieldKeyToLabel[f.field] || f.field
      }
      const op = f.operator

      if (op === 'open') { parts.push(`${fieldLabel}: 未关闭`); continue }
      if (op === 'closed') { parts.push(`${fieldLabel}: 已关闭`); continue }
      if (op === 'is_empty') { parts.push(`${fieldLabel}: 无`); continue }
      if (op === 'is_not_empty') { parts.push(`${fieldLabel}: 有`); continue }

      let values: string
      if (Array.isArray(f.value)) {
        values = f.value.map((v: string) => {
          if (v === '${currentUser}') return '我'
          if (f.field === 'type') return getIssueTypeLabelForRecord(v)
          if (f.field === 'priority') return priorityLabelMap[v] || v
          if (f.field === 'status') {
            const st = statusCache.value.find(s => s.code === v || s.id === v)
            return st ? localizeStatusName(st.name) : v
          }
          if (f.field === 'project') {
            const p = projectList.value.find(pr => pr.id === v)
            return p ? (p.key || p.name) : v
          }
          return v
        }).join(', ')
      } else {
        values = String(f.value || '')
      }

      let prefix = ''
      if (op === 'neq' || op === 'not_in') prefix = '-'

      if (op === 'between' && Array.isArray(f.value) && f.value.length >= 2) {
        parts.push(`${fieldLabel}: ${f.value[0]} .. ${f.value[1]}`)
      } else {
        parts.push(`${fieldLabel}: ${prefix}${values}`)
      }
    }
    return parts.join('  ')
  }

  function queryTextToFilters(text: string): SavedQueryFilter[] {
    if (!text || !text.trim()) return []
    const filters: SavedQueryFilter[] = []
    const normalized = text.replace(/：/g, ':')

    const allFields = [...Object.keys(queryFieldLabelToKey)]
    allFields.sort((a, b) => b.length - a.length)

    interface FieldMatch { field: string; valueStart: number; matchStart: number }
    const matches: FieldMatch[] = []

    for (const fieldLabel of allFields) {
      let searchFrom = 0
      while (true) {
        const idx = normalized.indexOf(`${fieldLabel}:`, searchFrom)
        if (idx < 0) break
        const charBefore = idx > 0 ? normalized[idx - 1] : ' '
        if (idx === 0 || charBefore === ' ') {
          const valueStart = idx + fieldLabel.length + 1
          const afterColon = normalized.substring(valueStart)
          const spaceMatch = afterColon.match(/^\s*/)
          const actualValueStart = valueStart + (spaceMatch ? spaceMatch[0].length : 0)
          matches.push({ field: fieldLabel, valueStart: actualValueStart, matchStart: idx })
        }
        searchFrom = idx + 1
      }
    }

    matches.sort((a, b) => a.matchStart - b.matchStart)

    for (let i = 0; i < matches.length; i++) {
      const { field: fieldLabel, valueStart } = matches[i]
      const valueEnd = i + 1 < matches.length ? matches[i + 1].matchStart : normalized.length
      let valuePart = normalized.substring(valueStart, valueEnd).trim()

      const fieldKey = queryFieldLabelToKey[fieldLabel] || fieldLabel

      if (valuePart === '未关闭') { filters.push({ field: fieldKey, operator: 'open', value: [] }); continue }
      if (valuePart === '已关闭') { filters.push({ field: fieldKey, operator: 'closed', value: [] }); continue }
      if (valuePart === '无') { filters.push({ field: fieldKey, operator: 'is_empty', value: [] }); continue }
      if (valuePart === '有') { filters.push({ field: fieldKey, operator: 'is_not_empty', value: [] }); continue }

      if (valuePart.includes('..')) {
        const rangeParts = valuePart.split('..').map(p => p.trim())
        if (rangeParts.length === 2 && rangeParts[0] && rangeParts[1]) {
          filters.push({ field: fieldKey, operator: 'between', value: rangeParts })
          continue
        }
      }

      const isNegative = valuePart.startsWith('-')
      if (isNegative) valuePart = valuePart.substring(1).trim()

      const values = valuePart.split(/[,，]/).map(v => v.trim()).filter(v => v)
      if (values.length === 0) continue

      const resolvedValues = values.map(v => resolveValueToId(fieldKey, v))

      const operator = isNegative
        ? (resolvedValues.length > 1 ? 'not_in' : 'neq')
        : (resolvedValues.length > 1 ? 'in' : 'eq')

      filters.push({ field: fieldKey, operator, value: resolvedValues })
    }
    return filters
  }

  function resolveValueToId(fieldKey: string, v: string): string {
    if (v === '我') return '${currentUser}'
    if (fieldKey === 'project') {
      const p = projectList.value.find(pr => pr.key === v || pr.name === v)
      return p ? p.id : v
    }
    if (fieldKey === 'priority') return priorityReverseLabelMap[v] || v
    if (fieldKey === 'type') {
      const entry = issueTypeOptions.value.find(o => o.label === v || o.value === v)
      return entry ? entry.value : v
    }
    if (fieldKey === 'status') {
      const st = statusCache.value.find(s => localizeStatusName(s.name) === v || s.name === v)
      return st ? st.id : v
    }
    return v
  }

  return {
    // State
    savedQueries,
    activeQueryId,
    activeQueryName,
    activeQueryObj,
    expandedGroups,
    panelSearch,
    panelLoadFailed,
    panelWidth,
    filteredQueries,
    // Tags
    favoriteTags,
    activeTagId,
    showManageTagsModal,
    availableTags,
    // Projects
    favoriteProjects,
    showManageProjectsModal,
    manageProjectSearch,
    manageProjectsLoading,
    filteredManageProjects,
    // Create/Edit/Rename query modals
    showCreateQueryModal,
    createQueryLoading,
    queryIconOptions,
    createQueryForm,
    showEditQueryModal,
    editQueryLoading,
    editQueryForm,
    showRenameQueryModal,
    renameQueryLoading,
    renameQueryForm,
    // Manage favorites
    showManageQueriesModal,
    manageQuerySearch,
    filteredManageQueries,
    // Methods
    loadPanel,
    loadTags,
    toggleGroup,
    isOwnQuery,
    isOwnQueryById,
    openCreateQueryModal,
    handleCreateQuery,
    confirmDeleteQuery,
    openEditQueryModal,
    handleEditQuery,
    openRenameQueryModal,
    handleRenameQuery,
    toggleQueryShared,
    toggleQueryPinned,
    openManageQueriesModal,
    toggleFavorite,
    handleRemoveFavorite,
    triggerContextMenu,
    openManageProjectsModal,
    toggleProjectFavorite,
    startPanelResize,
    togglePanelCollapse,
    selectTag,
    openManageTagsModal,
    toggleTagFavorite,
    filtersToQueryText,
    queryTextToFilters,
    resolveValueToId
  }
}
