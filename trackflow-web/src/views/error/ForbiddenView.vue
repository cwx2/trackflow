<template>
  <div class="forbidden-page">
    <div class="forbidden-content">
      <div class="forbidden-icon">🔒</div>
      <h1 class="forbidden-title">无权访问</h1>
      <p class="forbidden-desc">
        您没有权限访问此页面。如需访问，请联系系统管理员为您分配相应权限。
      </p>
      <div class="forbidden-actions">
        <a-button type="primary" @click="goHome">返回首页</a-button>
        <a-button @click="goBack">返回上一页</a-button>
      </div>
      <div class="forbidden-info">
        <span class="info-label">当前用户：</span>
        <span class="info-value">{{ userName }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const userName = computed(() => {
  return authStore.user?.displayName || authStore.user?.username || '未知用户'
})

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
.forbidden-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
}

.forbidden-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 400px;
}

.forbidden-icon {
  font-size: 56px;
  margin-bottom: 24px;
}

.forbidden-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 12px;
}

.forbidden-desc {
  font-size: 14px;
  color: var(--tf-text-secondary);
  line-height: 1.6;
  margin: 0 0 32px;
}

.forbidden-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 32px;
}

.forbidden-info {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  padding: 8px 16px;
  background: var(--tf-bg-surface);
  border-radius: 4px;
}

.info-label {
  color: var(--tf-text-tertiary);
}

.info-value {
  font-weight: 500;
}
</style>
