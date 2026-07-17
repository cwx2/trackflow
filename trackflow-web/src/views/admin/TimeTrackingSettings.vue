<template>
  <div class="time-tracking-settings">
    <!-- Back button + title -->
    <div class="settings-header">
      <router-link to="/admin" class="back-link">← 返回管理</router-link>
      <h1 class="settings-title">时间追踪设置</h1>
      <p class="settings-desc">配置系统级时间追踪参数，影响所有项目的工时单位换算和时间表显示。</p>
    </div>

    <div v-if="loading" class="settings-loading">
      <a-spin :size="20" />
      <span>加载中...</span>
    </div>

    <template v-else>
      <!-- Hours per day -->
      <div class="settings-section">
        <div class="section-header">
          <h2 class="section-title">每日工作时长</h2>
          <p class="section-desc">
            定义"1天"等于多少工作小时。此设置影响工时输入中"1d"的换算（例如设为 8，则 1d = 8h）以及时间表中的每日配额显示。
          </p>
        </div>
        <div class="field-row">
          <label class="field-label">每天工作小时数</label>
          <div class="field-input">
            <a-input-number
              v-model="form.hoursPerDay"
              :min="1"
              :max="24"
              :step="1"
              :style="{ width: '120px' }"
            />
            <span class="field-suffix">小时 / 天</span>
          </div>
          <p class="field-hint">有效范围：1 - 24 小时。默认值为 8 小时。</p>
        </div>
      </div>

      <!-- Working days -->
      <div class="settings-section">
        <div class="section-header">
          <h2 class="section-title">工作日</h2>
          <p class="section-desc">
            选择每周的工作日。非工作日在时间表中以灰色标注，不显示配额，燃尽图理想线在非工作日不下降。
          </p>
        </div>
        <div class="workdays-grid">
          <label
            v-for="day in allDays"
            :key="day.value"
            class="workday-item"
            :class="{ checked: form.workingDays.includes(day.value) }"
          >
            <input
              type="checkbox"
              :value="day.value"
              :checked="form.workingDays.includes(day.value)"
              @change="toggleWorkday(day.value)"
              class="workday-checkbox"
            />
            <span class="workday-label">{{ day.label }}</span>
          </label>
        </div>
        <p class="field-hint">至少选择一个工作日。</p>
      </div>

      <!-- Preview -->
      <div class="settings-section">
        <div class="section-header">
          <h2 class="section-title">换算预览</h2>
        </div>
        <div class="preview-grid">
          <div class="preview-item">
            <span class="preview-label">1d =</span>
            <span class="preview-value">{{ form.hoursPerDay }}h</span>
          </div>
          <div class="preview-item">
            <span class="preview-label">1w =</span>
            <span class="preview-value">{{ form.hoursPerDay * form.workingDays.length }}h ({{ form.workingDays.length }}天)</span>
          </div>
          <div class="preview-item">
            <span class="preview-label">每日配额 =</span>
            <span class="preview-value">{{ form.hoursPerDay }}h</span>
          </div>
        </div>
      </div>

      <!-- Save button -->
      <div class="settings-actions">
        <a-button type="primary" :loading="saving" @click="saveSettings">
          保存设置
        </a-button>
        <a-button @click="resetForm">
          重置
        </a-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { systemSettingApi } from '@/api/systemSetting'
import type { TimeTrackingSettingsVO } from '@/api/systemSetting'

const loading = ref(true)
const saving = ref(false)

const form = reactive({
  hoursPerDay: 8,
  workingDays: [1, 2, 3, 4, 5] as number[]
})

// Original values for reset
let originalSettings: TimeTrackingSettingsVO = { hoursPerDay: 8, workingDays: [1, 2, 3, 4, 5] }

const allDays = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' }
]

function toggleWorkday(day: number) {
  const idx = form.workingDays.indexOf(day)
  if (idx >= 0) {
    // Don't allow removing the last working day
    if (form.workingDays.length <= 1) {
      Message.warning('至少需要保留一个工作日')
      return
    }
    form.workingDays.splice(idx, 1)
  } else {
    form.workingDays.push(day)
    form.workingDays.sort((a, b) => a - b)
  }
}

async function loadSettings() {
  loading.value = true
  try {
    const res = await systemSettingApi.getTimeTrackingSettings()
    if (res.code === 0 && res.data) {
      form.hoursPerDay = res.data.hoursPerDay
      form.workingDays = [...res.data.workingDays]
      originalSettings = { ...res.data }
    }
  } catch (e) {
    Message.error('加载时间追踪设置失败')
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  if (form.workingDays.length === 0) {
    Message.warning('至少需要选择一个工作日')
    return
  }
  saving.value = true
  try {
    const res = await systemSettingApi.updateTimeTrackingSettings({
      hoursPerDay: form.hoursPerDay,
      workingDays: form.workingDays
    })
    if (res.code === 0 && res.data) {
      originalSettings = { ...res.data }
      Message.success('时间追踪设置已保存')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function resetForm() {
  form.hoursPerDay = originalSettings.hoursPerDay
  form.workingDays = [...originalSettings.workingDays]
}

onMounted(loadSettings)
</script>

<style scoped>
.time-tracking-settings {
  padding: 32px;
  max-width: 720px;
  height: 100%;
  overflow-y: auto;
}

.settings-header {
  margin-bottom: 32px;
}

.back-link {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  text-decoration: none;
  display: inline-block;
  margin-bottom: 12px;
  transition: color 0.15s;
}

.back-link:hover {
  color: var(--tf-accent);
}

.settings-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px 0;
}

.settings-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
  line-height: 1.5;
}

.settings-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
  color: var(--tf-text-tertiary);
  font-size: 13px;
}

.settings-section {
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--tf-border-light);
}

.settings-section:last-of-type {
  border-bottom: none;
}

.section-header {
  margin-bottom: 16px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 4px 0;
}

.section-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0;
  line-height: 1.5;
}

.field-row {
  margin-bottom: 8px;
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  margin-bottom: 6px;
}

.field-input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-suffix {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.field-hint {
  font-size: 11px;
  color: var(--tf-text-muted);
  margin: 6px 0 0 0;
}

.workdays-grid {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.workday-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 6px;
  border: 1px solid var(--tf-border-light);
  background: var(--tf-bg-surface);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  user-select: none;
}

.workday-item:hover {
  border-color: var(--tf-border);
  background: var(--tf-bg-hover);
}

.workday-item.checked {
  border-color: var(--tf-accent);
  background: var(--tf-accent-bg);
}

.workday-checkbox {
  width: 14px;
  height: 14px;
  accent-color: var(--tf-accent);
  cursor: pointer;
}

.workday-label {
  font-size: 13px;
  color: var(--tf-text-primary);
  font-weight: 500;
}

.preview-grid {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.preview-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.preview-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.preview-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.settings-actions {
  display: flex;
  gap: 12px;
  padding-top: 8px;
}
</style>
