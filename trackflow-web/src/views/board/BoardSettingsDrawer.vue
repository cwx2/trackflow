<template>
  <a-drawer
    :visible="visible"
    title="看板设置"
    :width="520"
    :mask-closable="true"
    :footer="true"
    @cancel="$emit('update:visible', false)"
  >
    <template #footer>
      <div class="drawer-footer">
        <a-button @click="$emit('update:visible', false)">取消</a-button>
        <a-button type="primary" :loading="saving" @click="handleSave">保存配置</a-button>
      </div>
    </template>

    <a-tabs v-model:active-key="activeTab" class="settings-tabs">
      <!-- 基本设置 标签页 -->
      <a-tab-pane key="general" title="基本设置">
        <GeneralSettingsPanel
          :name="editableBoardName"
          :can-view-roles="editableCanViewRoles"
          :can-edit-roles="editableCanEditRoles"
          :project-name="projectName"
          :filter-mode="editableFilterMode"
          :filter-query="editableFilterQuery"
          :done-retention-days="editableDoneRetentionDays"
          @update:name="editableBoardName = $event"
          @update:can-view-roles="editableCanViewRoles = $event"
          @update:can-edit-roles="editableCanEditRoles = $event"
          @update:filter-mode="editableFilterMode = $event"
          @update:filter-query="editableFilterQuery = $event"
          @update:done-retention-days="editableDoneRetentionDays = $event"
        />
      </a-tab-pane>

      <!-- 列设置 标签页 -->
      <a-tab-pane key="columns" title="列设置">
        <div class="settings-content">
          <div class="settings-hint">
            <p>配置看板中显示的状态列、排列顺序和 WIP 限制。拖拽列项可调整顺序。</p>
          </div>

          <!-- 快捷操作 -->
          <div class="quick-actions">
            <a-button size="mini" @click="selectAll">全选</a-button>
            <a-button size="mini" @click="selectNone">全不选</a-button>
            <a-button size="mini" type="primary" @click="selectRecommended">
              <template #icon><icon-thunderbolt /></template>
              智能推荐
            </a-button>
            <a-button size="mini" @click="selectWithIssues">仅显示有工单的状态</a-button>
          </div>

          <!-- 按 Category 分组的列配置列表 -->
          <div class="column-list">
            <div class="column-list-header">
              <span class="col-h-drag"></span>
              <span class="col-h-visible">显示</span>
              <span class="col-h-name">状态列</span>
              <span class="col-h-count">工单数</span>
              <span class="col-h-wip">WIP 限制</span>
            </div>
            <div
              ref="sortableContainer"
              class="column-list-body"
            >
              <template v-for="group in groupedColumns" :key="group.category">
                <!-- 分组标题 -->
                <div class="column-group-header" @click="toggleGroupCollapse(group.category)">
                  <span class="group-collapse-icon">{{ collapsedGroups.has(group.category) ? '▶' : '▼' }}</span>
                  <span class="group-title">{{ group.label }}</span>
                  <span class="group-count">{{ group.items.filter(c => c.visible).length }}/{{ group.items.length }}</span>
                </div>
                <!-- 分组内容 -->
                <template v-if="!collapsedGroups.has(group.category)">
                  <div
                    v-for="col in group.items"
                    :key="col.statusId"
                    class="column-item"
                    :class="{
                      'column-item--disabled': !col.visible,
                      'column-item--inactive': (col.issueCount ?? 0) === 0,
                      'column-item--dragging': dragIndex === getGlobalIndex(col),
                      'column-item--drop-above': dropIndex === getGlobalIndex(col) && dropPosition === 'above',
                      'column-item--drop-below': dropIndex === getGlobalIndex(col) && dropPosition === 'below'
                    }"
                    :draggable="true"
                    @dragstart="onItemDragStart($event, getGlobalIndex(col))"
                    @dragover="onItemDragOver($event, getGlobalIndex(col))"
                    @dragleave="onItemDragLeave"
                    @drop="onItemDrop($event, getGlobalIndex(col))"
                    @dragend="onItemDragEnd"
                  >
                    <span class="col-drag-handle" title="拖拽排序">⠿</span>
                    <a-checkbox v-model="col.visible" class="col-visible-check" />
                    <div class="col-name-cell">
                      <span class="column-color" :style="{ backgroundColor: col.statusColor }"></span>
                      <span class="column-name">{{ localizeStatusName(col.statusName) }}</span>
                      <span v-if="col.inWorkflow" class="column-workflow-badge" title="工作流中活跃的状态">⚡</span>
                    </div>
                    <div class="col-count-cell">
                      <span
                        class="issue-count-badge"
                        :class="{ 'issue-count-badge--zero': (col.issueCount ?? 0) === 0 }"
                      >{{ col.issueCount ?? 0 }}</span>
                    </div>
                    <div class="col-wip-cell">
                      <a-input-number
                        v-model="col.wipMin"
                        placeholder="Min"
                        size="mini"
                        :min="0"
                        :max="999"
                        :style="{ width: '64px' }"
                        :disabled="!col.visible"
                        hide-button
                        allow-clear
                      />
                      <span class="wip-separator">–</span>
                      <a-input-number
                        v-model="col.wipMax"
                        placeholder="Max"
                        size="mini"
                        :min="0"
                        :max="999"
                        :style="{ width: '64px' }"
                        :disabled="!col.visible"
                        hide-button
                        allow-clear
                      />
                    </div>
                  </div>
                </template>
              </template>
            </div>
          </div>

          <!-- WIP 限制说明 -->
          <div class="wip-help">
            <div class="wip-help-title">WIP 限制说明</div>
            <ul class="wip-help-list">
              <li><span class="wip-indicator wip-indicator--over">3/2</span> 卡片数超过 Max WIP 时列标题显示红色警告</li>
              <li><span class="wip-indicator wip-indicator--under">0/2</span> 卡片数低于 Min WIP 时列标题显示黄色提示</li>
              <li>留空表示不设置限制</li>
            </ul>
          </div>
        </div>
      </a-tab-pane>

      <!-- 卡片设置 标签页 -->
      <a-tab-pane key="cards" title="卡片">
        <CardSettingsPanel
          :visible-fields="editableCardFields"
          :color-scheme="editableColorScheme"
          @update:visible-fields="editableCardFields = $event"
          @update:color-scheme="editableColorScheme = $event"
        />
      </a-tab-pane>

      <!-- 泳道设置 标签页 -->
      <a-tab-pane key="swimlanes" title="泳道">
        <SwimlaneSettingsPanel
          :group-by-field="editableSwimlaneGroupBy"
          :merge-groups="editableMergeGroups"
          :columns="editableColumns"
          @update:group-by-field="editableSwimlaneGroupBy = $event"
          @update:merge-groups="editableMergeGroups = $event"
        />
      </a-tab-pane>
    </a-tabs>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { Message } from '@arco-design/web-vue'
import { boardApi } from '@/api'
import type { BoardColumnVO, BoardColumnItem } from '@/api/types'
import { localizeStatusName } from '@/utils/fieldLabels'
import CardSettingsPanel from './CardSettingsPanel.vue'
import GeneralSettingsPanel from './GeneralSettingsPanel.vue'
import SwimlaneSettingsPanel from './SwimlaneSettingsPanel.vue'

interface EditableColumn {
  statusId: string
  statusName: string
  statusCode: string
  statusColor: string
  statusCategory: string
  visible: boolean
  sortOrder: number
  collapsed: boolean
  wipMin: number | null | undefined
  wipMax: number | null | undefined
  issueCount: number | null
  inWorkflow: boolean | null
}

interface MergeGroupLocal {
  mergeGroupId: string
  mergeTitle: string
  statusIds: string[]
}

const props = defineProps<{
  visible: boolean
  projectId: string
  projectName: string
  columns: BoardColumnVO[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'saved': []
}>()

const saving = ref(false)
const initializing = ref(false)
const activeTab = ref('general')

// 列设置状态
const editableColumns = ref<EditableColumn[]>([])

// 卡片设置状态
const editableCardFields = ref<string[]>(['assignee', 'priority', 'type'])
const editableColorScheme = ref('none')

// 基本设置状态
const editableBoardName = ref('')
const editableCanViewRoles = ref<string[]>(['project_admin', 'tech_lead', 'developer', 'product_manager', 'tester', 'observer'])
const editableCanEditRoles = ref<string[]>(['project_admin', 'tech_lead'])
const editableFilterMode = ref('all')
const editableFilterQuery = ref<string | null>(null)
const editableDoneRetentionDays = ref<number | null>(null)

// 泳道设置状态
const editableSwimlaneGroupBy = ref('none')
const editableMergeGroups = ref<MergeGroupLocal[]>([])

// 拖拽排序状态
const dragIndex = ref<number | null>(null)
const dropIndex = ref<number | null>(null)
const dropPosition = ref<'above' | 'below' | null>(null)

// 当 drawer 打开时加载配置
watch(() => props.visible, async (newVisible) => {
  if (newVisible && props.projectId) {
    // 加载列配置
    if (props.columns.length > 0) {
      editableColumns.value = props.columns.map(c => ({
        ...c,
        wipMin: c.wipMin ?? undefined,
        wipMax: c.wipMax ?? undefined,
        issueCount: c.issueCount ?? 0,
        inWorkflow: c.inWorkflow ?? false
      }))
    } else {
      initializing.value = true
      try {
        const res = await boardApi.initializeColumns(props.projectId)
        const initialized = res.data || []
        editableColumns.value = initialized.map(c => ({
          ...c,
          wipMin: c.wipMin ?? undefined,
          wipMax: c.wipMax ?? undefined,
          issueCount: c.issueCount ?? 0,
          inWorkflow: c.inWorkflow ?? false
        }))
        emit('saved')
      } catch (e: any) {
        Message.error(e.response?.data?.message || '初始化看板列配置失败')
      } finally {
        initializing.value = false
      }
    }

    // 加载卡片配置
    try {
      const res = await boardApi.getCardConfig(props.projectId)
      if (res.data) {
        editableCardFields.value = res.data.visibleFields || ['assignee', 'priority', 'type']
        editableColorScheme.value = res.data.colorScheme || 'none'
      }
    } catch {
      // 使用默认值
      editableCardFields.value = ['assignee', 'priority', 'type']
      editableColorScheme.value = 'none'
    }

    // 加载泳道配置
    try {
      const res = await boardApi.getSwimlaneConfig(props.projectId)
      if (res.data) {
        editableSwimlaneGroupBy.value = res.data.groupByField || 'none'
      }
    } catch {
      editableSwimlaneGroupBy.value = 'none'
    }

    // 加载列合并配置
    try {
      const res = await boardApi.getColumnMerges(props.projectId)
      if (res.data && res.data.length > 0) {
        editableMergeGroups.value = res.data.map(g => ({
          mergeGroupId: g.mergeGroupId,
          mergeTitle: g.mergeTitle,
          statusIds: g.statusIds
        }))
      } else {
        editableMergeGroups.value = []
      }
    } catch {
      editableMergeGroups.value = []
    }

    // 加载基本设置
    try {
      const res = await boardApi.getGeneralConfig(props.projectId)
      if (res.data) {
        editableBoardName.value = res.data.name || ''
        editableCanViewRoles.value = res.data.canViewRoles || ['project_admin', 'tech_lead', 'developer', 'product_manager', 'tester', 'observer']
        editableCanEditRoles.value = res.data.canEditRoles || ['project_admin', 'tech_lead']
        editableFilterMode.value = res.data.filterMode || 'all'
        editableFilterQuery.value = res.data.filterQuery ?? null
        editableDoneRetentionDays.value = res.data.doneRetentionDays ?? null
      }
    } catch {
      editableBoardName.value = ''
      editableCanViewRoles.value = ['project_admin', 'tech_lead', 'developer', 'product_manager', 'tester', 'observer']
      editableCanEditRoles.value = ['project_admin', 'tech_lead']
      editableFilterMode.value = 'all'
      editableFilterQuery.value = null
      editableDoneRetentionDays.value = null
    }
  }
})

// 当 columns prop 变化且 drawer 打开时也更新
watch(() => props.columns, () => {
  if (props.visible && props.columns.length > 0) {
    editableColumns.value = props.columns.map(c => ({
      ...c,
      wipMin: c.wipMin ?? undefined,
      wipMax: c.wipMax ?? undefined,
      issueCount: c.issueCount ?? 0,
      inWorkflow: c.inWorkflow ?? false
    }))
  }
})

function categoryLabel(category: string): string {
  const map: Record<string, string> = {
    open: '待办',
    in_progress: '进行中',
    done: '已完成',
    cancelled: '已取消'
  }
  return map[category] || category
}

// 分组折叠状态
const collapsedGroups = ref<Set<string>>(new Set())

function toggleGroupCollapse(category: string) {
  const newSet = new Set(collapsedGroups.value)
  if (newSet.has(category)) {
    newSet.delete(category)
  } else {
    newSet.add(category)
  }
  collapsedGroups.value = newSet
}

// 按 category 分组
interface ColumnGroup {
  category: string
  label: string
  items: EditableColumn[]
}

const CATEGORY_ORDER = ['open', 'in_progress', 'done', 'cancelled']
const CATEGORY_LABELS: Record<string, string> = {
  open: '待办',
  in_progress: '进行中',
  done: '已完成',
  cancelled: '已取消'
}

const groupedColumns = computed<ColumnGroup[]>(() => {
  const groups: Map<string, EditableColumn[]> = new Map()
  for (const cat of CATEGORY_ORDER) {
    groups.set(cat, [])
  }
  for (const col of editableColumns.value) {
    const cat = col.statusCategory || 'open'
    if (!groups.has(cat)) {
      groups.set(cat, [])
    }
    groups.get(cat)!.push(col)
  }
  return CATEGORY_ORDER
    .filter(cat => (groups.get(cat) || []).length > 0)
    .map(cat => ({
      category: cat,
      label: CATEGORY_LABELS[cat] || cat,
      items: groups.get(cat)!
    }))
})

// 获取列项在 editableColumns 中的全局索引
function getGlobalIndex(col: EditableColumn): number {
  return editableColumns.value.findIndex(c => c.statusId === col.statusId)
}

function selectAll() {
  editableColumns.value.forEach(c => c.visible = true)
}

function selectNone() {
  editableColumns.value.forEach(c => c.visible = false)
}

/**
 * 智能推荐：选中种子状态 + 有工单的状态
 * 种子状态：open, in_progress, code_review, testing, done, cancelled
 */
function selectRecommended() {
  const seedCodes = new Set(['open', 'in_progress', 'code_review', 'testing', 'done', 'cancelled'])
  editableColumns.value.forEach(c => {
    c.visible = seedCodes.has(c.statusCode) || (c.issueCount ?? 0) > 0
  })
  Message.success('已按智能推荐设置列显示')
}

/**
 * 仅显示有工单的状态
 */
function selectWithIssues() {
  const hasAnyIssues = editableColumns.value.some(c => (c.issueCount ?? 0) > 0)
  if (!hasAnyIssues) {
    Message.warning('当前项目暂无工单，无法筛选')
    return
  }
  editableColumns.value.forEach(c => {
    c.visible = (c.issueCount ?? 0) > 0
  })
  Message.success('已显示有工单的状态列')
}

// ===== 拖拽排序 =====

function onItemDragStart(event: DragEvent, index: number) {
  dragIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(index))
  }
}

function onItemDragOver(event: DragEvent, index: number) {
  event.preventDefault()
  if (dragIndex.value === null || dragIndex.value === index) {
    dropIndex.value = null
    dropPosition.value = null
    return
  }

  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const midY = rect.top + rect.height / 2
  dropIndex.value = index
  dropPosition.value = event.clientY < midY ? 'above' : 'below'

  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onItemDragLeave() {
  dropIndex.value = null
  dropPosition.value = null
}

function onItemDrop(event: DragEvent, targetIndex: number) {
  event.preventDefault()
  if (dragIndex.value === null || dragIndex.value === targetIndex) {
    resetDragState()
    return
  }

  const items = [...editableColumns.value]
  const [draggedItem] = items.splice(dragIndex.value, 1)

  let insertAt = targetIndex
  if (dragIndex.value < targetIndex) {
    insertAt = dropPosition.value === 'above' ? targetIndex - 1 : targetIndex
  } else {
    insertAt = dropPosition.value === 'above' ? targetIndex : targetIndex + 1
  }

  items.splice(insertAt, 0, draggedItem)
  editableColumns.value = items

  resetDragState()
}

function onItemDragEnd() {
  resetDragState()
}

function resetDragState() {
  dragIndex.value = null
  dropIndex.value = null
  dropPosition.value = null
}

// ===== 保存 =====

async function handleSave() {
  saving.value = true
  try {
    // 保存列配置
    const visibleCount = editableColumns.value.filter(c => c.visible).length
    if (visibleCount === 0) {
      Message.warning('至少需要显示一个状态列')
      saving.value = false
      return
    }

    for (const col of editableColumns.value) {
      if (col.wipMin != null && col.wipMax != null && col.wipMin > col.wipMax) {
        Message.warning(`「${localizeStatusName(col.statusName)}」的 Min WIP 不能大于 Max WIP`)
        saving.value = false
        return
      }
    }

    const columns: BoardColumnItem[] = editableColumns.value.map((c, idx) => ({
      statusId: Number(c.statusId),
      visible: c.visible,
      sortOrder: idx,
      collapsed: c.collapsed,
      wipMin: c.wipMin != null ? c.wipMin : null,
      wipMax: c.wipMax != null ? c.wipMax : null
    }))

    // 保存卡片配置
    if (editableCardFields.value.length === 0) {
      Message.warning('卡片至少需要显示一个字段')
      saving.value = false
      return
    }

    // 校验合并组
    const validMergeGroups = editableMergeGroups.value.filter(g => g.statusIds.length >= 2)
    for (const group of validMergeGroups) {
      if (!group.mergeTitle.trim()) {
        Message.warning('合并组标题不能为空')
        saving.value = false
        return
      }
    }

    await Promise.all([
      boardApi.saveColumns(props.projectId, columns),
      boardApi.saveCardConfig(props.projectId, {
        visibleFields: editableCardFields.value,
        colorScheme: editableColorScheme.value
      }),
      boardApi.saveSwimlaneConfig(props.projectId, {
        groupByField: editableSwimlaneGroupBy.value
      }),
      boardApi.saveColumnMerges(props.projectId, {
        mergeGroups: validMergeGroups.map(g => ({
          mergeGroupId: g.mergeGroupId,
          mergeTitle: g.mergeTitle.trim(),
          statusIds: g.statusIds.map(id => Number(id))
        }))
      }),
      boardApi.saveGeneralConfig(props.projectId, {
        name: editableBoardName.value.trim(),
        canViewRoles: editableCanViewRoles.value,
        canEditRoles: editableCanEditRoles.value,
        filterMode: editableFilterMode.value,
        filterQuery: editableFilterQuery.value,
        doneRetentionDays: editableDoneRetentionDays.value
      })
    ])

    Message.success('看板设置已保存')
    emit('update:visible', false)
    emit('saved')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.settings-tabs {
  height: 100%;
}

.settings-tabs :deep(.arco-tabs-content) {
  padding-top: 16px;
}

.settings-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings-hint p {
  font-size: 13px;
  color: var(--color-text-3);
  margin: 0;
  line-height: 1.5;
}

.quick-actions {
  display: flex;
  gap: 8px;
}

/* ===== 列配置列表 ===== */
.column-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  overflow: hidden;
}

.column-list-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-fill-1);
  border-bottom: 1px solid var(--color-border);
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-3);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.col-h-drag {
  width: 20px;
  flex-shrink: 0;
}

.col-h-visible {
  width: 36px;
  flex-shrink: 0;
  text-align: center;
}

.col-h-name {
  flex: 1;
  min-width: 0;
}

.col-h-count {
  width: 52px;
  flex-shrink: 0;
  text-align: center;
}

.col-h-wip {
  width: 152px;
  flex-shrink: 0;
  text-align: center;
}

.column-list-body {
  display: flex;
  flex-direction: column;
}

.column-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border-light, var(--color-border));
  transition: background 0.15s;
  cursor: grab;
  user-select: none;
}

.column-item:last-child {
  border-bottom: none;
}

.column-item:hover {
  background: var(--color-fill-1);
}

.column-item--disabled {
  opacity: 0.55;
}

.column-item--inactive {
  color: var(--color-text-4);
}

.column-item--inactive .column-name {
  color: var(--color-text-3);
}

.column-item--dragging {
  opacity: 0.4;
  background: var(--color-fill-2);
}

.column-item--drop-above {
  border-top: 2px solid rgb(var(--primary-6));
}

.column-item--drop-below {
  border-bottom: 2px solid rgb(var(--primary-6));
}

.col-drag-handle {
  width: 20px;
  flex-shrink: 0;
  text-align: center;
  color: var(--color-text-4);
  font-size: 14px;
  cursor: grab;
  line-height: 1;
}

.col-drag-handle:active {
  cursor: grabbing;
}

.col-visible-check {
  width: 36px;
  flex-shrink: 0;
  display: flex;
  justify-content: center;
}

.col-name-cell {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.column-color {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

.column-name {
  font-size: 13px;
  color: var(--color-text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.column-category-badge {
  font-size: 10px;
  color: var(--color-text-4);
  background: var(--color-fill-2);
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.column-workflow-badge {
  font-size: 10px;
  flex-shrink: 0;
  opacity: 0.7;
}

/* ===== 分组标题 ===== */
.column-group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--color-fill-1);
  border-bottom: 1px solid var(--color-border-light, var(--color-border));
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.column-group-header:hover {
  background: var(--color-fill-2);
}

.group-collapse-icon {
  font-size: 10px;
  color: var(--color-text-3);
  width: 12px;
  text-align: center;
}

.group-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.group-count {
  font-size: 11px;
  color: var(--color-text-4);
  margin-left: auto;
}

/* ===== 工单数量 ===== */
.col-count-cell {
  width: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.issue-count-badge {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  padding: 1px 6px;
  border-radius: 3px;
  min-width: 20px;
  text-align: center;
}

.issue-count-badge--zero {
  color: var(--color-text-4);
  background: transparent;
}

.col-wip-cell {
  width: 152px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: center;
}

.wip-separator {
  color: var(--color-text-4);
  font-size: 12px;
}

/* ===== WIP 帮助说明 ===== */
.wip-help {
  background: var(--color-fill-1);
  border-radius: 6px;
  padding: 12px 16px;
}

.wip-help-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
  margin-bottom: 8px;
}

.wip-help-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.5;
}

.wip-help-list li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wip-indicator {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 3px;
  flex-shrink: 0;
}

.wip-indicator--over {
  color: rgb(var(--danger-6));
  background: rgba(var(--danger-6), 0.1);
}

.wip-indicator--under {
  color: rgb(var(--warning-6));
  background: rgba(var(--warning-6), 0.1);
}

/* ===== Footer ===== */
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
