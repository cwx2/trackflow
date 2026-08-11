<template>
  <div class="widget-config-fields">
    <template v-for="field in visibleFields" :key="field.key">
      <a-form-item
        :label="field.label"
        :rules="field.required ? [{ required: true, message: `请填写${field.label}` }] : undefined"
      >
        <!-- 项目选择器 -->
        <a-select
          v-if="field.type === 'project-select'"
          :model-value="formData[field.key]"
          :placeholder="field.placeholder"
          allow-clear
          @update:model-value="updateField(field.key, $event)"
        >
          <a-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.name }}
          </a-option>
        </a-select>

        <!-- 多项目选择器 -->
        <a-select
          v-else-if="field.type === 'multi-project-select'"
          :model-value="formData[field.key]"
          :placeholder="field.placeholder"
          multiple
          allow-clear
          :max-tag-count="field.maxTagCount || 3"
          @update:model-value="updateField(field.key, $event)"
        >
          <a-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.name }}
          </a-option>
        </a-select>

        <!-- 报表选择器 -->
        <a-select
          v-else-if="field.type === 'report-select'"
          :model-value="formData[field.key]"
          :placeholder="field.placeholder"
          allow-clear
          @update:model-value="updateField(field.key, $event)"
        >
          <a-option v-for="r in reports" :key="r.id" :value="r.id">
            {{ r.name }}
          </a-option>
        </a-select>

        <!-- Sprint 选择器 -->
        <a-select
          v-else-if="field.type === 'sprint-select'"
          :model-value="formData[field.key]"
          :placeholder="field.placeholder"
          allow-clear
          @update:model-value="updateField(field.key, $event)"
        >
          <a-option v-for="s in sprints" :key="s.id" :value="s.id">
            {{ s.name }} ({{ s.status }})
          </a-option>
        </a-select>

        <!-- 用户选择器（多选） -->
        <a-select
          v-else-if="field.type === 'multi-user-select'"
          :model-value="formData[field.key]"
          :placeholder="field.placeholder"
          multiple
          allow-clear
          :max-tag-count="field.maxTagCount || 3"
          @update:model-value="updateField(field.key, $event)"
        >
          <a-option v-for="u in users" :key="u.id" :value="u.id">
            {{ u.name }}
          </a-option>
        </a-select>

        <!-- 静态下拉（单选） -->
        <a-select
          v-else-if="field.type === 'select'"
          :model-value="formData[field.key]"
          :placeholder="field.placeholder"
          allow-clear
          @update:model-value="updateField(field.key, $event)"
        >
          <a-option
            v-for="opt in field.options"
            :key="opt.value"
            :value="opt.value"
          >{{ opt.label }}</a-option>
        </a-select>

        <!-- 静态下拉（多选） -->
        <a-select
          v-else-if="field.type === 'multi-select'"
          :model-value="formData[field.key]"
          :placeholder="field.placeholder"
          multiple
          allow-clear
          :max-tag-count="field.maxTagCount || 3"
          @update:model-value="updateField(field.key, $event)"
        >
          <a-option
            v-for="opt in field.options"
            :key="opt.value"
            :value="opt.value"
          >{{ opt.label }}</a-option>
        </a-select>

        <!-- 数字输入 -->
        <a-input-number
          v-else-if="field.type === 'number'"
          :model-value="formData[field.key]"
          :min="field.min"
          :max="field.max"
          :placeholder="field.placeholder"
          style="width: 100%"
          @update:model-value="updateField(field.key, $event)"
        />

        <!-- 查询输入框 -->
        <a-input
          v-else-if="field.type === 'query-input'"
          :model-value="formData[field.key]"
          :placeholder="field.placeholder || '例如: priority:Critical state:Open'"
          allow-clear
          @update:model-value="updateField(field.key, $event)"
        />

        <!-- 开关 -->
        <a-switch
          v-else-if="field.type === 'boolean'"
          :model-value="formData[field.key]"
          @update:model-value="updateField(field.key, $event)"
        />

        <!-- 多行文本 -->
        <a-textarea
          v-else-if="field.type === 'textarea'"
          :model-value="formData[field.key]"
          :placeholder="field.placeholder"
          :auto-size="{ minRows: 3, maxRows: 8 }"
          @update:model-value="updateField(field.key, $event)"
        />

        <!-- 默认文本输入 -->
        <a-input
          v-else
          :model-value="formData[field.key]"
          :placeholder="field.placeholder"
          @update:model-value="updateField(field.key, $event)"
        />

        <span v-if="field.hint" class="form-hint">{{ field.hint }}</span>
      </a-form-item>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { WidgetConfigField } from './types'

export interface DataItem {
  id: string
  name: string
  status?: string
}

const modelValue = defineModel<Record<string, any>>({ required: true })

const props = defineProps<{
  schema: WidgetConfigField[]
  projects?: DataItem[]
  reports?: DataItem[]
  sprints?: DataItem[]
  users?: DataItem[]
}>()

const formData = computed(() => modelValue.value)

/** 仅显示满足 showWhen 条件的字段 */
const visibleFields = computed(() => {
  return props.schema.filter(field => {
    if (!field.showWhen) return true
    return formData.value[field.showWhen.field] === field.showWhen.value
  })
})

function updateField(key: string, value: unknown) {
  modelValue.value = { ...modelValue.value, [key]: value }
}
</script>

<style scoped>
.form-hint {
  display: block;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
  line-height: 1.4;
}
</style>
