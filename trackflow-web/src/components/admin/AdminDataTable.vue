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
      <template #batch-actions="slotProps">
        <slot name="toolbar-batch" v-bind="slotProps" />
      </template>
      <template #right>
        <slot name="toolbar-right" />
      </template>
    </AdminTableToolbar>

    <!-- 数据区（独立滚动容器） -->
    <div ref="bodyRef" class="admin-data-table__body">
      <a-table
        :data="data"
        :loading="loading"
        :row-key="rowKey || 'id'"
        :pagination="false"
        :size="size || 'small'"
        :bordered="bordered || false"
        :row-selection="selectable ? rowSelectionConfig : undefined"
        :scroll="{ y: bodyHeight }"
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
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
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
  /** 搜索框宽度 */
  searchWidth?: string | number
  /** 是否显示工具栏（默认 true） */
  showToolbar?: boolean
  /** 是否显示重置按钮（默认 true） */
  showReset?: boolean

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
  /** 是否显示内置刷新按钮 */
  showRefresh?: boolean
  /** 刷新按钮是否 loading */
  refreshLoading?: boolean
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
  showRefresh: false,
  refreshLoading: false,
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
  'refresh': []
  'reset': []
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

// ===== 表格滚动区域高度（用于表头 sticky）=====
// 通过 ResizeObserver 动态测量 __body 容器高度，传给 a-table scroll.y
// 保证表头始终固定，内容区在容器内滚动
const bodyRef = ref<HTMLElement | null>(null)
const bodyHeight = ref(400)
let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  if (bodyRef.value) {
    bodyHeight.value = bodyRef.value.clientHeight
    resizeObserver = new ResizeObserver(() => {
      bodyHeight.value = bodyRef.value?.clientHeight || 400
    })
    resizeObserver.observe(bodyRef.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
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
  border-radius: 8px;
  overflow: hidden;
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

/* 表头 sticky 需要 arco-table-container 是实际滚动容器 */
.admin-data-table__body :deep(.arco-table) {
  width: 100%;
}

.admin-data-table__body :deep(.arco-table-container) {
  width: 100%;
}
</style>
