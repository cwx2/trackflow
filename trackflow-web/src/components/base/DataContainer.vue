<template>
  <div class="data-container">
    <!-- Loading 状态：使用 Arco a-spin，保持风格统一 -->
    <div v-if="loading" class="data-container__loading">
      <slot name="loading">
        <a-spin :size="28" />
        <span v-if="loadingText" class="loading-text">{{ loadingText }}</span>
      </slot>
    </div>

    <!-- Error 状态：复用 EmptyState type="error" -->
    <div v-else-if="error" class="data-container__state">
      <slot name="error" :error="error" :retry="retry">
        <EmptyState
          type="error"
          icon="exclamation-circle"
          title="加载失败"
          :description="error"
        >
          <template v-if="retry" #action>
            <a-button type="primary" size="small" @click="retry">重新加载</a-button>
          </template>
        </EmptyState>
      </slot>
    </div>

    <!-- Empty 状态：复用 EmptyState -->
    <div v-else-if="isEmpty" class="data-container__state">
      <slot name="empty">
        <EmptyState
          :title="emptyTitle || '暂无数据'"
          :description="emptyDescription"
        >
          <template v-if="createAction" #action>
            <a-button type="primary" size="small" @click="$emit('create')">
              {{ createAction }}
            </a-button>
          </template>
        </EmptyState>
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
 * 内部使用 Arco a-spin（loading）和 EmptyState（error/empty）保持全局风格一致。
 *
 * 用法：
 * <DataContainer :loading="loading" :error="error" :is-empty="list.length === 0"
 *   empty-title="暂无数据" create-action="创建" @create="openCreateDialog">
 *   <MyTable :data="list" />
 * </DataContainer>
 */
import { EmptyState } from '@/components/base'

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

/* loading 态：水平居中 spin */
.data-container__loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 48px 24px;
}

.loading-text {
  font-size: 13px;
  color: var(--tf-text-tertiary);
}

/* error/empty 态：交给 EmptyState 处理，外层只做最小容器 */
.data-container__state {
  width: 100%;
}
</style>
