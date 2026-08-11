<template>
  <!-- list 类型通过选项的 isDefault 设置，不显示独立的默认值输入 -->
  <div v-if="fieldFormat === 'list'" class="default-value-hint">
    <span class="hint-text">列表类型的默认值通过选项列表中的「默认」复选框设置</span>
  </div>

  <!-- bool 类型：三态选择（true / false / 不设置） -->
  <a-radio-group
    v-else-if="fieldFormat === 'bool'"
    :model-value="modelValue || 'unset'"
    type="button"
    size="small"
    @change="handleBoolChange"
  >
    <a-radio value="unset">不设置</a-radio>
    <a-radio value="true">是 (true)</a-radio>
    <a-radio value="false">否 (false)</a-radio>
  </a-radio-group>

  <!-- int 类型：整数输入 -->
  <a-input-number
    v-else-if="fieldFormat === 'int'"
    :model-value="intValue"
    :precision="0"
    placeholder="可选，输入整数"
    style="width: 200px"
    @change="handleNumberChange"
  />

  <!-- float 类型：小数输入 -->
  <a-input-number
    v-else-if="fieldFormat === 'float'"
    :model-value="floatValue"
    :precision="4"
    placeholder="可选，输入小数"
    style="width: 200px"
    @change="handleNumberChange"
  />

  <!-- date 类型：日期选择器 -->
  <a-date-picker
    v-else-if="fieldFormat === 'date'"
    :model-value="modelValue || undefined"
    placeholder="可选，选择日期"
    style="width: 200px"
    value-format="YYYY-MM-DD"
    allow-clear
    @change="handleDateChange"
  />

  <!-- datetime 类型：日期时间选择器 -->
  <a-date-picker
    v-else-if="fieldFormat === 'datetime'"
    :model-value="modelValue || undefined"
    show-time
    placeholder="可选，选择日期时间"
    style="width: 260px"
    value-format="YYYY-MM-DD HH:mm:ss"
    allow-clear
    @change="handleDateChange"
  />

  <!-- user 类型：用户搜索选择器 -->
  <a-select
    v-else-if="fieldFormat === 'user'"
    :model-value="modelValue || undefined"
    placeholder="可选，选择默认用户"
    allow-search
    allow-clear
    :loading="userLoading"
    style="width: 240px"
    @search="handleUserSearch"
    @change="handleUserChange"
  >
    <a-option v-for="u in userOptions" :key="u.id" :value="u.id">
      {{ u.displayName }} ({{ u.username }})
    </a-option>
  </a-select>

  <!-- text 类型：多行文本 -->
  <a-textarea
    v-else-if="fieldFormat === 'text'"
    :model-value="modelValue"
    placeholder="可选"
    :auto-size="{ minRows: 2, maxRows: 5 }"
    @update:model-value="modelValue = $event"
  />

  <!-- period 类型：时间周期 -->
  <a-input
    v-else-if="fieldFormat === 'period'"
    :model-value="modelValue"
    placeholder="如: 2h30m, 1d, 1w2d（分钟数）"
    style="width: 200px"
    @update:model-value="modelValue = $event"
  />

  <!-- string 类型和其他：普通文本输入 -->
  <a-input
    v-else
    :model-value="modelValue"
    placeholder="可选"
    @update:model-value="modelValue = $event"
  />
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { userApi } from '@/api'
import type { UserVO } from '@/api/types'

const modelValue = defineModel<string>({ default: '' })

const props = defineProps<{
  fieldFormat: string
}>()

// ========== Number handling ==========
const intValue = computed(() => {
  if (!modelValue.value) return undefined
  const n = parseInt(modelValue.value, 10)
  return isNaN(n) ? undefined : n
})

const floatValue = computed(() => {
  if (!modelValue.value) return undefined
  const n = parseFloat(modelValue.value)
  return isNaN(n) ? undefined : n
})

function handleNumberChange(val: number | undefined) {
  modelValue.value = val !== undefined && val !== null ? String(val) : ''
}

// ========== Bool handling ==========
function handleBoolChange(val: string | number | boolean) {
  modelValue.value = val === 'unset' ? '' : String(val)
}

// ========== Date handling ==========
function handleDateChange(val: string | Date | undefined) {
  modelValue.value = val ? String(val) : ''
}

// ========== User handling ==========
const userOptions = ref<UserVO[]>([])
const userLoading = ref(false)

// Load initial user if there's a value
watch(() => modelValue.value, async (val) => {
  if (props.fieldFormat === 'user' && val && userOptions.value.length === 0) {
    await loadInitialUser(val)
  }
}, { immediate: true })

async function loadInitialUser(_userId: string) {
  try {
    const res = await userApi.list({ pageSize: 50 })
    userOptions.value = res.data?.list || []
  } catch {
    // ignore
  }
}

async function handleUserSearch(keyword: string) {
  if (!keyword || keyword.length < 1) return
  userLoading.value = true
  try {
    const res = await userApi.list({ keyword, pageSize: 20 })
    userOptions.value = res.data?.list || []
  } catch {
    userOptions.value = []
  } finally {
    userLoading.value = false
  }
}

function handleUserChange(val: any) {
  modelValue.value = val || ''
}

// When field format changes, reset value if incompatible
watch(() => props.fieldFormat, () => {
  // Parent is responsible for resetting defaultValue when format changes
})
</script>

<style scoped>
.default-value-hint {
  padding: 6px 0;
}

.hint-text {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  font-style: italic;
}
</style>
