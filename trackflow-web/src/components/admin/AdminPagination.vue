<template>
  <div v-if="total > 0" class="admin-pagination">
    <a-pagination
      :total="total"
      :current="page"
      :page-size="pageSize"
      size="small"
      show-total
      show-page-size
      :page-size-options="pageSizeOptions"
      @change="$emit('page-change', $event)"
      @page-size-change="$emit('page-size-change', $event)"
    />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  total: number
  page: number
  pageSize: number
  pageSizeOptions?: number[]
}>(), {
  pageSizeOptions: () => [20, 50, 100]
})

defineEmits<{
  'page-change': [page: number]
  'page-size-change': [size: number]
}>()
</script>

<style scoped>
.admin-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--tf-border);
  background: var(--tf-bg-surface);
  flex-shrink: 0;
}
</style>
