<template>
  <div class="project-settings-page">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <a-spin :size="28" />
    </div>

    <template v-else-if="project">
      <!-- 顶部面包屑 -->
      <div class="page-header">
        <div class="breadcrumb">
          <a class="breadcrumb-link" @click="$router.push('/projects')">项目</a>
          <span class="breadcrumb-sep">/</span>
          <a class="breadcrumb-link" @click="$router.push(`/projects/${project.key}`)">{{ project.name }}</a>
          <span class="breadcrumb-sep">/</span>
          <span class="breadcrumb-current">设置</span>
        </div>
      </div>

      <!-- 设置标题 -->
      <div class="settings-title-section">
        <h1 class="settings-title">项目设置</h1>
        <p class="settings-desc">管理项目基本信息、成员、自定义字段和工作流配置</p>
      </div>

      <!-- Tab 导航 -->
      <a-tabs :active-key="activeTab" @change="handleTabChange" class="settings-tabs">
        <a-tab-pane key="general" title="基本设置">
          <ProjectSettingsGeneral
            :project="project"
            :can-edit="canEditProject"
            :is-archived="isArchived"
            @updated="handleProjectUpdated"
          />
        </a-tab-pane>
        <a-tab-pane key="members" title="成员管理">
          <ProjectSettingsMembers
            :project="project"
            :can-manage="canManageMembers"
            :is-archived="isArchived"
          />
        </a-tab-pane>
        <a-tab-pane key="custom-fields" title="自定义字段">
          <ProjectSettingsCustomFields
            :project="project"
            :can-manage="canManageCustomFields"
            :is-archived="isArchived"
          />
        </a-tab-pane>
        <a-tab-pane key="workflow" title="工作流">
          <ProjectSettingsWorkflow
            :project="project"
            :can-manage="canManageWorkflow"
            :is-archived="isArchived"
          />
        </a-tab-pane>
        <a-tab-pane key="time-tracking" title="时间追踪">
          <ProjectSettingsTimeTracking
            :project="project"
            :can-manage="canEditProject"
            :is-archived="isArchived"
          />
        </a-tab-pane>
      </a-tabs>
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
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { IconCloseCircle } from '@arco-design/web-vue/es/icon'
import { projectApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { loadProjectPermissions } from '@/composables/usePermission'
import type { ProjectDetailVO } from '@/api/types'
import ProjectSettingsGeneral from './ProjectSettingsGeneral.vue'
import ProjectSettingsMembers from './ProjectSettingsMembers.vue'
import ProjectSettingsCustomFields from './ProjectSettingsCustomFields.vue'
import ProjectSettingsTimeTracking from './ProjectSettingsTimeTracking.vue'
import ProjectSettingsWorkflow from './ProjectSettingsWorkflow.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const project = ref<ProjectDetailVO | null>(null)
const loading = ref(true)
const error = ref('')
const projectPerms = ref<Set<string>>(new Set())

// Active tab from route query or default
const activeTab = computed(() => {
  const tab = route.query.tab as string
  return ['general', 'members', 'custom-fields', 'workflow', 'time-tracking'].includes(tab) ? tab : 'general'
})

// Permissions
const canEditProject = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('project:edit')
})

const canManageMembers = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('project:manage_members')
})

const canManageCustomFields = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('project:manage_custom_fields')
})

const canManageWorkflow = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('project:manage_workflow')
})

const isArchived = computed(() => project.value?.status === 'archived')

function handleTabChange(key: string | number) {
  router.replace({ query: { ...route.query, tab: key as string } })
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

    // 如果 URL 使用的是数字 ID，重定向到 key 格式
    if (project.value.key && projectKey !== project.value.key) {
      router.replace({ path: `/projects/${project.value.key}/settings`, query: route.query })
    }

    // 加载权限（使用项目 Key）
    try {
      projectPerms.value = await loadProjectPermissions(project.value.key || project.value.id)
    } catch {
      projectPerms.value = new Set()
    }
  } catch (e: any) {
    if (e.response?.status === 403) {
      error.value = '您没有权限查看此项目设置'
    } else if (e.response?.status === 404) {
      error.value = '项目不存在'
    } else {
      error.value = e.response?.data?.message || '加载项目信息失败'
    }
  } finally {
    loading.value = false
  }
}

function handleProjectUpdated(updated: ProjectDetailVO) {
  project.value = updated
}

onMounted(() => {
  loadProject()
})

// Watch route param changes (e.g., navigating between different project settings)
watch(() => route.params.projectKey, () => {
  loadProject()
})
</script>

<style scoped>
.project-settings-page {
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

/* Breadcrumb */
.page-header {
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
.breadcrumb-link:hover {
  opacity: 0.8;
}

.breadcrumb-sep {
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
}

.breadcrumb-current {
  color: var(--tf-text-secondary);
}

/* Title */
.settings-title-section {
  margin-bottom: 24px;
}

.settings-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 4px;
  line-height: 1.3;
}

.settings-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

/* Tabs */
.settings-tabs :deep(.arco-tabs-nav-tab) {
  margin-bottom: 0;
}

.settings-tabs :deep(.arco-tabs-content) {
  padding-top: 24px;
}

/* Error */
.error-state {
  text-align: center;
  padding: 80px 24px;
}

.error-icon {
  font-size: 48px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  margin-bottom: 16px;
}

.error-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}

.error-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0 0 24px;
}
</style>
