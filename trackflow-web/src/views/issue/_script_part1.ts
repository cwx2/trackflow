const router = useRouter()
const route = useRoute()

// ===== Core composables =====
const {
  issues, totalIssues, currentPage, pageSize, loading, loadError,
  sortState, loadIssues, goPage, changePageSize, updateLocalIssue, removeLocalIssue
} = useIssueList()

const {
  selectedIds, selectedCount, selectedIssues,
  toggle, toggleAll, clearSelection
} = useSelection(issues)

const { isCellEditing, executeEdit } = useInlineEdit(issues)
const { batchTransitStatus, batchAssign, batchUpdateSprint, batchUpdatePriority, batchTagAdd, batchTagRemove, batchAddLink, batchDelete } = useBatchOps()
const { loadPermissions, canEditIssue, canDeleteIssue } = usePermission(issues)

const {
  layout, density, structure,
  isTreeMode, isListLayout, isTableLayout,
  setLayout, setDensity, setStructure
} = useViewSettings()

const {
  isManualSorted, isOwnerOrder, manualOrderData,
  loadManualOrder, saveOrder: saveManualOrder, discardOrder: discardManualOrder, reset: resetManualOrder
} = useManualOrder()

// Auth & permissions
const authStore = useAuthStore()
const canCreateIssueGlobal = authStore.canCreateIssue

const canBatchOps = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  if (authStore.permissionsLoaded) return authStore.hasGlobalPermission('nav:batch_ops')
  return true
})

const canViewSprintGlobal = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  if (authStore.permissionsLoaded) return authStore.hasGlobalPermission('nav:sprint_view') || authStore.hasGlobalPermission('nav:sprint_manage')
  return true
})

// Shared state (declared early for composable dependencies)
const activeProjectId = ref<string | null>(null)
const filterBarRef = ref<InstanceType<typeof FilterBar> | null>(null)
const filterProject = ref<string | undefined>(undefined)
const searchKeyword = ref('')
const globalFilterParams = ref<Record<string, any>>({})
const initialFilterChips = ref<any[]>([])
const statusCache = ref<IssueStatusVO[]>([])
const sprintOptionsCache = reactive<Record<string, SprintVO[]>>({})

// Priority & issue type options
const priorityOptions = ref(DEFAULT_PRIORITY_OPTIONS.map(o => ({ ...o })))
const issueTypeOptions = ref(DEFAULT_ISSUE_TYPE_OPTIONS.map(o => ({ ...o })))

// Column config
const {
  visibleColumns, toggleColumn, reorderColumn,
  standardColumns, customFieldColumns, isVisible: isColumnVisible, resetToDefault: resetColumns
} = useColumnConfig(activeProjectId as any)

// ===== Project & Tag panel composable =====
const {
  projectList, favoriteProjects, showManageProjectsModal, manageProjectSearch,
  manageProjectsLoading, filteredManageProjects,
  loadProjects, openManageProjectsModal, toggleProjectFavorite,
  favoriteTags, activeTagId, showManageTagsModal, availableTags,
  loadTags, openManageTagsModal, toggleTagFavorite
} = useProjectTagPanel({ activeProjectId })

// Hide resolved toggle
const HIDE_RESOLVED_KEY = 'trackflow:hide-resolved'
const hideResolved = ref(localStorage.getItem(HIDE_RESOLVED_KEY) === 'true')

function toggleHideResolved() {
  hideResolved.value = !hideResolved.value
  localStorage.setItem(HIDE_RESOLVED_KEY, String(hideResolved.value))
  currentPage.value = 1
  refreshList()
  loadPanel()
}

// ===== Query Panel composable =====
const {
  savedQueries, activeQueryId, activeQueryName, activeQueryObj,
  expandedGroups, panelSearch, panelLoadFailed, panelWidth,
  filteredQueries,
  showCreateQueryModal, createQueryLoading, queryIconOptions, createQueryForm,
  showEditQueryModal, editQueryLoading, editQueryForm,
  showRenameQueryModal, renameQueryLoading, renameQueryForm,
  showManageQueriesModal, manageQuerySearch, filteredManageQueries,
  loadPanel, isOwnQuery, isOwnQueryById,
  openCreateQueryModal, handleCreateQuery, confirmDeleteQuery,
  openEditQueryModal, handleEditQuery, openRenameQueryModal, handleRenameQuery,
  toggleQueryShared, toggleQueryPinned,
  openManageQueriesModal, toggleFavorite, handleRemoveFavorite,
  triggerContextMenu, startPanelResize, togglePanelCollapse,
  selectTag: queryPanelSelectTag,
  filtersToQueryText, queryTextToFilters, resolveValueToId
} = useQueryPanel({
  statusCache,
  projectList,
  issueTypeOptions,
  priorityOptions,
  activeProjectId,
  hideResolved,
  refreshList,
  getIssueTypeLabelForRecord
})

// ===== Export composable =====
const { exportLoading, handleExport, onBatchExport } = useIssueExport({
  activeProjectId, filterProject, hideResolved, globalFilterParams, searchKeyword, selectedIds
})

// ===== Dashboard Filter composable =====
const { applyDashboardFilter, hasDashboardFilterParams } = useDashboardFilter({
  activeQueryId, activeQueryObj, activeProjectId, filterProject,
  searchKeyword, globalFilterParams, initialFilterChips, activeQueryName,
  statusCache, sprintOptionsCache, projectList, filterBarRef, refreshList
})

// ===== Preview mode =====
const PREVIEW_MODE_KEY = 'trackflow:preview-mode'
type PreviewMode = 'sidebar' | 'off'
const previewMode = ref<PreviewMode>((localStorage.getItem(PREVIEW_MODE_KEY) as PreviewMode) || 'off')
const previewVisible = ref(false)
const previewIssueId = ref<string | null>(null)
const activeIssueIndex = ref<number>(-1)

function setPreviewMode(mode: PreviewMode) {
  previewMode.value = mode
  localStorage.setItem(PREVIEW_MODE_KEY, mode)
  if (mode === 'off') { previewVisible.value = false; previewIssueId.value = null; activeIssueIndex.value = -1 }
}
function openPreview(issue: IssueVO, index: number) { previewIssueId.value = issue.id; previewVisible.value = true; activeIssueIndex.value = index }
function closePreview() { previewVisible.value = false; activeIssueIndex.value = -1 }
function onPreviewVisibleChange(val: boolean) { previewVisible.value = val; if (!val) activeIssueIndex.value = -1 }
function onPreviewGoDetail(issueId: string) { previewVisible.value = false; router.push({ name: 'IssueDetail', params: { id: issueId } }) }

// ===== Keyboard Nav composable =====
const showCommandDialog = ref(false)
const showShortcutsHelp = ref(false)
const showCreatePanel = ref(false)

const {
  focusedIndex, focusedIssueId, handleKeyboardNav, navigateIssue
} = useKeyboardNav({
  issues, canCreateIssueGlobal, canBatchOps,
  previewMode, previewVisible, previewIssueId, activeIssueIndex,
  showCommandDialog, showCreatePanel, showShortcutsHelp, selectedCount,
  toggle, toggleAll, openPreview, closePreview, onPreviewGoDetail
})

// ===== Context Menu composable =====
const {
  contextMenu, ctxTransitions, ctxTransitionsLoading,
  ctxSprintsLoading, ctxSprintSubVisible, ctxSprintGroups,
  openContextMenu, closeContextMenu,
  onListItemContextMenu, onTableRowContextMenu,
  ctxCopyIssueKey, ctxCopyLink, ctxOpenNewTab,
  ctxSetStatus, ctxLoadSprints, ctxMoveSprint, ctxSelectSprint,
  onGlobalKeydownCtx
} = useContextMenu({ refreshList, updateLocalIssue, sprintOptionsCache, canEditIssue })

// ===== Table Config composable =====
const {
  columnWidths, tableMinWidth, tableColumns, rowSelection,
  onColumnResize, onHeaderSort, onHeaderRemove, onHeaderDragDrop,
  getColumnSortDir, isColumnFixed, isColumnSortable,
  isResolved, getStatusName, getStatusColor, getSprintName,
  getDueDateStatus, getDueDateTooltip, getRowClass,
  formatHoursCell, formatRemainingCell, getSpentHoursClass, getRemainingClass
} = useTableConfig({
  visibleColumns, canBatchOps, statusCache, sortState,
  previewMode, previewVisible, previewIssueId, focusedIssueId,
  sprintOptionsCache, toggleColumn, reorderColumn
})

// ===== Drafts =====
const { draftList, draftCount, hasDrafts, saveDraft, deleteDraft, deleteAllDrafts, getDraft } = useDrafts()
const activeDraftId = ref<string | null>(null)
const recoveredDraftId = ref<string | null>(null)

function formatDraftTime(timestamp: number): string {
  const now = Date.now()
  const diff = now - timestamp
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  return new Date(timestamp).toLocaleDateString()
}
function openDraftCreate() { activeDraftId.value = null; showCreatePanel.value = true }
function openDraft(draft: IssueDraft) { activeDraftId.value = draft.id; showCreatePanel.value = true }
function handleDeleteDraft(draftId: string) { deleteDraft(draftId); Message.success('草稿已删除') }
function handleDeleteAllDrafts() {
  const { confirmDangerDelete } = useConfirmDelete()
  confirmDangerDelete({ itemName: `全部 ${draftCount.value} 个草稿`, impactDescription: '删除后无法恢复', confirmText: '全部删除', onConfirm: () => { deleteAllDrafts(); Message.success('所有草稿已删除') } })
}
function onCreatePanelCancel(formData: any) {
  if (formData && (formData.title?.trim() || formData.description?.trim())) { saveDraft(formData, activeDraftId.value || undefined); Message.info('已保存为草稿') }
  activeDraftId.value = null
}
function onCreatePanelCreated() { if (activeDraftId.value) { deleteDraft(activeDraftId.value); activeDraftId.value = null }; refreshList() }
function onCreatePanelExpand(formData: any) {
  showCreatePanel.value = false; activeDraftId.value = null
  if (formData && (formData.title?.trim() || formData.description?.trim())) { const draftId = saveDraft(formData); if (draftId) { router.push({ name: 'IssueCreate', query: { draftId } }); return } }
  router.push({ name: 'IssueCreate' })
}
const createPanelRef = ref<InstanceType<typeof IssueCreatePanel> | null>(null)
