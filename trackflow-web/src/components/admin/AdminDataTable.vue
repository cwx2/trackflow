<template>
  <div class="admin-data-table">
    <!-- 工具栏（可选） -->
    <AdminTableToolbar
      v-if="showToolbar !== false"
      :model-value="internalSearch"
      :search-placeholder="searchPlaceholder"
      :search-width="searchWidth"
      :selected-count="internalSelectedKeys?.length || 0"
      :show-refresh="showRefresh"
      :refresh-loading="refreshLoading"
      :show-reset="showReset"
      @update:model-value="internalSearch = $event"
      @search="handleSearch"
      @clear="handleClear"
      @refresh="$emit('refresh')"
      @reset="$emit('reset')"
    >
      <slot name="toolbar-filters" />
      <template #batch-actions="{ count }">
        <slot name="toolbar-batch" :count="count" />
      </template>
      <template #right>
        <slot name="toolbar-right" />
      </template>
    </AdminTableToolbar>

    <!-- 数据区 -->
    <div class="admin-data-table__body">
      <a-table
        :data="data"
        :loading="loading"
        :row-key="rowKey"
        :pagination="false"
        :size="size"
        :bordered="bordered"
        :stripe="stripe"
        :hoverable="true"
        :sticky-header="true"
        :column-resizable="columnResizable"
        :row-selection="selectable ? rowSelectionConfig : undefined"
        :row-class="rowClass"
        :draggable="draggable ? { type: 'handle' } : undefined"
        :scrollbar="true"
        v-model:selected-keys="internalSelectedKeys"
        @row-click="(record: any) => $emit('row-click', record)"
        @row-contextmenu="(record: any, ev: Event) => { $emit('row-contextmenu', record, ev); showContextMenu(record, ev as MouseEvent) }"
        @change="(_data: any, _extra: any) => draggable && $emit('order-change', _data)"
      >
        <template #columns>
          <slot name="columns" />
        </template>
        <template #empty>
          <slot name="empty">
            <EmptyState
              icon="file"
              :title="emptyTitle || '暂无数据'"
              :description="emptyDescription"
            />
          </slot>
        </template>
        <template v-if="$slots.expand" #expand-row="slotProps">
          <slot name="expand" v-bind="slotProps" />
        </template>
      </a-table>
    </div>

    <!-- 右键菜单（contextmenu） -->
    <div
      v-if="contextMenuVisible"
      class="admin-ctx-menu"
      :style="{ top: contextMenuY + 'px', left: contextMenuX + 'px' }"
      @mouseleave="contextMenuVisible = false"
    >
      <slot name="context-menu" :record="contextMenuRecord" :close="() => contextMenuVisible = false" />
    </div>

    <!-- 分页（固定底部） -->
    <AdminPagination
      :current="internalCurrent"
      :page-size="internalPageSize"
      :total="total"
      :page-size-options="pageSizeOptions"
      @update:current="handleCurrentChange"
      @update:page-size="handlePageSizeChange"
      @change="(page: number) => $emit('page-change', page)"
      @page-size-change="(size: number) => $emit('page-size-change', size)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import AdminTableToolbar from './AdminTableToolbar.vue'
import AdminPagination from './AdminPagination.vue'
import { EmptyState } from '@/components/base'

/**
 * AdminDataTable — 管理后台通用数据表格
 *
 * 职责：
 * - 封装工具栏（搜索/筛选/刷新/重置）
 * - 封装 a-table 并透传 UX 增强特性
 * - 封装分页
 *
 * UX 特性：
 * - stickyHeader: 表头自动固定（无需测量高度）
 * - columnResizable: 列宽拖拽调整
 * - rowClass: 支持行条件样式（如禁用行变灰）
 * - draggable: 拖拽排序，触发 order-change emit
 * - row-contextmenu: 右键菜单 slot
 */

export interface AdminDataTableProps {
  // ===== 数据 =====
  data: any[]
  loading?: boolean
  rowKey?: string

  // ===== 分页 =====
  total: number
  current: number
  pageSize: number
  pageSizeOptions?: number[]

  // ===== 搜索 =====
  searchKeyword?: string
  searchPlaceholder?: string
  searchWidth?: string | number
  showToolbar?: boolean
  showReset?: boolean

  // ===== 行选择 =====
  selectedKeys?: string[]
  selectable?: boolean

  // ===== 空状态 =====
  emptyTitle?: string
  emptyDescription?: string

  // ===== 表格外观 =====
  size?: 'small' | 'medium' | 'large'
  bordered?: boolean
  /** 斑马纹 */
  stripe?: boolean
  showRefresh?: boolean
  refreshLoading?: boolean

  // ===== UX 增强 =====
  /** 列宽拖拽调整，默认 true */
  columnResizable?: boolean
  /** 拖拽排序，开启后显示拖拽手柄列 */
  draggable?: boolean
  /** 行条件 class，用于禁用/高亮等场景 */
  rowClass?: string | ((record: any, rowIndex: number) => any)
}

const props = withDefaults(defineProps<AdminDataTableProps>(), {
  loading: false,
  rowKey: 'id',
  pageSizeOptions: () => [20, 50, 100],
  searchKeyword: '',
  searchPlaceholder: '搜索...',
  showToolbar: true,
  showReset: true,
  selectedKeys: () => [],
  selectable: false,
  emptyTitle: '暂无数据',
  emptyDescription: '',
  size: 'medium',
  bordered: false,
  stripe: false,
  showRefresh: false,
  refreshLoading: false,
  columnResizable: true,
  draggable: false,
})

const emit = defineEmits<{
  'update:current': [page: number]
  'update:page-size': [size: number]
  'update:search-keyword': [keyword: string]
  'update:selected-keys': [keys: string[]]
  'page-change': [page: number]
  'page-size-change': [size: number]
  'search': [keyword: string]
  'row-click': [record: any]
  'row-contextmenu': [record: any, ev: Event]
  'refresh': []
  'reset': []
  /** 拖拽排序后触发，传入重新排列后的完整数据数组 */
  'order-change': [data: any[]]
}>()

// ===== 内部双向绑定 =====

const internalCurrent = computed({
  get: () => props.current,
  set: (val: number) => emit('update:current', val)
})

const internalPageSize = computed({
  get: () => props.pageSize,
  set: (val: number) => emit('update:page-size', val)
})

const internalSearch = computed({
  get: () => props.searchKeyword,
  set: (val: string) => emit('update:search-keyword', val)
})

const internalSelectedKeys = computed({
  get: () => props.selectedKeys,
  set: (val: string[]) => emit('update:selected-keys', val)
})

// ===== 行选择配置 =====

const rowSelectionConfig = computed(() => ({
  type: 'checkbox' as const,
  showCheckedAll: true,
}))

// ===== 右键菜单 =====
// 监听 row-contextmenu，在鼠标位置显示 context-menu slot
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const contextMenuRecord = ref<any>(null)

function showContextMenu(record: any, ev: MouseEvent) {
  if (!slots['context-menu']) return
  ev.preventDefault()
  contextMenuRecord.value = record
  contextMenuX.value = ev.clientX
  contextMenuY.value = ev.clientY
  contextMenuVisible.value = true
}

// 点击页面其他地方关闭右键菜单
function handleDocumentClick() {
  contextMenuVisible.value = false
}

onMounted(() => document.addEventListener('click', handleDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', handleDocumentClick))

// ===== 事件处理 =====

const slots = defineSlots<{
  columns(): any
  empty(): any
  expand(props: { record: any }): any
  'toolbar-filters'(): any
  'toolbar-batch'(props: { count: number }): any
  'toolbar-right'(): any
  'context-menu'(props: { record: any; close: () => void }): any
}>()

function handleSearch(value: string) {
  emit('search', value)
}

function handleClear() {
  emit('update:search-keyword', '')
  emit('search', '')
}

function handleCurrentChange(page: number) {
  emit('update:current', page)
}

function handlePageSizeChange(size: number) {
  emit('update:page-size', size)
}
</script>

<style scoped>
.admin-data-table {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 8px;
  overflow: hidden;
  position: relative;
}

.admin-data-table__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* 表头样式 */
.admin-data-table__body :deep(.arco-table-th) {
  background: var(--tf-bg-body);
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

/* fixed 列背景与表头保持一致 */
.admin-data-table__body :deep(.arco-table-th.arco-table-col-fixed-right),
.admin-data-table__body :deep(.arco-table-td.arco-table-col-fixed-right) {
  background: var(--tf-bg-surface);
}

/* 列宽拖拽手柄 */
.admin-data-table__body :deep(.arco-table-col-resizable)::after {
  background-color: var(--tf-border);
}

/* 拖拽排序手柄列 */
.admin-data-table__body :deep(.arco-table-drag-handle-wrapper) {
  color: var(--tf-text-tertiary);
  cursor: grab;
}

/* 操作列（最后一列）左侧竖线分隔，仅限管理表格 */
.admin-data-table__body :deep(.arco-table-td:last-child),
.admin-data-table__body :deep(.arco-table-th:last-child) {
  border-left: 1px solid var(--tf-border) !important;
}

/* 操作列 cell 减小水平 padding */
.admin-data-table__body :deep(.arco-table-td:last-child .arco-table-cell),
.admin-data-table__body :deep(.arco-table-th:last-child .arco-table-cell) {
  padding-left: 8px !important;
  padding-right: 8px !important;
}

/* 右键菜单 */
.admin-ctx-menu {
  position: fixed;
  z-index: 9999;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  padding: 4px 0;
  min-width: 160px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
}
</style>
