<template>
  <a-modal
    v-model:visible="visible"
    title="添加关联"
    :width="560"
    :mask-closable="false"
    @cancel="handleCancel"
    @before-ok="handleConfirm"
  >
    <div class="add-link-form">
      <!-- 关联类型 -->
      <div class="form-row">
        <label class="form-label">关联类型</label>
        <a-select
          v-model="selectedLinkTypeId"
          :options="linkTypeOptions"
          placeholder="选择关联类型"
          :allow-search="false"
          style="width: 100%"
        />
      </div>

      <!-- 搜索工单 -->
      <div class="form-row">
        <label class="form-label">搜索工单</label>
        <a-input
          v-model="keyword"
          placeholder="输入工单 ID 或标题关键词..."
          allow-clear
          @input="onKeywordInput"
        >
          <template #prefix><icon-search /></template>
        </a-input>
      </div>

      <!-- 工单列表 -->
      <div class="issue-list-wrapper">
        <a-spin :loading="searching" style="width: 100%">
          <div v-if="searchResults.length === 0 && !searching" class="empty-hint">
            {{ keyword ? '未找到匹配的工单' : '输入工单 ID 或标题关键词搜索（支持跨项目）' }}
          </div>
          <div v-else class="issue-list">
            <div
              v-for="issue in searchResults"
              :key="issue.id"
              class="issue-item"
              :class="{ selected: selectedIssueId === issue.id, disabled: issue.id === currentIssueId }"
              @click="issue.id !== currentIssueId && (selectedIssueId = issue.id)"
            >
              <span
                v-if="issue.projectKey !== currentProjectKey"
                class="issue-project-badge"
              >{{ issue.projectKey }}</span>
              <span class="issue-key">{{ issue.issueKey }}</span>
              <span class="issue-title">{{ issue.title }}</span>
              <span class="issue-status" :style="{ color: issue.statusColor || '#666' }">
                {{ issue.statusName }}
              </span>
              <span v-if="issue.id === currentIssueId" class="self-tag">当前</span>
            </div>
          </div>
        </a-spin>
      </div>
    </div>

    <template #footer>
      <a-button @click="handleCancel">取消</a-button>
      <a-button
        type="primary"
        :disabled="!selectedIssueId || !selectedLinkTypeId"
        :loading="submitting"
        @click="handleConfirm"
      >
        添加关联
      </a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconSearch } from '@arco-design/web-vue/es/icon'
import { issueApi } from '@/api'

interface LinkType {
  id: string
  name: string
  outwardName: string
  inwardName: string
  direction: string
}

interface IssueItem {
  id: string
  issueKey: string
  projectKey: string
  title: string
  statusName?: string
  statusColor?: string
}

const props = defineProps<{
  issueId: string
  projectId: string
  projectKey?: string
}>()

const emit = defineEmits<{
  linked: []
}>()

const visible = defineModel<boolean>('visible', { default: false })

// 关联类型
const linkTypes = ref<LinkType[]>([])
const selectedLinkTypeId = ref<string>('')

const linkTypeOptions = computed(() =>
  linkTypes.value.map(t => ({
    value: t.name,          // 后端按 name（内部标识，如 relates_to）验证
    label: t.outwardName,   // 显示 outwardName（如 "relates to"）
  }))
)

// 搜索
const keyword = ref('')
const searchResults = ref<IssueItem[]>([])
const searching = ref(false)
const selectedIssueId = ref<string>('')
const submitting = ref(false)

// 当前工单 ID（排除自身）
const currentIssueId = computed(() => props.issueId)

// 当前项目 Key（用于判断是否跨项目）
const currentProjectKey = computed(() => props.projectKey || '')

let searchTimer: ReturnType<typeof setTimeout> | null = null

function onKeywordInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => doSearch(), 300)
}

async function doSearch() {
  if (!keyword.value.trim()) {
    searchResults.value = []
    return
  }
  searching.value = true
  try {
    const res = await issueApi.list({
      keyword: keyword.value.trim(),
      pageSize: 20,
    })
    searchResults.value = (res.data?.list || []).map((item: any) => ({
      id: item.id,
      issueKey: item.issueKey,
      projectKey: item.issueKey?.split('-')[0] || '',
      title: item.title,
      statusName: item.statusName,
      statusColor: item.statusColor,
    }))
  } catch {
    searchResults.value = []
  } finally {
    searching.value = false
  }
}

// 加载关联类型
async function loadLinkTypes() {
  try {
    const res = await issueApi.listLinkTypes()
    linkTypes.value = res.data || []
    // 默认选 relates to
    const relatesTo = linkTypes.value.find(t => t.name?.toLowerCase().includes('relates'))
    selectedLinkTypeId.value = relatesTo?.name || linkTypes.value[0]?.name || ''
  } catch {
    // ignore
  }
}

// 打开时初始化
watch(visible, (val) => {
  if (val) {
    keyword.value = ''
    searchResults.value = []
    selectedIssueId.value = ''
    submitting.value = false
    loadLinkTypes()
  }
})

function handleCancel() {
  visible.value = false
}

async function handleConfirm() {
  if (!selectedIssueId.value || !selectedLinkTypeId.value) return
  submitting.value = true
  try {
    await issueApi.addLink(props.issueId, {
      targetIssueId: selectedIssueId.value,
      linkTypeId: selectedLinkTypeId.value,
    })
    Message.success('关联添加成功')
    visible.value = false
    emit('linked')
  } catch (e: any) {
    Message.error(e?.response?.data?.message || '添加关联失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.add-link-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  color: var(--tf-text-secondary);
  font-weight: 500;
}

.issue-list-wrapper {
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  min-height: 160px;
  max-height: 280px;
  overflow-y: auto;
}

.empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 160px;
  color: var(--tf-text-tertiary);
  font-size: 13px;
}

.issue-list {
  padding: 4px 0;
}

.issue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 150ms;
  border-radius: 4px;
  margin: 2px 4px;
}

.issue-item:hover:not(.disabled) {
  background: var(--tf-bg-hover);
}

.issue-item.selected {
  background: color-mix(in srgb, var(--tf-accent) 12%, transparent);
}

.issue-item.disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.issue-key {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-accent);
  min-width: 72px;
  flex-shrink: 0;
}

.issue-project-badge {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-active);
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.issue-title {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.issue-status {
  font-size: 11px;
  flex-shrink: 0;
}

.self-tag {
  font-size: 11px;
  background: var(--tf-bg-active);
  color: var(--tf-text-tertiary);
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}
</style>
