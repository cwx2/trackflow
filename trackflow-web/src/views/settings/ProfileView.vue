<template>
  <div class="settings-page">
    <h2 class="page-title">个人设置</h2>

    <div class="settings-section">
      <h3 class="section-title">基本信息</h3>
      <div class="info-grid">
        <div class="info-item">
          <span class="info-label">用户名</span>
          <span class="info-value">{{ user?.username || '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">显示名称</span>
          <span class="info-value">{{ user?.displayName || '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">邮箱</span>
          <span class="info-value">{{ user?.email || '-' }}</span>
        </div>
      </div>
      <p class="info-hint">账号信息由公司身份系统 (Keycloak) 管理，如需修改请联系管理员。</p>
    </div>

    <div class="settings-section">
      <h3 class="section-title">界面偏好</h3>
      <div class="pref-item">
        <div class="pref-info">
          <span class="pref-label">主题模式</span>
          <span class="pref-desc">选择界面显示风格</span>
        </div>
        <a-select v-model="currentTheme" size="small" style="width: 120px" @change="onThemeChange">
          <a-option value="dark">暗色</a-option>
          <a-option value="light">亮色</a-option>
        </a-select>
      </div>
    </div>

    <!-- 导航到通知设置 -->
    <div class="settings-footer">
      <router-link to="/settings/security" class="footer-link">
        账号安全（API Key） →
      </router-link>
      <router-link to="/settings/notifications" class="footer-link">
        通知设置 →
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'

const authStore = useAuthStore()
const { theme, setTheme } = useTheme()

const user = computed(() => authStore.user)
const currentTheme = ref(theme.value)

function onThemeChange(val: string) {
  setTheme(val as 'dark' | 'light')
}
</script>

<style scoped>
.settings-page {
  padding: 32px;
  max-width: 640px;
  overflow-y: auto;
  height: 100%;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin-bottom: 24px;
}

.settings-section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--tf-border-light);
}

.info-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 16px;
}

.info-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  width: 80px;
  flex-shrink: 0;
}

.info-value {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.info-hint {
  margin-top: 12px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  line-height: 1.4;
}

.pref-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
}

.pref-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pref-label {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.pref-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.settings-footer {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--tf-border-light);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.footer-link {
  font-size: 12px;
  color: var(--tf-text-accent);
  text-decoration: none;
  transition: opacity 150ms;
}

.footer-link:hover {
  opacity: 0.8;
}
</style>
