<template>
  <div class="admin-data-table">
    <!-- 工具栏（可选） -->
    <AdminTableToolbar
      v-if="showToolbar !== false"
      :model-value="internalSearch"
      :search-placeholder="searchPlaceholder"
      :selected-count="internalSelectedKeys?.length || 0"
      @update:model-value="internalSearch = $event"
      @search="handleSearch"
      @clear="handleClear"
    >
      <slot name="toolbar-filters" />
      <template #batch-actions="slotProps">
        <slot name="toolbar-batch" v-bind="slotProps" />
      </template>
      <template #right>
        <slot name="toolbar-right" />
      </template>
    </AdminTableToolbar>

    <!-- 数据区（独立滚动容器） -->
    <div class="admin-data-table__body">
      <a-table
        :data="data"
        :loading="loading"
        :row-key="rowKey || 'id'"
        :pagination="false"
        :size="size || 'small'"
        :bordered="bordered || false"
        :row-selection="selectable ? rowSelectionConfig : undefined"
        v-model:selected-keys="internalSelectedKeys"
        @row-click="(record: any) => $emit('row-click', record)"
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
import { computed } from 'vue'
import AdminTableToolbar from './AdminTableToolbar.vue'
import AdminPagination from './AdminPagination.vue'
import { EmptyState } from '@/components/base'

export interface AdminDataTableProps {
  // ===== 数据 =====
  /** 表格数据 */
  data: any[]
  /** 是否加载中 */
  loading?: boolean
  /** 行 key 字段名，默认 'id' */
  rowKey?: string

  // ===== 分页 =====
  /** 总条数 */
  total: number
  /** 当前页（支持 v-model） */
  current: number
  /** 每页条数（支持 v-model） */
  pageSize: number
  /** 每页条数候选值 */
  pageSizeOptions?: number[]

  // ===== 搜索 =====
  /** 搜索关键词（支持 v-model） */
  searchKeyword?: string
  /** 搜索框占位符 */
  searchPlaceholder?: string
  /** 是否显示工具栏（默认 true） */
  showToolbar?: boolean

  // ===== 行选择 =====
  /** 已选中的行 key 列表（支持 v-model） */
  selectedKeys?: string[]
  /** 是否启用行选择（checkbox） */
  selectable?: boolean

  // ===== 空状态 =====
  /** 空状态标题 */
  emptyTitle?: string
  /** 空状态描述 */
  emptyDescription?: string

  // ===== 表格外观 =====
  /** Arco Table size */
  size?: 'small' | 'medium' | 'large'
  /** 是否显示边框 */
  bordered?: boolean
}

const props = withDefaults(defineProps<AdminDataTableProps>(), {
  loading: false,
  rowKey: 'id',
  pageSizeOptions: () => [20, 50, 100],
  searchKeyword: '',
  searchPlaceholder: '搜索...',
  showToolbar: true,
  selectedKeys: () => [],
  selectable: false,
  emptyTitle: '暂无数据',
  emptyDescription: '',
  size: 'small',
  bordered: false
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
  showCheckedAll: true
}))

// ===== 事件处理 =====

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
  border-radius: 6px;
  overflow: hidden;
}

.admin-data-table__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

/* Make table header sticky within the scroll container */
.admin-data-table__body :deep(.arco-table-th) {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--tf-bg-surface);
}

/* Override Arco's internal scrollbar containers that break sticky */
.admin-data-table__body :deep(.arco-scrollbar) {
  overflow: visible;
}

.admin-data-table__body :deep(.arco-scrollbar-container) {
  overflow: visible !important;
}

.admin-data-table__body :deep(.arco-table-content-scroll-x) {
  overflow: visible !important;
}

.admin-data-table__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
  gap: 8px;
}

</style>
