<template>
  <div v-if="show" class="sprint-guidance-banner" :class="{ 'is-warning': !hasActiveSprint, 'is-info': hasActiveSprint }">
    <component
      :is="hasActiveSprint ? IconInfoCircle : IconExclamationCircle"
      class="warning-bar-icon"
    />
    <span class="warning-bar-text">{{ message }}</span>
    <a-tooltip v-if="!hasActiveSprint" :content="activateTooltip">
      <span class="tooltip-wrapper">
        <a-button
          size="mini"
          type="primary"
          class="warning-bar-action"
          :disabled="!canActivate"
          @click="$emit('activate')"
        >
          开始迭代
        </a-button>
      </span>
    </a-tooltip>
  </div>
</template>

<script setup lang="ts">
import { IconInfoCircle, IconExclamationCircle } from '@arco-design/web-vue/es/icon'

defineProps<{
  show: boolean
  hasActiveSprint: boolean
  message: string
  activateTooltip?: string
  canActivate: boolean
}>()

defineEmits<{
  activate: []
}>()
</script>

<style scoped>
.sprint-guidance-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 6px;
  margin-bottom: 4px;
}
.sprint-guidance-banner.is-warning { background: var(--tf-warning-bg); border: 1px solid rgba(var(--warning-6), 0.25); }
.sprint-guidance-banner.is-info { background: var(--tf-accent-subtle); border: 1px solid var(--tf-accent-bg); }
.warning-bar-icon { font-size: 16px; flex-shrink: 0; color: inherit; }
.warning-bar-text { flex: 1; font-size: 13px; color: var(--color-text-1); line-height: 1.4; }
.warning-bar-action { flex-shrink: 0; }
.tooltip-wrapper { display: inline-block; }
</style>
