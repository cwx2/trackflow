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
            <a-button size="mini" @click="selectDefault">恢复默认</a-button>
          </div>

          <!-- 可拖拽的列配置列表 -->
          <div class="column-list">
            <div class="column-list-header">
              <span class="col-h-drag"></span>
              <span class="col-h-visible">显示</span>
              <span class="col-h-name">状态列</span>
              <span class="col-h-wip">WIP 限制</span>
            </div>
            <div
              ref="sortableContainer"
              class="column-list-body"
            >
              <div
                v-for="(col, index) in editableColumns"
                :key="col.statusId"
                class="column-item"
                :class="{
                  'column-item--disabled': !col.visible,
                  'column-item--dragging': dragIndex === index,
                  'column-item--drop-above': dropIndex === index && dropPosition === 'above',
                  'column-item--drop-below': dropIndex === index && dropPosition === 'below'
                }"
                :draggable="true"
                @dragstart="onItemDragStart($event, index)"
                @dragover="onItemDragOver($event, index)"
                @dragleave="onItemDragLeave"
                @drop="onItemDrop($event, index)"
                @dragend="onItemDragEnd"
              >
                <span class="col-drag-handle" title="拖拽排序">⠿</span>
                <a-checkbox v-model="col.visible" class="col-visible-check" />
                <div class="col-name-cell">
                  <span class="column-color" :style="{ backgroundColor: col.statusColor }"></span>
                  <span class="column-name">{{ localizeStatusName(col.statusName) }}</span>
                  <span class="column-category-badge">{{ categoryLabel(col.statusCategory) }}</span>
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
import { ref, watch } from 'vue'
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
        wipMax: c.wipMax ?? undefined
      }))
    } else {
      initializing.value = true
      try {
        const res = await boardApi.initializeColumns(props.projectId)
        const initialized = res.data || []
        editableColumns.value = initialized.map(c => ({
          ...c,
          wipMin: c.wipMin ?? undefined,
          wipMax: c.wipMax ?? undefined
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
      wipMax: c.wipMax ?? undefined
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

function selectAll() {
  editableColumns.value.forEach(c => c.visible = true)
}

function selectNone() {
  editableColumns.value.forEach(c => c.visible = false)
}

function selectDefault() {
  const categoryLimits: Record<string, number> = {
    open: 1,
    in_progress: 3,
    done: 1,
    cancelled: 1
  }
  const categoryCounters: Record<string, number> = {}

  editableColumns.value.forEach(c => {
    const cat = c.statusCategory || 'open'
    const limit = categoryLimits[cat] ?? 1
    const count = categoryCounters[cat] || 0
    c.visible = count < limit
    categoryCounters[cat] = count + 1
  })
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
