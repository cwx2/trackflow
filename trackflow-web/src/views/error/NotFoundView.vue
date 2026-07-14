<template>
  <div class="not-found-page">
    <div class="not-found-content">
      <div class="not-found-icon">🔍</div>
      <h1 class="not-found-title">页面不存在</h1>
      <p class="not-found-desc">
        您访问的页面不存在或已被移除。请检查链接地址是否正确。
      </p>
      <div class="not-found-actions">
        <a-button type="primary" @click="goHome">返回首页</a-button>
        <a-button @click="goBack">返回上一页</a-button>
      </div>
      <div class="not-found-path">
        <span class="path-label">当前路径：</span>
        <code class="path-value">{{ currentPath }}</code>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const currentPath = computed(() => route.fullPath)

function goHome() {
  router.push({ name: 'Issues' })
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'Issues' })
  }
}
</script>

<style scoped>
.not-found-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
}

.not-found-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 400px;
}

.not-found-icon {
  font-size: 56px;
  margin-bottom: 24px;
}

.not-found-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 12px;
}

.not-found-desc {
  font-size: 14px;
  color: var(--tf-text-secondary);
  line-height: 1.6;
  margin: 0 0 32px;
}

.not-found-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 32px;
}

.not-found-path {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  padding: 8px 16px;
  background: var(--tf-bg-surface);
  border-radius: 4px;
}

.path-label {
  color: var(--tf-text-tertiary);
}

.path-value {
  font-family: monospace;
  font-weight: 500;
  color: var(--tf-text-secondary);
}
</style>
