<template>
  <div v-if="issueListData.length > 0" class="widget-issue-list">
    <div v-for="issue in issueListData" :key="issue.id" class="issue-item" @click="navigateToIssue(issue.id)">
      <span class="issue-key">{{ issue.issueKey }}</span>
      <span class="issue-title">{{ issue.title }}</span>
    </div>
  </div>
  <div v-else-if="(config.queryType || config.filterQuery) && dataLoaded" class="widget-configure-hint">
    <icon-check-circle :size="32" class="hint-icon" style="color: var(--tf-text-quaternary)" />
    <span class="hint-text" style="color: var(--tf-text-tertiary)">暂无匹配的工单</span>
  </div>
  <div v-else-if="!config.queryType && !config.filterQuery" class="widget-configure-hint">
    <icon-list :size="32" class="hint-icon" />
    <span class="hint-text">点击「编辑配置」设置查询条件</span>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { IconList, IconCheckCircle } from '@arco-design/web-vue/es/icon'
import { issueApi } from '@/api/issue'
import { parseWidgetFilterQuery, buildIssueListRoute } from '../utils/widgetFilterParser'

const props = defineProps<{
  config: Record<string, any>
}>()

const emit = defineEmits<{
  loaded: []
  error: [message: string]
  titleClick: []
}>()

const router = useRouter()
const issueListData = ref<Array<{ id: string; issueKey: string; title: string }>>([])
const dataLoaded = ref(false)

// Cache for status name → ID resolution
let statusCache: Array<{ id: string; name: string }> | null = null

/**
 * Resolve a status name (e.g. "Blocked", "Open") to its numeric ID.
 * Supports comma-separated values: "Blocked,Open" → "7,1"
 * Matching is case-insensitive.
 */
async function resolveStatusNameToId(nameInput: string): Promise<string | null> {
  if (!statusCache) {
    try {
      const res = await issueApi.listStatuses()
      statusCache = (res.data || []).map((s: any) => ({ id: String(s.id), name: s.name }))
    } catch {
      return null
    }
  }

  const names = nameInput.split(',').map(n => n.trim().toLowerCase())
  const resolvedIds: string[] = []

  for (const name of names) {
    const found = statusCache.find(s => s.name.toLowerCase() === name)
    if (found) {
      resolvedIds.push(found.id)
    }
    // If not found, skip (don't pass invalid names to backend)
  }

  return resolvedIds.length > 0 ? resolvedIds.join(',') : null
}

function navigateToIssue(issueId: string) {
  router.push(`/issues/${issueId}`)
}

/** Navigate to issue list with current filter conditions applied */
function navigateToFilteredList() {
  const routeQuery = buildIssueListRoute(props.config)
  router.push({ path: '/issues', query: routeQuery })
}

async function loadData(_force = false) {
  try {
    const config = props.config
    const params: Record<string, any> = { pageSize: config.pageSize || 10, page: 1 }
    const queryType = config.queryType as string | undefined
    if (queryType === 'open') {
      params.hideResolved = 'true'
    } else if (queryType === 'closed') {
      params.onlyResolved = 'true'
    } else if (queryType === 'my_open') {
      params.hideResolved = 'true'
      params.assigneeId = 'me'
    }
    if (config.projectId) params.projectId = config.projectId
    params.sort = config.sort || '-updatedAt'

    // Parse advanced filter query and merge into params
    if (config.filterQuery) {
      const filterParams = parseWidgetFilterQuery(config.filterQuery)

      // Resolve _statusName to numeric statusId via API
      if (filterParams._statusName) {
        const resolvedId = await resolveStatusNameToId(filterParams._statusName)
        if (resolvedId) {
          filterParams.statusId = resolvedId
        }
        // Remove the internal marker regardless
        delete filterParams._statusName
      }

      Object.assign(params, filterParams)
    }

    const res = await issueApi.list(params)
    const issues = res.data?.list || []
    issueListData.value = issues.map(item => ({
      id: item.id,
      issueKey: item.issueKey || '',
      title: item.title || ''
    }))
    dataLoaded.value = true
    emit('loaded')
  } catch (e: any) {
    emit('error', e.response?.data?.message || '加载工单列表失败')
  }
}

onMounted(() => {
  loadData()
})

defineExpose({ loadData, navigateToFilteredList })
</script>

<style scoped>
.widget-issue-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.issue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 6px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.issue-item:hover {
  background: var(--tf-bg-hover);
}

.issue-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-accent);
  white-space: nowrap;
  flex-shrink: 0;
}

.issue-title {
  font-size: 12px;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.widget-configure-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 8px;
  padding: 12px;
}

.hint-icon {
  color: var(--tf-text-tertiary);
  opacity: 0.4;
}

.hint-text {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-align: center;
  line-height: 1.4;
}
</style>
