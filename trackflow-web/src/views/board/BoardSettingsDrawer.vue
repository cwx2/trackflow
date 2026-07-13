<template>
  <a-drawer
    :visible="visible"
    title="看板列设置"
    :width="400"
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

    <div class="settings-content">
      <div class="settings-hint">
        <p>选择看板中要显示的状态列。隐藏的列不会在看板中出现。</p>
      </div>

      <!-- 快捷操作 -->
      <div class="quick-actions">
        <a-button size="mini" @click="selectAll">全选</a-button>
        <a-button size="mini" @click="selectNone">全不选</a-button>
        <a-button size="mini" @click="selectDefault">恢复默认</a-button>
      </div>

      <!-- 按分类分组展示 -->
      <div v-for="group in groupedColumns" :key="group.category" class="column-group">
        <div class="group-header">
          <span class="group-icon">{{ categoryIcon(group.category) }}</span>
          <span class="group-title">{{ categoryLabel(group.category) }}</span>
          <span class="group-count">{{ group.columns.filter(c => c.visible).length }}/{{ group.columns.length }}</span>
        </div>
        <div class="group-items">
          <div
            v-for="col in group.columns"
            :key="col.statusId"
            class="column-item"
            :class="{ 'column-item--disabled': !col.visible }"
          >
            <a-checkbox v-model="col.visible" class="column-checkbox">
              <div class="column-info">
                <span class="column-color" :style="{ backgroundColor: col.statusColor }"></span>
                <span class="column-name">{{ col.statusName }}</span>
              </div>
            </a-checkbox>
          </div>
        </div>
      </div>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { boardApi } from '@/api'
import type { BoardColumnVO, BoardColumnItem } from '@/api/types'

const props = defineProps<{
  visible: boolean
  projectId: string
  columns: BoardColumnVO[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'saved': []
}>()

const saving = ref(false)

// 可编辑的列配置（深拷贝）
const editableColumns = ref<BoardColumnVO[]>([])

// 当 drawer 打开或 columns 变化时，深拷贝
watch(() => [props.visible, props.columns], () => {
  if (props.visible && props.columns.length > 0) {
    editableColumns.value = props.columns.map(c => ({ ...c }))
  }
}, { immediate: true })

// 按分类分组
const groupedColumns = computed(() => {
  const groups: { category: string; columns: BoardColumnVO[] }[] = []
  const categoryOrder = ['open', 'in_progress', 'done', 'cancelled']
  const map = new Map<string, BoardColumnVO[]>()

  for (const col of editableColumns.value) {
    const cat = col.statusCategory || 'open'
    if (!map.has(cat)) map.set(cat, [])
    map.get(cat)!.push(col)
  }

  for (const cat of categoryOrder) {
    if (map.has(cat)) {
      groups.push({ category: cat, columns: map.get(cat)! })
    }
  }

  // 处理可能的其他分类
  for (const [cat, cols] of map.entries()) {
    if (!categoryOrder.includes(cat)) {
      groups.push({ category: cat, columns: cols })
    }
  }

  return groups
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

function categoryIcon(category: string): string {
  const map: Record<string, string> = {
    open: '📋',
    in_progress: '🔄',
    done: '✅',
    cancelled: '🚫'
  }
  return map[category] || '📌'
}

function selectAll() {
  editableColumns.value.forEach(c => c.visible = true)
}

function selectNone() {
  editableColumns.value.forEach(c => c.visible = false)
}

function selectDefault() {
  // 默认选择规则：基于 statusCategory 智能选择，无需硬编码状态代码
  // - open: 第 1 个状态
  // - in_progress: 前 3 个状态（通常是 In Progress / Code Review / Testing）
  // - done: 第 1 个状态
  // - cancelled: 第 1 个状态
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

async function handleSave() {
  // 至少选择一个列
  const visibleCount = editableColumns.value.filter(c => c.visible).length
  if (visibleCount === 0) {
    Message.warning('至少需要显示一个状态列')
    return
  }

  saving.value = true
  try {
    const columns: BoardColumnItem[] = editableColumns.value.map((c, idx) => ({
      statusId: Number(c.statusId),
      visible: c.visible,
      sortOrder: idx,
      collapsed: c.collapsed
    }))
    await boardApi.saveColumns(props.projectId, columns)
    Message.success('看板列配置已保存')
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

.column-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
}

.group-icon {
  font-size: 14px;
}

.group-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-1);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.group-count {
  font-size: 11px;
  color: var(--color-text-3);
  margin-left: auto;
}

.group-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-left: 4px;
}

.column-item {
  display: flex;
  align-items: center;
  padding: 6px 8px;
  border-radius: 6px;
  transition: background 0.15s;
}

.column-item:hover {
  background: var(--color-fill-2);
}

.column-item--disabled {
  opacity: 0.5;
}

.column-checkbox {
  width: 100%;
}

.column-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.column-color {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  flex-shrink: 0;
}

.column-name {
  font-size: 13px;
  color: var(--color-text-1);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
