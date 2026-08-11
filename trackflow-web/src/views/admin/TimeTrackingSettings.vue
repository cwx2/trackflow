<template>
  <div class="time-tracking-settings">
    <!-- Back button + title -->
    <div class="settings-header">
      <router-link to="/admin" class="back-link">← 返回管理</router-link>
      <h1 class="settings-title">时间追踪设置</h1>
      <p class="settings-desc">配置系统级时间追踪参数，影响所有项目的工时单位换算和时间表显示。</p>
    </div>

    <DataContainer :loading="loading" :is-empty="false">
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
        <a-checkbox-group v-model="form.workingDays" class="workdays-grid" @change="onWorkingDaysChange">
          <a-checkbox
            v-for="day in allDays"
            :key="day.value"
            :value="day.value"
            class="workday-item"
          >
            {{ day.label }}
          </a-checkbox>
        </a-checkbox-group>
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
        <a-button type="primary" :loading="saving" @click="handleSave">
          保存设置
        </a-button>
        <a-button @click="resetForm">
          重置
        </a-button>
      </div>
    </DataContainer>

    <!-- Recalculation Strategy Dialog -->
    <a-modal
      v-model:visible="showRecalcDialog"
      :title="'重新计算工时数据'"
      :width="560"
      :mask-closable="false"
      :closable="!saving"
      :footer="false"
      @cancel="cancelRecalculation"
    >
      <div class="recalc-dialog">
        <div class="recalc-warning">
          <icon-exclamation-circle-fill class="recalc-warning-icon" />
          <p class="recalc-warning-text">
            每日工作时长从 <strong>{{ originalSettings.hoursPerDay }}h</strong> 修改为 <strong>{{ form.hoursPerDay }}h</strong>，
            已有的工时数据需要重新计算。请选择计算策略：
          </p>
        </div>

        <a-radio-group v-model="selectedStrategy" direction="vertical" class="recalc-options">
          <div
            class="recalc-option"
            :class="{ selected: selectedStrategy === 'PRESERVE_MINUTES' }"
            @click="selectedStrategy = 'PRESERVE_MINUTES'"
          >
            <a-radio value="PRESERVE_MINUTES">
              <template #radio="{ checked }">
                <span class="recalc-radio-dot" :class="{ 'is-checked': checked }"></span>
              </template>
            </a-radio>
            <div class="recalc-option-content">
              <span class="recalc-option-title">保留分钟数</span>
              <span class="recalc-option-desc">
                已有工时的实际分钟数不变，仅更新天/小时的换算展示。
                例如：480 分钟原来显示为 1d，修改后显示为 {{ formatMinutesPreview(480, form.hoursPerDay) }}。
              </span>
            </div>
          </div>

          <div
            class="recalc-option"
            :class="{ selected: selectedStrategy === 'PRESERVE_DAYS' }"
            @click="selectedStrategy = 'PRESERVE_DAYS'"
          >
            <a-radio value="PRESERVE_DAYS">
              <template #radio="{ checked }">
                <span class="recalc-radio-dot" :class="{ 'is-checked': checked }"></span>
              </template>
            </a-radio>
            <div class="recalc-option-content">
              <span class="recalc-option-title">保留天数</span>
              <span class="recalc-option-desc">
                已有工时的分钟数按比例重新计算，使天数展示保持不变。
                例如：{{ originalSettings.hoursPerDay * 60 }}m (1d@{{ originalSettings.hoursPerDay }}h) 变为 {{ form.hoursPerDay * 60 }}m (1d@{{ form.hoursPerDay }}h)。
              </span>
              <span class="recalc-option-warning">
                ⚠ 此操作将修改数据库中的实际数值，建议提前备份数据库。
              </span>
            </div>
          </div>
        </a-radio-group>

        <!-- Example table -->
        <div class="recalc-example">
          <div class="recalc-example-title">换算示例</div>
          <a-table
            :data="recalcExamples"
            :pagination="false"
            :bordered="false"
            size="small"
            class="recalc-table"
          >
            <template #columns>
              <a-table-column title="当前值" data-index="current">
                <template #cell="{ record }">
                  <span class="mono-text">{{ formatMinutes(record.minutes, originalSettings.hoursPerDay) }}</span>
                </template>
              </a-table-column>
              <a-table-column title="保留分钟数" data-index="preserveMinutes">
                <template #cell="{ record }">
                  <span class="mono-text">{{ formatMinutes(record.minutes, form.hoursPerDay) }}</span>
                </template>
              </a-table-column>
              <a-table-column title="保留天数" data-index="preserveDays">
                <template #cell="{ record }">
                  <span class="mono-text">{{ formatMinutes(Math.round(record.minutes * form.hoursPerDay / originalSettings.hoursPerDay), form.hoursPerDay) }}</span>
                </template>
              </a-table-column>
            </template>
          </a-table>
        </div>

        <div class="recalc-actions">
          <a-button @click="cancelRecalculation" :disabled="saving">取消</a-button>
          <a-button
            type="primary"
            :loading="saving"
            :disabled="!selectedStrategy"
            @click="confirmRecalculation"
          >
            确认并保存
          </a-button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { Message } from '@arco-design/web-vue'
import { systemSettingApi } from '@/api/systemSetting'
import type { TimeTrackingSettingsVO, UpdateTimeTrackingSettingsDTO } from '@/api/systemSetting'
import DataContainer from '@/components/base/DataContainer.vue'

const loading = ref(true)
const saving = ref(false)
const showRecalcDialog = ref(false)
const selectedStrategy = ref<'PRESERVE_MINUTES' | 'PRESERVE_DAYS'>('PRESERVE_MINUTES')

const form = reactive({
  hoursPerDay: 8,
  workingDays: [1, 2, 3, 4, 5] as number[]
})

// Original values for reset and comparison
let originalSettings: TimeTrackingSettingsVO = { hoursPerDay: 8, workingDays: [1, 2, 3, 4, 5] }
let lastValidWorkingDays: number[] = [1, 2, 3, 4, 5]

const allDays = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' }
]

// Example data for the recalculation preview table
const recalcExamples = computed(() => {
  const oldH = originalSettings.hoursPerDay
  return [
    { minutes: oldH * 60 },         // 1d
    { minutes: oldH * 60 + 120 },   // 1d2h
    { minutes: 360 },               // 6h
    { minutes: oldH * 60 * 5 + oldH * 60 * 3 }  // 1w3d
  ]
})

function onWorkingDaysChange(values: (string | number | boolean)[]) {
  const numValues = values as number[]
  if (numValues.length === 0) {
    // Prevent unchecking the last day — restore previous state
    Message.warning('至少需要保留一个工作日')
    // v-model already set to empty, so we revert on next tick
    nextTick(() => {
      form.workingDays = lastValidWorkingDays.slice()
    })
    return
  }
  const sorted = numValues.sort((a, b) => a - b)
  form.workingDays = sorted
  lastValidWorkingDays = sorted.slice()
}

/**
 * Format minutes into human-readable period string (e.g., "1d2h30m")
 */
function formatMinutes(totalMinutes: number, hoursPerDay: number): string {
  if (totalMinutes <= 0) return '0m'
  const minutesPerDay = hoursPerDay * 60
  const days = Math.floor(totalMinutes / minutesPerDay)
  let remaining = totalMinutes % minutesPerDay
  const hours = Math.floor(remaining / 60)
  const minutes = remaining % 60

  const parts: string[] = []
  if (days > 0) parts.push(`${days}d`)
  if (hours > 0) parts.push(`${hours}h`)
  if (minutes > 0) parts.push(`${minutes}m`)
  return parts.join('') || '0m'
}

function formatMinutesPreview(totalMinutes: number, newHoursPerDay: number): string {
  return formatMinutes(totalMinutes, newHoursPerDay)
}

async function loadSettings() {
  loading.value = true
  try {
    const res = await systemSettingApi.getTimeTrackingSettings()
    if (res.code === 0 && res.data) {
      form.hoursPerDay = res.data.hoursPerDay
      form.workingDays = [...res.data.workingDays]
      originalSettings = { ...res.data }
      lastValidWorkingDays = [...res.data.workingDays]
    }
  } catch (e) {
    Message.error('加载时间追踪设置失败')
  } finally {
    loading.value = false
  }
}

/**
 * Handle save button click. If hoursPerDay changed, show recalculation dialog.
 */
function handleSave() {
  if (form.workingDays.length === 0) {
    Message.warning('至少需要选择一个工作日')
    return
  }

  if (form.hoursPerDay !== originalSettings.hoursPerDay) {
    // hoursPerDay changed → show recalculation strategy dialog
    selectedStrategy.value = 'PRESERVE_MINUTES'
    showRecalcDialog.value = true
  } else {
    // Only workingDays changed → save directly (no recalculation needed)
    doSave(undefined)
  }
}

function cancelRecalculation() {
  showRecalcDialog.value = false
}

function confirmRecalculation() {
  doSave(selectedStrategy.value)
}

async function doSave(strategy?: 'PRESERVE_MINUTES' | 'PRESERVE_DAYS') {
  saving.value = true
  try {
    const payload: UpdateTimeTrackingSettingsDTO = {
      hoursPerDay: form.hoursPerDay,
      workingDays: form.workingDays
    }
    if (strategy) {
      payload.recalculationStrategy = strategy
    }

    const res = await systemSettingApi.updateTimeTrackingSettings(payload)
    if (res.code === 0 && res.data) {
      originalSettings = { ...res.data.settings }
      showRecalcDialog.value = false

      if (res.data.recalculated) {
        Message.success(
          `设置已保存，已重新计算 ${res.data.affectedTimeEntries} 条工时记录和 ${res.data.affectedEstimations} 条预估工时`
        )
      } else if (res.data.strategy === 'PRESERVE_MINUTES') {
        Message.success('设置已保存，已有工时数据保持不变，仅更新换算展示')
      } else {
        Message.success('时间追踪设置已保存')
      }
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
  lastValidWorkingDays = [...originalSettings.workingDays]
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

.workdays-grid :deep(.arco-checkbox) {
  padding: 8px 14px;
  border-radius: 6px;
  border: 1px solid var(--tf-border-light);
  background: var(--tf-bg-surface);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  margin-right: 0;
}

.workdays-grid :deep(.arco-checkbox:hover) {
  border-color: var(--tf-border);
  background: var(--tf-bg-hover);
}

.workdays-grid :deep(.arco-checkbox-checked) {
  border-color: var(--tf-accent);
  background: var(--tf-accent-bg);
}

.workdays-grid :deep(.arco-checkbox-label) {
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

/* Recalculation Dialog Styles */
.recalc-dialog {
  padding: 4px 0;
}

.recalc-warning {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  background: var(--tf-warning-bg);
  border-radius: 6px;
  margin-bottom: 20px;
  align-items: flex-start;
}

.recalc-warning-icon {
  color: var(--tf-warning);
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 2px;
}

.recalc-warning-text {
  font-size: 13px;
  color: var(--tf-text-primary);
  line-height: 1.5;
  margin: 0;
}

.recalc-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
  width: 100%;
}

.recalc-options :deep(.arco-radio-group) {
  width: 100%;
}

.recalc-option {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--tf-border-light);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.recalc-option:hover {
  border-color: var(--tf-border);
  background: var(--tf-bg-hover);
}

.recalc-option.selected {
  border-color: var(--tf-accent);
  background: var(--tf-accent-bg);
}

.recalc-option :deep(.arco-radio) {
  margin-top: 2px;
  flex-shrink: 0;
}

.recalc-radio-dot {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid var(--tf-border);
  transition: border-color 0.15s;
}

.recalc-radio-dot.is-checked {
  border-color: var(--tf-accent);
  background: radial-gradient(circle, var(--tf-accent) 40%, transparent 40%);
}

.recalc-option-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.recalc-option-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.recalc-option-desc {
  font-size: 12px;
  color: var(--tf-text-secondary);
  line-height: 1.5;
}

.recalc-option-warning {
  font-size: 11px;
  color: var(--tf-warning);
  margin-top: 4px;
}

.recalc-example {
  margin-bottom: 20px;
}

.recalc-example-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-tertiary);
  margin-bottom: 8px;
}

.recalc-table :deep(.arco-table-th) {
  font-size: 12px;
}

.recalc-table :deep(.arco-table-td) {
  font-size: 12px;
}

.mono-text {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  color: var(--tf-text-primary);
}

.recalc-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 4px;
}
</style>
