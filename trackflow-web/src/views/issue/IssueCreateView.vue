<template>
  <div class="create-issue-page">
    <!-- 顶部栏 -->
    <div class="create-header">
      <div class="header-left">
        <a-breadcrumb>
          <a-breadcrumb-item>
            <router-link to="/">问题</router-link>
          </a-breadcrumb-item>
          <a-breadcrumb-item v-if="selectedProjectName">
            <span class="breadcrumb-project">{{ selectedProjectName }}</span>
          </a-breadcrumb-item>
          <a-breadcrumb-item>新建工单</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
      <div class="header-right">
        <a-button type="primary" status="success" @click="submit" :loading="submitting" :disabled="!canSubmit">
          创建工单
        </a-button>
      </div>
    </div>

    <!-- 标题输入 -->
    <div class="title-bar">
      <a-input
        v-model="form.title"
        class="title-input"
        placeholder="输入标题"
        :bordered="false"
        size="large"
      />
    </div>

    <div class="create-body">
      <!-- 左侧：编辑区 -->
      <div class="editor-area">
        <!-- 富文本工具栏 -->
        <div class="editor-toolbar">
          <a-space size="mini">
            <a-select v-model="editorFormat" size="mini" style="width: 80px" :bordered="false">
              <a-option value="normal">普通文本</a-option>
              <a-option value="h1">标题 1</a-option>
              <a-option value="h2">标题 2</a-option>
              <a-option value="h3">标题 3</a-option>
            </a-select>
            <a-divider direction="vertical" />
            <a-button type="text" size="mini"><strong>B</strong></a-button>
            <a-button type="text" size="mini"><em>I</em></a-button>
            <a-button type="text" size="mini"><s>S</s></a-button>
            <a-button type="text" size="mini">A<sup>x</sup></a-button>
            <a-divider direction="vertical" />
            <a-button type="text" size="mini">❞</a-button>
            <a-button type="text" size="mini">&lt;/&gt;</a-button>
            <a-button type="text" size="mini">🔗</a-button>
            <a-divider direction="vertical" />
            <a-button type="text" size="mini">• 列表</a-button>
            <a-button type="text" size="mini">1. 列表</a-button>
            <a-button type="text" size="mini">☐ 任务</a-button>
            <a-divider direction="vertical" />
            <a-button type="text" size="mini">📷</a-button>
            <a-button type="text" size="mini">📎</a-button>
          </a-space>
          <div class="toolbar-right">
            <a-radio-group v-model="editorMode" size="mini" type="button">
              <a-radio value="visual">外观</a-radio>
              <a-radio value="markdown">Markdown</a-radio>
            </a-radio-group>
          </div>
        </div>

        <!-- 描述编辑器 -->
        <div class="editor-content">
          <a-textarea
            v-model="form.description"
            class="description-editor"
            placeholder="在此处键入或粘贴描述"
            :auto-size="{ minRows: 15, maxRows: 30 }"
            :bordered="false"
          />
        </div>

        <!-- 附件区域 -->
        <div class="attachment-area">
          <a-upload
            :auto-upload="false"
            :show-file-list="true"
            multiple
            @change="handleFileChange"
          >
            <template #upload-button>
              <div class="upload-trigger">
                <icon-attachment />
                <span>点击或<a-link>浏览</a-link>或将文件拖到此处</span>
              </div>
            </template>
          </a-upload>
        </div>

        <!-- 类似问题 -->
        <div class="similar-section">
          <a-collapse :bordered="false">
            <a-collapse-item key="similar" header="类似问题和文章">
              <p class="hint-text">当你开始输入标题时，此处会显示可能涉及同一主题的问题、工单和文章</p>
            </a-collapse-item>
            <a-collapse-item key="preview" header="预览">
              <div v-if="form.description" class="preview-content">{{ form.description }}</div>
              <p v-else class="hint-text">输入描述后在此预览</p>
            </a-collapse-item>
          </a-collapse>
        </div>

        <!-- 底部操作栏 -->
        <div class="editor-footer">
          <a-space>
            <a-dropdown>
              <a-button type="primary" :loading="submitting" :disabled="!canSubmit">
                创建
                <icon-down />
              </a-button>
              <template #content>
                <a-doption @click="submit">创建并打开</a-doption>
                <a-doption @click="submitAndNew">创建并新建</a-doption>
              </template>
            </a-dropdown>
            <a-button @click="goBack">取消</a-button>
          </a-space>
          <div class="footer-right">
            <a-tooltip content="问题创建者可见">
              <a-button type="text" size="small">
                <icon-eye />
                问题查看者可见
              </a-button>
            </a-tooltip>
            <a-button type="text" size="small" @click="fullscreen = !fullscreen">
              <icon-expand />
              全页查看
            </a-button>
          </div>
        </div>
      </div>

      <!-- 右侧：属性面板 -->
      <div class="props-panel">
        <!-- 项目 -->
        <div class="prop-row">
          <span class="prop-label">项目</span>
          <a-select
            v-model="form.projectId"
            placeholder="选择项目"
            allow-search
            size="small"
            @change="onProjectChange"
          >
            <template #label="{ data }">
              <span class="prop-value-text">{{ data?.label }}</span>
            </template>
            <a-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name">
              <div class="project-option">
                <div class="project-dot" :style="{ background: getProjectColor(p) }"></div>
                {{ p.key }} - {{ p.name }}
              </div>
            </a-option>
          </a-select>
        </div>

        <!-- 类型 -->
        <div class="prop-row">
          <span class="prop-label">Type</span>
          <a-select v-model="form.issueType" size="small">
            <a-option value="Task">任务</a-option>
            <a-option value="Bug">缺陷</a-option>
            <a-option value="Feature">需求</a-option>
            <a-option value="Story">故事</a-option>
            <a-option value="Epic">史诗</a-option>
          </a-select>
        </div>

        <!-- 优先级 -->
        <div class="prop-row">
          <span class="prop-label">Priority</span>
          <a-select v-model="form.priority" size="small">
            <a-option value="Critical">
              <span class="priority-indicator critical"></span>紧急
            </a-option>
            <a-option value="High">
              <span class="priority-indicator high"></span>高
            </a-option>
            <a-option value="Normal">
              <span class="priority-indicator normal"></span>普通
            </a-option>
            <a-option value="Low">
              <span class="priority-indicator low"></span>低
            </a-option>
          </a-select>
        </div>

        <!-- 状态 -->
        <div class="prop-row">
          <span class="prop-label">State</span>
          <a-select v-model="form.statusId" size="small">
            <a-option v-for="s in statuses" :key="s.id" :value="s.id">
              <span class="status-dot" :style="{ background: s.color }"></span>
              {{ s.name }}
            </a-option>
          </a-select>
        </div>

        <!-- Sprint -->
        <div class="prop-row">
          <span class="prop-label">Sprints</span>
          <a-select v-model="form.sprintId" placeholder="Unscheduled" size="small" allow-clear>
            <a-option v-for="s in sprints" :key="s.id" :value="s.id">{{ s.name }}</a-option>
          </a-select>
        </div>

        <!-- 负责人 -->
        <div class="prop-row">
          <span class="prop-label">Assignee</span>
          <a-select v-model="form.assigneeId" placeholder="Unassigned" size="small" allow-clear allow-search>
            <a-option v-for="m in members" :key="m.userId" :value="m.userId">
              {{ m.displayName || m.username || ('用户' + m.userId) }}
            </a-option>
          </a-select>
        </div>

        <!-- 截止日期 -->
        <div class="prop-row">
          <span class="prop-label">Due Date</span>
          <a-date-picker v-model="form.dueDate" size="small" style="width: 100%" placeholder="无" />
        </div>

        <!-- 预估工时 -->
        <div class="prop-row">
          <span class="prop-label">Estimation</span>
          <a-input-number
            v-model="form.estimatedHours"
            size="small"
            placeholder="0"
            :min="0"
            :precision="1"
            hide-button
            style="width: 100%"
          >
            <template #suffix>小时</template>
          </a-input-number>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import {
  IconDown,
  IconEye,
  IconExpand,
  IconAttachment
} from '@arco-design/web-vue/es/icon'
import { projectApi, issueApi, sprintApi } from '@/api'

const router = useRouter()
const route = useRoute()

const submitting = ref(false)
const fullscreen = ref(false)
const editorMode = ref('visual')
const editorFormat = ref('normal')

const projects = ref<any[]>([])
const members = ref<any[]>([])
const sprints = ref<any[]>([])
const statuses = ref<any[]>([])

const form = reactive({
  projectId: undefined as string | undefined,
  title: '',
  description: '',
  issueType: 'Task',
  priority: 'Normal',
  statusId: undefined as string | undefined,
  assigneeId: undefined as string | undefined,
  sprintId: undefined as string | undefined,
  dueDate: '',
  estimatedHours: undefined as number | undefined
})

const colorPool = [
  '#e91e63', '#9c27b0', '#673ab7', '#3f51b5', '#2196f3',
  '#00bcd4', '#009688', '#4caf50', '#ff9800', '#ff5722'
]

function getProjectColor(p: any) {
  return colorPool[(p.id || 0) % colorPool.length]
}

const canSubmit = computed(() => {
  return !!form.projectId && !!form.title.trim()
})

const selectedProjectName = computed(() => {
  if (!form.projectId) return ''
  const p = projects.value.find((p: any) => p.id === form.projectId)
  return p ? p.name : ''
})

// 加载项目列表
async function loadProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    projects.value = res.data?.list || []
  } catch (e) {
    projects.value = []
  }
}

// 加载状态列表
async function loadStatuses() {
  try {
    const res = await issueApi.listStatuses()
    statuses.value = res.data || []
    // 设置默认状态
    const defaultStatus = statuses.value.find((s: any) => s.isDefault)
    if (defaultStatus) form.statusId = defaultStatus.id
  } catch (e) {
    statuses.value = [
      { id: 1, name: '待处理', color: '#4CAF50' },
      { id: 2, name: '进行中', color: '#2196F3' },
      { id: 3, name: '代码审查', color: '#9C27B0' },
      { id: 4, name: '测试中', color: '#FF9800' },
      { id: 5, name: '已完成', color: '#607D8B' }
    ]
    form.statusId = '1'
  }
}

// 项目切换后加载成员和 Sprint
async function onProjectChange(val: any) {
  const projectId = val ? String(val) : ''
  if (!projectId) {
    members.value = []
    sprints.value = []
    return
  }
  // 加载成员
  try {
    const res = await projectApi.listMembers(projectId)
    members.value = res.data || []
  } catch (e) {
    members.value = []
  }
  // 加载 Sprint
  try {
    const res = await sprintApi.listByProject(projectId)
    sprints.value = (res.data || []).filter((s: any) => s.status !== 'completed')
  } catch (e) {
    sprints.value = []
  }
}

// 如果 URL 带了 projectId
watch(() => route.query.project, (val) => {
  if (val) {
    form.projectId = String(val)
    onProjectChange(form.projectId)
  }
}, { immediate: true })

function handleFileChange(_fileList: any) {
  // TODO: 文件上传实现
}

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const payload: any = {
      projectId: form.projectId,
      title: form.title.trim(),
      description: form.description || undefined,
      issueType: form.issueType,
      priority: form.priority,
      dueDate: form.dueDate || undefined,
      estimatedHours: form.estimatedHours || undefined,
      sprintId: form.sprintId || undefined,
      assigneeId: form.assigneeId || undefined
    }

    const res = await issueApi.create(payload)
    Message.success('工单创建成功')
    // 跳转到新建的 Issue
    const issueId = res.data?.id
    if (issueId) {
      router.push(`/issues/${issueId}`)
    } else {
      router.push('/')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

async function submitAndNew() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const payload: any = {
      projectId: form.projectId,
      title: form.title.trim(),
      description: form.description || undefined,
      issueType: form.issueType,
      priority: form.priority,
      dueDate: form.dueDate || undefined,
      estimatedHours: form.estimatedHours || undefined,
      sprintId: form.sprintId || undefined,
      assigneeId: form.assigneeId || undefined
    }
    await issueApi.create(payload)
    Message.success('工单创建成功，继续新建')
    // 清空表单（保留项目选择）
    form.title = ''
    form.description = ''
    form.assigneeId = undefined
    form.dueDate = ''
    form.estimatedHours = undefined
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.back()
}

onMounted(() => {
  loadProjects()
  loadStatuses()
})
</script>

<style scoped>
.create-issue-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-bg-1);
}

/* 顶部栏 */
.create-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
  min-height: 44px;
}

.header-left {
  display: flex;
  align-items: center;
}

.breadcrumb-project {
  color: rgb(var(--primary-6));
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 标题栏 */
.title-bar {
  padding: 8px 20px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.title-input {
  font-size: 18px;
  font-weight: 500;
  color: var(--color-text-1);
}
.title-input :deep(.arco-input) {
  font-size: 18px;
  font-weight: 500;
}

/* 主体 */
.create-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧编辑区 */
.editor-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  border-right: 1px solid var(--color-border);
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 16px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.toolbar-right {
  display: flex;
  align-items: center;
}

.editor-content {
  flex: 1;
  padding: 0;
}

.description-editor {
  min-height: 300px;
  font-size: 14px;
  line-height: 1.6;
}
.description-editor :deep(.arco-textarea) {
  padding: 16px 20px;
  font-size: 14px;
  line-height: 1.6;
  resize: none;
}

/* 附件 */
.attachment-area {
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
}

.upload-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-3);
  cursor: pointer;
}

/* 类似问题 */
.similar-section {
  padding: 0 20px 12px;
}
.hint-text {
  font-size: 12px;
  color: var(--color-text-3);
}

/* 底部操作栏 */
.editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 右侧属性面板 */
.props-panel {
  width: 280px;
  flex-shrink: 0;
  padding: 16px;
  overflow-y: auto;
}

.prop-row {
  margin-bottom: 16px;
}

.prop-label {
  display: block;
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 4px;
}

.prop-value-text {
  font-size: 13px;
  color: rgb(var(--primary-6));
}

.project-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.project-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

.priority-indicator {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}
.priority-indicator.critical { background: #ef4444; }
.priority-indicator.high { background: #f59e0b; }
.priority-indicator.normal { background: #6366f1; }
.priority-indicator.low { background: #64748b; }

.preview-content {
  white-space: pre-wrap;
  font-size: 13px;
  color: var(--color-text-1);
}
</style>
