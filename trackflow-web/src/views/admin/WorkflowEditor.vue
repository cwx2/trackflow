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
          <a-option value="Bug">缺陷</a-option>
          <a-option value="Task">任务</a-option>
          <a-option value="Feature">需求</a-option>
        </a-select>

        <a-select
          v-model="selectedRole"
          placeholder="角色"
          style="width: 140px"
          @change="onFilterChange"
        >
          <a-option value="2">项目管理员</a-option>
          <a-option value="3">开发人员</a-option>
          <a-option value="4">测试人员</a-option>
          <a-option value="5">观察者</a-option>
        </a-select>

        <a-button type="primary" :loading="saving" @click="saveMatrix">
          保存工作流
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
                {{ status.name }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="fromStatus in statuses" :key="fromStatus.id">
              <td class="row-header">
                <span class="status-dot" :style="{ background: fromStatus.color }"></span>
                {{ fromStatus.name }}
              </td>
              <td
                v-for="toStatus in statuses"
                :key="toStatus.id"
                class="matrix-cell"
                :class="{ disabled: fromStatus.id === toStatus.id }"
              >
                <input
                  v-if="fromStatus.id !== toStatus.id"
                  type="checkbox"
                  :checked="isAllowed(fromStatus.id, toStatus.id)"
                  @change="toggleTransition(fromStatus.id, toStatus.id)"
                  class="matrix-checkbox"
                />
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
      共 {{ statuses.length }} 个状态。
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { issueApi, projectApi, workflowApi } from '@/api'
import type { IssueStatusVO, ProjectVO } from '@/api/types'

const selectedProject = ref('0')
const selectedType = ref('*')
const selectedRole = ref('3')
const loading = ref(false)
const saving = ref(false)

const statuses = ref<IssueStatusVO[]>([])
const projects = ref<ProjectVO[]>([])

// 转换矩阵 Set: "fromId-toId"
const allowedTransitions = reactive(new Set<string>())

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

function onFilterChange() {
  loadMatrix()
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

async function loadMatrix() {
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
  await Promise.all([loadStatuses(), loadProjects()])
  await loadMatrix()
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
