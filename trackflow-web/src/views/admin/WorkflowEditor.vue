<template>
  <div class="workflow-page">
    <div class="page-header">
      <h2 class="page-title">工作流编辑器</h2>
      <div class="header-filters">
        <a-select
          v-model="selectedProject"
          placeholder="选择项目"
          style="width: 180px"
          @change="onFilterChange"
        >
          <a-option value="0">全局（默认）</a-option>
          <a-option
            v-for="p in projects"
            :key="p.id"
            :value="p.id"
          >{{ p.name }}</a-option>
        </a-select>

        <a-select
          v-model="selectedType"
          placeholder="工单类型"
          style="width: 140px"
          @change="onFilterChange"
        >
          <a-option value="*">所有类型</a-option>
          <a-option
            v-for="t in issueTypes"
            :key="t"
            :value="t"
          >{{ t }}</a-option>
        </a-select>

        <a-select
          v-model="selectedRole"
          placeholder="角色"
          style="width: 160px"
          @change="onFilterChange"
        >
          <a-option
            v-for="role in roles"
            :key="role.id"
            :value="role.id"
          >{{ role.name }}</a-option>
        </a-select>

        <a-button type="primary" :loading="saving" @click="saveMatrix">
          保存工作流
        </a-button>
        <a-button @click="showHistory = true">
          <template #icon><icon-history /></template>
          变更历史
        </a-button>
      </div>
    </div>

    <!-- Author/Assignee 模式 Tab -->
    <div class="mode-tabs" v-if="statuses.length > 0">
      <a-radio-group v-model="selectedMode" type="button" @change="onFilterChange">
        <a-radio value="normal">
          <template #default>
            基础规则
            <a-tooltip content="对拥有该角色的所有用户生效的通用转换规则。">
              <icon-info-circle class="mode-info-icon" />
            </a-tooltip>
          </template>
        </a-radio>
        <a-radio value="author">
          <template #default>
            创建者额外规则
            <a-tooltip content="此处配置的规则仅对工单创建者额外生效。例如：允许创建者取消自己提交的工单。">
              <icon-info-circle class="mode-info-icon" />
            </a-tooltip>
          </template>
        </a-radio>
        <a-radio value="assignee">
          <template #default>
            负责人额外规则
            <a-tooltip content="此处配置的规则仅对工单负责人额外生效。例如：允许负责人直接关闭自己负责的工单。">
              <icon-info-circle class="mode-info-icon" />
            </a-tooltip>
          </template>
        </a-radio>
      </a-radio-group>
    </div>

    <!-- 矩阵工具栏：搜索 + 筛选 -->
    <div class="matrix-toolbar" v-if="statuses.length > 0">
      <a-input
        v-model="searchKeyword"
        placeholder="搜索状态名..."
        style="width: 200px"
        allow-clear
        @clear="searchKeyword = ''"
      >
        <template #prefix><icon-search /></template>
      </a-input>

      <a-switch
        v-model="onlyConfigured"
        checked-text="只显示已配置"
        unchecked-text="显示全部"
      />

      <span class="toolbar-stats">
        {{ filteredStatuses.length }} / {{ statuses.length }} 个状态
      </span>
    </div>

    <a-spin :loading="loading" tip="加载中...">
      <!-- 转换矩阵 -->
      <div class="matrix-container" v-if="filteredStatuses.length > 0">
        <table class="matrix-table">
          <thead>
            <tr>
              <th class="corner-cell">从 ↓ / 到 →</th>
              <template v-for="group in columnGroups" :key="group.category">
                <th
                  v-if="group.statuses.length > 0"
                  :colspan="group.statuses.length"
                  class="group-header"
                  :class="'group-' + group.category"
                >
                  {{ localizeCategoryName(group.category) }}
                  <span class="group-count">({{ group.statuses.length }})</span>
                </th>
              </template>
            </tr>
            <tr>
              <th class="corner-cell corner-cell-sub"></th>
              <th
                v-for="(status, colIdx) in filteredStatuses"
                :key="status.id"
                class="col-header"
                :class="{ highlighted: highlightCol === colIdx }"
              >
                <span class="status-dot" :style="{ background: status.color }"></span>
                <span class="col-header-name" :title="localizeStatusName(status.name)">
                  {{ localizeStatusName(status.name) }}
                </span>
              </th>
            </tr>
          </thead>
          <tbody>
            <template v-for="group in rowGroups" :key="group.category">
              <!-- 行分组标题 -->
              <tr v-if="group.statuses.length > 0" class="group-row">
                <td
                  :colspan="filteredStatuses.length + 1"
                  class="group-row-header"
                  :class="'group-' + group.category"
                  @click="toggleGroupCollapse(group.category)"
                >
                  <span class="group-toggle">{{ collapsedGroups.has(group.category) ? '▶' : '▼' }}</span>
                  {{ localizeCategoryName(group.category) }}
                  <span class="group-count">({{ group.statuses.length }})</span>
                </td>
              </tr>
              <!-- 行数据 -->
              <template v-if="!collapsedGroups.has(group.category)">
                <tr
                  v-for="fromStatus in group.statuses"
                  :key="fromStatus.id"
                >
                  <td
                    class="row-header"
                    :class="{ highlighted: highlightRow === getRowIndex(fromStatus) }"
                  >
                    <span class="status-dot" :style="{ background: fromStatus.color }"></span>
                    {{ localizeStatusName(fromStatus.name) }}
                  </td>
                  <td
                    v-for="(toStatus, colIdx) in filteredStatuses"
                    :key="toStatus.id"
                    class="matrix-cell"
                    :class="{
                      disabled: fromStatus.id === toStatus.id,
                      clickable: fromStatus.id !== toStatus.id && isAllowed(fromStatus.id, toStatus.id),
                      highlighted: highlightRow === getRowIndex(fromStatus) || highlightCol === colIdx
                    }"
                    @click="fromStatus.id !== toStatus.id && isAllowed(fromStatus.id, toStatus.id) && openActionPanel(fromStatus, toStatus)"
                    @mouseenter="onCellHover(fromStatus, colIdx)"
                    @mouseleave="onCellLeave"
                  >
                    <div v-if="fromStatus.id !== toStatus.id" class="cell-content">
                      <input
                        type="checkbox"
                        :checked="isAllowed(fromStatus.id, toStatus.id)"
                        @change="toggleTransition(fromStatus.id, toStatus.id)"
                        @click.stop
                        class="matrix-checkbox"
                      />
                      <span
                        v-if="hasAction(fromStatus.id, toStatus.id)"
                        class="action-dot"
                        title="已配置动作"
                      ></span>
                    </div>
                    <span v-else class="cell-dash">—</span>
                  </td>
                </tr>
              </template>
            </template>
          </tbody>
        </table>
      </div>

      <!-- 搜索无结果 -->
      <div v-else-if="!loading && statuses.length > 0 && filteredStatuses.length === 0" class="empty-state">
        <icon-search :size="48" />
        <h3>没有匹配的状态</h3>
        <p>尝试修改搜索关键词或关闭"只显示已配置"筛选。</p>
        <a-button type="primary" @click="resetFilters">重置筛选</a-button>
      </div>

      <!-- 空状态 -->
      <div v-else-if="!loading" class="empty-state">
        <icon-settings :size="48" />
        <h3>暂无状态数据</h3>
        <p>系统中未定义任何工单状态，请先配置状态列表。</p>
      </div>
    </a-spin>

    <div class="help-text" v-if="statuses.length > 0">
      <icon-info-circle />
      <template v-if="selectedMode === 'normal'">
        勾选单元格表示允许从行状态转换到列状态（针对当前选择的角色，对所有拥有该角色的用户生效）。
      </template>
      <template v-else-if="selectedMode === 'author'">
        勾选单元格表示仅当用户是工单<b>创建者</b>时，额外允许此转换（不影响基础规则）。
      </template>
      <template v-else>
        勾选单元格表示仅当用户是工单<b>负责人</b>时，额外允许此转换（不影响基础规则）。
      </template>
      点击已允许的转换可配置自动化动作。hover 单元格高亮对应行列。
    </div>

    <!-- 动作配置面板 -->
    <TransitionActionPanel
      v-model:visible="actionPanelVisible"
      :project-id="selectedProject"
      :old-status-id="actionPanelFrom"
      :new-status-id="actionPanelTo"
      :old-status-name="actionPanelFromName"
      :new-status-name="actionPanelToName"
      :issue-type="selectedType"
      @refresh="onActionRefresh"
    />

    <!-- 变更历史抽屉 -->
    <WorkflowActivityDrawer
      v-model:visible="showHistory"
      :project-id="selectedProject"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { IconSettings, IconInfoCircle, IconHistory, IconSearch } from '@arco-design/web-vue/es/icon'
import { issueApi, projectApi, workflowApi, transitionActionApi } from '@/api'
import type { IssueStatusVO, ProjectVO, RoleVO } from '@/api/types'
import TransitionActionPanel from './TransitionActionPanel.vue'
import WorkflowActivityDrawer from './WorkflowActivityDrawer.vue'
import { localizeStatusName, localizeCategoryName } from '@/utils/fieldLabels'

const selectedProject = ref('0')
const selectedType = ref('*')
const selectedRole = ref('')
const selectedMode = ref<'normal' | 'author' | 'assignee'>('normal')
const loading = ref(false)
const saving = ref(false)
const showHistory = ref(false)

const statuses = ref<IssueStatusVO[]>([])
const projects = ref<ProjectVO[]>([])
const roles = ref<RoleVO[]>([])
const issueTypes = ref<string[]>([])

// 矩阵工具栏状态
const searchKeyword = ref('')
const onlyConfigured = ref(false)
const collapsedGroups = reactive(new Set<string>())

// 行列高亮
const highlightRow = ref<number | null>(null)
const highlightCol = ref<number | null>(null)

// 转换矩阵 Set: "fromId-toId"
const allowedTransitions = reactive(new Set<string>())

// 动作指示器: "fromId-toId" 有配置动作的转换路径
const actionPaths = reactive(new Set<string>())

// 动作面板状态
const actionPanelVisible = ref(false)
const actionPanelFrom = ref('')
const actionPanelTo = ref('')
const actionPanelFromName = ref('')
const actionPanelToName = ref('')

// --- 分类顺序 ---
const categoryOrder = ['open', 'in_progress', 'done', 'cancelled']

// --- 筛选后的状态列表 ---
const filteredStatuses = computed(() => {
  let result = [...statuses.value]

  // 按搜索关键词筛选
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    result = result.filter(s => {
      const localized = localizeStatusName(s.name).toLowerCase()
      const original = s.name.toLowerCase()
      return localized.includes(kw) || original.includes(kw)
    })
  }

  // 只显示已配置转换
  if (onlyConfigured.value) {
    const idsWithTransitions = new Set<string>()
    for (const key of allowedTransitions) {
      const [from, to] = key.split('-')
      idsWithTransitions.add(from)
      idsWithTransitions.add(to)
    }
    result = result.filter(s => idsWithTransitions.has(s.id))
  }

  // 按 category 排序
  result.sort((a, b) => {
    const ai = categoryOrder.indexOf(a.category)
    const bi = categoryOrder.indexOf(b.category)
    if (ai !== bi) return ai - bi
    return (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
  })

  return result
})

// --- 分组（行和列） ---
interface StatusGroup {
  category: string
  statuses: IssueStatusVO[]
}

const rowGroups = computed<StatusGroup[]>(() => {
  return buildGroups(filteredStatuses.value)
})

const columnGroups = computed<StatusGroup[]>(() => {
  return buildGroups(filteredStatuses.value)
})

function buildGroups(list: IssueStatusVO[]): StatusGroup[] {
  const groups: StatusGroup[] = []
  for (const cat of categoryOrder) {
    const items = list.filter(s => s.category === cat)
    if (items.length > 0) {
      groups.push({ category: cat, statuses: items })
    }
  }
  // 处理未知分类
  const known = new Set(categoryOrder)
  const unknown = list.filter(s => !known.has(s.category))
  if (unknown.length > 0) {
    groups.push({ category: 'other', statuses: unknown })
  }
  return groups
}

// --- 行索引（用于高亮） ---
function getRowIndex(status: IssueStatusVO): number {
  return filteredStatuses.value.findIndex(s => s.id === status.id)
}

// --- 分组折叠 ---
function toggleGroupCollapse(category: string) {
  if (collapsedGroups.has(category)) {
    collapsedGroups.delete(category)
  } else {
    collapsedGroups.add(category)
  }
}

// --- Hover 高亮 ---
function onCellHover(fromStatus: IssueStatusVO, colIdx: number) {
  highlightRow.value = getRowIndex(fromStatus)
  highlightCol.value = colIdx
}

function onCellLeave() {
  highlightRow.value = null
  highlightCol.value = null
}

// --- 重置筛选 ---
function resetFilters() {
  searchKeyword.value = ''
  onlyConfigured.value = false
}

function isAllowed(from: string, to: string) {
  return allowedTransitions.has(`${from}-${to}`)
}

function toggleTransition(from: string, to: string) {
  const key = `${from}-${to}`
  if (allowedTransitions.has(key)) {
    allowedTransitions.delete(key)
  } else {
    allowedTransitions.add(key)
  }
}

function hasAction(from: string, to: string): boolean {
  return actionPaths.has(`${from}-${to}`)
}

function openActionPanel(fromStatus: IssueStatusVO, toStatus: IssueStatusVO) {
  if (!isAllowed(fromStatus.id, toStatus.id)) return
  actionPanelFrom.value = fromStatus.id
  actionPanelTo.value = toStatus.id
  actionPanelFromName.value = localizeStatusName(fromStatus.name)
  actionPanelToName.value = localizeStatusName(toStatus.name)
  actionPanelVisible.value = true
}

async function loadActionPaths() {
  try {
    const projectId = selectedProject.value || '0'
    const res = await transitionActionApi.list(projectId)
    const actions = res.data || []
    actionPaths.clear()
    for (const a of actions) {
      actionPaths.add(`${a.oldStatusId}-${a.newStatusId}`)
    }
  } catch {
    actionPaths.clear()
  }
}

function onActionRefresh() {
  loadActionPaths()
}

function onFilterChange() {
  loadMatrix()
  loadActionPaths()
}

async function loadStatuses() {
  try {
    const res = await issueApi.listStatuses()
    statuses.value = res.data || []
  } catch {
    statuses.value = []
    Message.error('加载状态列表失败')
  }
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ page: 1, pageSize: 100 })
    projects.value = res.data?.list || []
  } catch {
    projects.value = []
    Message.error('加载项目列表失败')
  }
}

async function loadRoles() {
  try {
    const res = await workflowApi.listProjectRoles()
    roles.value = res.data || []
    // 默认选中第一个角色
    if (roles.value.length > 0 && !selectedRole.value) {
      selectedRole.value = roles.value[0].id
    }
  } catch {
    roles.value = []
    Message.error('加载角色列表失败')
  }
}

async function loadIssueTypes() {
  try {
    const res = await workflowApi.listIssueTypes()
    issueTypes.value = res.data || []
  } catch {
    issueTypes.value = ['Bug', 'Task', 'Feature']
  }
}

async function loadMatrix() {
  if (!selectedRole.value) return
  loading.value = true
  try {
    const params: Record<string, string | boolean> = { roleId: selectedRole.value }
    if (selectedType.value !== '*') params.issueType = selectedType.value

    // 按模式过滤 author/assignee
    if (selectedMode.value === 'normal') {
      params.author = false
      params.assignee = false
    } else if (selectedMode.value === 'author') {
      params.author = true
    } else if (selectedMode.value === 'assignee') {
      params.assignee = true
    }

    const projectId = selectedProject.value || '0'

    const res = await workflowApi.getTransitionMatrix(projectId, params)
    const transitions = res.data || []

    allowedTransitions.clear()
    for (const t of transitions) {
      allowedTransitions.add(`${t.oldStatusId}-${t.newStatusId}`)
    }
  } catch {
    allowedTransitions.clear()
    Message.error('加载工作流数据失败')
  } finally {
    loading.value = false
  }
}

async function saveMatrix() {
  const modeLabel = selectedMode.value === 'normal' ? '基础规则'
    : selectedMode.value === 'author' ? '创建者额外规则' : '负责人额外规则'

  Modal.confirm({
    title: '确认更新工作流',
    content: `此操作将替换当前筛选条件下"${modeLabel}"模式的所有转换规则，确认保存？`,
    okText: '确认保存',
    cancelText: '取消',
    async onOk() {
      const transitions = Array.from(allowedTransitions).map(key => {
        const [from, to] = key.split('-')
        return { from: Number(from), to: Number(to), allowed: true }
      })

      saving.value = true
      try {
        const projectId = selectedProject.value || '0'
        await workflowApi.updateTransitionMatrix(projectId, {
          issueType: selectedType.value,
          roleId: Number(selectedRole.value),
          author: selectedMode.value === 'author' ? true : false,
          assignee: selectedMode.value === 'assignee' ? true : false,
          transitions
        })
        Message.success('工作流已保存')
      } catch {
        Message.error('保存失败，请检查权限或重试')
      } finally {
        saving.value = false
      }
    }
  })
}

onMounted(async () => {
  await Promise.all([loadStatuses(), loadProjects(), loadRoles(), loadIssueTypes()])
  await Promise.all([loadMatrix(), loadActionPaths()])
})
</script>

<style scoped>
.workflow-page {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-bright);
}

.header-filters {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* 矩阵工具栏 */
.matrix-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 8px 12px;
  background: var(--bg-secondary);
  border-radius: 6px;
}

.toolbar-stats {
  font-size: 12px;
  color: var(--text-muted);
  margin-left: auto;
}

.matrix-container {
  overflow-x: auto;
}

.matrix-table {
  border-collapse: collapse;
  width: 100%;
  min-width: max-content;
}

.matrix-table th,
.matrix-table td {
  padding: 6px 8px;
  border: 1px solid var(--border-color);
  text-align: center;
  font-size: 11px;
}

/* 分组头（列方向） */
.group-header {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-weight: 600;
  font-size: 11px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  padding: 4px 8px;
  border-bottom: none;
}

.group-header.group-open { border-top: 2px solid #58a6ff; }
.group-header.group-in_progress { border-top: 2px solid #d29922; }
.group-header.group-done { border-top: 2px solid #3fb950; }
.group-header.group-cancelled { border-top: 2px solid #f85149; }

.group-count {
  font-weight: 400;
  opacity: 0.7;
}

.corner-cell {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-weight: 500;
  text-align: left;
  min-width: 140px;
  position: sticky;
  left: 0;
  z-index: 3;
}

.corner-cell-sub {
  border-top: none;
}

.col-header {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-weight: 500;
  white-space: nowrap;
  font-size: 11px;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: background-color 100ms ease;
}

.col-header.highlighted {
  background: var(--bg-hover, rgba(88, 166, 255, 0.08));
}

.col-header-name {
  display: inline-block;
  max-width: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}

/* 行分组标题 */
.group-row-header {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-weight: 600;
  font-size: 11px;
  letter-spacing: 0.5px;
  text-align: left;
  padding: 4px 12px;
  cursor: pointer;
  user-select: none;
  position: sticky;
  left: 0;
  z-index: 2;
  transition: background-color 150ms ease;
}

.group-row-header:hover {
  background: var(--bg-hover, rgba(255, 255, 255, 0.04));
}

.group-row-header.group-open { border-left: 3px solid #58a6ff; }
.group-row-header.group-in_progress { border-left: 3px solid #d29922; }
.group-row-header.group-done { border-left: 3px solid #3fb950; }
.group-row-header.group-cancelled { border-left: 3px solid #f85149; }

.group-toggle {
  display: inline-block;
  width: 16px;
  font-size: 10px;
}

.row-header {
  background: var(--bg-secondary);
  color: var(--text-primary);
  text-align: left;
  font-weight: 500;
  white-space: nowrap;
  position: sticky;
  left: 0;
  z-index: 1;
  transition: background-color 100ms ease;
}

.row-header.highlighted {
  background: var(--bg-hover, rgba(88, 166, 255, 0.08));
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}

.matrix-cell {
  background: var(--bg-primary);
  transition: background-color 100ms ease;
}
.matrix-cell.disabled {
  background: var(--bg-tertiary);
}
.matrix-cell.clickable {
  cursor: pointer;
}
.matrix-cell.clickable:hover {
  background: var(--bg-tertiary, rgba(255, 255, 255, 0.04));
}
.matrix-cell.highlighted {
  background: var(--bg-hover, rgba(88, 166, 255, 0.04));
}

.cell-content {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.action-dot {
  position: absolute;
  top: -4px;
  right: -8px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent-blue, rgb(var(--arcoblue-6)));
  box-shadow: 0 0 0 2px var(--bg-primary, var(--color-bg-2));
}

.matrix-checkbox {
  width: 14px;
  height: 14px;
  cursor: pointer;
  accent-color: var(--accent-blue);
}

.cell-dash {
  color: var(--text-muted);
  font-size: 10px;
}

.help-text {
  margin-top: 16px;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  color: var(--text-muted);
}

.empty-state h3 {
  margin-top: 16px;
  font-size: 16px;
  font-weight: 500;
  color: var(--text-secondary);
}

.empty-state p {
  margin-top: 8px;
  font-size: 13px;
}

/* 模式 Tab */
.mode-tabs {
  margin-bottom: 12px;
}

.mode-info-icon {
  margin-left: 4px;
  font-size: 12px;
  color: var(--text-muted);
  vertical-align: middle;
  cursor: help;
}
</style>
