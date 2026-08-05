<template>
  <div v-if="issueListData.length > 0" class="widget-issue-list">
    <div v-for="issue in issueListData" :key="issue.id" class="issue-item" @click="navigateToIssue(issue.id)">
      <span class="issue-key">{{ issue.issueKey }}</span>
      <span class="issue-title">{{ issue.title }}</span>
    </div>
  </div>
  <div v-else-if="config.queryType && dataLoaded" class="widget-configure-hint">
    <icon-check-circle :size="32" class="hint-icon" style="color: var(--tf-text-quaternary)" />
    <span class="hint-text" style="color: var(--tf-text-tertiary)">暂无匹配的工单</span>
  </div>
  <div v-else-if="!config.queryType" class="widget-configure-hint">
    <icon-list :size="32" class="hint-icon" />
    <span class="hint-text">点击「编辑配置」设置查询条件</span>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { IconList, IconCheckCircle } from '@arco-design/web-vue/es/icon'
import { issueApi } from '@/api/issue'

const props = defineProps<{
  config: Record<string, any>
}>()

const emit = defineEmits<{
  loaded: []
  error: [message: string]
}>()

const router = useRouter()
const issueListData = ref<Array<{ id: string; issueKey: string; title: string }>>([])
const dataLoaded = ref(false)

function navigateToIssue(issueId: string) {
  router.push(`/issues/${issueId}`)
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

defineExpose({ loadData })
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
