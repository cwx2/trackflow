<template>
  <div class="data-container">
    <!-- Loading 状态 -->
    <div v-if="loading" class="data-container__loading">
      <slot name="loading">
        <div class="data-container__loading-default">
          <div class="loading-spinner"></div>
          <span class="loading-text">{{ loadingText }}</span>
        </div>
      </slot>
    </div>

    <!-- Error 状态 -->
    <div v-else-if="error" class="data-container__error">
      <slot name="error" :error="error" :retry="retry">
        <div class="data-container__error-default">
          <icon-exclamation-circle-fill class="error-icon" :size="48" />
          <h3 class="error-title">加载失败</h3>
          <p class="error-desc">{{ error }}</p>
          <button v-if="retry" class="btn-retry" @click="retry">重新加载</button>
        </div>
      </slot>
    </div>

    <!-- Empty 状态 -->
    <div v-else-if="isEmpty" class="data-container__empty">
      <slot name="empty">
        <div class="data-container__empty-default">
          <slot name="empty-icon">
              <icon-empty class="empty-icon" :size="48" />
            </slot>
          <h3 class="empty-title">{{ emptyTitle }}</h3>
          <p v-if="emptyDescription" class="empty-desc">{{ emptyDescription }}</p>
          <button v-if="createAction" class="btn-create" @click="$emit('create')">
            {{ createAction }}
          </button>
        </div>
      </slot>
    </div>

    <!-- 正常内容 -->
    <slot v-else />
  </div>
</template>

<script setup lang="ts">
/**
 * DataContainer — 统一管理 loading / error / empty 三态显示容器
 *
 * 用法：
 * <DataContainer :loading="loading" :error="error" :is-empty="list.length === 0"
 *   empty-title="暂无数据" create-action="创建" @create="openCreateDialog">
 *   <MyTable :data="list" />
 * </DataContainer>
 */
defineProps<{
  /** 是否加载中 */
  loading: boolean
  /** 错误信息（truthy 时显示 error 态） */
  error?: string | null
  /** 是否为空（true 时显示 empty 态） */
  isEmpty?: boolean
  /** 加载中文案 */
  loadingText?: string
  /** 空状态标题 */
  emptyTitle?: string
  /** 空状态描述 */
  emptyDescription?: string
  /** 创建按钮文案（存在时显示按钮） */
  createAction?: string
  /** 重试函数（存在时在 error 态显示"重新加载"按钮） */
  retry?: (() => void) | null
}>()

defineEmits<{
  (e: 'create'): void
}>()
</script>

<style scoped>
.data-container {
  width: 100%;
  min-height: 120px;
}

.data-container__loading,
.data-container__error,
.data-container__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
}

.data-container__loading-default {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.loading-spinner {
  width: 28px;
  height: 28px;
  border: 2.5px solid var(--tf-border, #e5e7eb);
  border-top-color: var(--tf-accent, #58a6ff);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-text {
  font-size: 13px;
  color: var(--tf-text-tertiary, #6b7280);
}

.data-container__error-default,
.data-container__empty-default {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;
}

.error-icon,
.empty-icon {
  color: var(--tf-text-quaternary, #4b5563);
  margin-bottom: 4px;
}

.error-icon {
  color: var(--tf-error, #f85149);
}

.error-title,
.empty-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary, #e6edf3);
  margin: 0;
}

.error-desc,
.empty-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary, #6b7280);
  margin: 0;
  max-width: 320px;
}

.btn-retry,
.btn-create {
  margin-top: 12px;
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 6px;
  border: 1px solid var(--tf-border, #e5e7eb);
  background: var(--tf-bg-surface, #22252a);
  color: var(--tf-text-primary, #e6edf3);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.btn-retry:hover,
.btn-create:hover {
  background: var(--tf-bg-hover, #2a2d33);
  border-color: var(--tf-accent, #58a6ff);
}
</style>
