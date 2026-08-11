<template>
  <section class="json-data-viewer" :class="{ error }" :aria-label="label">
    <pre>{{ formattedValue }}</pre>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  value?: unknown
  label?: string
  error?: boolean
}>(), {
  label: '原始数据',
  error: false,
})

const formattedValue = computed(() => {
  if (props.value == null) return '—'
  if (typeof props.value === 'string') return props.value
  try { return JSON.stringify(props.value, null, 2) }
  catch { return String(props.value) }
})
</script>

<style scoped>
.json-data-viewer { display: flex; flex: 1; min-height: 0; overflow: hidden; border: 1px solid var(--tf-border); border-radius: 8px; background: var(--tf-bg-body); }
.json-data-viewer pre { flex: 1; min-width: 0; min-height: 0; margin: 0; padding: 12px; overflow: auto; color: var(--tf-text-secondary); font-size: 12px; line-height: 1.6; white-space: pre; }
.json-data-viewer.error { border-color: color-mix(in srgb, var(--tf-danger) 38%, var(--tf-border)); background: var(--tf-danger-bg); }
.json-data-viewer.error pre { color: var(--tf-danger); }
</style>
