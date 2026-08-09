<template>
  <div class="issue-search-config">
    <a-form :model="local" layout="vertical" size="small">
      <div class="full-filter-alert">
        <strong>完整筛选</strong>
        <span>选择需求列表中已保存的筛选，可直接复用其中的自定义字段、日期、负责人、Sprint、标签等全部条件。</span>
      </div>

      <a-form-item label="保存的完整筛选">
        <a-select
          :model-value="getInputLiteral('savedQueryId')"
          placeholder="选择需求列表中已保存的筛选（可选）"
          :loading="savedQueriesLoading"
          allow-clear
          @change="onSavedQueryChange"
        >
          <a-optgroup v-if="pinnedQueries.length" label="置顶筛选">
            <a-option v-for="query in pinnedQueries" :key="query.id" :value="Number(query.id)">
              {{ query.icon || '🔍' }} {{ query.name }}
            </a-option>
          </a-optgroup>
          <a-optgroup v-if="regularQueries.length" label="我的筛选">
            <a-option v-for="query in regularQueries" :key="query.id" :value="Number(query.id)">
              {{ query.icon || '🔍' }} {{ query.name }}
            </a-option>
          </a-optgroup>
        </a-select>
        <template #extra>
          <span v-if="getInputLiteral('savedQueryId')">已启用完整筛选，下面的基础条件不会参与本节点执行。</span>
          <span v-else>在需求页面保存筛选后即可选择；若执行身份不是当前用户，请将筛选设为共享。</span>
        </template>
      </a-form-item>

      <div class="filter-actions">
        <a-button size="small" type="outline" @click="openIssueFilters">配置完整筛选</a-button>
        <a-button size="small" type="primary" :loading="previewLoading" :disabled="!getInputLiteral('savedQueryId')" @click="previewSavedQuery">
          试运行筛选
        </a-button>
      </div>

      <div v-if="previewResult" class="preview-result">
        <span>匹配 <strong>{{ previewResult.total }}</strong> 条需求</span>
        <span v-if="previewResult.samples.length">：{{ previewResult.samples.join('、') }}</span>
      </div>

      <div class="section-label">基础条件</div>

      <a-form-item label="项目">
        <a-select
          :model-value="getInputLiteral('projectId')"
          placeholder="选择项目"
          :loading="projectsLoading"
          allow-clear
          :disabled="hasSavedQuery"
          @change="(val: any) => setInputLiteral('projectId', val)"
        >
          <a-option v-for="p in projects" :key="p.id" :value="Number(p.id)">
            {{ p.name }}
          </a-option>
        </a-select>
        <template #extra>留空则搜索所有项目的工单；指定项目后只返回该项目内的结果</template>
      </a-form-item>

      <a-form-item label="状态筛选">
        <a-select
          :model-value="statusIdArray"
          placeholder="选择一个或多个状态"
          :loading="statusesLoading"
          multiple
          allow-clear
          :disabled="hasSavedQuery"
          @change="onStatusChange"
        >
          <a-option v-for="s in statuses" :key="s.id" :value="s.id">
            <span class="status-dot" :style="{ backgroundColor: s.color }"></span>
            {{ s.name }}
          </a-option>
        </a-select>
        <template #extra>多选；运行时存储为逗号分隔的状态 ID 字符串，如 "3,7,12"</template>
      </a-form-item>

      <a-form-item label="优先级">
        <a-select
          :model-value="priorityArray"
          placeholder="选择一个或多个优先级"
          multiple
          allow-clear
          :disabled="hasSavedQuery"
          @change="onPriorityChange"
        >
          <a-option value="critical">
            <span class="priority-dot" style="background: var(--tf-priority-critical)"></span> Critical
          </a-option>
          <a-option value="high">
            <span class="priority-dot" style="background: var(--tf-priority-high)"></span> High
          </a-option>
          <a-option value="medium">
            <span class="priority-dot" style="background: var(--tf-priority-medium)"></span> Medium
          </a-option>
          <a-option value="low">
            <span class="priority-dot" style="background: var(--tf-priority-low)"></span> Low
          </a-option>
        </a-select>
        <template #extra>多选；留空不过滤优先级，只返回勾选级别的工单</template>
      </a-form-item>

      <a-form-item label="工单类型">
        <a-select
          :model-value="issueTypeArray"
          placeholder="选择一个或多个类型"
          multiple
          allow-clear
          :disabled="hasSavedQuery"
          @change="onIssueTypeChange"
        >
          <a-option value="Bug">Bug</a-option>
          <a-option value="Feature">Feature</a-option>
          <a-option value="Task">Task</a-option>
          <a-option value="Chore">Chore</a-option>
          <a-option value="Epic">Epic</a-option>
        </a-select>
        <template #extra>多选；留空不过滤类型，只返回勾选类型的工单</template>
      </a-form-item>

      <a-form-item label="排序方式">
        <a-select
          :model-value="getInputLiteral('sort') || '-priority,-created_at'"
          :disabled="hasSavedQuery"
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
          :disabled="hasSavedQuery"
          @change="(val: any) => setInputLiteral('assignedToMe', val)"
        />
      </a-form-item>

      <a-form-item label="关键词">
        <a-input
          :model-value="String(getInputLiteral('keyword') ?? '')"
          placeholder="按标题/描述搜索"
          allow-clear
          :disabled="hasSavedQuery"
          @input="(val: any) => setInputLiteral('keyword', val || undefined)"
        />
      </a-form-item>

      <a-form-item label="数量上限">
        <a-input-number
          :model-value="getInputLiteral('limit') ?? 20"
          :min="1"
          :max="100"
          :disabled="hasSavedQuery"
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
import { useRouter } from 'vue-router'
import { projectApi, issueApi, queryApi } from '@/api'
import type { ProjectVO, IssueStatusVO, QueryPanelItemVO } from '@/api/types'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:data', value: Record<string, any>): void }>()
const router = useRouter()

const local = ref<Record<string, any>>(JSON.parse(JSON.stringify(props.data || {})))

// Data sources
const projects = ref<ProjectVO[]>([])
const statuses = ref<IssueStatusVO[]>([])
const projectsLoading = ref(false)
const statusesLoading = ref(false)
const savedQueriesLoading = ref(false)
const pinnedQueries = ref<QueryPanelItemVO[]>([])
const regularQueries = ref<QueryPanelItemVO[]>([])
const previewLoading = ref(false)
const previewResult = ref<{ total: number; samples: string[] } | null>(null)

const hasSavedQuery = computed(() => Boolean(getInputLiteral('savedQueryId')))

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

function onStatusChange(value: unknown) {
  const selectedIds = Array.isArray(value) ? value.map(String) : []
  const normalizedValue = selectedIds.length ? selectedIds.join(',') : undefined
  setInputLiteral('statusIds', normalizedValue)
}

function onPriorityChange(value: unknown) {
  const selected = Array.isArray(value) ? value.map(String) : []
  const normalizedValue = selected.length ? selected.join(',') : undefined
  setInputLiteral('priority', normalizedValue)
}

function onIssueTypeChange(value: unknown) {
  const selected = Array.isArray(value) ? value.map(String) : []
  const normalizedValue = selected.length ? selected.join(',') : undefined
  setInputLiteral('issueType', normalizedValue)
}

function onSavedQueryChange(value: unknown) {
  const savedQueryId = typeof value === 'number' ? value : undefined
  setInputLiteral('savedQueryId', savedQueryId)
  previewResult.value = null
}

function openIssueFilters() {
  router.push('/issues')
}

async function previewSavedQuery() {
  const savedQueryId = getInputLiteral('savedQueryId')
  if (!savedQueryId) return
  previewLoading.value = true
  try {
    const res = await queryApi.executeById(String(savedQueryId), { page: 1, pageSize: 5 })
    if (res.code === 0) {
      previewResult.value = {
        total: res.data.pagination.total,
        samples: (res.data.list || []).map((issue: any) => issue.issueKey || issue.title).filter(Boolean),
      }
    }
  } finally {
    previewLoading.value = false
  }
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

async function loadSavedQueries() {
  savedQueriesLoading.value = true
  try {
    const res = await queryApi.getPanel()
    if (res.code === 0) {
      pinnedQueries.value = res.data.pinned || []
      regularQueries.value = res.data.queries || []
    }
  } catch { /* keep the node usable with its basic filters */ }
  finally { savedQueriesLoading.value = false }
}

// Sync props
watch(() => props.data, (val) => {
  local.value = JSON.parse(JSON.stringify(val || {}))
}, { deep: true })

onMounted(() => {
  loadProjects()
  loadStatuses()
  loadSavedQueries()
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

.full-filter-alert {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-bottom: 14px;
  padding: 9px 10px;
  border-radius: 6px;
  color: var(--tf-text-secondary);
  background: var(--tf-accent-bg);
  font-size: 12px;
}
.full-filter-alert strong { color: var(--tf-accent); }
.filter-actions { display: flex; gap: 8px; margin: -2px 0 10px; }
.section-label {
  margin: 16px 0 8px;
  padding-top: 10px;
  border-top: 1px solid var(--tf-border);
  color: var(--tf-text-secondary);
  font-size: 12px;
  font-weight: 600;
}
.preview-result {
  margin: 0 0 12px;
  padding: 8px 10px;
  border-radius: 6px;
  color: var(--tf-text-secondary);
  background: var(--tf-success-bg);
  font-size: 12px;
}
.preview-result strong { color: var(--tf-success); }

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
