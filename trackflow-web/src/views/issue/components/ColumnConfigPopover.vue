<template>
  <a-trigger trigger="click" position="bl" :popup-offset="4">
    <a-button type="text" size="mini" class="config-btn" title="列配置">
      <template #icon><icon-ordered-list /></template>
    </a-button>
    <template #content>
      <div class="column-config-panel">
        <div class="config-header">
          <span class="config-title">显示列</span>
          <a-button type="text" size="mini" @click="$emit('reset')">重置</a-button>
        </div>

        <!-- 标准列 -->
        <div class="config-group-label">标准字段</div>
        <div class="config-list">
          <div v-for="col in standardColumns" :key="col.key" class="config-item">
            <a-checkbox :model-value="isVisible(col.key)" @change="$emit('toggle', col.key)">
              {{ col.label }}
            </a-checkbox>
          </div>
        </div>

        <!-- 自定义字段列 -->
        <template v-if="customFieldColumns.length > 0">
          <div class="config-group-label">自定义字段</div>
          <div class="config-list">
            <div v-for="col in customFieldColumns" :key="col.key" class="config-item">
              <a-checkbox :model-value="isVisible(col.key)" @change="$emit('toggle', col.key)">
                {{ col.label }}
              </a-checkbox>
            </div>
          </div>
        </template>
      </div>
    </template>
  </a-trigger>
</template>

<script setup lang="ts">
import { IconOrderedList } from '@arco-design/web-vue/es/icon'
import type { ColumnDef } from '../composables/useColumnConfig'

defineProps<{
  standardColumns: ColumnDef[]
  customFieldColumns: ColumnDef[]
  isVisible: (key: string) => boolean
}>()

defineEmits<{
  toggle: [key: string]
  reset: []
}>()
</script>

<style scoped>
.config-btn { color: var(--tf-text-tertiary); }
.config-btn:hover { color: var(--tf-text-primary); }

.column-config-panel {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  padding: 8px;
  min-width: 200px;
  max-height: 360px;
  overflow-y: auto;
}

.config-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px 8px;
  border-bottom: 1px solid var(--tf-border-light);
  margin-bottom: 4px;
}

.config-title { font-size: 12px; font-weight: 500; color: var(--tf-text-primary); }

.config-group-label {
  padding: 8px 8px 4px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 500;
}

.config-list { display: flex; flex-direction: column; gap: 2px; }

.config-item {
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.15s;
}
.config-item:hover { background: var(--tf-bg-hover); }
</style>
