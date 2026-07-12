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
          @change="loadSprints"
        >
          <a-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.key }} - {{ p.name }}
          </a-option>
        </a-select>
        <a-button type="primary" size="small" :disabled="!selectedProject" @click="showCreate = true">
          + 新建迭代
        </a-button>
      </div>
    </div>

    <!-- Sprint 列表 -->
    <div class="sprint-list" v-if="sprints.length > 0">
      <!-- Active Sprint -->
      <div v-for="sprint in activeSprints" :key="sprint.id" class="sprint-card active">
        <div class="sprint-header">
          <div class="sprint-info">
            <span class="sprint-status-badge active">进行中</span>
            <h3 class="sprint-name">{{ sprint.name }}</h3>
          </div>
          <div class="sprint-dates">
            {{ sprint.startDate }} — {{ sprint.endDate }}
          </div>
        </div>
        <p class="sprint-goal" v-if="sprint.goal">{{ sprint.goal }}</p>
        <div class="sprint-actions">
          <a-button size="mini" @click="completeSprint(sprint.id)">完成迭代</a-button>
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
            {{ sprint.startDate }} — {{ sprint.endDate }}
          </div>
        </div>
        <p class="sprint-goal" v-if="sprint.goal">{{ sprint.goal }}</p>
        <div class="sprint-actions">
          <a-button type="primary" size="mini" @click="activateSprint(sprint.id)">开始迭代</a-button>
          <a-popconfirm content="确定删除此迭代？" @ok="deleteSprint(sprint.id)">
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
            {{ sprint.startDate }} — {{ sprint.endDate }}
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty-state">
      <div class="empty-icon">🏃</div>
      <h3 class="empty-title">{{ selectedProject ? '暂无迭代' : '请选择项目' }}</h3>
      <p class="empty-desc">
        {{ selectedProject ? '点击"+ 新建迭代"创建第一个 Sprint' : '从上方下拉框选择项目查看迭代' }}
      </p>
      <a-button v-if="selectedProject" type="primary" size="small" @click="showCreate = true">
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { Message } from '@arco-design/web-vue'
import { projectApi, sprintApi } from '@/api'

const selectedProject = ref<string | undefined>(undefined)
const projects = ref<any[]>([])
const sprints = ref<any[]>([])
const showCreate = ref(false)
const creating = ref(false)

const createForm = reactive({
  name: '',
  goal: '',
  startDate: '',
  endDate: ''
})

const activeSprints = computed(() => sprints.value.filter(s => s.status === 'active' || s.status === 'Active'))
const plannedSprints = computed(() => sprints.value.filter(s => s.status === 'planned' || s.status === 'Planned'))
const completedSprints = computed(() => sprints.value.filter(s => s.status === 'completed' || s.status === 'Completed'))

async function loadProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    projects.value = res.data?.list || []
  } catch {
    projects.value = []
  }
}

async function loadSprints() {
  if (!selectedProject.value) { sprints.value = []; return }
  try {
    const res = await sprintApi.listByProject(selectedProject.value)
    sprints.value = res.data || []
  } catch {
    sprints.value = []
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

async function completeSprint(id: string) {
  try {
    await sprintApi.complete(id)
    Message.success('迭代已完成')
    loadSprints()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
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

onMounted(() => {
  loadProjects()
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
}
.sprint-status-badge.active { background: rgba(var(--primary-6), 0.1); color: rgb(var(--primary-6)); }
.sprint-status-badge.planned { background: rgba(var(--warning-6), 0.1); color: rgb(var(--warning-6)); }
.sprint-status-badge.completed { background: var(--color-fill-2); color: var(--color-text-3); }

.sprint-name {
  font-size: 14px;
  color: var(--color-text-1);
  font-weight: 500;
}

.sprint-dates {
  font-size: 12px;
  color: var(--color-text-3);
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
</style>
