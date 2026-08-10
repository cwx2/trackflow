<template>
  <div class="work-time-form">
    <!-- 记录人（有权限时显示） -->
    <div v-if="canLogForOthers && projectMembers.length > 0" class="wtf-field">
      <label class="wtf-label">记录人</label>
      <a-select
        v-model="inner.forUserId"
        placeholder="选择记录人（默认为自己）"
        allow-search
        allow-clear
        style="width: 100%"
        @change="onForUserChange"      >
        <a-option
          v-for="member in projectMembers"
          :key="member.userId"
          :value="member.userId"
        >
          <span class="wtf-author-option">
            <UserAvatar :name="member.displayName || member.username || '?'" :size="20" />
            <span class="wtf-author-name">{{ member.displayName || member.username }}</span>
            <span v-if="member.userId === currentUserId" class="wtf-author-self">（我）</span>
          </span>
        </a-option>
      </a-select>
    </div>

    <!-- 日期 -->
    <div class="wtf-field">
      <label class="wtf-label wtf-required">日期</label>
      <a-date-picker v-model="inner.workDate" style="width: 100%" />
    </div>

    <!-- 实际用时 -->
    <div class="wtf-field">
      <label class="wtf-label wtf-required">实际用时</label>
      <a-input v-model="inner.durationText" placeholder="例如: 2h30m, 1h, 45m">
        <template #prefix>⏱</template>
      </a-input>
    </div>

    <!-- 工作类型（内置属性） -->
    <div v-if="workTypeValues.length > 0" class="wtf-field">
      <label class="wtf-label">工作类型</label>
      <a-select v-model="inner.attrValues[WORK_TYPE_ATTR_ID]" placeholder="选择工作类型" allow-clear>
        <a-option v-for="val in workTypeValues" :key="val.id" :value="val.id">
          <span v-if="val.color" class="wtf-attr-dot" :style="{ background: val.color }"></span>
          {{ val.name }}
        </a-option>
      </a-select>
    </div>

    <!-- 动态扩展属性 -->
    <div v-for="attr in extraAttributes" :key="attr.id" class="wtf-field">
      <label class="wtf-label">{{ attr.name }}</label>
      <a-select v-model="inner.attrValues[attr.id]" :placeholder="`选择${attr.name}`" allow-clear>
        <a-option v-for="val in attr.values" :key="val.id" :value="val.id">
          <span v-if="val.color" class="wtf-attr-dot" :style="{ background: val.color }"></span>
          {{ val.name }}
        </a-option>
      </a-select>
    </div>

    <!-- 描述 -->
    <div class="wtf-field">
      <label class="wtf-label">描述</label>
      <a-textarea
        v-model="inner.description"
        placeholder="描述这段时间您做了什么"
        :auto-size="{ minRows: 2, maxRows: 5 }"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { UserAvatar } from '@/components/base'
import type { WorkItemAttributeVO } from '@/api/timeEntry'

/** 内置工作类型属性的固定 id（后端约定） */
const WORK_TYPE_ATTR_ID = '1'

export interface WorkTimeFormData {
  workDate: string
  durationText: string
  description: string
  forUserId: string
  attrValues: Record<string, string>
}

withDefaults(defineProps<{
  /** 是否可以为他人记录（控制记录人字段显示） */
  canLogForOthers?: boolean
  /** 可选的项目成员列表（canLogForOthers 为 true 时需提供） */
  projectMembers?: { userId: string; username?: string; displayName: string }[]
  /** 当前登录用户 ID（用于标注"我"） */
  currentUserId?: string
  /** 工作类型可选值 */
  workTypeValues?: { id: string; name: string; color?: string }[]
  /** 额外动态属性列表 */
  extraAttributes?: WorkItemAttributeVO[]
}>(), {
  canLogForOthers: false,
  projectMembers: () => [],
  currentUserId: '',
  workTypeValues: () => [],
  extraAttributes: () => [],
})

/** 内部状态 */
const inner = reactive<WorkTimeFormData>({
  workDate: new Date().toISOString().slice(0, 10),
  durationText: '',
  description: '',
  forUserId: '',
  attrValues: {},
})

function onForUserChange(_val: string | number | boolean | Record<string, unknown> | (string | number | boolean | Record<string, unknown>)[]) {
  // 可扩展：切换记录人时触发外部逻辑
}

/** 校验表单，返回错误信息或 null */
function validate(): string | null {
  if (!inner.workDate) return '请选择日期'
  if (!inner.durationText.trim()) return '请输入实际用时'
  const duration = parseDuration(inner.durationText)
  if (!duration || duration <= 0) return '时长格式无效，请使用如 2h30m, 1h, 45m'
  return null
}

/** 解析时长文本，返回分钟数 */
function parseDuration(text: string): number | null {
  const cleaned = text.trim().toLowerCase()
  let total = 0
  const weekMatch = cleaned.match(/(\d+)\s*w/)
  const dayMatch  = cleaned.match(/(\d+)\s*d/)
  const hourMatch = cleaned.match(/(\d+)\s*h/)
  const minMatch  = cleaned.match(/(\d+)\s*m(?!o)/)
  if (weekMatch) total += parseInt(weekMatch[1]) * 5 * 8 * 60
  if (dayMatch)  total += parseInt(dayMatch[1])  * 8 * 60
  if (hourMatch) total += parseInt(hourMatch[1]) * 60
  if (minMatch)  total += parseInt(minMatch[1])
  if (!weekMatch && !dayMatch && !hourMatch && !minMatch) {
    const num = parseFloat(cleaned)
    if (!isNaN(num)) total = Math.round(num * 60)
  }
  return total > 0 ? total : null
}

/** 获取已填写的表单数据（含解析后的 duration 分钟数） */
function getFormData(): WorkTimeFormData & { duration: number } {
  return {
    ...inner,
    attrValues: { ...inner.attrValues },
    duration: parseDuration(inner.durationText) ?? 0,
  }
}

/** 用已有工时记录回填表单（编辑模式） */
function fill(entry: {
  workDate?: string
  duration?: number | null
  description?: string
  userId?: string
  attributeValues?: { attributeId: string; valueId: string }[]
}) {
  inner.workDate      = entry.workDate ?? new Date().toISOString().slice(0, 10)
  inner.durationText  = entry.duration ? formatDuration(entry.duration) : ''
  inner.description   = entry.description ?? ''
  inner.forUserId     = entry.userId ?? ''
  inner.attrValues    = {}
  if (entry.attributeValues) {
    for (const av of entry.attributeValues) {
      inner.attrValues[av.attributeId] = av.valueId
    }
  }
}

/** 重置表单为初始状态 */
function reset() {
  inner.workDate     = new Date().toISOString().slice(0, 10)
  inner.durationText = ''
  inner.description  = ''
  inner.forUserId    = ''
  inner.attrValues   = {}
}

/** 将分钟数格式化为 Xh Ym 字符串 */
function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h ${m}m`
}

defineExpose({ validate, getFormData, fill, reset })
</script>

<style scoped>
.work-time-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.wtf-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.wtf-label {
  font-size: 12px;
  color: var(--tf-text-secondary);
  font-weight: 500;
}

.wtf-required::after {
  content: ' *';
  color: var(--tf-danger);
}

.wtf-author-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wtf-author-name {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.wtf-author-self {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.wtf-attr-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  flex-shrink: 0;
}
</style>
