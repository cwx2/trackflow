<template>
  <div class="error-page">
    <EmptyState
      icon="search"
      title="页面不存在"
      description="您访问的页面不存在或已被移除。请检查链接地址是否正确。"
    >
      <template #action>
        <div class="error-actions">
          <a-button type="primary" @click="goHome">返回首页</a-button>
          <a-button @click="goBack">返回上一页</a-button>
        </div>
      </template>
    </EmptyState>
    <div class="error-info">
      <span class="info-label">当前路径：</span>
      <code class="path-value">{{ currentPath }}</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { EmptyState } from '@/components/base'

const router = useRouter()
const route = useRoute()

const currentPath = computed(() => route.fullPath)

function goHome() { router.push({ name: 'Issues' }) }
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push({ name: 'Issues' })
}
</script>

<style scoped>
.error-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
}

.error-actions { display: flex; gap: 12px; }

.error-info {
  margin-top: 24px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
  padding: 8px 16px;
  background: var(--tf-bg-surface);
  border-radius: 4px;
}

.info-label { color: var(--tf-text-tertiary); }
.path-value { font-family: monospace; font-weight: 500; color: var(--tf-text-secondary); }
</style>
