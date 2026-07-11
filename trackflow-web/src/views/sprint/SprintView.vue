<template>
  <div class="sprint-page">
    <div class="page-header">
      <h2 class="page-title">迭代管理</h2>
      <div class="header-actions">
        <select v-model="selectedProject" class="filter-select" @change="loadSprints">
          <option value="">选择项目</option>
        </select>
        <button class="btn-create" @click="showCreate = true">+ 新建迭代</button>
      </div>
    </div>

    <!-- Sprint 列表 -->
    <div class="sprint-list">
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
          <button class="btn-sm" @click="completeSprint(sprint.id)">完成迭代</button>
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
          <button class="btn-sm primary" @click="activateSprint(sprint.id)">开始迭代</button>
          <button class="btn-sm" @click="deleteSprint(sprint.id)">删除</button>
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

      <div v-if="sprints.length === 0" class="empty-state">
        暂无迭代，请先选择项目并创建第一个迭代。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { sprintApi } from '@/api/index'

const selectedProject = ref('')
const sprints = ref<any[]>([])
const showCreate = ref(false)

const activeSprints = computed(() => sprints.value.filter(s => s.status === 'active'))
const plannedSprints = computed(() => sprints.value.filter(s => s.status === 'planned'))
const completedSprints = computed(() => sprints.value.filter(s => s.status === 'completed'))

async function loadSprints() {
  if (!selectedProject.value) { sprints.value = []; return }
  try {
    const res = await sprintApi.listByProject(selectedProject.value)
    sprints.value = res.data || []
  } catch (e) { sprints.value = [] }
}

async function activateSprint(id: string) {
  await sprintApi.activate(id)
  loadSprints()
}

async function completeSprint(id: string) {
  await sprintApi.complete(id)
  loadSprints()
}

async function deleteSprint(id: string) {
  await sprintApi.delete(id)
  loadSprints()
}

onMounted(loadSprints)
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
  color: var(--tf-text-primary);
}

.header-actions {
  display: flex;
  gap: 8px;
}

.filter-select {
  height: 32px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  padding: 0 12px;
  color: var(--tf-text-primary);
  font-size: 12px;
  transition: border-color 0.15s;
}
.filter-select:hover {
  border-color: var(--tf-accent);
}
.filter-select:focus {
  outline: none;
  border-color: var(--tf-accent);
}

.btn-create {
  height: 32px;
  padding: 0 16px;
  background: var(--tf-accent);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}
.btn-create:hover {
  background: var(--tf-accent-hover);
}

.sprint-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sprint-card {
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  padding: 16px 20px;
  transition: border-color 0.15s;
}
.sprint-card:hover {
  border-color: var(--tf-accent);
}
.sprint-card.active {
  border-left: 3px solid var(--tf-accent);
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
.sprint-status-badge.active { background: var(--tf-accent-bg); color: var(--tf-accent); }
.sprint-status-badge.planned { background: rgba(210, 153, 34, 0.12); color: var(--tf-warning); }
.sprint-status-badge.completed { background: rgba(107, 114, 128, 0.12); color: var(--tf-text-tertiary); }

.sprint-name {
  font-size: 14px;
  color: var(--tf-text-primary);
  font-weight: 500;
}

.sprint-dates {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.sprint-goal {
  margin-top: 8px;
  font-size: 13px;
  color: var(--tf-text-secondary);
  line-height: 1.5;
}

.sprint-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.btn-sm {
  height: 28px;
  padding: 0 12px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  color: var(--tf-text-primary);
  font-size: 11px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.btn-sm:hover {
  background: var(--tf-bg-hover);
  border-color: var(--tf-text-tertiary);
}
.btn-sm.primary {
  background: var(--tf-accent);
  color: #fff;
  border: none;
}
.btn-sm.primary:hover {
  background: var(--tf-accent-hover);
}

.empty-state {
  text-align: center;
  padding: 48px;
  color: var(--tf-text-tertiary);
  font-size: 13px;
}
</style>
