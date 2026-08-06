<template>
  <div class="event-item">
    <div class="event-info">
      <span class="event-label">{{ label }}</span>
      <span class="event-desc">{{ desc }}</span>
    </div>
    <a-switch :model-value="modelValue" size="small" @change="handleChange" />
  </div>

<script setup lang="ts">
defineProps<{
  label: string
  desc: string
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  change: []
}>()

function handleChange(val: boolean | string | number) {
  emit('update:modelValue', val as boolean)
  emit('change')
}
</script>

<style scoped>
.event-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
}

.event-item + .event-item {
  border-top: 1px solid var(--tf-border-subtle, rgba(255,255,255,0.04));
}

.event-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.event-label {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.event-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}
</style>
