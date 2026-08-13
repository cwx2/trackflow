<template>
  <div class="widget-note">
    <p class="note-placeholder" v-if="!content">
      点击编辑添加笔记内容...
    </p>
    <div v-else class="note-content" v-html="content"></div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  config: Record<string, any>
  /** 仪表盘所属项目 ID（来自 project_overview 仪表盘） */
  dashboardProjectId?: string
}>()

const content = computed(() => props.config.content || '')
</script>

<style scoped>
.widget-note {
  flex: 1;
}

.note-placeholder {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  font-style: italic;
  margin: 0;
}

.note-content {
  font-size: 13px;
  color: var(--tf-text-primary);
  line-height: 1.5;
}
</style>
