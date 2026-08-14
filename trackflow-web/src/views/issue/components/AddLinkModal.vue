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

      <!-- 模式切换 Tab -->
      <a-tabs v-model:active-key="mode" class="mode-tabs">
        <a-tab-pane key="search" title="关联现有工单">

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
        </a-tab-pane>

        <a-tab-pane key="create" title="创建新工单">
        <div class="form-row">
          <label class="form-label">新工单标题 <span class="required">*</span></label>
          <a-input
            v-model="newIssueTitle"
            placeholder="输入新工单标题..."
            allow-clear
            :max-length="200"
          />
        </div>

        <div class="form-row-inline">
          <div class="form-row form-row-half">
            <label class="form-label">项目</label>
            <a-select
              v-model="newIssueProjectId"
              :options="projectOptions"
              placeholder="选择项目"
              allow-search
              :filter-option="filterProjectOption"
            />
          </div>
          <div class="form-row form-row-half">
            <label class="form-label">工单类型</label>
            <a-select
              v-model="newIssueType"
              :options="issueTypeOptions"
              placeholder="选择类型"
              :loading="loadingIssueTypes"
            />
          </div>
        </div>
        </a-tab-pane>
      </a-tabs>
    </div>

    <template #footer>
      <a-button @click="handleCancel">取消</a-button>
      <a-button
        v-if="mode === 'search'"
        type="primary"
        :disabled="!selectedIssueId || !selectedLinkTypeId"
        :loading="submitting"
        @click="handleConfirm"
      >
        添加关联
      </a-button>
      <a-button
        v-else
        type="primary"
        :disabled="!newIssueTitle.trim() || !selectedLinkTypeId || !newIssueProjectId"
        :loading="submitting"
        @click="handleCreateAndLink"
      >
        创建并关联
      </a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { handleApiError } from '@/utils/errorHandler'
import { IconSearch } from '@arco-design/web-vue/es/icon'
import { issueApi, projectApi } from '@/api'
import type { ProjectVO } from '@/api/types'

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

interface IssueTypeOption {
  id: string
  value: string
  color: string | null
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

// 模式：搜索现有 / 创建新工单
const mode = ref<'search' | 'create'>('search')

// 关联类型
const linkTypes = ref<LinkType[]>([])
const selectedLinkTypeId = ref<string>('')

const linkTypeOptions = computed(() =>
  linkTypes.value.map(t => ({
    value: t.name,          // 后端按 name（内部标识，如 relates_to）验证
    label: t.outwardName,   // 显示 outwardName（如 "relates to"）
  }))
)

// ========== 搜索模式 ==========
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

// ========== 创建模式 ==========
const newIssueTitle = ref('')
const newIssueProjectId = ref<string>('')
const newIssueType = ref<string>('Task')
const projects = ref<ProjectVO[]>([])
const issueTypeOptionsList = ref<IssueTypeOption[]>([])
const loadingIssueTypes = ref(false)

const projectOptions = computed(() =>
  projects.value.map(p => ({
    value: p.id,
    label: `${p.name} (${p.key})`,
  }))
)

const issueTypeOptions = computed(() =>
  issueTypeOptionsList.value.map(t => ({
    value: t.value,
    label: t.value,
  }))
)

function filterProjectOption(inputValue: string, option: { label: string }) {
  return option.label.toLowerCase().includes(inputValue.toLowerCase())
}

/** 加载项目列表 */
async function loadProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    projects.value = res.data?.list || []
    // 默认选中当前工单所在项目
    if (props.projectId) {
      newIssueProjectId.value = props.projectId
    }
  } catch {
    projects.value = []
  }
}

/** 加载工单类型选项 */
async function loadIssueTypes(projectId: string) {
  if (!projectId) {
    issueTypeOptionsList.value = []
    return
  }
  loadingIssueTypes.value = true
  try {
    const res = await issueApi.getIssueTypeOptions(projectId)
    issueTypeOptionsList.value = (res.data || []).filter((t: any) => !t.isArchived)
    // 默认选中 Task 或列表中第一个
    const taskType = issueTypeOptionsList.value.find(t => t.value === 'Task')
    newIssueType.value = taskType?.value || issueTypeOptionsList.value[0]?.value || 'Task'
  } catch {
    issueTypeOptionsList.value = []
  } finally {
    loadingIssueTypes.value = false
  }
}

// 当选择的项目变化时，重新加载工单类型
watch(newIssueProjectId, (val) => {
  if (val) {
    loadIssueTypes(val)
  }
})

// ========== 公共逻辑 ==========

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
    // 重置搜索模式
    keyword.value = ''
    searchResults.value = []
    selectedIssueId.value = ''
    submitting.value = false
    mode.value = 'search'
    // 重置创建模式
    newIssueTitle.value = ''
    newIssueProjectId.value = props.projectId || ''
    newIssueType.value = 'Task'
    // 加载数据
    loadLinkTypes()
    loadProjects()
    if (props.projectId) {
      loadIssueTypes(props.projectId)
    }
  }
})

function handleCancel() {
  visible.value = false
}

/** 搜索模式：添加关联 */
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
  } catch (e) {
    handleApiError(e, '添加关联失败')
  } finally {
    submitting.value = false
  }
}

/** 创建模式：创建新工单并关联 */
async function handleCreateAndLink() {
  const title = newIssueTitle.value.trim()
  if (!title) {
    Message.warning('请输入工单标题')
    return
  }
  if (!selectedLinkTypeId.value) {
    Message.warning('请选择关联类型')
    return
  }
  if (!newIssueProjectId.value) {
    Message.warning('请选择项目')
    return
  }

  submitting.value = true
  try {
    // 第一步：创建新工单
    const createRes = await issueApi.create({
      projectId: newIssueProjectId.value,
      title,
      issueType: newIssueType.value || undefined,
      quickCreate: true,
    })
    const newIssueId = createRes.data?.id
    if (!newIssueId) {
      Message.error('创建工单失败：未获取到新工单 ID')
      return
    }

    // 第二步：建立关联
    try {
      await issueApi.addLink(props.issueId, {
        targetIssueId: newIssueId,
        linkTypeId: selectedLinkTypeId.value,
      })
    } catch (linkError: any) {
      // 工单已创建但关联失败，提示用户
      Message.warning(`工单已创建（${createRes.data?.issueKey}），但关联建立失败：${linkError?.response?.data?.message || '未知错误'}`)
      visible.value = false
      emit('linked')
      return
    }

    Message.success(`工单 ${createRes.data?.issueKey} 已创建并关联`)
    visible.value = false
    emit('linked')
  } catch (e) {
    handleApiError(e, '创建工单失败')
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

.form-row-inline {
  display: flex;
  gap: 12px;
}

.form-row-half {
  flex: 1;
}

.form-label {
  font-size: 13px;
  color: var(--tf-text-secondary);
  font-weight: 500;
}

.form-label .required {
  color: var(--tf-danger);
  margin-left: 2px;
}

/* 模式切换 Tab */
/* arco-tabs-content padding:0 已在 components.css 全局设置 */
.mode-tabs :deep(.arco-tabs-content) { margin-top: 12px; }

/* 搜索模式样式 */
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
