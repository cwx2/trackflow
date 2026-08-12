<template>
  <div class="admin-page-layout" :style="maxWidthStyle">
    <!-- 面包屑：有 slot 用 slot，否则自动渲染"管理 > {title}" -->
    <div class="admin-page-breadcrumb">
      <slot name="breadcrumb">
        <a-breadcrumb>
          <a-breadcrumb-item>
            <router-link to="/admin">管理</router-link>
          </a-breadcrumb-item>
          <a-breadcrumb-item>{{ title }}</a-breadcrumb-item>
        </a-breadcrumb>
      </slot>
    </div>

    <!-- 页面头部：标题 + 操作按钮 -->
    <div class="admin-page-header">
      <div class="admin-page-title-area">
        <div class="admin-page-title-row">
          <h2 class="admin-page-title">{{ title }}</h2>
          <span v-if="subtitle" class="admin-page-subtitle">{{ subtitle }}</span>
        </div>
      </div>
      <div v-if="$slots.actions" class="admin-page-actions">
        <slot name="actions" />
      </div>
    </div>

    <!-- 分隔线 -->
    <div class="admin-page-divider" />

    <!-- 统计卡片（可选） -->
    <slot name="stats" />

    <!-- 标签页（可选，放在内容区上方） -->
    <div v-if="$slots.tabs" class="admin-page-tabs">
      <slot name="tabs" />
    </div>

    <!-- 页面主内容 -->
    <div class="admin-page-body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** 页面标题（显示在左上角） */
  title: string
  /** 副标题（可选，显示在主标题下方，用于说明页面用途） */
  subtitle?: string
  /** 页面最大宽度，默认不限制 */
  maxWidth?: string | number
}>()

const maxWidthStyle = computed(() => {
  if (!props.maxWidth) return undefined
  const value = typeof props.maxWidth === 'number' ? `${props.maxWidth}px` : props.maxWidth
  return { maxWidth: value }
})
</script>

<style scoped>
.admin-page-layout {
  padding: 24px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.admin-page-breadcrumb {
  flex-shrink: 0;
  margin-bottom: 12px;
}

.admin-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-shrink: 0;
  margin-bottom: 16px;
}

.admin-page-title-area {
  min-width: 0;
}

.admin-page-title-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.admin-page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
  line-height: 1.3;
  flex-shrink: 0;
}

.admin-page-subtitle {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
  line-height: 1.3;
}

.admin-page-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.admin-page-divider {
  height: 1px;
  background: var(--tf-border);
  flex-shrink: 0;
  margin-bottom: 16px;
}

.admin-page-tabs {
  flex-shrink: 0;
  margin-bottom: 0;
}

.admin-page-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
