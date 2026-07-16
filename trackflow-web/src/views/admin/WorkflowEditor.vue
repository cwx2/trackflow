<template>
  <div class="workflow-page">
    <div class="page-header">
      <h2 class="page-title">工作流编辑器</h2>
      <div class="header-filters">
        <a-select
          v-model="selectedProject"
          placeholder="选择项目"
          style="width: 180px"
          @change="onFilterChange"
        >
          <a-option value="0">全局（默认）</a-option>
          <a-option
            v-for="p in projects"
            :key="p.id"
            :value="p.id"
          >{{ p.name }}</a-option>
        </a-select>

        <a-select
          v-model="selectedType"
          placeholder="工单类型"
          style="width: 140px"
          @change="onFilterChange"
        >
          <a-option value="*">所有类型</a-option>
          <a-option
            v-for="t in issueTypes"
            :key="t"
            :value="t"
          >{{ t }}</a-option>
        </a-select>

        <a-select
          v-model="selectedRole"
          placeholder="角色"
          style="width: 160px"
          @change="onFilterChange"
        >
          <a-option
            v-for="role in roles"
            :key="role.id"
            :value="role.id"
          >{{ role.name }}</a-option>
        </a-select>

        <a-button type="primary" :loading="saving" @click="saveMatrix">
          保存工作流
        </a-button>
        <a-button @click="showHistory = true">
          <template #icon><icon-history /></template>
          变更历史
        </a-button>
      </div>
    </div>

    <a-spin :loading="loading" tip="加载中...">
      <!-- 转换矩阵 -->
      <div class="matrix-container" v-if="statuses.length > 0">
        <table class="matrix-table">
          <thead>
            <tr>
              <th class="corner-cell">从 ↓ / 到 →</th>
              <th v-for="status in statuses" :key="status.id" class="col-header">
                <span class="status-dot" :style="{ background: status.color }"></span>
                {{ localizeStatusName(status.name) }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="fromStatus in statuses" :key="fromStatus.id">
              <td class="row-header">
                <span class="status-dot" :style="{ background: fromStatus.color }"></span>
                {{ localizeStatusName(fromStatus.name) }}
              </td>
              <td
                v-for="toStatus in statuses"
                :key="toStatus.id"
                class="matrix-cell"
                :class="{
                  disabled: fromStatus.id === toStatus.id,
                  clickable: fromStatus.id !== toStatus.id && isAllowed(fromStatus.id, toStatus.id)
                }"
                @click="fromStatus.id !== toStatus.id && isAllowed(fromStatus.id, toStatus.id) && openActionPanel(fromStatus, toStatus)"
              >
                <div v-if="fromStatus.id !== toStatus.id" class="cell-content">
                  <input
                    type="checkbox"
                    :checked="isAllowed(fromStatus.id, toStatus.id)"
                    @change="toggleTransition(fromStatus.id, toStatus.id)"
                    @click.stop
                    class="matrix-checkbox"
                  />
                  <span
                    v-if="hasAction(fromStatus.id, toStatus.id)"
                    class="action-dot"
                    title="已配置动作"
                  ></span>
                </div>
                <span v-else class="cell-dash">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 空状态 -->
      <div v-else-if="!loading" class="empty-state">
        <icon-settings :size="48" />
        <h3>暂无状态数据</h3>
        <p>系统中未定义任何工单状态，请先配置状态列表。</p>
      </div>
    </a-spin>

    <div class="help-text" v-if="statuses.length > 0">
      <icon-info-circle /> 勾选单元格表示允许从行状态转换到列状态（针对当前选择的角色）。
      共 {{ statuses.length }} 个状态。点击已允许的转换可配置自动化动作。
    </div>

    <!-- 动作配置面板 -->
    <TransitionActionPanel
      v-model:visible="actionPanelVisible"
      :project-id="selectedProject"
      :old-status-id="actionPanelFrom"
      :new-status-id="actionPanelTo"
      :old-status-name="actionPanelFromName"
      :new-status-name="actionPanelToName"
      :issue-type="selectedType"
      @refresh="onActionRefresh"
    />

    <!-- 变更历史抽屉 -->
    <WorkflowActivityDrawer
      v-model:visible="showHistory"
      :project-id="selectedProject"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { IconSettings, IconInfoCircle, IconHistory } from '@arco-design/web-vue/es/icon'
import { issueApi, projectApi, workflowApi, transitionActionApi } from '@/api'
import type { IssueStatusVO, ProjectVO, RoleVO } from '@/api/types'
import TransitionActionPanel from './TransitionActionPanel.vue'
import WorkflowActivityDrawer from './WorkflowActivityDrawer.vue'
import { localizeStatusName } from '@/utils/fieldLabels'

const selectedProject = ref('0')
const selectedType = ref('*')
const selectedRole = ref('')
const loading = ref(false)
const saving = ref(false)
const showHistory = ref(false)

const statuses = ref<IssueStatusVO[]>([])
const projects = ref<ProjectVO[]>([])
const roles = ref<RoleVO[]>([])
const issueTypes = ref<string[]>([])

// 转换矩阵 Set: "fromId-toId"
const allowedTransitions = reactive(new Set<string>())

// 动作指示器: "fromId-toId" 有配置动作的转换路径
const actionPaths = reactive(new Set<string>())

// 动作面板状态
const actionPanelVisible = ref(false)
const actionPanelFrom = ref('')
const actionPanelTo = ref('')
const actionPanelFromName = ref('')
const actionPanelToName = ref('')

function isAllowed(from: string, to: string) {
  return allowedTransitions.has(`${from}-${to}`)
}

function toggleTransition(from: string, to: string) {
  const key = `${from}-${to}`
  if (allowedTransitions.has(key)) {
    allowedTransitions.delete(key)
  } else {
    allowedTransitions.add(key)
  }
}

function hasAction(from: string, to: string): boolean {
  return actionPaths.has(`${from}-${to}`)
}

function openActionPanel(fromStatus: IssueStatusVO, toStatus: IssueStatusVO) {
  if (!isAllowed(fromStatus.id, toStatus.id)) return
  actionPanelFrom.value = fromStatus.id
  actionPanelTo.value = toStatus.id
  actionPanelFromName.value = localizeStatusName(fromStatus.name)
  actionPanelToName.value = localizeStatusName(toStatus.name)
  actionPanelVisible.value = true
}

async function loadActionPaths() {
  try {
    const projectId = selectedProject.value || '0'
    const res = await transitionActionApi.list(projectId)
    const actions = res.data || []
    actionPaths.clear()
    for (const a of actions) {
      actionPaths.add(`${a.oldStatusId}-${a.newStatusId}`)
    }
  } catch {
    actionPaths.clear()
  }
}

function onActionRefresh() {
  loadActionPaths()
}

function onFilterChange() {
  loadMatrix()
  loadActionPaths()
}

async function loadStatuses() {
  try {
    const res = await issueApi.listStatuses()
    statuses.value = res.data || []
  } catch {
    statuses.value = []
    Message.error('加载状态列表失败')
  }
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ page: 1, pageSize: 100 })
    projects.value = res.data?.list || []
  } catch {
    projects.value = []
    Message.error('加载项目列表失败')
  }
}

async function loadRoles() {
  try {
    const res = await workflowApi.listProjectRoles()
    roles.value = res.data || []
    // 默认选中第一个角色
    if (roles.value.length > 0 && !selectedRole.value) {
      selectedRole.value = roles.value[0].id
    }
  } catch {
    roles.value = []
    Message.error('加载角色列表失败')
  }
}

async function loadIssueTypes() {
  try {
    const res = await workflowApi.listIssueTypes()
    issueTypes.value = res.data || []
  } catch {
    issueTypes.value = ['Bug', 'Task', 'Feature']
  }
}

async function loadMatrix() {
  if (!selectedRole.value) return
  loading.value = true
  try {
    const params: Record<string, string> = { roleId: selectedRole.value }
    if (selectedType.value !== '*') params.issueType = selectedType.value
    const projectId = selectedProject.value || '0'

    const res = await workflowApi.getTransitionMatrix(projectId, params)
    const transitions = res.data || []

    allowedTransitions.clear()
    for (const t of transitions) {
      allowedTransitions.add(`${t.oldStatusId}-${t.newStatusId}`)
    }
  } catch {
    allowedTransitions.clear()
    Message.error('加载工作流数据失败')
  } finally {
    loading.value = false
  }
}

async function saveMatrix() {
  Modal.confirm({
    title: '确认更新工作流',
    content: '此操作将替换当前筛选条件下的所有转换规则，确认保存？',
    okText: '确认保存',
    cancelText: '取消',
    async onOk() {
      const transitions = Array.from(allowedTransitions).map(key => {
        const [from, to] = key.split('-')
        return { from: Number(from), to: Number(to), allowed: true }
      })

      saving.value = true
      try {
        const projectId = selectedProject.value || '0'
        await workflowApi.updateTransitionMatrix(projectId, {
          issueType: selectedType.value,
          roleId: Number(selectedRole.value),
          transitions
        })
        Message.success('工作流已保存')
      } catch {
        Message.error('保存失败，请检查权限或重试')
      } finally {
        saving.value = false
      }
    }
  })
}

onMounted(async () => {
  await Promise.all([loadStatuses(), loadProjects(), loadRoles(), loadIssueTypes()])
  await Promise.all([loadMatrix(), loadActionPaths()])
})
</script>

<style scoped>
.workflow-page {
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
  color: var(--text-bright);
}

.header-filters {
  display: flex;
  gap: 8px;
  align-items: center;
}

.matrix-container {
  overflow-x: auto;
}

.matrix-table {
  border-collapse: collapse;
  width: 100%;
  min-width: max-content;
}

.matrix-table th,
.matrix-table td {
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  text-align: center;
  font-size: var(--font-size-sm);
}

.corner-cell {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-weight: 500;
  text-align: left;
  min-width: 160px;
  position: sticky;
  left: 0;
  z-index: 2;
}

.col-header {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-weight: 500;
  white-space: nowrap;
  font-size: 11px;
}

.row-header {
  background: var(--bg-secondary);
  color: var(--text-primary);
  text-align: left;
  font-weight: 500;
  white-space: nowrap;
  position: sticky;
  left: 0;
  z-index: 1;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}

.matrix-cell {
  background: var(--bg-primary);
}
.matrix-cell.disabled {
  background: var(--bg-tertiary);
}
.matrix-cell.clickable {
  cursor: pointer;
}
.matrix-cell.clickable:hover {
  background: var(--bg-tertiary, rgba(255, 255, 255, 0.04));
}

.cell-content {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.action-dot {
  position: absolute;
  top: -4px;
  right: -8px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent-blue, rgb(var(--arcoblue-6)));
  box-shadow: 0 0 0 2px var(--bg-primary, var(--color-bg-2));
}

.matrix-checkbox {
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: var(--accent-blue);
}

.cell-dash {
  color: var(--text-muted);
}

.help-text {
  margin-top: 16px;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  color: var(--text-muted);
}

.empty-state h3 {
  margin-top: 16px;
  font-size: 16px;
  font-weight: 500;
  color: var(--text-secondary);
}

.empty-state p {
  margin-top: 8px;
  font-size: 13px;
}
</style>
