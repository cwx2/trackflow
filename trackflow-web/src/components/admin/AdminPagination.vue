<template>
  <div
    v-if="!hideOnEmpty || total > 0"
    class="admin-pagination"
    :class="{ 'admin-pagination--inline': inline }"
  >
    <span class="admin-pagination-total">共 {{ total }} 条</span>
    <a-pagination
      :total="total"
      :current="current"
      :page-size="pageSize"
      size="small"
      show-page-size
      :page-size-options="pageSizeOptions"
      @change="handlePageChange"
      @page-size-change="handlePageSizeChange"
    />
  </div>
</template>

<script setup lang="ts">
interface AdminPaginationProps {
  /** 总条数 */
  total: number
  /** 当前页码（支持 v-model） */
  current: number
  /** 每页条数（支持 v-model） */
  pageSize: number
  /** 每页条数候选值，默认 [20, 50, 100, 200] */
  pageSizeOptions?: number[]
  /** total = 0 时是否隐藏，默认 true */
  hideOnEmpty?: boolean
  /** 是否显示在容器内（不加 border-top），默认 false */
  inline?: boolean
}

withDefaults(defineProps<AdminPaginationProps>(), {
  pageSizeOptions: () => [20, 50, 100, 200],
  hideOnEmpty: true,
  inline: false,
})

const emit = defineEmits<{
  'update:current': [page: number]
  'update:page-size': [size: number]
  'change': [page: number]
  'page-size-change': [size: number]
}>()

function handlePageChange(page: number) {
  emit('update:current', page)
  emit('change', page)
}

function handlePageSizeChange(size: number) {
  emit('update:page-size', size)
  emit('update:current', 1)
  emit('page-size-change', size)
}
</script>

<style scoped>
.admin-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 10px 16px;
  border-top: 1px solid var(--tf-border);
  background: var(--tf-bg-surface);
  flex-shrink: 0;
}

.admin-pagination--inline {
  border-top: none;
  padding: 8px 12px;
  background: transparent;
}

.admin-pagination-total {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}
</style>
