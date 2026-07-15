<template>
  <div class="sprint-page">
    <div class="page-header">
      <h2 class="page-title">迭代管理</h2>
      <div class="header-actions">
        <a-select
          v-model="selectedProject"
          placeholder="选择项目"
          style="width: 200px"
          size="small"
          allow-search
          :loading="projectLoadState === 'loading'"
          @change="loadSprints"
        >
          <template v-if="projectLoadState === 'error'" #empty>
            <div class="select-error-state">
              <span>加载失败</span>
              <a-link @click.stop="loadProjects">重试</a-link>
            </div>
          </template>
          <a-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.key }} - {{ p.name }}
          </a-option>
        </a-select>
        <a-button v-if="canCreateSprint" type="primary" size="small" :disabled="!selectedProject" @click="showCreate = true">
          + 新建迭代
        </a-button>
      </div>
    </div>

    <!-- Sprint 列表 -->
    <div class="sprint-list" v-if="loadingState === 'success' && sprints.length > 0">
      <!-- Active Sprints -->
      <div v-for="sprint in activeSprints" :key="sprint.id" class="sprint-card active">
        <div class="sprint-header">
          <div class="sprint-info">
            <span class="sprint-status-badge active">进行中</span>
            <h3 class="sprint-name">{{ sprint.name }}</h3>
            <span class="sprint-remaining" v-if="getRemainingDays(sprint) !== null">
              <template v-if="(getRemainingDays(sprint) ?? 0) > 0">
                <span class="remaining-icon">⏳</span> 还剩 {{ getRemainingDays(sprint) }} 天
              </template>
              <template v-else-if="getRemainingDays(sprint) === 0">
                <span class="remaining-icon warning">⚠️</span> 今天截止
              </template>
              <template v-else>
                <span class="remaining-icon overdue">🚨</span> 已超期 {{ Math.abs(getRemainingDays(sprint) ?? 0) }} 天
              </template>
            </span>
          </div>
          <div class="sprint-dates">
            {{ formatDate(sprint.startDate) }} — {{ formatDate(sprint.endDate) }}
          </div>
        </div>

        <!-- 进度区域 -->
        <div class="sprint-progress-section">
          <div class="progress-bar-container">
            <div class="progress-bar">
              <div
                class="progress-segment done"
                :style="{ width: getProgressPercent(sprint, 'done') + '%' }"
                :title="`已完成: ${sprint.doneIssues}`"
              ></div>
              <div
                class="progress-segment in-progress"
                :style="{ width: getProgressPercent(sprint, 'inProgress') + '%' }"
                :title="`进行中: ${sprint.inProgressIssues}`"
              ></div>
              <div
                class="progress-segment todo"
                :style="{ width: getProgressPercent(sprint, 'todo') + '%' }"
                :title="`待办: ${sprint.todoIssues}`"
              ></div>
            </div>
            <span class="progress-percent">{{ getCompletionPercent(sprint) }}%</span>
          </div>
          <div class="progress-stats">
            <span class="stat-item done">
              <span class="stat-dot"></span>
              完成 {{ sprint.doneIssues }}
            </span>
            <span class="stat-item in-progress">
              <span class="stat-dot"></span>
              进行中 {{ sprint.inProgressIssues }}
            </span>
            <span class="stat-item todo">
              <span class="stat-dot"></span>
              待办 {{ sprint.todoIssues }}
            </span>
            <span class="stat-item total">
              共 {{ sprint.totalIssues }} 个工单
            </span>
            <span class="stat-item overdue" v-if="sprint.overdueIssues > 0">
              <span class="stat-dot"></span>
              逾期 {{ sprint.overdueIssues }}
            </span>
          </div>
        </div>

        <!-- 燃尽图 -->
        <SprintBurndownChart
          v-if="sprint.startDate && sprint.endDate && sprint.totalIssues > 0"
          :sprint-id="sprint.id"
          :sprint-end-date="sprint.endDate"
          :is-completed="false"
        />

        <p class="sprint-goal" v-if="sprint.goal">{{ sprint.goal }}</p>
        <div class="sprint-actions">
          <a-button size="mini" type="text" @click="viewSprintIssues(sprint)">查看工单</a-button>
          <a-button v-if="canEditSprint" size="mini" @click="handleCompleteSprint(sprint)">完成迭代</a-button>
        </div>
      </div>

      <!-- Planned Sprints -->
      <div v-for="sprint in plannedSprints" :key="sprint.id" class="sprint-card planned">
        <div class="sprint-header">
          <div class="sprint-info">
            <span class="sprint-status-badge planned">计划中</span>
            <h3 class="sprint-name">{{ sprint.name }}</h3>
          </div>
          <div class="sprint-dates" v-if="sprint.startDate">
            {{ formatDate(sprint.startDate) }} — {{ formatDate(sprint.endDate) }}
          </div>
        </div>

        <!-- 进度区域 -->
        <div class="sprint-progress-section" v-if="sprint.totalIssues > 0">
          <div class="progress-bar-container">
            <div class="progress-bar">
              <div
                class="progress-segment done"
                :style="{ width: getProgressPercent(sprint, 'done') + '%' }"
              ></div>
              <div
                class="progress-segment in-progress"
                :style="{ width: getProgressPercent(sprint, 'inProgress') + '%' }"
              ></div>
              <div
                class="progress-segment todo"
                :style="{ width: getProgressPercent(sprint, 'todo') + '%' }"
              ></div>
            </div>
            <span class="progress-percent">{{ getCompletionPercent(sprint) }}%</span>
          </div>
          <div class="progress-stats">
            <span class="stat-item done">
              <span class="stat-dot"></span>
              完成 {{ sprint.doneIssues }}
            </span>
            <span class="stat-item in-progress">
              <span class="stat-dot"></span>
              进行中 {{ sprint.inProgressIssues }}
            </span>
            <span class="stat-item todo">
              <span class="stat-dot"></span>
              待办 {{ sprint.todoIssues }}
            </span>
            <span class="stat-item total">
              共 {{ sprint.totalIssues }} 个工单
            </span>
          </div>
        </div>
        <div class="sprint-no-issues" v-else>
          <span class="no-issues-text">暂无工单</span>
        </div>

        <p class="sprint-goal" v-if="sprint.goal">{{ sprint.goal }}</p>
        <div class="sprint-actions">
          <a-button size="mini" type="text" @click="viewSprintIssues(sprint)" v-if="sprint.totalIssues > 0">查看工单</a-button>
          <a-button v-if="canEditSprint" type="primary" size="mini" @click="activateSprint(sprint.id)">开始迭代</a-button>
          <a-popconfirm v-if="canDeleteSprint" content="确定删除此迭代？" @ok="deleteSprint(sprint.id)">
            <a-button size="mini" status="danger">删除</a-button>
          </a-popconfirm>
        </div>
      </div>

      <!-- Completed Sprints -->
      <div v-for="sprint in completedSprints" :key="sprint.id" class="sprint-card completed">
        <div class="sprint-header">
          <div class="sprint-info">
            <span class="sprint-status-badge completed">已完成</span>
            <h3 class="sprint-name">{{ sprint.name }}</h3>
          </div>
          <div class="sprint-dates">
            {{ formatDate(sprint.startDate) }} — {{ formatDate(sprint.endDate) }}
          </div>
        </div>

        <!-- 完成统计 -->
        <div class="sprint-progress-section" v-if="sprint.totalIssues > 0">
          <div class="progress-bar-container">
            <div class="progress-bar">
              <div
                class="progress-segment done"
                :style="{ width: getProgressPercent(sprint, 'done') + '%' }"
              ></div>
              <div
                class="progress-segment in-progress"
                :style="{ width: getProgressPercent(sprint, 'inProgress') + '%' }"
              ></div>
              <div
                class="progress-segment todo"
                :style="{ width: getProgressPercent(sprint, 'todo') + '%' }"
              ></div>
            </div>
            <span class="progress-percent">{{ getCompletionPercent(sprint) }}%</span>
          </div>
          <div class="progress-stats">
            <span class="stat-item done">
              <span class="stat-dot"></span>
              完成 {{ sprint.doneIssues }}
            </span>
            <span class="stat-item in-progress" v-if="sprint.inProgressIssues > 0">
              <span class="stat-dot"></span>
              进行中 {{ sprint.inProgressIssues }}
            </span>
            <span class="stat-item todo" v-if="sprint.todoIssues > 0">
              <span class="stat-dot"></span>
              待办 {{ sprint.todoIssues }}
            </span>
            <span class="stat-item total">
              共 {{ sprint.totalIssues }} 个工单
            </span>
          </div>
        </div>

        <div class="sprint-actions">
          <a-button size="mini" type="text" @click="viewSprintIssues(sprint)" v-if="sprint.totalIssues > 0">查看工单</a-button>
          <a-button
            size="mini"
            type="text"
            @click="toggleCompletedBurndown(sprint.id)"
            v-if="sprint.startDate && sprint.endDate && sprint.totalIssues > 0"
          >
            {{ expandedCompletedSprints.has(sprint.id) ? '收起燃尽图' : '查看燃尽图' }}
          </a-button>
        </div>

        <!-- 已完成 Sprint 的燃尽图（展开时显示） -->
        <SprintBurndownChart
          v-if="expandedCompletedSprints.has(sprint.id)"
          :sprint-id="sprint.id"
          :sprint-end-date="sprint.endDate"
          :is-completed="true"
        />
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-else-if="loadingState === 'loading'" class="empty-state">
      <a-spin :size="32" />
      <p class="empty-desc" style="margin-top: 16px;">正在加载迭代列表…</p>
    </div>

    <!-- 错误状态：API 错误 / 网络异常 -->
    <div v-else-if="loadingState === 'error'" class="empty-state">
      <div class="empty-icon">⚠️</div>
      <h3 class="empty-title">加载失败</h3>
      <p class="empty-desc">无法获取迭代列表，请稍后重试</p>
      <a-button type="primary" size="small" @click="loadSprints">重试</a-button>
    </div>

    <!-- 权限不足状态 -->
    <div v-else-if="loadingState === 'forbidden'" class="empty-state">
      <div class="empty-icon">🔒</div>
      <h3 class="empty-title">暂无可查看的迭代</h3>
      <p class="empty-desc">当前项目尚未创建迭代，或您没有查看权限。请联系项目管理员。</p>
    </div>

    <!-- 项目加载失败 -->
    <div v-else-if="!selectedProject && projectLoadState === 'error'" class="empty-state">
      <div class="empty-icon">⚠️</div>
      <h3 class="empty-title">项目列表加载失败</h3>
      <p class="empty-desc">无法获取可用项目，请检查网络后重试</p>
      <a-button type="primary" size="small" @click="loadProjects">重试</a-button>
    </div>

    <!-- 无可访问项目 -->
    <div v-else-if="!selectedProject && projectLoadState === 'success' && projects.length === 0" class="empty-state">
      <div class="empty-icon">📁</div>
      <h3 class="empty-title">暂无可访问的项目</h3>
      <p class="empty-desc">您尚未加入任何项目，请联系管理员添加为项目成员</p>
    </div>

    <!-- 正常空状态 -->
    <div v-else class="empty-state">
      <div class="empty-icon">🏃</div>
      <h3 class="empty-title">{{ selectedProject ? '暂无迭代' : '请选择项目' }}</h3>
      <p class="empty-desc">
        <template v-if="!selectedProject">从上方下拉框选择项目查看迭代</template>
        <template v-else-if="canCreateSprint">创建第一个 Sprint 来规划团队工作</template>
        <template v-else>当前项目尚未创建迭代，请联系项目管理员。</template>
      </p>
      <a-button v-if="selectedProject && canCreateSprint" type="primary" size="small" @click="showCreate = true">
        + 新建迭代
      </a-button>
    </div>

    <!-- 创建 Sprint 弹窗 -->
    <a-modal v-model:visible="showCreate" title="新建迭代" :width="480" @ok="handleCreate" :ok-loading="creating">
      <a-form :model="createForm" layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model="createForm.name" placeholder="如：Sprint 25" />
        </a-form-item>
        <a-form-item label="目标">
          <a-textarea v-model="createForm.goal" placeholder="本迭代目标（可选）" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>
        <a-form-item label="开始日期">
          <a-date-picker v-model="createForm.startDate" style="width: 100%" />
        </a-form-item>
        <a-form-item label="结束日期">
          <a-date-picker v-model="createForm.endDate" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 完成 Sprint 确认弹窗 -->
    <a-modal
      v-model:visible="showCompleteModal"
      :title="`完成迭代：${completingSprintName}`"
      :width="560"
      :ok-loading="completing"
      :ok-text="'确认完成'"
      @ok="confirmCompleteSprint"
      @cancel="showCompleteModal = false"
    >
      <!-- 无未完成工单 -->
      <div v-if="completionPreview && completionPreview.openIssues.length === 0" class="complete-no-issues">
        <div class="complete-icon">✅</div>
        <p class="complete-desc">该迭代中所有工单已完成，确认关闭迭代？</p>
      </div>

      <!-- 有未完成工单 -->
      <div v-else-if="completionPreview" class="complete-with-issues">
        <div class="complete-warning">
          <span class="warning-icon">⚠️</span>
          <span>该迭代中仍有 <strong>{{ completionPreview.openIssues.length }}</strong> 个未完成工单</span>
        </div>

        <!-- 未完成工单列表 -->
        <div class="open-issues-list">
          <div
            v-for="issue in completionPreview.openIssues"
            :key="issue.id"
            class="open-issue-item"
          >
            <span class="issue-status-dot" :style="{ background: issue.statusColor || '#6b7280' }"></span>
            <span class="issue-key">{{ issue.issueKey }}</span>
            <span class="issue-title">{{ issue.title }}</span>
            <span class="issue-assignee" v-if="issue.assigneeName">{{ issue.assigneeName }}</span>
          </div>
        </div>

        <!-- 处理方式选择 -->
        <div class="move-option-section">
          <p class="move-option-label">请选择未完成工单的处理方式：</p>
          <a-radio-group v-model="moveOption" direction="vertical">
            <a-radio value="backlog">
              <span class="radio-label">移回 Backlog</span>
              <span class="radio-desc">清空工单的迭代归属，回到待规划状态</span>
            </a-radio>
            <a-radio value="next_sprint" :disabled="completionPreview.targetSprints.length === 0">
              <span class="radio-label">移入其他迭代</span>
              <span class="radio-desc" v-if="completionPreview.targetSprints.length > 0">
                将未完成工单转移到指定的迭代中
              </span>
              <span class="radio-desc disabled" v-else>
                当前项目没有其他可用迭代
              </span>
            </a-radio>
          </a-radio-group>

          <!-- 目标 Sprint 选择 -->
          <div v-if="moveOption === 'next_sprint' && completionPreview.targetSprints.length > 0" class="target-sprint-select">
            <a-select v-model="targetSprintId" placeholder="选择目标迭代" style="width: 100%">
              <a-option
                v-for="target in completionPreview.targetSprints"
                :key="target.id"
                :value="target.id"
              >
                {{ target.name }}
                <span class="target-status-tag">{{ target.status === 'active' ? '进行中' : '计划中' }}</span>
              </a-option>
            </a-select>
          </div>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-else class="complete-loading">
        <a-spin :size="24" />
        <p style="margin-top: 12px; color: var(--color-text-3);">正在获取工单信息…</p>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { sprintApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import { usePermission } from '@/composables/usePermission'
import { useProjectList } from '@/composables/useProjectList'
import type { SprintVO, CompletionPreviewVO } from '@/api/types'
import SprintBurndownChart from './SprintBurndownChart.vue'

const router = useRouter()
const projectStore = useProjectStore()

const selectedProject = computed({
  get: () => projectStore.selectedProjectId,
  set: (val) => projectStore.selectProject(val)
})

// 权限控制（必须在 selectedProject 定义之后）
const { canCreateSprint, canEditSprint, canDeleteSprint } = usePermission(() => selectedProject.value)
const { projects, projectLoadState, loadProjects } = useProjectList()
const sprints = ref<SprintVO[]>([])
const showCreate = ref(false)
const creating = ref(false)
const expandedCompletedSprints = ref<Set<string>>(new Set())

// ===== 完成迭代相关 =====
const showCompleteModal = ref(false)
const completing = ref(false)
const completingSprintId = ref<string>('')
const completingSprintName = ref<string>('')
const completionPreview = ref<CompletionPreviewVO | null>(null)
const moveOption = ref<string>('backlog')
const targetSprintId = ref<string>('')

/**
 * 加载状态机：
 * - idle: 未加载（未选择项目）
 * - loading: 加载中
 * - success: 加载成功（可能数据为空）
 * - error: 网络/服务端错误
 * - forbidden: 权限不足（403）
 */
type LoadingState = 'idle' | 'loading' | 'success' | 'error' | 'forbidden'
const loadingState = ref<LoadingState>('idle')

const createForm = reactive({
  name: '',
  goal: '',
  startDate: '',
  endDate: ''
})

const activeSprints = computed(() => sprints.value.filter(s => s.status === 'active' || s.status === 'Active'))
const plannedSprints = computed(() => sprints.value.filter(s => s.status === 'planned' || s.status === 'Planned'))
const completedSprints = computed(() => sprints.value.filter(s => s.status === 'completed' || s.status === 'Completed'))

// ===== 工具函数 =====

function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function getRemainingDays(sprint: SprintVO): number | null {
  if (!sprint.endDate) return null
  const end = new Date(sprint.endDate)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  end.setHours(0, 0, 0, 0)
  return Math.ceil((end.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
}

function getProgressPercent(sprint: SprintVO, type: 'done' | 'inProgress' | 'todo'): number {
  if (sprint.totalIssues === 0) return 0
  const map = {
    done: sprint.doneIssues,
    inProgress: sprint.inProgressIssues,
    todo: sprint.todoIssues
  }
  return (map[type] / sprint.totalIssues) * 100
}

function getCompletionPercent(sprint: SprintVO): number {
  if (sprint.totalIssues === 0) return 0
  return Math.round((sprint.doneIssues / sprint.totalIssues) * 100)
}

function viewSprintIssues(sprint: SprintVO) {
  // 跳转到 Issue 列表，按 Sprint 筛选
  router.push({ path: '/', query: { sprint: sprint.id, label: sprint.name } })
}

function toggleCompletedBurndown(sprintId: string) {
  const set = new Set(expandedCompletedSprints.value)
  if (set.has(sprintId)) {
    set.delete(sprintId)
  } else {
    set.add(sprintId)
  }
  expandedCompletedSprints.value = set
}

// ===== API 调用 =====

async function loadSprints() {
  if (!selectedProject.value) { sprints.value = []; loadingState.value = 'idle'; return }
  loadingState.value = 'loading'
  try {
    const res = await sprintApi.listByProject(selectedProject.value, { _silent403: true })
    sprints.value = res.data || []
    loadingState.value = 'success'
  } catch (e: any) {
    sprints.value = []
    if (e?.response?.status === 403) {
      loadingState.value = 'forbidden'
    } else {
      loadingState.value = 'error'
    }
  }
}

async function activateSprint(id: string) {
  try {
    await sprintApi.activate(id)
    Message.success('迭代已开始')
    loadSprints()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleCompleteSprint(sprint: SprintVO) {
  completingSprintId.value = sprint.id
  completingSprintName.value = sprint.name
  completionPreview.value = null
  moveOption.value = 'backlog'
  targetSprintId.value = ''
  showCompleteModal.value = true

  try {
    const res = await sprintApi.completionPreview(sprint.id)
    completionPreview.value = res.data

    // 如果有未完成工单且有可迁移目标，默认选中第一个
    if (res.data.openIssues.length > 0 && res.data.targetSprints.length > 0) {
      targetSprintId.value = res.data.targetSprints[0].id
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '获取预览信息失败')
    showCompleteModal.value = false
  }
}

async function confirmCompleteSprint() {
  if (!completionPreview.value) return

  const hasOpenIssues = completionPreview.value.openIssues.length > 0

  // 有未完成工单时需要验证选项
  if (hasOpenIssues) {
    if (moveOption.value === 'next_sprint' && !targetSprintId.value) {
      Message.warning('请选择目标迭代')
      return
    }
  }

  completing.value = true
  try {
    const body = hasOpenIssues
      ? { moveOption: moveOption.value, targetSprintId: moveOption.value === 'next_sprint' ? targetSprintId.value : undefined }
      : undefined

    await sprintApi.complete(completingSprintId.value, body)
    Message.success('迭代已完成')
    showCompleteModal.value = false
    loadSprints()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    completing.value = false
  }
}

async function deleteSprint(id: string) {
  try {
    await sprintApi.delete(id)
    Message.success('迭代已删除')
    loadSprints()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  }
}

async function handleCreate() {
  if (!createForm.name.trim()) {
    Message.warning('请输入迭代名称')
    return
  }
  creating.value = true
  try {
    await sprintApi.create(selectedProject.value!, {
      name: createForm.name.trim(),
      goal: createForm.goal || undefined,
      startDate: createForm.startDate || undefined,
      endDate: createForm.endDate || undefined
    })
    Message.success('迭代创建成功')
    showCreate.value = false
    createForm.name = ''
    createForm.goal = ''
    createForm.startDate = ''
    createForm.endDate = ''
    loadSprints()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

onMounted(async () => {
  await loadProjects()

  // 如果已有选中的项目（从 Store 恢复或自动选择），自动加载迭代
  if (selectedProject.value) {
    loadSprints()
  }
})
</script>

<style scoped>
.sprint-page {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-1);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sprint-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sprint-card {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 16px 20px;
  transition: border-color 0.15s;
}
.sprint-card:hover {
  border-color: rgb(var(--primary-6));
}
.sprint-card.active {
  border-left: 3px solid rgb(var(--primary-6));
}
.sprint-card.completed {
  opacity: 0.7;
}

.sprint-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sprint-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sprint-status-badge {
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 3px;
  font-weight: 500;
  letter-spacing: 0.2px;
  flex-shrink: 0;
}
.sprint-status-badge.active { background: rgba(var(--primary-6), 0.1); color: rgb(var(--primary-6)); }
.sprint-status-badge.planned { background: rgba(var(--warning-6), 0.1); color: rgb(var(--warning-6)); }
.sprint-status-badge.completed { background: var(--color-fill-2); color: var(--color-text-3); }

.sprint-name {
  font-size: 14px;
  color: var(--color-text-1);
  font-weight: 500;
}

.sprint-remaining {
  font-size: 12px;
  color: var(--color-text-2);
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: var(--color-fill-1);
  border-radius: 3px;
}
.sprint-remaining .remaining-icon.warning {
  color: rgb(var(--warning-6));
}
.sprint-remaining .remaining-icon.overdue {
  color: rgb(var(--danger-6));
}

.sprint-dates {
  font-size: 12px;
  color: var(--color-text-3);
}

/* ===== 进度区域 ===== */
.sprint-progress-section {
  margin-top: 12px;
  padding: 12px 0;
}

.progress-bar-container {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: var(--color-fill-2);
  border-radius: 3px;
  overflow: hidden;
  display: flex;
}

.progress-segment {
  height: 100%;
  transition: width 0.3s ease;
}
.progress-segment.done {
  background: #3fb950;
}
.progress-segment.in-progress {
  background: #58a6ff;
}
.progress-segment.todo {
  background: var(--color-fill-3);
}

.progress-percent {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-1);
  min-width: 36px;
  text-align: right;
}

.progress-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.stat-item {
  font-size: 12px;
  color: var(--color-text-2);
  display: flex;
  align-items: center;
  gap: 4px;
}
.stat-item.total {
  color: var(--color-text-3);
  margin-left: auto;
}
.stat-item.overdue {
  color: rgb(var(--danger-6));
  font-weight: 500;
}

.stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.stat-item.done .stat-dot { background: #3fb950; }
.stat-item.in-progress .stat-dot { background: #58a6ff; }
.stat-item.todo .stat-dot { background: var(--color-fill-3); }
.stat-item.overdue .stat-dot { background: rgb(var(--danger-6)); }

/* ===== 其他 ===== */
.sprint-no-issues {
  margin-top: 12px;
  padding: 8px 0;
}
.no-issues-text {
  font-size: 12px;
  color: var(--color-text-3);
  font-style: italic;
}

.sprint-goal {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-text-2);
  line-height: 1.5;
}

.sprint-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-1);
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 13px;
  color: var(--color-text-3);
  margin-bottom: 16px;
}

/* ===== 完成迭代弹窗 ===== */
.complete-no-issues {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 0;
  text-align: center;
}
.complete-icon {
  font-size: 36px;
  margin-bottom: 12px;
}
.complete-desc {
  font-size: 14px;
  color: var(--color-text-2);
}

.complete-with-issues {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.complete-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: rgba(var(--warning-6), 0.08);
  border-radius: 6px;
  font-size: 13px;
  color: var(--color-text-1);
}
.warning-icon {
  font-size: 16px;
}

.open-issues-list {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: 6px;
}
.open-issue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 12px;
  border-bottom: 1px solid var(--color-border);
}
.open-issue-item:last-child {
  border-bottom: none;
}
.issue-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.issue-key {
  color: var(--color-text-3);
  font-family: monospace;
  font-size: 11px;
  flex-shrink: 0;
}
.issue-title {
  color: var(--color-text-1);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.issue-assignee {
  color: var(--color-text-3);
  font-size: 11px;
  flex-shrink: 0;
}

.move-option-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.move-option-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
  margin: 0;
}
.radio-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}
.radio-desc {
  display: block;
  font-size: 12px;
  color: var(--color-text-3);
  margin-top: 2px;
}
.radio-desc.disabled {
  color: var(--color-text-4);
  font-style: italic;
}
.target-sprint-select {
  margin-top: 8px;
  margin-left: 24px;
}
.target-status-tag {
  font-size: 11px;
  color: var(--color-text-3);
  margin-left: 8px;
}

.complete-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 0;
}
</style>
