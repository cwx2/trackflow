<template>
  <div class="project-detail-page">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <a-spin :size="28" />
    </div>

    <!-- 项目详情 -->
    <template v-else-if="project">
      <!-- 顶部面包屑 + 操作 -->
      <div class="page-header">
        <div class="breadcrumb">
          <a class="breadcrumb-link" @click="$router.push('/projects')">项目</a>
          <span class="breadcrumb-sep">/</span>
          <span class="breadcrumb-current">{{ project.name }}</span>
        </div>
        <div class="header-actions">
          <a-button v-if="canEditProject" size="small" @click="goToSettings">
            <template #icon><icon-settings /></template>
            项目设置
          </a-button>
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
            <span v-if="project.myRoleName" class="meta-item role-badge">
              <icon-user class="meta-icon" />
              {{ project.myRoleName }}
            </span>
            <span v-if="project.memberCount" class="meta-item">
              <icon-user-group class="meta-icon" />
              {{ project.memberCount }} 名成员
            </span>
            <span v-if="project.leadName" class="meta-item">
              <icon-star class="meta-icon" />
              负责人：{{ project.leadName }}
            </span>
          </div>
        </div>
      </div>

      <!-- 项目说明 -->
      <div v-if="project.description" class="section description-section">
        <h2 class="section-title">项目说明</h2>
        <p class="description-text">{{ project.description }}</p>
      </div>
      <div v-else class="section description-section">
        <h2 class="section-title">项目说明</h2>
        <p class="description-empty">暂无项目说明</p>
      </div>

      <!-- 功能入口 -->
      <div class="section">
        <h2 class="section-title">功能入口</h2>
        <div class="nav-grid">
          <div class="nav-card" @click="goToIssues">
            <div class="nav-card-icon issues-icon">
              <icon-list />
            </div>
            <div class="nav-card-info">
              <span class="nav-card-title">问题列表</span>
              <span class="nav-card-desc">查看和管理项目工单</span>
            </div>
            <icon-right class="nav-card-arrow" />
          </div>

          <div class="nav-card" @click="goToBoard">
            <div class="nav-card-icon board-icon">
              <icon-apps />
            </div>
            <div class="nav-card-info">
              <span class="nav-card-title">看板</span>
              <span class="nav-card-desc">可视化任务流转状态</span>
            </div>
            <icon-right class="nav-card-arrow" />
          </div>

          <div v-if="canViewSprints" class="nav-card" @click="goToSprints">
            <div class="nav-card-icon sprint-icon">
              <icon-thunderbolt />
            </div>
            <div class="nav-card-info">
              <span class="nav-card-title">迭代</span>
              <span class="nav-card-desc">查看 Sprint 计划和进度</span>
            </div>
            <icon-right class="nav-card-arrow" />
          </div>

          <div v-if="canManageMembers" class="nav-card" @click="goToMembers">
            <div class="nav-card-icon members-icon">
              <icon-user-group />
            </div>
            <div class="nav-card-info">
              <span class="nav-card-title">成员管理</span>
              <span class="nav-card-desc">管理项目成员和角色</span>
            </div>
            <icon-right class="nav-card-arrow" />
          </div>
        </div>
      </div>

      <!-- 项目成员列表 -->
      <div class="section">
        <h2 class="section-title">项目成员</h2>
        <div v-if="membersLoading" class="members-loading">
          <a-spin :size="20" />
        </div>
        <div v-else-if="members.length > 0" class="members-list">
          <div v-for="member in members" :key="member.id" class="member-row">
            <a-avatar :size="28" :style="{ backgroundColor: getMemberColor(member.userId) }">
              {{ (member.displayName || member.username || '?').charAt(0) }}
            </a-avatar>
            <div class="member-info">
              <span class="member-name">{{ member.displayName || member.username }}</span>
              <span class="member-email">{{ member.email }}</span>
            </div>
            <span class="member-role">{{ getRoleName(member.roleId) }}</span>
          </div>
        </div>
        <div v-else class="members-empty">
          <p>暂无成员信息</p>
        </div>
      </div>
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
  IconList,
  IconApps,
  IconThunderbolt,
  IconRight,
  IconCloseCircle
} from '@arco-design/web-vue/es/icon'
import { projectApi, workflowApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { loadProjectPermissions } from '@/composables/usePermission'
import type { ProjectDetailVO, ProjectMemberVO } from '@/api/types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const project = ref<ProjectDetailVO | null>(null)
const members = ref<ProjectMemberVO[]>([])
const loading = ref(true)
const membersLoading = ref(false)
const error = ref('')

// 权限
const projectPerms = ref<Set<string>>(new Set())

const canEditProject = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('project:edit')
})

const canManageMembers = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('project:manage_members')
})

const canViewSprints = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('sprint:view')
})

// 角色映射（动态加载）
const roleMap = ref<Record<string, string>>({})

async function loadRoles() {
  try {
    const res = await workflowApi.listProjectRoles()
    const roles = res.data || []
    const map: Record<string, string> = {}
    roles.forEach((r) => {
      map[String(r.id)] = r.name
    })
    roleMap.value = map
  } catch {
    roleMap.value = {}
  }
}

function getRoleName(roleId: string): string {
  return roleMap.value[roleId] || `角色 ${roleId}`
}

// 颜色
const colorPool = [
  '#e91e63', '#9c27b0', '#673ab7', '#3f51b5', '#2196f3',
  '#00bcd4', '#009688', '#4caf50', '#ff9800', '#ff5722',
  '#795548', '#607d8b'
]

function getProjectColor() {
  const id = parseInt(project.value?.id || '0', 10)
  return colorPool[id % colorPool.length]
}

function getMemberColor(userId: string) {
  const id = parseInt(userId || '0', 10)
  return colorPool[id % colorPool.length]
}

// 加载数据
async function loadProject() {
  const projectId = route.params.id as string
  if (!projectId) {
    error.value = '无效的项目 ID'
    loading.value = false
    return
  }

  loading.value = true
  error.value = ''

  try {
    const res = await projectApi.getDetail(projectId)
    project.value = res.data

    // 并行加载权限、成员和角色
    await Promise.all([
      loadPerms(projectId),
      loadMembers(projectId),
      loadRoles()
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

async function loadMembers(projectId: string) {
  membersLoading.value = true
  try {
    const res = await projectApi.listMembers(projectId)
    members.value = res.data || []
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

// 导航
function goToIssues() {
  router.push({ path: '/', query: { project: project.value?.id } })
}

function goToBoard() {
  router.push({ path: '/boards', query: { project: project.value?.id } })
}

function goToSprints() {
  router.push({ path: '/sprints', query: { project: project.value?.id } })
}

function goToMembers() {
  // 暂时回到项目列表的成员管理弹窗
  router.push({ path: '/projects', query: { manage: project.value?.id } })
}

function goToSettings() {
  // 暂时回到项目列表编辑
  router.push({ path: '/projects', query: { edit: project.value?.id } })
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

/* 加载状态 */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

/* 面包屑 + 头部 */
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
.breadcrumb-link:hover {
  opacity: 0.8;
}

.breadcrumb-sep {
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
}

.breadcrumb-current {
  color: var(--tf-text-secondary);
}

/* 项目英雄区 */
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

.project-main-info {
  flex: 1;
  min-width: 0;
}

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

/* Section */
.section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 12px;
}

/* 描述 */
.description-text {
  font-size: 13px;
  color: var(--tf-text-secondary);
  line-height: 1.6;
  margin: 0;
  white-space: pre-wrap;
}

.description-empty {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
  font-style: italic;
}

/* 功能入口网格 */
.nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.nav-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border, rgba(255, 255, 255, 0.06));
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;
}
.nav-card:hover {
  background: var(--tf-bg-hover);
  border-color: var(--tf-accent);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.nav-card-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
  color: #fff;
}

.issues-icon { background: #3f51b5; }
.board-icon { background: #009688; }
.sprint-icon { background: #ff9800; }
.members-icon { background: #9c27b0; }

.nav-card-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-card-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.nav-card-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.nav-card-arrow {
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  font-size: 14px;
  flex-shrink: 0;
}

/* 成员列表 */
.members-loading {
  padding: 16px;
  text-align: center;
}

.members-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  transition: background 0.15s;
}
.member-row:hover {
  background: var(--tf-bg-hover);
}

.member-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.member-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.member-email {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-role {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-surface);
  padding: 2px 8px;
  border-radius: 3px;
  white-space: nowrap;
}

.members-empty {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  padding: 16px 0;
}
.members-empty p {
  margin: 0;
}

/* 错误状态 */
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  text-align: center;
}

.error-icon {
  font-size: 48px;
  color: var(--tf-danger, #f85149);
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

/* 操作按钮 */
.header-actions {
  display: flex;
  gap: 8px;
}
</style>
