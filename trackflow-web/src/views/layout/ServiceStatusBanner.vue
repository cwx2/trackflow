<template>
  <Transition name="banner-slide">
    <div v-if="isServiceDown" class="service-status-banner">
      <div class="banner-content">
        <icon-exclamation-circle-fill class="banner-icon" />
        <span class="banner-message">{{ errorMessage }}</span>
        <a-button size="mini" type="outline" class="banner-retry-btn" @click="handleRetry">
          <template #icon><icon-refresh /></template>
          重新连接
        </a-button>
        <a-button size="mini" type="text" class="banner-close-btn" @click="handleDismiss">
          <template #icon><icon-close /></template>
        </a-button>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { useServiceStatus } from '@/composables/useServiceStatus'

const { isServiceDown, errorMessage, clearServiceError } = useServiceStatus()

function handleRetry() {
  clearServiceError()
  // 重新加载当前页面的数据
  window.location.reload()
}

function handleDismiss() {
  clearServiceError()
}
</script>

<style scoped>
.service-status-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  background: var(--color-danger-light-1, #ffece8);
  border-bottom: 1px solid var(--color-danger-light-3, #fdcdc5);
  padding: 8px 16px;
}

:root[arco-theme="dark"] .service-status-banner {
  background: rgba(var(--danger-6), 0.15);
  border-bottom-color: rgba(var(--danger-6), 0.3);
}

.banner-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  max-width: 1200px;
  margin: 0 auto;
}

.banner-icon {
  font-size: 16px;
  color: var(--color-danger-6, #f53f3f);
  flex-shrink: 0;
}

.banner-message {
  font-size: 13px;
  color: var(--color-danger-6, #f53f3f);
  font-weight: 500;
}

:root[arco-theme="dark"] .banner-message {
  color: var(--color-danger-light-3, #fdcdc5);
}

.banner-retry-btn {
  margin-left: 8px;
  flex-shrink: 0;
}

.banner-close-btn {
  flex-shrink: 0;
  color: var(--color-danger-6, #f53f3f);
}

/* Transition animation */
.banner-slide-enter-active,
.banner-slide-leave-active {
  transition: transform 200ms ease, opacity 200ms ease;
}

.banner-slide-enter-from,
.banner-slide-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}
</style>
