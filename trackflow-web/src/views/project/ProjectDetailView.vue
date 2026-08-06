<template>
  <div class="project-detail-page">
    <!-- 鍔犺浇鐘舵€?-->
    <div v-if="loading" class="loading-state">
      <a-spin :size="28" />
    </div>

    <!-- 椤圭洰璇︽儏 -->
    <template v-else-if="project">
      <!-- 褰掓。鐘舵€佹彁绀?-->
      <div v-if="isArchived" class="archived-banner">
        <icon-lock class="archived-icon" />
        <div class="archived-info">
          <span class="archived-title">姝ら」鐩凡褰掓。</span>
          <span class="archived-desc">褰掓。椤圭洰涓哄彧璇荤姸鎬侊紝鏃犳硶鍒涘缓鎴栦慨鏀瑰伐鍗曘€佽凯浠ｅ拰鎴愬憳</span>
        </div>
        <a-button v-if="canEditProject" size="small" type="outline" @click="handleRestore">
          鎭㈠椤圭洰
        </a-button>
      </div>

      <!-- 椤堕儴闈㈠寘灞?+ 鎿嶄綔 -->
      <div class="page-header">
        <div class="breadcrumb">
          <a class="breadcrumb-link" @click="$router.push('/projects')">椤圭洰</a>
          <span class="breadcrumb-sep">/</span>
          <span class="breadcrumb-current">{{ project.name }}</span>
        </div>
        <div class="header-actions">
          <a-button v-if="canEditProject && !isArchived" size="small" @click="goToSettings">
            <template #icon><icon-settings /></template>
            椤圭洰璁剧疆
          </a-button>
          <a-dropdown v-if="canDeleteProject" trigger="click">
            <a-button type="text" size="small">
              <icon-more />
            </a-button>
            <template #content>
              <a-doption class="danger-option" @click="confirmDeleteProject">鍒犻櫎椤圭洰</a-doption>
            </template>
          </a-dropdown>
        </div>
      </div>

      <!-- 椤圭洰淇℃伅鍖哄煙 -->
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
              {{ project.memberCount }} 鍚嶆垚鍛?
            </span>
            <span v-if="project.leadName" class="meta-item lead-item" :class="{ editable: canEditProject && !isArchived, 'lead-disabled': project.leadStatus === 'disabled' }" @click="canEditProject && !isArchived && goToSettings()">
              <icon-star class="meta-icon" />
              璐熻矗浜猴細{{ project.leadName }}
              <span v-if="project.leadStatus === 'disabled'" class="lead-disabled-badge">宸茬鐢?/span>
              <icon-edit v-if="canEditProject && !isArchived" class="edit-hint-icon" />
            </span>
            <span v-else-if="canEditProject && !isArchived" class="meta-item lead-item editable" @click="goToSettings()">
              <icon-star class="meta-icon" />
              璁剧疆璐熻矗浜?
              <icon-edit class="edit-hint-icon" />
            </span>
          </div>
        </div>
      </div>

      <!-- 椤圭洰璇存槑 -->
      <div class="section description-section">
        <h2 class="section-title">椤圭洰璇存槑</h2>
        <div v-if="project.description" v-html="projectDescriptionHtml" class="description-rendered"></div>
        <p v-else class="description-empty">鏆傛棤椤圭洰璇存槑</p>
      </div>

      <!-- 椤圭洰缁熻 + 鐘舵€佸垎甯?+ Sprint 杩涘害 -->
      <ProjectStatistics :statistics="statistics" />

      <!-- Widget 鍖哄煙锛堥」鐩瑙堜华琛ㄧ洏锛?-->
      <ProjectWidgetPanel
        v-if="project"
        :project-id="project.key || project.id"
        :can-edit="canEditProject"
        :is-archived="isArchived"
      />

      <!-- 杩戞湡娲诲姩 -->
      <ProjectActivityFeed
        v-if="project"
        :project-id="project.key || project.id"
      />

      <!-- 鍔熻兘鍏ュ彛 -->
      <div class="section">
        <h2 class="section-title">鍔熻兘鍏ュ彛</h2>
        <div class="nav-grid">
          <div class="nav-card" @click="goToIssues">
            <div class="nav-card-icon issues-icon">
              <icon-list />
            </div>
            <div class="nav-card-info">
              <span class="nav-card-title">闂鍒楄〃</span>
              <span class="nav-card-desc">鏌ョ湅鍜岀鐞嗛」鐩伐鍗?/span>
            </div>
            <icon-right class="nav-card-arrow" />
          </div>

          <div class="nav-card" @click="goToBoard">
            <div class="nav-card-icon board-icon">
              <icon-apps />
            </div>
            <div class="nav-card-info">
              <span class="nav-card-title">鐪嬫澘</span>
              <span class="nav-card-desc">鍙鍖栦换鍔℃祦杞姸鎬?/span>
            </div>
            <icon-right class="nav-card-arrow" />
          </div>

          <div v-if="canViewSprints" class="nav-card" @click="goToSprints">
            <div class="nav-card-icon sprint-icon">
              <icon-thunderbolt />
            </div>
            <div class="nav-card-info">
              <span class="nav-card-title">杩唬</span>
              <span class="nav-card-desc">鏌ョ湅 Sprint 璁″垝鍜岃繘搴?/span>
            </div>
            <icon-right class="nav-card-arrow" />
          </div>

          <div v-if="canManageMembers && !isArchived" class="nav-card" @click="goToMembers">
            <div class="nav-card-icon members-icon">
              <icon-user-group />
            </div>
            <div class="nav-card-info">
              <span class="nav-card-title">鎴愬憳绠＄悊</span>
              <span class="nav-card-desc">绠＄悊椤圭洰鎴愬憳鍜岃鑹?/span>
            </div>
            <icon-right class="nav-card-arrow" />
          </div>
        </div>
      </div>

      <!-- 椤圭洰鎴愬憳鍒楄〃 -->
      <ProjectMemberList v-if="project" :project-id="project.key || project.id" />

    </template>

    <!-- 閿欒鐘舵€?-->
    <div v-else-if="error" class="error-state">
      <icon-close-circle class="error-icon" />
      <h3 class="error-title">鍔犺浇澶辫触</h3>
      <p class="error-desc">{{ error }}</p>
      <a-button type="primary" @click="loadProject">閲嶈瘯</a-button>
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
import { renderMarkdown } from '@/utils/markdown'
import type { ProjectDetailVO, ProjectStatisticsVO } from '@/api/types'
import { Message, Modal } from '@arco-design/web-vue'
import ProjectWidgetPanel from './ProjectWidgetPanel.vue'
import ProjectActivityFeed from './ProjectActivityFeed.vue'
import ProjectStatistics from './ProjectStatistics.vue'
import ProjectMemberList from './ProjectMemberList.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const project = ref<ProjectDetailVO | null>(null)
const loading = ref(true)
const error = ref('')

// 缁熻鏁版嵁
const statistics = ref<ProjectStatisticsVO | null>(null)

// 鏉冮檺
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

// 椤圭洰鎻忚堪 Markdown 娓叉煋
const projectDescriptionHtml = computed(() => renderMarkdown(project.value?.description || ''))

// 鍙鎬?
const visibilityLabel = computed(() => {
  const map: Record<string, string> = { private: '绉佹湁椤圭洰', internal: '鍐呴儴椤圭洰', public: '鍏紑椤圭洰' }
  return map[project.value?.visibility || 'private'] || '绉佹湁椤圭洰'
})

// 棰滆壊
const colorPool = [
  '#e91e63', '#9c27b0', '#673ab7', '#3f51b5', '#2196f3',
  '#00bcd4', '#009688', '#4caf50', '#ff9800', '#ff5722',
  '#795548', '#607d8b'
]

function getProjectColor() {
  const id = parseInt(project.value?.id || '0', 10)
  return colorPool[id % colorPool.length]
}

// 鍔犺浇鏁版嵁
async function loadProject() {
  const projectKey = route.params.projectKey as string
  if (!projectKey) {
    error.value = '鏃犳晥鐨勯」鐩爣璇?
    loading.value = false
    return
  }

  loading.value = true
  error.value = ''

  try {
    const res = await projectApi.getDetail(projectKey)
    project.value = res.data

    // 濡傛灉 URL 浣跨敤鐨勬槸鏁板瓧 ID锛岄噸瀹氬悜鍒?key 鏍煎紡锛堟洿鍙锛?
    if (project.value!.key && projectKey !== project.value!.key) {
      router.replace({ path: `/projects/${project.value!.key}` })
    }

    // 浣跨敤椤圭洰 Key 璋冪敤鍚庣画 API
    const projectIdentifier = project.value!.key || project.value!.id
    await Promise.all([
      loadPerms(projectIdentifier),
      loadStatistics(projectIdentifier)
    ])
  } catch (e: any) {
    if (e.response?.status === 403) {
      error.value = '鎮ㄦ病鏈夋潈闄愭煡鐪嬫椤圭洰'
    } else if (e.response?.status === 404) {
      error.value = '椤圭洰涓嶅瓨鍦?
    } else {
      error.value = e.response?.data?.message || '鍔犺浇椤圭洰淇℃伅澶辫触'
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

// 瀵艰埅
function goToIssues() {
  router.push({ path: '/issues', query: { project: project.value?.key } })
}

function goToBoard() {
  router.push({ path: '/boards', query: { project: project.value?.key } })
}

function goToSprints() {
  router.push({ path: '/sprints', query: { project: project.value?.key } })
}

function goToMembers() {
  router.push({ path: `/projects/${project.value?.key}/settings`, query: { tab: 'members' } })
}

function goToSettings() {
  if (!project.value) return
  router.push({ path: `/projects/${project.value.key}/settings` })
}

async function handleRestore() {
  if (!project.value) return

  Modal.warning({
    title: '鎭㈠椤圭洰',
    content: `纭畾瑕佸皢椤圭洰銆?{project.value.name}銆嶆仮澶嶄负娲昏穬鐘舵€侊紵鎭㈠鍚庨」鐩皢閲嶆柊鍏佽鍒涘缓鍜屼慨鏀瑰伐鍗曘€俙,
    okText: '纭鎭㈠',
    cancelText: '鍙栨秷',
    onOk: async () => {
      try {
        await projectApi.restore(project.value!.key)
        Message.success('椤圭洰宸叉仮澶嶄负娲昏穬鐘舵€?)
        await loadProject()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '鎭㈠椤圭洰澶辫触')
      }
    }
  })
}

async function confirmDeleteProject() {
  if (!project.value) return
  // Navigate to settings page danger zone
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

/* 鍔犺浇鐘舵€?*/
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

/* 闈㈠寘灞?+ 澶撮儴 */
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

/* 椤圭洰鑻遍泟鍖?*/
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

.lead-item {
  position: relative;
}

.lead-item.lead-disabled {
  color: var(--tf-text-tertiary);
}

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

.lead-item.editable:hover {
  background: var(--tf-bg-hover);
}

.edit-hint-icon {
  font-size: 11px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  opacity: 0;
  transition: opacity 0.15s;
  margin-left: 2px;
}

.lead-item.editable:hover .edit-hint-icon {
  opacity: 1;
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

/* 鎻忚堪 */
.description-rendered {
  font-size: 13px;
  color: var(--tf-text-secondary);
  line-height: 1.6;
}

.description-rendered :deep(p) {
  margin: 0 0 8px;
}

.description-rendered :deep(h1),
.description-rendered :deep(h2),
.description-rendered :deep(h3) {
  color: var(--tf-text-primary);
  font-weight: 600;
  margin: 12px 0 6px;
}

.description-rendered :deep(h1) { font-size: 16px; }
.description-rendered :deep(h2) { font-size: 14px; }
.description-rendered :deep(h3) { font-size: 13px; }

.description-rendered :deep(strong) {
  color: var(--tf-text-primary);
  font-weight: 600;
}

.description-rendered :deep(code) {
  background: var(--tf-bg-code, var(--tf-bg-surface));
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
}

.description-rendered :deep(pre) {
  background: var(--tf-bg-code, var(--tf-bg-surface));
  padding: 10px 12px;
  border-radius: 4px;
  overflow-x: auto;
  border: 1px solid var(--tf-border);
  margin: 8px 0;
}

.description-rendered :deep(pre code) {
  background: none;
  padding: 0;
}

.description-rendered :deep(blockquote) {
  border-left: 3px solid var(--tf-accent);
  padding-left: 10px;
  color: var(--tf-text-tertiary);
  margin: 6px 0;
}

.description-rendered :deep(a) {
  color: var(--tf-accent);
  text-decoration: none;
}

.description-rendered :deep(a:hover) {
  text-decoration: underline;
}

.description-rendered :deep(ul),
.description-rendered :deep(ol) {
  padding-left: 20px;
  margin: 4px 0 8px;
}

.description-rendered :deep(li) {
  margin-bottom: 2px;
}

.description-empty {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
  font-style: italic;
}

/* 鍔熻兘鍏ュ彛缃戞牸 */
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

/* 閿欒鐘舵€?*/
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

/* 鎿嶄綔鎸夐挳 */
.header-actions {
  display: flex;
  gap: 8px;
}

/* 褰掓。鐘舵€佹í骞?*/
.archived-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(210, 153, 34, 0.08);
  border: 1px solid rgba(210, 153, 34, 0.25);
  border-radius: 8px;
  margin-bottom: 20px;
}

.archived-icon {
  font-size: 20px;
  color: #d29922;
  flex-shrink: 0;
}

.archived-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.archived-title {
  font-size: 13px;
  font-weight: 600;
  color: #d29922;
}

.archived-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}
:deep(.arco-dropdown-option.danger-option) {
  color: var(--tf-danger);
}

/* Visibility Badge */
.visibility-badge { padding: 2px 8px; border-radius: 3px; font-weight: 500; }
.visibility-private { background: var(--tf-bg-surface); color: var(--tf-text-tertiary); }
.visibility-internal { background: rgba(88, 166, 255, 0.1); color: var(--tf-accent); }
.visibility-public { background: rgba(63, 185, 80, 0.1); color: #3fb950; }
</style>
