<template>
  <div class="admin-page-layout">
    <div class="admin-page-header">
      <h2 class="admin-page-title">{{ title }}</h2>
      <div class="admin-page-actions">
        <slot name="actions" />
      </div>
    </div>
    <div class="admin-page-body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>

<style scoped>
.admin-page-layout {
  padding: 24px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.admin-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.admin-page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.admin-page-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
