<template>
  <div class="admin-data-table">
    <!-- 工具栏（卡片外，无背景） -->
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

    <!-- 表格卡片（带边框圆角的容器，只包表头+数据行） -->
    <div class="admin-data-table__card">
      <div ref="bodyRef" class="admin-data-table__body">
        <a-table
          :data="data"
          :loading="loading"
          :row-key="rowKey"
          :pagination="false"
          :size="size"
          :bordered="bordered"
          :stripe="stripe"
          :hoverable="true"
          :scroll="{ y: bodyHeight }"
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
            <!-- 配置驱动：传入 columns prop 时自动渲染 -->
            <template v-if="columns && columns.length > 0">
              <template v-for="col in columns" :key="getColKey(col)">
                <!-- index: 序号 -->
                <a-table-column
                  v-if="col.type === 'index'"
                  :title="col.title || '#'"
                  :width="col.width || 56"
                  :align="col.align || 'center'"
                >
                  <template #cell="{ rowIndex }">
                    <span class="adt-index">{{ rowIndex + 1 }}</span>
                  </template>
                </a-table-column>

                <!-- text: 普通文字，可 ellipsis -->
                <a-table-column
                  v-else-if="col.type === 'text'"
                  :title="col.title"
                  :data-index="col.key"
                  :width="col.width"
                  :ellipsis="col.ellipsis"
                  :align="col.align"
                >
                  <template #cell="{ record }">
                    <span class="adt-text">
                      {{ col.format ? col.format(record[col.key], record) : (record[col.key] ?? (col.empty ?? '—')) }}
                    </span>
                  </template>
                </a-table-column>

                <!-- code: <code> 标签样式 -->
                <a-table-column
                  v-else-if="col.type === 'code'"
                  :title="col.title"
                  :data-index="col.key"
                  :width="col.width"
                  :align="col.align"
                >
                  <template #cell="{ record }">
                    <code class="adt-code">{{ record[col.key] ?? '—' }}</code>
                  </template>
                </a-table-column>

                <!-- badge: 彩色圆角类型标签 -->
                <a-table-column
                  v-else-if="col.type === 'badge'"
                  :title="col.title"
                  :data-index="col.key"
                  :width="col.width"
                  :align="col.align"
                >
                  <template #cell="{ record }">
                    <span
                      class="adt-badge"
                      :class="col.colorMap?.[record[col.key]] ? `adt-badge--${col.colorMap[record[col.key]]}` : ''"
                    >
                      {{ col.labelMap?.[record[col.key]] ?? record[col.key] ?? '—' }}
                    </span>
                  </template>
                </a-table-column>

                <!-- status: ● 启用 / ● 禁用 -->
                <a-table-column
                  v-else-if="col.type === 'status'"
                  :title="col.title"
                  :width="col.width || 90"
                  :align="col.align || 'center'"
                >
                  <template #cell="{ record }">
                    <template v-if="resolveActive(col, record)">
                      <a-tag color="green" size="small" class="adt-status-tag">
                        <template #icon><span class="adt-dot adt-dot--on" /></template>
                        {{ col.activeLabel || '启用' }}
                      </a-tag>
                    </template>
                    <template v-else>
                      <a-tag color="red" size="small" class="adt-status-tag">
                        <template #icon><span class="adt-dot adt-dot--off" /></template>
                        {{ col.inactiveLabel || '禁用' }}
                      </a-tag>
                    </template>
                  </template>
                </a-table-column>

                <!-- count: 数字 + 单位 -->
                <a-table-column
                  v-else-if="col.type === 'count'"
                  :title="col.title"
                  :data-index="col.key"
                  :width="col.width || 90"
                  :align="col.align || 'center'"
                >
                  <template #cell="{ record }">
                    <span
                      class="adt-count"
                      :class="{ 'adt-count--clickable': !!col.onClick && record[col.key] > 0 }"
                      @click="col.onClick && record[col.key] > 0 && col.onClick(record)"
                    >
                      {{ record[col.key] ?? 0 }}{{ col.unit ? ` ${col.unit}` : '' }}
                    </span>
                  </template>
                </a-table-column>

                <!-- date: 格式化时间 -->
                <a-table-column
                  v-else-if="col.type === 'date'"
                  :title="col.title"
                  :data-index="col.key"
                  :width="col.width || 160"
                  :align="col.align"
                >
                  <template #cell="{ record }">
                    <span class="adt-date">{{ formatColDate(record[col.key], col.format) }}</span>
                  </template>
                </a-table-column>

                <!-- user: 头像 + 名称 + 副标题 -->
                <a-table-column
                  v-else-if="col.type === 'user'"
                  :title="col.title"
                  :data-index="col.key"
                  :width="col.width || 240"
                >
                  <template #cell="{ record }">
                    <div class="adt-user">
                      <UserAvatar :name="record[col.key] || record.username" :size="col.avatarSize || 28" />
                      <div class="adt-user__info">
                        <component
                          :is="col.href ? 'a' : 'span'"
                          class="adt-user__name"
                          v-bind="col.href ? { href: col.href(record), onClick: (e: Event) => e.stopPropagation() } : {}"
                        >{{ record[col.key] || '—' }}</component>
                        <span v-if="col.subKey && record[col.subKey]" class="adt-user__sub">
                          @{{ record[col.subKey] }}
                        </span>
                      </div>
                    </div>
                  </template>
                </a-table-column>

                <!-- boolean: 是/否 tag -->
                <a-table-column
                  v-else-if="col.type === 'boolean'"
                  :title="col.title"
                  :data-index="col.key"
                  :width="col.width || 80"
                  :align="col.align || 'center'"
                >
                  <template #cell="{ record }">
                    <a-tag
                      :color="record[col.key] ? (col.trueColor || 'arcoblue') : (col.falseColor || 'gray')"
                      size="small"
                    >
                      {{ record[col.key] ? (col.trueLabel || '是') : (col.falseLabel || '否') }}
                    </a-tag>
                  </template>
                </a-table-column>

                <!-- switch: 开关 -->
                <a-table-column
                  v-else-if="col.type === 'switch'"
                  :title="col.title"
                  :data-index="col.key"
                  :width="col.width || 80"
                  :align="col.align || 'center'"
                >
                  <template #cell="{ record }">
                    <a-switch
                      :model-value="record[col.key]"
                      size="small"
                      :disabled="col.disabled ? col.disabled(record) : false"
                      @change="(val: any) => col.onChange(record, val as boolean)"
                    />
                  </template>
                </a-table-column>

                <!-- actions: 操作按钮组（最后列） -->
                <a-table-column
                  v-else-if="col.type === 'actions'"
                  :title="col.title || '操作'"
                  :width="col.width || 160"
                  :align="col.align || 'right'"
                >
                  <template #cell="{ record }">
                    <div class="adt-actions">
                      <template v-for="action in col.actions(record)" :key="action.label">
                        <a-button
                          v-if="!resolveBoolean(action.hidden, record)"
                          type="text"
                          size="mini"
                          :status="action.danger ? 'danger' : 'normal'"
                          :disabled="resolveBoolean(action.disabled, record)"
                          @click.stop="action.onClick(record)"
                        >{{ action.label }}</a-button>
                      </template>
                    </div>
                  </template>
                </a-table-column>

                <!-- render: 完全自定义（h() 函数） -->
                <a-table-column
                  v-else-if="col.type === 'render'"
                  :title="col.title"
                  :data-index="col.key"
                  :width="col.width"
                  :ellipsis="col.ellipsis"
                  :align="col.align"
                >
                  <template #cell="{ record, rowIndex }">
                    <component :is="() => col.render(record, rowIndex)" />
                  </template>
                </a-table-column>
              </template>
            </template>

            <!-- Fallback：使用原来的 #columns slot（兼容旧写法 + 复杂列） -->
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

      <!-- 分页（卡片底部） -->
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
    </div><!-- end admin-data-table__card -->

    <!-- 右键菜单（contextmenu，fixed 定位不受卡片影响） -->
    <div
      v-if="contextMenuVisible"
      class="admin-ctx-menu"
      :style="{ top: contextMenuY + 'px', left: contextMenuX + 'px' }"
      @mouseleave="contextMenuVisible = false"
    >
      <slot name="context-menu" :record="contextMenuRecord" :close="() => contextMenuVisible = false" />
    </div>

  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import AdminTableToolbar from './AdminTableToolbar.vue'
import AdminPagination from './AdminPagination.vue'
import { EmptyState } from '@/components/base'
import { UserAvatar } from '@/components/base'
import { formatDate, formatDateTime, formatRelativeTime } from '@/utils/date'
import type { ColumnDef, StatusColumnDef } from './types/column'

export type { ColumnDef } from './types/column'

/**
 * AdminDataTable — 管理后台通用数据表格
 *
 * 职责：
 * - 封装工具栏（搜索/筛选/刷新/重置）
 * - 封装 a-table：支持配置驱动（columns prop）和 slot（#columns）两种方式
 * - 封装分页
 *
 * 列类型（columns prop）：
 * - index: 序号
 * - text: 普通文字
 * - code: <code> 标签
 * - badge: 彩色类型标签
 * - status: ● 启用/禁用
 * - count: 数字+单位
 * - date: 格式化时间
 * - user: 头像+名称+副标题
 * - boolean: 是/否 tag
 * - switch: a-switch 开关
 * - actions: 操作按钮组
 * - render: h() 自定义渲染
 */

export interface AdminDataTableProps {
  // ===== 列配置（配置驱动，优先渲染；可与 #columns slot 共存） =====
  columns?: ColumnDef[]

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

// ===== 表格高度（传给 scroll.y，让 Arco 自管内部滚动和表头固定）=====
// ResizeObserver 动态测量 __body 容器高度，容器本身 overflow:hidden 不滚动
const bodyRef = ref<HTMLElement | null>(null)
const bodyHeight = ref(500)
let resizeObserver: ResizeObserver | null = null

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

/** 计算 scroll.y：容器高度 − 表头高度（Arco scroll.y 只作用于 tbody 区域） */
function calcBodyHeight() {
  const container = bodyRef.value
  if (!container) return
  const containerH = container.clientHeight
  // 尝试读取真实表头高度，兜底使用 size 对应的默认值
  const thead = container.querySelector<HTMLElement>('.arco-table-thead')
  const sizeDefault = props.size === 'small' ? 32 : props.size === 'large' ? 48 : 40
  const theadH = thead ? thead.clientHeight : sizeDefault
  bodyHeight.value = Math.max(containerH - theadH, 120)
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  // 测量 __body 高度，响应窗口/布局变化
  if (bodyRef.value) {
    calcBodyHeight()
    resizeObserver = new ResizeObserver(() => {
      calcBodyHeight()
    })
    resizeObserver.observe(bodyRef.value)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  resizeObserver?.disconnect()
})

// ===== 列辅助函数 =====

/** 为每列生成稳定 key */
function getColKey(col: ColumnDef): string {
  if (col.type === 'index') return '__index'
  if (col.type === 'actions') return '__actions'
  return (col as any).key || col.type
}

/** 解析 status 列的"是否启用"状态 */
function resolveActive(col: StatusColumnDef, record: any): boolean {
  const av = col.activeValue
  if (av === undefined) {
    const v = record[col.key]
    return v === true || v === 'active' || v === 'enabled'
  }
  if (typeof av === 'function') return av(record)
  return record[col.key] === av
}

/** 解析 boolean | ((record) => boolean) */
function resolveBoolean(val: boolean | ((record: any) => boolean) | undefined, record: any): boolean {
  if (val === undefined) return false
  if (typeof val === 'function') return val(record)
  return val
}

/** 格式化日期列 */
function formatColDate(value: any, format?: 'date' | 'datetime' | 'relative'): string {
  if (!value) return '—'
  if (format === 'date') return formatDate(value)
  if (format === 'relative') return formatRelativeTime(value)
  return formatDateTime(value)
}

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
  gap: 8px;
  position: relative;
}

/* 工具栏无卡片背景，直接在页面背景上 */

/* 表格卡片：带圆角和边框，层次感与参考截图一致 */
.admin-data-table__card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
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

/* ===== 配置驱动列的公共 cell 样式（adt- 前缀） ===== */

/* index */
.adt-index {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

/* text */
.adt-text {
  font-size: 13px;
  color: var(--tf-text-primary);
}

/* code */
.adt-code {
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  background: var(--tf-bg-body);
  border: 1px solid var(--tf-border-light);
  border-radius: 4px;
  padding: 2px 6px;
  color: var(--tf-accent);
}

/* badge */
.adt-badge {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
  background: var(--tf-bg-elevated);
  color: var(--tf-text-secondary);
}
.adt-badge--blue   { background: var(--tf-accent-light); color: var(--tf-accent); }
.adt-badge--green  { background: var(--tf-success-bg); color: var(--tf-success); }
.adt-badge--red    { background: var(--tf-danger-bg); color: var(--tf-danger); }
.adt-badge--orange { background: var(--tf-warning-bg); color: var(--tf-warning); }
.adt-badge--purple { background: var(--tf-purple-bg); color: var(--tf-purple); }
.adt-badge--gray   { background: var(--tf-bg-elevated); color: var(--tf-text-tertiary); }

/* status tag */
.adt-status-tag { font-size: 12px; }
.adt-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 4px;
}
.adt-dot--on  { background: var(--tf-success); }
.adt-dot--off { background: var(--tf-danger); }

/* count */
.adt-count {
  font-size: 13px;
  color: var(--tf-text-tertiary);
}
.adt-count--clickable {
  color: var(--tf-accent);
  cursor: pointer;
  text-decoration: underline dotted;
}

/* date */
.adt-date {
  font-size: 12px;
  color: var(--tf-text-secondary);
}

/* user */
.adt-user {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.adt-user__info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}
.adt-user__name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-decoration: none;
}
a.adt-user__name {
  color: var(--tf-accent);
}
a.adt-user__name:hover { text-decoration: underline; }
.adt-user__sub {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* actions */
.adt-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  flex-wrap: nowrap;
  white-space: nowrap;
}
</style>
