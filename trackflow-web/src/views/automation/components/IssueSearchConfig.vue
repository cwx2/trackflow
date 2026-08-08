<template>
  <div class="issue-search-config">
    <a-form layout="vertical" size="small">
      <a-form-item label="项目">
        <a-select
          :model-value="getInputLiteral('projectId')"
          placeholder="选择项目"
          :loading="projectsLoading"
          allow-clear
          @change="(val: any) => setInputLiteral('projectId', val)"
        >
          <a-option v-for="p in projects" :key="p.id" :value="Number(p.id)">
            {{ p.name }}
          </a-option>
        </a-select>
        <div class="form-hint">留空表示搜索所有项目的工单</div>
      </a-form-item>

      <a-form-item label="状态筛选">
        <a-select
          :model-value="statusIdArray"
          placeholder="选择一个或多个状态"
          :loading="statusesLoading"
          multiple
          allow-clear
          @change="onStatusChange"
        >
          <a-option v-for="s in statuses" :key="s.id" :value="s.id">
            <span class="status-dot" :style="{ backgroundColor: s.color }"></span>
            {{ s.name }}
          </a-option>
        </a-select>
        <div class="form-hint">多选，最终以逗号分隔的 ID 字符串存储</div>
      </a-form-item>

      <a-form-item label="优先级">
        <a-select
          :model-value="priorityArray"
          placeholder="选择一个或多个优先级"
          multiple
          allow-clear
          @change="onPriorityChange"
        >
          <a-option value="critical">
            <span class="priority-dot" style="background: #ef4444"></span> Critical
          </a-option>
          <a-option value="high">
            <span class="priority-dot" style="background: #f97316"></span> High
          </a-option>
          <a-option value="medium">
            <span class="priority-dot" style="background: #eab308"></span> Medium
          </a-option>
          <a-option value="low">
            <span class="priority-dot" style="background: #6b7280"></span> Low
          </a-option>
        </a-select>
        <div class="form-hint">多选，只返回指定优先级的工单</div>
      </a-form-item>

      <a-form-item label="工单类型">
        <a-select
          :model-value="issueTypeArray"
          placeholder="选择一个或多个类型"
          multiple
          allow-clear
          @change="onIssueTypeChange"
        >
          <a-option value="Bug">Bug</a-option>
          <a-option value="Feature">Feature</a-option>
          <a-option value="Task">Task</a-option>
          <a-option value="Chore">Chore</a-option>
          <a-option value="Epic">Epic</a-option>
        </a-select>
        <div class="form-hint">多选，只返回指定类型的工单</div>
      </a-form-item>

      <a-form-item label="排序方式">
        <a-select
          :model-value="getInputLiteral('sort') || '-priority,-created_at'"
          @change="(val: any) => setInputLiteral('sort', val)"
        >
          <a-option value="-priority,-created_at">优先级降序（默认）</a-option>
          <a-option value="created_at">创建时间升序</a-option>
          <a-option value="-created_at">创建时间降序</a-option>
          <a-option value="-updated_at">更新时间降序</a-option>
          <a-option value="due_date">截止日期升序</a-option>
        </a-select>
      </a-form-item>

      <a-form-item label="只查分配给执行人的工单">
        <a-switch
          :model-value="Boolean(getInputLiteral('assignedToMe'))"
          @change="(val: any) => setInputLiteral('assignedToMe', val)"
        />
      </a-form-item>

      <a-form-item label="关键词">
        <a-input
          :model-value="String(getInputLiteral('keyword') ?? '')"
          placeholder="按标题/描述搜索"
          allow-clear
          @input="(val: any) => setInputLiteral('keyword', val || undefined)"
        />
      </a-form-item>

      <a-form-item label="数量上限">
        <a-input-number
          :model-value="getInputLiteral('limit') ?? 20"
          :min="1"
          :max="100"
          style="width: 100%"
          placeholder="默认20，最多100"
          @change="(val: any) => setInputLiteral('limit', val)"
        />
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { projectApi, issueApi } from '@/api'
import type { ProjectVO, IssueStatusVO } from '@/api/types'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:data', value: Record<string, any>): void }>()

const local = ref<Record<string, any>>(JSON.parse(JSON.stringify(props.data || {})))

// Data sources
const projects = ref<ProjectVO[]>([])
const statuses = ref<IssueStatusVO[]>([])
const projectsLoading = ref(false)
const statusesLoading = ref(false)

// Compute statusIds as array for multi-select
const statusIdArray = computed(() => {
  const raw = getInputLiteral('statusIds')
  if (!raw) return []
  if (typeof raw === 'string') {
    return raw.split(',').filter(Boolean).map(s => s.trim())
  }
  return []
})

// Compute priority as array
const priorityArray = computed(() => {
  const raw = getInputLiteral('priority')
  if (!raw) return []
  if (typeof raw === 'string') {
    return raw.split(',').filter(Boolean).map(s => s.trim())
  }
  return []
})

// Compute issueType as array
const issueTypeArray = computed(() => {
  const raw = getInputLiteral('issueType')
  if (!raw) return []
  if (typeof raw === 'string') {
    return raw.split(',').filter(Boolean).map(s => s.trim())
  }
  return []
})

function onStatusChange(selectedIds: string[]) {
  const value = selectedIds.length ? selectedIds.join(',') : undefined
  setInputLiteral('statusIds', value)
}

function onPriorityChange(selected: string[]) {
  const value = selected.length ? selected.join(',') : undefined
  setInputLiteral('priority', value)
}

function onIssueTypeChange(selected: string[]) {
  const value = selected.length ? selected.join(',') : undefined
  setInputLiteral('issueType', value)
}

// Input helpers (same pattern as GenericNodeConfig)
function findInput(name: string) {
  return (local.value.inputs || []).find((item: any) => item.name === name)
}

function getInputLiteral(name: string): any {
  const item = findInput(name)
  return item?.value?.type === 'literal' ? item.value.value : undefined
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

function commit() {
  emit('update:data', JSON.parse(JSON.stringify(local.value)))
}

// Load projects
async function loadProjects() {
  projectsLoading.value = true
  try {
    const res = await projectApi.list({ page: 1, pageSize: 100 })
    if (res.code === 0) {
      projects.value = res.data.list || []
    }
  } catch { /* ignore */ }
  finally { projectsLoading.value = false }
}

// Load statuses
async function loadStatuses() {
  statusesLoading.value = true
  try {
    const res = await issueApi.listStatuses()
    if (res.code === 0) {
      statuses.value = res.data || []
    }
  } catch { /* ignore */ }
  finally { statusesLoading.value = false }
}

// Sync props
watch(() => props.data, (val) => {
  local.value = JSON.parse(JSON.stringify(val || {}))
}, { deep: true })

onMounted(() => {
  loadProjects()
  loadStatuses()
})
</script>

<style scoped>
.issue-search-config {
  padding: 12px 16px;
}

.form-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}

.priority-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}

:deep(.arco-form-item) {
  margin-bottom: 12px;
}

:deep(.arco-form-item-label) {
  font-size: 12px;
  color: var(--tf-text-secondary);
}
</style>
