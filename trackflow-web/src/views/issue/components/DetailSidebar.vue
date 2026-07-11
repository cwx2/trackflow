<template>
  <aside class="detail-sidebar">
    <!-- 状态 -->
    <div class="sb-block">
      <div class="sb-label">状态</div>
      <span class="status-pill" :style="{ background: status.color }">{{ status.name }}</span>
      <div class="transitions" v-if="transitions.length > 0">
        <button
          v-for="t in transitions"
          :key="t.id"
          class="trans-btn"
          :style="{ borderColor: t.color, color: t.color }"
          @click="$emit('transition', t)"
        >→ {{ t.name }}</button>
      </div>
    </div>

    <!-- 字段列表 -->
    <div
      v-for="field in fields"
      :key="field.key"
      class="sb-field"
      :class="{ readonly: field.readonly, separator: field.key === '_sep' }"
      @click="!field.readonly && field.key !== '_sep' && $emit('edit-field', field.key)"
    >
      <template v-if="field.key !== '_sep'">
        <div class="sb-label">{{ field.label }}</div>
        <div class="sb-value" :class="field.class">
          <span v-if="field.dot" class="val-dot" :style="{ background: field.dot }"></span>
          {{ field.value }}
        </div>
      </template>
      <div v-else class="sep-line"></div>
    </div>
  </aside>
</template>

<script setup lang="ts">
export interface SidebarField {
  key: string
  label: string
  value: string
  dot?: string
  class?: string
  readonly?: boolean
}

export interface StatusInfo {
  id: string
  name: string
  color: string
}

defineProps<{
  status: StatusInfo
  transitions: StatusInfo[]
  fields: SidebarField[]
}>()

defineEmits<{
  transition: [target: StatusInfo]
  'edit-field': [fieldKey: string]
}>()
</script>

<style scoped>
.detail-sidebar {
  width: 240px;
  flex-shrink: 0;
  border-left: 1px solid var(--tf-border);
  padding: 16px;
  overflow-y: auto;
  font-size: 12px;
  background: var(--tf-bg-surface);
}

.sb-block {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--tf-border-light);
}

.sb-label {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.status-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 3px;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 8px;
}

.transitions { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
.trans-btn {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
  background: none;
  border: 1px solid;
  cursor: pointer;
  transition: opacity 150ms, background 150ms;
}
.trans-btn:hover { opacity: .8; background: var(--tf-bg-hover); }

.sb-field {
  padding: 8px 4px;
  border-radius: 3px;
  cursor: pointer;
  margin-bottom: 0;
  transition: background 150ms;
}
.sb-field:not(.readonly):not(.separator):hover { background: var(--tf-bg-hover); }
.sb-field.readonly { cursor: default; }
.sb-field.separator { padding: 0; margin: 8px 0; }

.sb-value {
  font-size: 12px;
  color: var(--tf-text-primary);
  display: flex;
  align-items: center;
  gap: 4px;
}
.val-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.sep-line { height: 1px; background: var(--tf-border-light); }
</style>
