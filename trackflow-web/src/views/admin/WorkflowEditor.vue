<template>
  <div class="workflow-page">
    <div class="page-header">
      <h2 class="page-title">工作流编辑器</h2>
      <div class="header-filters">
        <select v-model="selectedProject" class="filter-select">
          <option value="">全局（默认）</option>
        </select>
        <select v-model="selectedType" class="filter-select">
          <option value="*">所有类型</option>
          <option value="Bug">缺陷</option>
          <option value="Task">任务</option>
          <option value="Feature">需求</option>
        </select>
        <select v-model="selectedRole" class="filter-select">
          <option value="2">项目管理员</option>
          <option value="3">开发人员</option>
          <option value="4">测试人员</option>
          <option value="5">观察者</option>
        </select>
        <button class="btn-save" @click="saveMatrix">保存</button>
      </div>
    </div>

    <!-- 转换矩阵 -->
    <div class="matrix-container">
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

    <div class="help-text">
      勾选单元格表示允许从行状态转换到列状态（针对当前选择的角色）。
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import request from '@/api/request'

const selectedProject = ref('')
const selectedType = ref('*')
const selectedRole = ref('3')

const statuses = ref([
  { id: 1, name: '待处理', color: '#4CAF50' },
  { id: 2, name: '进行中', color: '#2196F3' },
  { id: 3, name: '代码审查', color: '#9C27B0' },
  { id: 4, name: '测试中', color: '#FF9800' },
  { id: 5, name: '已完成', color: '#607D8B' },
  { id: 6, name: '已取消', color: '#9E9E9E' },
  { id: 7, name: '已重开', color: '#F44336' }
])

// 转换矩阵 Set: "fromId-toId"
const allowedTransitions = reactive(new Set<string>())

function isAllowed(from: number, to: number) {
  return allowedTransitions.has(`${from}-${to}`)
}

function toggleTransition(from: number, to: number) {
  const key = `${from}-${to}`
  if (allowedTransitions.has(key)) {
    allowedTransitions.delete(key)
  } else {
    allowedTransitions.add(key)
  }
}

async function loadMatrix() {
  try {
    const params: any = { roleId: selectedRole.value }
    if (selectedType.value !== '*') params.issueType = selectedType.value
    const projectId = selectedProject.value || '0'

    const res: any = await request.get(`/projects/${projectId}/workflows`, { params })
    const transitions = res.data || []

    allowedTransitions.clear()
    for (const t of transitions) {
      allowedTransitions.add(`${t.oldStatusId}-${t.newStatusId}`)
    }
  } catch (e) {
    // 加载默认
    allowedTransitions.clear()
  }
}

async function saveMatrix() {
  const transitions = Array.from(allowedTransitions).map(key => {
    const [from, to] = key.split('-')
    return { from: Number(from), to: Number(to), allowed: true }
  })

  try {
    const projectId = selectedProject.value || '0'
    await request.put(`/projects/${projectId}/workflows`, {
      issueType: selectedType.value,
      roleId: Number(selectedRole.value),
      transitions
    })
    alert('工作流已保存！')
  } catch (e) {
    alert('保存失败')
  }
}

onMounted(loadMatrix)
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
}

.filter-select {
  height: 32px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 0 10px;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
}

.btn-save {
  height: 32px;
  padding: 0 16px;
  background: var(--accent-blue);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
}

.matrix-container {
  overflow-x: auto;
}

.matrix-table {
  border-collapse: collapse;
  width: 100%;
}

.matrix-table th,
.matrix-table td {
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  text-align: center;
  font-size: var(--font-size-sm);
}

.corner-cell {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-weight: 500;
  text-align: left;
  min-width: 140px;
}

.col-header {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-weight: 500;
  white-space: nowrap;
}

.row-header {
  background: var(--bg-secondary);
  color: var(--text-primary);
  text-align: left;
  font-weight: 500;
  white-space: nowrap;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
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
}
</style>
