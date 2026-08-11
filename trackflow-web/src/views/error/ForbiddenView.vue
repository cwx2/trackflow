<template>
  <div class="error-page">
    <EmptyState
      icon="lock"
      title="无权访问"
      description="您没有权限访问此页面。如需访问，请联系系统管理员为您分配相应权限。"
    >
      <template #action>
        <div class="error-actions">
          <a-button type="primary" @click="goHome">返回首页</a-button>
          <a-button @click="goBack">返回上一页</a-button>
        </div>
      </template>
    </EmptyState>
    <div class="error-info">
      <span class="info-label">当前用户：</span>
      <span class="info-value">{{ userName }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { EmptyState } from '@/components/base'

const router = useRouter()
const authStore = useAuthStore()

const userName = computed(() => authStore.user?.displayName || authStore.user?.username || '未知用户')

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
.info-value { font-weight: 500; color: var(--tf-text-secondary); }
</style>
