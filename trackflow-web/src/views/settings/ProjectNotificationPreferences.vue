<template>
  <div class="project-preferences-section">
    <div class="section-header">
      <div class="section-header-text">
        <h3 class="section-title">项目级偏好覆盖</h3>
        <p class="section-desc">为特定项目设置不同的通知偏好。未配置的项目将使用上面的全局设置。</p>
      </div>
      <button
        v-if="!showAddPanel"
        class="add-project-btn"
        @click="showAddPanel = true"
      >
        <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
          <path d="M7.75 2a.75.75 0 0 1 .75.75V7h4.25a.75.75 0 0 1 0 1.5H8.5v4.25a.75.75 0 0 1-1.5 0V8.5H2.75a.75.75 0 0 1 0-1.5H7V2.75A.75.75 0 0 1 7.75 2Z"/>
        </svg>
        添加项目
      </button>
    </div>

    <!-- 添加项目面板 -->
    <div v-if="showAddPanel" class="add-panel">
      <a-select
        v-model="selectedProjectId"
        placeholder="选择要配置的项目..."
        :loading="projectsLoading"
        allow-search
        size="small"
        style="flex: 1"
      >
        <a-option
          v-for="project in availableProjects"
          :key="project.id"
          :value="project.id"
          :label="project.name"
        >
          <span class="project-option">
            <span class="project-key">{{ project.key }}</span>
            <span class="project-name">{{ project.name }}</span>
          </span>
        </a-option>
      </a-select>
      <button class="panel-btn confirm" :disabled="!selectedProjectId" @click="handleAddProject">
        确定
      </button>
      <button class="panel-btn cancel" @click="showAddPanel = false; selectedProjectId = ''">
        取消
      </button>
    </div>

    <!-- 已配置的项目列表 -->
    <div v-if="loading" class="project-loading">
      <a-spin size="small" />
      <span>加载项目偏好...</span>
    </div>

    <div v-else-if="projectPreferences.length === 0 && !showAddPanel" class="project-empty">
      <span class="empty-icon">🌐</span>
      <span class="empty-text">所有项目均使用全局设置</span>
    </div>

    <div v-else class="project-list">
      <div
        v-for="item in projectPreferences"
        :key="item.pref.id"
        class="project-card"
        :class="{ expanded: expandedProjectId === item.pref.projectId }"
      >
        <div class="project-card-header" @click="toggleExpand(item.pref.projectId!)">
          <div class="project-card-info">
            <span class="project-card-key">{{ item.projectKey }}</span>
            <span class="project-card-name">{{ item.projectName }}</span>
          </div>
          <div class="project-card-actions">
            <button
              class="project-card-action reset"
              title="重置为全局设置"
              @click.stop="handleDeleteProjectPref(item.pref.projectId!)"
            >
              <svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor">
                <path d="M3.72 3.72a.75.75 0 0 1 1.06 0L8 6.94l3.22-3.22a.75.75 0 1 1 1.06 1.06L9.06 8l3.22 3.22a.75.75 0 1 1-1.06 1.06L8 9.06l-3.22 3.22a.75.75 0 0 1-1.06-1.06L6.94 8 3.72 4.78a.75.75 0 0 1 0-1.06Z"/>
              </svg>
            </button>
            <svg
              class="expand-icon"
              :class="{ rotated: expandedProjectId === item.pref.projectId }"
              width="14" height="14" viewBox="0 0 16 16" fill="currentColor"
            >
              <path d="M12.78 5.22a.749.749 0 0 1 0 1.06l-4.25 4.25a.749.749 0 0 1-1.06 0L3.22 6.28a.749.749 0 1 1 1.06-1.06L8 8.94l3.72-3.72a.749.749 0 0 1 1.06 0Z"/>
            </svg>
          </div>
        </div>

        <!-- 展开后的偏好开关 -->
        <div v-if="expandedProjectId === item.pref.projectId" class="project-card-body">
          <div class="event-items">
            <div class="event-item">
              <span class="event-label">工单分配给我</span>
              <a-switch
                :model-value="item.pref.onIssueAssigned"
                size="small"
                @change="(v: boolean | string | number) => handleProjectPrefChange(item, 'onIssueAssigned', v as boolean)"
              />
            </div>
            <div class="event-item">
              <span class="event-label">工单状态变更</span>
              <a-switch
                :model-value="item.pref.onIssueStatusChanged"
                size="small"
                @change="(v: boolean | string | number) => handleProjectPrefChange(item, 'onIssueStatusChanged', v as boolean)"
              />
            </div>
            <div class="event-item">
              <span class="event-label">新评论</span>
              <a-switch
                :model-value="item.pref.onIssueCommented"
                size="small"
                @change="(v: boolean | string | number) => handleProjectPrefChange(item, 'onIssueCommented', v as boolean)"
              />
            </div>
            <div class="event-item">
              <span class="event-label">@提及我</span>
              <a-switch
                :model-value="item.pref.onMentioned"
                size="small"
                @change="(v: boolean | string | number) => handleProjectPrefChange(item, 'onMentioned', v as boolean)"
              />
            </div>
            <div class="event-item">
              <span class="event-label">工单已解决</span>
              <a-switch
                :model-value="item.pref.onIssueResolved"
                size="small"
                @change="(v: boolean | string | number) => handleProjectPrefChange(item, 'onIssueResolved', v as boolean)"
              />
            </div>
            <div class="event-item">
              <span class="event-label">Sprint 启动</span>
              <a-switch
                :model-value="item.pref.onSprintStarted"
                size="small"
                @change="(v: boolean | string | number) => handleProjectPrefChange(item, 'onSprintStarted', v as boolean)"
              />
            </div>
            <div class="event-item">
              <span class="event-label">Sprint 完成</span>
              <a-switch
                :model-value="item.pref.onSprintCompleted"
                size="small"
                @change="(v: boolean | string | number) => handleProjectPrefChange(item, 'onSprintCompleted', v as boolean)"
              />
            </div>
            <div class="event-item">
              <span class="event-label">成员变更</span>
              <a-switch
                :model-value="item.pref.onProjectMemberChanged"
                size="small"
                @change="(v: boolean | string | number) => handleProjectPrefChange(item, 'onProjectMemberChanged', v as boolean)"
              />
            </div>
            <div class="event-item">
              <span class="event-label">项目归档/恢复</span>
              <a-switch
                :model-value="item.pref.onProjectLifecycle"
                size="small"
                @change="(v: boolean | string | number) => handleProjectPrefChange(item, 'onProjectLifecycle', v as boolean)"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { notificationPreferenceApi, projectApi } from '@/api'
import type { NotificationPreferenceVO } from '@/api/notificationPreference'
import type { ProjectVO } from '@/api/types'

interface ProjectPrefItem {
  pref: NotificationPreferenceVO
  projectName: string
  projectKey: string
}

const loading = ref(false)
const projectsLoading = ref(false)
const showAddPanel = ref(false)
const selectedProjectId = ref('')
const expandedProjectId = ref<string | null>(null)

const projectPreferences = ref<ProjectPrefItem[]>([])
const allProjects = ref<ProjectVO[]>([])

/** 可选项目 = 全部项目 - 已配置项目 */
const availableProjects = computed(() => {
  const configuredIds = new Set(projectPreferences.value.map(p => p.pref.projectId))
  return allProjects.value.filter(p => !configuredIds.has(p.id))
})

onMounted(async () => {
  await Promise.all([loadProjectPreferences(), loadProjects()])
})

async function loadProjects() {
  projectsLoading.value = true
  try {
    const res = await projectApi.list({ pageSize: 100 })
    if (res.code === 0 && res.data) {
      allProjects.value = res.data.list || []
    }
  } catch {
    // 静默
  } finally {
    projectsLoading.value = false
  }
}

async function loadProjectPreferences() {
  loading.value = true
  try {
    const res = await notificationPreferenceApi.listProjectPreferences()
    if (res.code === 0 && res.data) {
      // 将 VO 列表映射为带项目信息的结构
      projectPreferences.value = res.data.map(pref => ({
        pref,
        projectName: getProjectName(pref.projectId!),
        projectKey: getProjectKey(pref.projectId!)
      }))
    }
  } catch {
    Message.error('加载项目级偏好失败')
  } finally {
    loading.value = false
  }
}

function getProjectName(projectId: string): string {
  const p = allProjects.value.find(proj => proj.id === projectId)
  return p?.name || '未知项目'
}

function getProjectKey(projectId: string): string {
  const p = allProjects.value.find(proj => proj.id === projectId)
  return p?.key || '???'
}

function toggleExpand(projectId: string) {
  expandedProjectId.value = expandedProjectId.value === projectId ? null : projectId
}

async function handleAddProject() {
  if (!selectedProjectId.value) return
  try {
    // 调用 PUT 创建项目级偏好（使用全局默认值初始化）
    const res = await notificationPreferenceApi.updateProjectPreference(selectedProjectId.value, {})
    if (res.code === 0 && res.data) {
      projectPreferences.value.push({
        pref: res.data,
        projectName: getProjectName(res.data.projectId!),
        projectKey: getProjectKey(res.data.projectId!)
      })
      Message.success('已添加项目级偏好')
      expandedProjectId.value = res.data.projectId
    }
  } catch {
    Message.error('添加失败')
  } finally {
    showAddPanel.value = false
    selectedProjectId.value = ''
  }
}

async function handleDeleteProjectPref(projectId: string) {
  try {
    const res = await notificationPreferenceApi.deleteProjectPreference(projectId)
    if (res.code === 0) {
      projectPreferences.value = projectPreferences.value.filter(p => p.pref.projectId !== projectId)
      if (expandedProjectId.value === projectId) {
        expandedProjectId.value = null
      }
      Message.success('已恢复使用全局设置')
    }
  } catch {
    Message.error('操作失败')
  }
}

let saveTimeout: ReturnType<typeof setTimeout> | null = null

function handleProjectPrefChange(item: ProjectPrefItem, field: string, value: boolean) {
  // 乐观更新
  ;(item.pref as any)[field] = value

  // 防抖保存
  if (saveTimeout) clearTimeout(saveTimeout)
  saveTimeout = setTimeout(async () => {
    try {
      const dto: any = {}
      dto[field] = value
      const res = await notificationPreferenceApi.updateProjectPreference(item.pref.projectId!, dto)
      if (res.code === 0) {
        Message.success('已保存')
      }
    } catch {
      // 回滚
      ;(item.pref as any)[field] = !value
      Message.error('保存失败')
    }
  }, 500)
}
</script>

<style scoped>
.project-preferences-section {
  margin-top: 0;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-header-text {
  flex: 1;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 4px;
}

.section-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

.add-project-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--tf-border);
  background: transparent;
  border-radius: 6px;
  color: var(--tf-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
  flex-shrink: 0;
}
.add-project-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-accent);
  border-color: var(--tf-accent);
}

/* 添加面板 */
.add-panel {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px;
  background: var(--tf-bg-surface, #22252a);
  border-radius: 6px;
  border: 1px solid var(--tf-border-light);
}

.panel-btn {
  padding: 5px 12px;
  border: 1px solid var(--tf-border);
  background: transparent;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s;
}
.panel-btn.confirm {
  color: var(--tf-accent);
  border-color: var(--tf-accent);
}
.panel-btn.confirm:hover:not(:disabled) {
  background: var(--tf-accent-bg);
}
.panel-btn.confirm:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.panel-btn.cancel {
  color: var(--tf-text-tertiary);
}
.panel-btn.cancel:hover {
  background: var(--tf-bg-hover);
}

/* Loading & empty */
.project-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.project-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px 16px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.empty-icon {
  font-size: 16px;
  opacity: 0.5;
}

/* 项目卡片列表 */
.project-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.project-card {
  border: 1px solid var(--tf-border-light);
  border-radius: 6px;
  overflow: hidden;
  transition: border-color 0.15s;
}
.project-card.expanded {
  border-color: var(--tf-accent);
}

.project-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.15s;
}
.project-card-header:hover {
  background: var(--tf-bg-hover);
}

.project-card-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.project-card-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-hover);
  padding: 2px 6px;
  border-radius: 3px;
}

.project-card-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.project-card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.project-card-action {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 4px;
  color: var(--tf-text-tertiary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.project-card-action:hover {
  background: var(--tf-bg-active);
  color: var(--tf-error, #f85149);
}

.expand-icon {
  color: var(--tf-text-tertiary);
  transition: transform 0.2s;
}
.expand-icon.rotated {
  transform: rotate(180deg);
}

/* 展开的偏好开关列表 */
.project-card-body {
  padding: 0 14px 12px;
  border-top: 1px solid var(--tf-border-light);
}

.project-card-body .event-items {
  display: flex;
  flex-direction: column;
}

.project-card-body .event-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}

.project-card-body .event-item + .event-item {
  border-top: 1px solid var(--tf-border-subtle, rgba(255,255,255,0.04));
}

.project-card-body .event-label {
  font-size: 12px;
  color: var(--tf-text-secondary);
}

/* 项目选择下拉选项样式 */
.project-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.project-option .project-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-hover);
  padding: 1px 4px;
  border-radius: 2px;
}

.project-option .project-name {
  font-size: 13px;
  color: var(--tf-text-primary);
}
</style>
