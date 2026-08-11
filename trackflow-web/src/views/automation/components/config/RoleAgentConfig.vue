<template>
  <div class="node-config role-agent-config">
    <a-form :model="local" layout="vertical">
      <a-form-item label="Agent 角色">
        <a-select
          :model-value="getInputLiteral('roleId')"
          placeholder="选择 Agent 角色"
          :loading="rolesLoading"
          allow-clear
          @change="onRoleChange"
        >
          <a-option v-for="r in roles" :key="r.id" :value="Number(r.id)">
            <div class="role-option">
              <span>{{ r.name }}</span>
              <span class="provider-tag">{{ providerLabel(r.providerType) }}</span>
            </div>
          </a-option>
        </a-select>
      </a-form-item>

      <!-- 选中角色后展示描述 -->
      <div v-if="selectedRole" class="role-detail">
        <div class="role-detail-name">{{ selectedRole.name }}</div>
        <div v-if="selectedRole.description" class="role-detail-desc">{{ selectedRole.description }}</div>
        <div class="role-detail-meta">
          <span class="provider-badge">{{ providerLabel(selectedRole.providerType) }}</span>
          <span v-if="selectedRole.model" class="model-badge">{{ selectedRole.model }}</span>
        </div>
      </div>

      <a-form-item label="任务描述">
        <template #extra>支持 ${变量名} 引用全局变量或上游节点输出，如 ${issue.title}、${file_content}</template>
        <a-textarea
          :model-value="String(getInputLiteral('task') ?? '')"
          placeholder="描述要让 AI 完成的任务，可用 ${issue.title} 等变量引用工单字段"
          :auto-size="{ minRows: 3, maxRows: 8 }"
          @input="(val: any) => setInputLiteral('task', val || undefined)"
        />
      </a-form-item>

      <a-form-item label="上下文（可选）">
        <template #extra>可选；JSON 对象格式，会作为结构化上下文传给 Agent，补充任务描述中不便内联的大段数据</template>
        <div v-if="isReference('context')" class="reference-value">
          <code>{{ referenceLabel('context') }}</code>
          <a-button size="mini" type="text" @click="clearInput('context')">改为固定值</a-button>
        </div>
        <a-textarea
          v-else
          :model-value="contextJson"
          placeholder='{"key": "value"}'
          :auto-size="{ minRows: 2, maxRows: 5 }"
          @input="onContextInput"
        />
      </a-form-item>

      <a-form-item label="工作目录">
        <template #extra>在角色允许范围内指定子路径，如 {workspace}/src；留空则使用角色默认工作目录</template>
        <a-input
          :model-value="String(getInputLiteral('workDir') ?? '')"
          placeholder="{workspace}"
          allow-clear
          @input="(val: any) => setInputLiteral('workDir', val || undefined)"
        />
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { automationApi } from '@/api'
import type { AutomationRoleProfile } from '@/api'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:data', value: Record<string, any>): void }>()

const local = ref<Record<string, any>>(JSON.parse(JSON.stringify(props.data || {})))

// Data sources
const roles = ref<AutomationRoleProfile[]>([])
const rolesLoading = ref(false)

// Selected role detail
const selectedRole = computed(() => {
  const roleId = getInputLiteral('roleId')
  if (!roleId) return null
  return roles.value.find(r => Number(r.id) === Number(roleId)) || null
})

// Context as JSON string for display
const contextJson = computed(() => {
  const val = getInputLiteral('context')
  if (!val) return ''
  if (typeof val === 'object') return JSON.stringify(val, null, 2)
  return String(val)
})

function onContextInput(val: string) {
  if (!val || !val.trim()) {
    setInputLiteral('context', undefined)
    return
  }
  try {
    const parsed = JSON.parse(val)
    setInputLiteral('context', parsed)
  } catch {
    // Don't update until valid JSON
  }
}

function onRoleChange(val: any) {
  setInputLiteral('roleId', val)
}

function providerLabel(type: string): string {
  switch (type) {
    case 'cli': return 'CLI'
    case 'http': return 'HTTP'
    case 'openai_compatible': return 'OpenAI'
    default: return type
  }
}

// Input helpers
function findInput(name: string) {
  return (local.value.inputs || []).find((item: any) => item.name === name)
}

function getInputLiteral(name: string): any {
  const item = findInput(name)
  return item?.value?.type === 'literal' ? item.value.value : undefined
}

function isReference(name: string): boolean {
  const item = findInput(name)
  return item?.value?.type === 'ref'
}

function referenceLabel(name: string): string {
  const item = findInput(name)
  if (item?.value?.type === 'ref') {
    return `${item.value.nodeId}.${item.value.outputName}`
  }
  return ''
}

function setInputLiteral(name: string, value: unknown) {
  const item = findInput(name)
  if (item) {
    item.value = (value === undefined || value === null || value === '')
      ? null
      : { type: 'literal', value }
  }
  commit()
}

function clearInput(name: string) {
  const item = findInput(name)
  if (item) item.value = null
  commit()
}

function commit() {
  emit('update:data', JSON.parse(JSON.stringify(local.value)))
}

// Load roles
async function loadRoles() {
  rolesLoading.value = true
  try {
    const res = await automationApi.listRoles()
    if (res.code === 0) {
      roles.value = (res.data || []).filter(r => r.enabled)
    }
  } catch { /* ignore */ }
  finally { rolesLoading.value = false }
}

// Sync props
watch(() => props.data, (val) => {
  local.value = JSON.parse(JSON.stringify(val || {}))
}, { deep: true })

onMounted(() => {
  loadRoles()
})
</script>

<style scoped>
.role-agent-config {
  padding: 12px 16px;
}

.form-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.role-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.provider-tag {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--tf-bg-surface);
  color: var(--tf-text-tertiary);
}

.role-detail {
  margin-bottom: 12px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
}

.role-detail-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin-bottom: 4px;
}

.role-detail-desc {
  font-size: 11px;
  color: var(--tf-text-secondary);
  margin-bottom: 6px;
  line-height: 1.4;
}

.role-detail-meta {
  display: flex;
  gap: 6px;
}

.provider-badge,
.model-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--tf-accent-subtle);
  color: var(--tf-accent);
}

.model-badge {
  background: var(--tf-bg-hover);
  color: var(--tf-text-secondary);
}

.reference-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.reference-value code {
  color: var(--color-primary-6);
}


</style>
