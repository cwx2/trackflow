<template>
  <div class="login-container">
    <div class="login-card">
      <h1>TrackFlow</h1>
      <p>内部项目管理系统</p>
      <div v-if="logoutReason" class="logout-reason">
        <span class="logout-reason-icon">⚠️</span>
        {{ logoutReason }}
      </div>
      <a-button type="primary" size="large" @click="handleLogin">
        使用公司账号登录
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const logoutReason = ref<string | null>(null)

onMounted(() => {
  // 读取并显示登出原因（如 token 过期）
  const reason = sessionStorage.getItem('tf_logout_reason')
  if (reason) {
    logoutReason.value = reason
    sessionStorage.removeItem('tf_logout_reason')
  }
})

function handleLogin() {
  authStore.login()
}
</script>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--tf-bg-body);
}

.login-card {
  text-align: center;
  padding: 48px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 12px;
  color: var(--tf-text-primary);
  box-shadow: var(--tf-shadow);
}

.login-card h1 {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.3px;
  margin-bottom: 8px;
  color: var(--tf-text-primary);
}

.login-card p {
  font-size: 14px;
  color: var(--tf-text-tertiary);
  margin-bottom: 32px;
}

.logout-reason {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  margin-bottom: 24px;
  background: rgba(var(--warning-6), 0.08);
  border: 1px solid rgba(var(--warning-6), 0.3);
  border-radius: 6px;
  color: var(--tf-text-secondary);
  font-size: 13px;
}

.logout-reason-icon {
  font-size: 16px;
}

.login-card :deep(.arco-btn) {
  height: 40px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
}
</style>
