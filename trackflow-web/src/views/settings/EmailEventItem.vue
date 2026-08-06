<template>
  <div class="email-event-item">
    <span class="email-event-label">{{ label }}</span>
    <a-switch :model-value="modelValue" size="small" @change="handleChange" />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string
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
.email-event-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  border-radius: 4px;
  transition: background-color 150ms;
}

.email-event-item:hover {
  background: var(--tf-bg-hover, rgba(255, 255, 255, 0.04));
}

.email-event-label {
  font-size: 12px;
  color: var(--tf-text-primary);
}
</style>
