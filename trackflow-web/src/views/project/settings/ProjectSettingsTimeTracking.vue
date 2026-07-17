<template>
  <div class="settings-time-tracking">
    <!-- 归档提示 -->
    <div v-if="isArchived" class="archived-notice">
      <icon-lock class="notice-icon" />
      <span>项目已归档，设置为只读状态</span>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <a-spin :size="24" />
    </div>

    <template v-else>
      <!-- 启用开关 Section -->
      <div class="settings-section">
        <h3 class="section-title">时间追踪</h3>
        <p class="section-desc">
          启用后，该项目的工单将显示工时记录功能，团队成员可以为工单添加花费的时间。
        </p>
        <div class="setting-row">
          <div class="setting-info">
            <span class="setting-label">启用时间追踪</span>
            <span class="setting-hint">
              禁用后，该项目工单详情页不再显示"添加花费的时间"按钮和工时进度条
            </span>
          </div>
          <a-switch
            :model-value="enabled"
            :disabled="!canManage || isArchived"
            @change="handleToggle"
          />
        </div>
      </div>

      <!-- 状态说明 -->
      <div v-if="!enabled" class="disabled-notice">
        <icon-info-circle class="notice-icon" />
        <div class="notice-content">
          <span class="notice-title">时间追踪已禁用</span>
          <span class="notice-desc">
            该项目的工单不会显示工时相关功能。已有的工时记录仍然保留，重新启用后可继续使用。
          </span>
        </div>
      </div>

      <!-- 已启用时的额外信息 -->
      <div v-if="enabled" class="enabled-info">
        <div class="settings-section">
          <h3 class="section-title">功能说明</h3>
          <div class="feature-list">
            <div class="feature-item">
              <icon-check-circle class="feature-icon" />
              <span>工单详情页显示"添加花费的时间"按钮</span>
            </div>
            <div class="feature-item">
              <icon-check-circle class="feature-icon" />
              <span>工单侧边栏显示预估工时和已花时间</span>
            </div>
            <div class="feature-item">
              <icon-check-circle class="feature-icon" />
              <span>支持通过工时表页面记录和查看工时</span>
            </div>
            <div class="feature-item">
              <icon-check-circle class="feature-icon" />
              <span>工作项属性（如工作类型）可按项目配置</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconLock, IconInfoCircle, IconCheckCircle } from '@arco-design/web-vue/es/icon'
import { projectApi } from '@/api'
import type { ProjectDetailVO } from '@/api/types'

const props = defineProps<{
  project: ProjectDetailVO
  canManage: boolean
  isArchived: boolean
}>()

const loading = ref(true)
const enabled = ref(true)

onMounted(async () => {
  await loadSettings()
})

async function loadSettings() {
  loading.value = true
  try {
    const res = await projectApi.getTimeTrackingSettings(props.project.id)
    if (res.code === 0 && res.data) {
      enabled.value = res.data.enabled
    }
  } catch {
    // Fail silently, default to enabled
  } finally {
    loading.value = false
  }
}

async function handleToggle(val: boolean | string | number) {
  const newEnabled = val as boolean
  try {
    const res = await projectApi.updateTimeTrackingSettings(props.project.id, { enabled: newEnabled })
    if (res.code === 0 && res.data) {
      enabled.value = res.data.enabled
      Message.success(newEnabled ? '时间追踪已启用' : '时间追踪已禁用')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}
</script>

<style scoped>
.settings-time-tracking {
  max-width: 640px;
}

/* Archived notice */
.archived-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--tf-bg-elevated, var(--color-fill-2));
  border-radius: 6px;
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin-bottom: 24px;
}

.archived-notice .notice-icon {
  font-size: 16px;
  color: var(--tf-text-tertiary);
}

/* Loading */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120px;
}

/* Section */
.settings-section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 4px;
}

.section-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0 0 16px;
  line-height: 1.5;
}

/* Setting row */
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: var(--tf-bg-surface, var(--color-fill-1));
  border-radius: 6px;
  border: 1px solid var(--tf-border, var(--color-border));
}

.setting-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.setting-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.setting-hint {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

/* Disabled notice */
.disabled-notice {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: var(--tf-bg-surface, var(--color-fill-1));
  border-radius: 6px;
  border: 1px solid var(--tf-border, var(--color-border));
}

.disabled-notice .notice-icon {
  font-size: 18px;
  color: var(--color-warning-6, #d29922);
  flex-shrink: 0;
  margin-top: 2px;
}

.notice-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.notice-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.notice-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  line-height: 1.5;
}

/* Enabled info */
.enabled-info {
  margin-top: 8px;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.feature-icon {
  font-size: 16px;
  color: var(--color-success-6, #3fb950);
  flex-shrink: 0;
}
</style>
