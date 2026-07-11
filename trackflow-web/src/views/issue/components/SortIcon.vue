<template>
  <span class="sort-icon" :class="{ active: state !== null }">
    <template v-if="state === 'asc'">↑</template>
    <template v-else-if="state === 'desc'">↓</template>
    <template v-else>↕</template>
  </span>
</template>

<script setup lang="ts">
defineProps<{
  state: 'asc' | 'desc' | null
}>()
</script>

<style scoped>
.sort-icon {
  font-size: 10px;
  margin-left: 2px;
  color: var(--tf-text-quaternary);
  transition: color 0.15s;
}
.sort-icon.active {
  color: var(--tf-accent);
}
</style>
