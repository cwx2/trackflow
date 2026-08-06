<template>
  <div class="project-detail-page">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <a-spin :size="28" />
    </div>

    <!-- 项目详情 -->
    <template v-else-if="project">
      <!-- 归档状态提示 -->
      <div v-if="isArchived" class="archived-banner">
        <icon-lock class="archived-icon" />
        <div class="archived-info">
          <span class="archived-title">此项目已归档</span>
          <span class="archived-desc">归档项目为只读状态，无法创建或修改工单、迭代和成员</span>
        </div>
        <a-button v-if="canEditProject" size="small" type="outline" @click="handleRestore">
          恢复项目
        </a-button>
      </div>

      <!-- 顶部面包屑 + 操作 -->
      <div class="page-header">
        <div class="breadcrumb">
          <a class="breadcrumb-link" @click="$router.push('/projects')">项目</a>
          <span class="breadcrumb-sep">/</span>
          <span class="breadcrumb-current">{{ project.name }}</span>
        </div>
        <div class="header-actions">
          <a-button v-if="canEditProject && !isArchived" size="small" @click="goToSettings">
            <template #icon><icon-settings /></template>
            项目设置
          </a-button>
          <a-dropdown v-if="canDeleteProject" trigger="click">
            <a-button type="text" size="small">
              <icon-more />
            </a-button>
            <template #content>
              <a-doption class="danger-option" @click="confirmDeleteProject">删除项目</a-doption>
            </template>
          </a-dropdown>
        </div>
      </div>

      <!-- 项目信息区域 -->
      <div class="project-hero">
        <div class="project-icon" :style="{ background: getProjectColor() }">
          <span class="icon-text">{{ project.key?.substring(0, 3) }}</span>
        </div>
        <div class="project-main-info">
          <h1 class="project-title">{{ project.name }}</h1>
          <div class="project-meta">
            <span class="meta-item">
              <icon-code class="meta-icon" />
              {{ project.key }}
            </span>
            <span v-if="project.myRoleNames && project.myRoleNames.length > 0" class="meta-item role-badge">
              <icon-user class="meta-icon" />
              {{ project.myRoleNames.join(' / ') }}
            </span>
            <span v-else-if="project.myRoleName" class="meta-item role-badge">
              <icon-user class="meta-icon" />
              {{ project.myRoleName }}
            </span>
            <span class="meta-item visibility-badge" :class="'visibility-' + project.visibility">
              <icon-eye v-if="project.visibility !== 'private'" class="meta-icon" />
              <icon-eye-invisible v-else class="meta-icon" />
              {{ visibilityLabel }}
            </span>
            <span v-if="project.memberCount" class="meta-item">
              <icon-user-group class="meta-icon" />
              {{ project.memberCount }} 名成员
            </span>
            <span v-if="project.leadName" class="meta-item lead-item" :class="{ editable: canEditProject && !isArchived, 'lead-disabled': project.leadStatus === 'disabled' }" @click="canEditProject && !isArchived && goToSettings()">
              <icon-star class="meta-icon" />
              负责人：{{ project.leadName }}
              <span v-if="project.leadStatus === 'disabled'" class="lead-disabled-badge">已禁用</span>
              <icon-edit v-if="canEditProject && !isArchived" class="edit-hint-icon" />
            </span>
            <span v-else-if="canEditProject && !isArchived" class="meta-item lead-item editable" @click="goToSettings()">
              <icon-star class="meta-icon" />
              设置负责人
              <icon-edit class="edit-hint-icon" />
            </span>
          </div>
        </div>
      </div>

      <!-- 项目说明 -->
      <ProjectDescription :description="project.description" />

      <!-- 项目统计 -->
      <ProjectStatistics :statistics="statistics" />

      <!-- Widget 仪表盘 -->
      <ProjectWidgetPanel
        v-if="project"
        :project-id="project.key || project.id"
        :can-edit="canEditProject"
        :is-archived="isArchived"
      />

      <!-- 近期活动 -->
      <ProjectActivityFeed
        v-if="project"
        :project-id="project.key || project.id"
      />

      <!-- 功能入口 -->
      <ProjectNavGrid
        :can-view-sprints="canViewSprints"
        :can-manage-members="canManageMembers"
        :is-archived="isArchived"
        @navigate="handleNavigate"
      />

      <!-- 项目成员列表 -->
      <ProjectMemberList v-if="project" :project-id="project.key || project.id" />

    </template>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error-state">
      <icon-close-circle class="error-icon" />
      <h3 class="error-title">加载失败</h3>
      <p class="error-desc">{{ error }}</p>
      <a-button type="primary" @click="loadProject">重试</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  IconSettings,
  IconCode,
  IconUser,
  IconUserGroup,
  IconStar,
  IconCloseCircle,
  IconEdit,
  IconLock,
  IconMore,
  IconEye,
  IconEyeInvisible
} from '@arco-design/web-vue/es/icon'
import { projectApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { loadProjectPermissions } from '@/composables/usePermission'
import type { ProjectDetailVO, ProjectStatisticsVO } from '@/api/types'
import { Message, Modal } from '@arco-design/web-vue'
import ProjectWidgetPanel from './ProjectWidgetPanel.vue'
import ProjectActivityFeed from './ProjectActivityFeed.vue'
import ProjectStatistics from './ProjectStatistics.vue'
import ProjectMemberList from './ProjectMemberList.vue'
import ProjectNavGrid from './ProjectNavGrid.vue'
import ProjectDescription from './ProjectDescription.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const project = ref<ProjectDetailVO | null>(null)
const loading = ref(true)
const error = ref('')
const statistics = ref<ProjectStatisticsVO | null>(null)
const projectPerms = ref<Set<string>>(new Set())

const canEditProject = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('project:edit')
})

const canManageMembers = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('project:manage_members')
})

const canDeleteProject = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('project:delete')
})

const canViewSprints = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('sprint:view')
})

const isArchived = computed(() => project.value?.status === 'archived')

const visibilityLabel = computed(() => {
  const map: Record<string, string> = { private: '私有项目', internal: '内部项目', public: '公开项目' }
  return map[project.value?.visibility || 'private'] || '私有项目'
})

const colorPool = [
  '#e91e63', '#9c27b0', '#673ab7', '#3f51b5', '#2196f3',
  '#00bcd4', '#009688', '#4caf50', '#ff9800', '#ff5722',
  '#795548', '#607d8b'
]

function getProjectColor() {
  const id = parseInt(project.value?.id || '0', 10)
  return colorPool[id % colorPool.length]
}

async function loadProject() {
  const projectKey = route.params.projectKey as string
  if (!projectKey) {
    error.value = '无效的项目标识'
    loading.value = false
    return
  }

  loading.value = true
  error.value = ''

  try {
    const res = await projectApi.getDetail(projectKey)
    project.value = res.data

    if (project.value!.key && projectKey !== project.value!.key) {
      router.replace({ path: `/projects/${project.value!.key}` })
    }

    const projectIdentifier = project.value!.key || project.value!.id
    await Promise.all([
      loadPerms(projectIdentifier),
      loadStatistics(projectIdentifier)
    ])
  } catch (e: any) {
    if (e.response?.status === 403) {
      error.value = '您没有权限查看此项目'
    } else if (e.response?.status === 404) {
      error.value = '项目不存在'
    } else {
      error.value = e.response?.data?.message || '加载项目信息失败'
    }
  } finally {
    loading.value = false
  }
}

async function loadPerms(projectId: string) {
  try {
    projectPerms.value = await loadProjectPermissions(projectId)
  } catch {
    projectPerms.value = new Set()
  }
}

async function loadStatistics(projectId: string) {
  try {
    const res = await projectApi.getStatistics(projectId)
    statistics.value = res.data
  } catch {
    statistics.value = null
  }
}

function handleNavigate(target: 'issues' | 'board' | 'sprints' | 'members') {
  const key = project.value?.key
  switch (target) {
    case 'issues':
      router.push({ path: '/issues', query: { project: key } })
      break
    case 'board':
      router.push({ path: '/boards', query: { project: key } })
      break
    case 'sprints':
      router.push({ path: '/sprints', query: { project: key } })
      break
    case 'members':
      router.push({ path: `/projects/${key}/settings`, query: { tab: 'members' } })
      break
  }
}

function goToSettings() {
  if (!project.value) return
  router.push({ path: `/projects/${project.value.key}/settings` })
}

async function handleRestore() {
  if (!project.value) return

  Modal.warning({
    title: '恢复项目',
    content: `确定要将项目「${project.value.name}」恢复为活跃状态？恢复后项目将重新允许创建和修改工单。`,
    okText: '确认恢复',
    cancelText: '取消',
    onOk: async () => {
      try {
        await projectApi.restore(project.value!.key)
        Message.success('项目已恢复为活跃状态')
        await loadProject()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '恢复项目失败')
      }
    }
  })
}

function confirmDeleteProject() {
  if (!project.value) return
  router.push({ path: `/projects/${project.value.key}/settings`, query: { tab: 'general' } })
}

onMounted(() => {
  loadProject()
})
</script>

<style scoped>
.project-detail-page {
  height: 100%;
  overflow-y: auto;
  padding: 24px;
  max-width: 960px;
  margin: 0 auto;
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--tf-text-tertiary);
}

.breadcrumb-link {
  color: var(--tf-accent);
  cursor: pointer;
  transition: opacity 0.15s;
}
.breadcrumb-link:hover { opacity: 0.8; }

.breadcrumb-sep { color: var(--tf-text-quaternary, var(--tf-text-tertiary)); }
.breadcrumb-current { color: var(--tf-text-secondary); }

.project-hero {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 32px;
}

.project-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-text {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  text-transform: uppercase;
}

.project-main-info { flex: 1; min-width: 0; }

.project-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
  letter-spacing: -0.3px;
  line-height: 1.2;
}

.project-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.meta-icon {
  font-size: 14px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
}

.role-badge {
  background: var(--tf-accent-bg, rgba(88, 166, 255, 0.1));
  color: var(--tf-accent);
  padding: 2px 8px;
  border-radius: 3px;
  font-weight: 500;
}

.lead-item { position: relative; }
.lead-item.lead-disabled { color: var(--tf-text-tertiary); }

.lead-disabled-badge {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  line-height: 1;
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--color-warning-light-2, rgba(209, 153, 34, 0.15));
  color: var(--color-warning-6, #d29922);
  margin-left: 4px;
  font-weight: 500;
}

.lead-item.editable {
  cursor: pointer;
  border-radius: 3px;
  padding: 2px 6px;
  margin: -2px -6px;
  transition: background 0.15s;
}
.lead-item.editable:hover { background: var(--tf-bg-hover); }

.edit-hint-icon {
  font-size: 11px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  opacity: 0;
  transition: opacity 0.15s;
  margin-left: 2px;
}
.lead-item.editable:hover .edit-hint-icon { opacity: 1; }

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
}

.error-icon {
  font-size: 40px;
  color: var(--tf-danger, #f85149);
  margin-bottom: 12px;
}

.error-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}

.error-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0 0 16px;
}

.archived-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--color-warning-light-1, rgba(209, 153, 34, 0.08));
  border: 1px solid var(--color-warning-light-3, rgba(209, 153, 34, 0.2));
  border-radius: 6px;
  margin-bottom: 20px;
}

.archived-icon { font-size: 20px; color: #d29922; flex-shrink: 0; }

.archived-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.archived-title { font-size: 13px; font-weight: 600; color: #d29922; }
.archived-desc { font-size: 12px; color: var(--tf-text-tertiary); }

:deep(.arco-dropdown-option.danger-option) { color: var(--tf-danger); }

.visibility-badge { padding: 2px 8px; border-radius: 3px; font-weight: 500; }
.visibility-private { background: var(--tf-bg-surface); color: var(--tf-text-tertiary); }
.visibility-internal { background: rgba(88, 166, 255, 0.1); color: var(--tf-accent); }
.visibility-public { background: rgba(63, 185, 80, 0.1); color: #3fb950; }
</style>
