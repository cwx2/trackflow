<template>
  <div class="section description-section">
    <h2 class="section-title">项目说明</h2>
    <div v-if="description" v-html="renderedHtml" class="description-rendered"></div>
    <p v-else class="description-empty">暂无项目说明</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{
  description: string | null | undefined
}>()

const renderedHtml = computed(() => renderMarkdown(props.description || ''))
</script>

<style scoped>
.section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 12px;
}

.description-rendered {
  font-size: 13px;
  color: var(--tf-text-secondary);
  line-height: 1.6;
}

.description-rendered :deep(p) {
  margin: 0 0 8px;
}

.description-rendered :deep(h1),
.description-rendered :deep(h2),
.description-rendered :deep(h3) {
  color: var(--tf-text-primary);
  font-weight: 600;
  margin: 12px 0 6px;
}

.description-rendered :deep(h1) { font-size: 16px; }
.description-rendered :deep(h2) { font-size: 14px; }
.description-rendered :deep(h3) { font-size: 13px; }

.description-rendered :deep(strong) {
  color: var(--tf-text-primary);
  font-weight: 600;
}

.description-rendered :deep(code) {
  background: var(--tf-bg-code, var(--tf-bg-surface));
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
}

.description-rendered :deep(pre) {
  background: var(--tf-bg-code, var(--tf-bg-surface));
  padding: 10px 12px;
  border-radius: 4px;
  overflow-x: auto;
  border: 1px solid var(--tf-border);
  margin: 8px 0;
}

.description-rendered :deep(pre code) {
  background: none;
  padding: 0;
}

.description-rendered :deep(blockquote) {
  border-left: 3px solid var(--tf-accent);
  padding-left: 10px;
  color: var(--tf-text-tertiary);
  margin: 6px 0;
}

.description-rendered :deep(a) {
  color: var(--tf-accent);
  text-decoration: none;
}

.description-rendered :deep(a:hover) {
  text-decoration: underline;
}

.description-rendered :deep(ul),
.description-rendered :deep(ol) {
  padding-left: 20px;
  margin: 4px 0 8px;
}

.description-rendered :deep(li) {
  margin-bottom: 2px;
}

.description-empty {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
  font-style: italic;
}
</style>
