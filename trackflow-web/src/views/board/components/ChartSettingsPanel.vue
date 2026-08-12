<template>
  <div class="chart-settings">
    <div class="settings-hint">
      <p>配置看板上显示的图表类型和计算方式。图表将展示在看板顶部，可展开/折叠。</p>
    </div>

    <!-- 图表类型 -->
    <div class="setting-group">
      <div class="setting-label">图表类型</div>
      <a-radio-group
        :model-value="chartType"
        direction="vertical"
        @change="$emit('update:chartType', $event as string)"
      >
        <a-radio value="burndown">
          <div class="radio-label">
            <span class="radio-title">燃尽图 (Burndown Chart)</span>
            <span class="radio-desc">显示 Sprint 剩余工作量随时间递减的趋势</span>
          </div>
        </a-radio>
        <a-radio value="cumulative_flow">
          <div class="radio-label">
            <span class="radio-title">累积流图 (Cumulative Flow)</span>
            <span class="radio-desc">显示各状态工单数量随时间变化的面积图</span>
          </div>
        </a-radio>
      </a-radio-group>
    </div>

    <!-- Burndown 计算方式（仅 burndown 类型时显示） -->
    <div v-if="chartType === 'burndown'" class="setting-group">
      <div class="setting-label">计算方式</div>
      <a-radio-group
        :model-value="burndownCalculation"
        direction="vertical"
        @change="$emit('update:burndownCalculation', $event as string)"
      >
        <a-radio value="issue_count">
          <div class="radio-label">
            <span class="radio-title">按工单数量</span>
            <span class="radio-desc">燃尽图按照未完成工单数量计算</span>
          </div>
        </a-radio>
        <a-radio value="estimation">
          <div class="radio-label">
            <span class="radio-title">按估算值</span>
            <span class="radio-desc">燃尽图基于估算字段的值计算（需配置估算字段）</span>
          </div>
        </a-radio>
        <a-radio value="work_items">
          <div class="radio-label">
            <span class="radio-title">按已花工时</span>
            <span class="radio-desc">燃尽图按照实际花费工时计算（需启用时间追踪）</span>
          </div>
        </a-radio>
      </a-radio-group>
    </div>

    <!-- 估算字段配置（仅 estimation 计算方式时显示） -->
    <div v-if="chartType === 'burndown' && burndownCalculation === 'estimation'" class="setting-group">
      <div class="setting-label">估算字段</div>
      <div class="estimation-fields">
        <div class="field-item">
          <span class="field-item-label">当前估算字段</span>
          <a-select
            :model-value="estimationFieldId ?? undefined"
            placeholder="选择估算字段"
            allow-clear
            :style="{ width: '200px' }"
            @change="$emit('update:estimationFieldId', $event as string | null)"
          >
            <a-option v-for="field in numericFields" :key="field.id" :value="field.id">
              {{ field.name }}
            </a-option>
          </a-select>
          <span class="field-item-help">用于显示看板上的估算值</span>
        </div>
        <div class="field-item">
          <span class="field-item-label">原始估算字段</span>
          <a-select
            :model-value="originalEstimationFieldId ?? undefined"
            placeholder="选择原始估算字段（可选）"
            allow-clear
            :style="{ width: '200px' }"
            @change="$emit('update:originalEstimationFieldId', $event as string | null)"
          >
            <a-option v-for="field in numericFields" :key="field.id" :value="field.id">
              {{ field.name }}
            </a-option>
          </a-select>
          <span class="field-item-help">用于计算 Burndown 偏差（可选）</span>
        </div>
      </div>
    </div>

    <!-- Issue 过滤器 -->
    <div class="setting-group">
      <div class="setting-label">工单过滤器</div>
      <a-radio-group
        :model-value="issueFilterMode"
        direction="vertical"
        @change="handleFilterModeChange($event as string)"
      >
        <a-radio value="all_cards">
          <div class="radio-label">
            <span class="radio-title">所有卡片</span>
            <span class="radio-desc">当前 Sprint 中所有卡片纳入图表计算</span>
          </div>
        </a-radio>
        <a-radio value="custom">
          <div class="radio-label">
            <span class="radio-title">自定义过滤</span>
            <span class="radio-desc">按自定义条件过滤纳入图表的工单</span>
          </div>
        </a-radio>
      </a-radio-group>

      <!-- 自定义过滤输入框 -->
      <div v-if="issueFilterMode === 'custom'" class="filter-query-box">
        <a-textarea
          :model-value="issueFilterQuery || ''"
          placeholder="输入过滤条件，如: type = Bug OR priority = High"
          :auto-size="{ minRows: 2, maxRows: 4 }"
          @input="$emit('update:issueFilterQuery', ($event as any).target?.value || $event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { customFieldApi } from '@/api'

interface NumericField {
  id: string
  name: string
}

const props = defineProps<{
  chartType: string
  burndownCalculation: string
  issueFilterMode: string
  issueFilterQuery: string | null
  estimationFieldId: string | null
  originalEstimationFieldId: string | null
  projectId: string
}>()

const emit = defineEmits<{
  'update:chartType': [value: string]
  'update:burndownCalculation': [value: string]
  'update:issueFilterMode': [value: string]
  'update:issueFilterQuery': [value: string | null]
  'update:estimationFieldId': [value: string | null]
  'update:originalEstimationFieldId': [value: string | null]
}>()

const numericFields = ref<NumericField[]>([])

function handleFilterModeChange(mode: string) {
  emit('update:issueFilterMode', mode)
  if (mode === 'all_cards') {
    emit('update:issueFilterQuery', null)
  }
}

onMounted(async () => {
  if (!props.projectId) return
  try {
    // 使用项目级端点获取自定义字段（无需管理员权限）
    const res = await customFieldApi.listByProject(props.projectId)
    const allFields = res.data || []
    // 过滤出数字类型字段（integer 和 float）
    numericFields.value = allFields
      .filter((f: any) => f.fieldFormat === 'integer' || f.fieldFormat === 'float')
      .map((f: any) => ({
        id: String(f.id),
        name: f.name
      }))
  } catch {
    // 加载失败时估算字段列表为空，不影响其他配置
    numericFields.value = []
  }
})
</script>

<style scoped>
.chart-settings {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.settings-hint p {
  font-size: 13px;
  color: var(--color-text-3);
  margin: 0;
  line-height: 1.5;
}

.setting-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.setting-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.radio-label {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.radio-title {
  font-size: 13px;
  color: var(--color-text-1);
}

.radio-desc {
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.4;
}

.estimation-fields {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-left: 4px;
}

.field-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-item-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-2);
}

.field-item-help {
  font-size: 11px;
  color: var(--color-text-4);
}

.filter-query-box {
  margin-top: 8px;
  padding-left: 24px;
}
</style>
