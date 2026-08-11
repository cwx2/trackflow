<template>
  <div class="settings-time-tracking">
    <!-- 归档提示 -->
    <div v-if="isArchived" class="archived-notice">
      <icon-lock class="notice-icon" />
      <span>项目已归档，设置为只读状态</span>
    </div>

    <!-- 加载/内容区 -->
    <DataContainer :loading="loading">
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
            :disabled="!canManage || isArchived || toggling"
            :loading="toggling"
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
    </DataContainer>

    <!-- 禁用确认对话框 -->
    <a-modal
      v-model:visible="disableModalVisible"
      :title="'确认禁用时间追踪'"
      :ok-text="'确认禁用'"
      :cancel-text="'取消'"
      :ok-button-props="{ status: 'danger' }"
      :ok-loading="disabling"
      :mask-closable="false"
      @ok="confirmDisable"
      @cancel="cancelDisable"
    >
      <div class="disable-modal-content">
        <div class="impact-summary">
          <div class="impact-icon-row">
            <icon-exclamation-circle-fill class="impact-warning-icon" />
            <span class="impact-title">此操作将影响以下数据</span>
          </div>

          <div v-if="impactLoading" class="impact-loading">
            <a-spin :size="20" />
            <span>正在评估影响...</span>
          </div>

          <div v-else class="impact-stats">
            <div class="impact-stat-row">
              <span class="stat-label">工时记录</span>
              <span class="stat-value">{{ impact.totalTimeEntries }} 条</span>
            </div>
            <div class="impact-stat-row">
              <span class="stat-label">涉及成员</span>
              <span class="stat-value">{{ impact.affectedUsers }} 人</span>
            </div>
            <div v-if="impact.activeTimers > 0" class="impact-stat-row impact-stat-warning">
              <span class="stat-label">活跃计时器</span>
              <span class="stat-value">{{ impact.activeTimers }} 个（将被自动停止）</span>
            </div>
          </div>
        </div>

        <div class="impact-description">
          <p>禁用后：</p>
          <ul>
            <li>团队成员将无法在该项目的工单上记录工时</li>
            <li v-if="impact.activeTimers > 0">正在进行的 {{ impact.activeTimers }} 个计时器将被自动停止并保存已用时长</li>
            <li>已有工时记录将保留，不会被删除</li>
            <li>重新启用后所有功能恢复正常</li>
          </ul>
        </div>

        <div class="impact-reversible">
          <icon-sync class="reversible-icon" />
          <span>此操作可逆——重新启用后数据完全恢复</span>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconLock, IconInfoCircle, IconCheckCircle, IconExclamationCircleFill, IconSync } from '@arco-design/web-vue/es/icon'
import { projectApi } from '@/api'
import type { ProjectDetailVO } from '@/api/types'
import { DataContainer } from '@/components/base'

const props = defineProps<{
  project: ProjectDetailVO
  canManage: boolean
  isArchived: boolean
}>()

const loading = ref(true)
const enabled = ref(true)
const toggling = ref(false)

// Disable confirmation modal state
const disableModalVisible = ref(false)
const disabling = ref(false)
const impactLoading = ref(false)
const impact = ref({
  totalTimeEntries: 0,
  affectedUsers: 0,
  activeTimers: 0
})

onMounted(async () => {
  await loadSettings()
})

async function loadSettings() {
  loading.value = true
  try {
    const res = await projectApi.getTimeTrackingSettings(props.project.key)
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
  if (newEnabled) {
    // 启用：直接执行，无需确认
    await doToggle(true)
  } else {
    // 禁用：弹出确认对话框 + 影响评估
    toggling.value = true
    disableModalVisible.value = true
    impactLoading.value = true
    try {
      const res = await projectApi.getTimeTrackingDisableImpact(props.project.key)
      if (res.code === 0 && res.data) {
        impact.value = res.data
      }
    } catch {
      // 即使获取影响失败，也允许继续禁用
      impact.value = { totalTimeEntries: 0, affectedUsers: 0, activeTimers: 0 }
    } finally {
      impactLoading.value = false
      toggling.value = false
    }
  }
}

async function confirmDisable() {
  disabling.value = true
  try {
    await doToggle(false)
    disableModalVisible.value = false
  } finally {
    disabling.value = false
  }
}

function cancelDisable() {
  disableModalVisible.value = false
}

async function doToggle(newEnabled: boolean) {
  try {
    const res = await projectApi.updateTimeTrackingSettings(props.project.key, { enabled: newEnabled })
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
  color: var(--tf-warning);
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
  color: var(--tf-success);
  flex-shrink: 0;
}

/* Disable modal */
.disable-modal-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.impact-summary {
  padding: 16px;
  background: var(--tf-bg-surface, var(--color-fill-1));
  border-radius: 6px;
  border: 1px solid var(--tf-border, var(--color-border));
}

.impact-icon-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.impact-warning-icon {
  font-size: 18px;
  color: var(--tf-warning);
}

.impact-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.impact-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
  padding: 8px 0;
}

.impact-stats {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.impact-stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--tf-bg-elevated, var(--color-fill-2));
  border-radius: 4px;
}

.impact-stat-warning {
  background: color-mix(in srgb, var(--tf-warning) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--tf-warning) 20%, transparent);
}

.stat-label {
  font-size: 12px;
  color: var(--tf-text-secondary);
}

.stat-value {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.impact-stat-warning .stat-value {
  color: var(--tf-warning);
}

.impact-description {
  font-size: 13px;
  color: var(--tf-text-secondary);
  line-height: 1.6;
}

.impact-description p {
  margin: 0 0 4px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.impact-description ul {
  margin: 0;
  padding-left: 20px;
}

.impact-description li {
  margin-bottom: 4px;
}

.impact-reversible {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: color-mix(in srgb, var(--tf-success) 8%, transparent);
  border-radius: 4px;
  font-size: 12px;
  color: var(--tf-success);
}

.reversible-icon {
  font-size: 14px;
  flex-shrink: 0;
}
</style>
