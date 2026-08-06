<template>
  <div class="user-profile-page">
    <!-- 加载状态 -->
    <div v-if="loading" class="profile-loading">
      <a-spin :size="24" />
      <span class="loading-text">加载用户资料...</span>
    </div>

    <!-- 403 无权限 -->
    <div v-else-if="forbidden" class="profile-forbidden">
      <div class="forbidden-content">
        <div class="forbidden-icon">🔒</div>
        <h2 class="forbidden-title">您没有权限查看该用户的资料</h2>
        <p class="forbidden-desc">
          您与该用户没有共同参与的项目，因此无法查看其资料。
        </p>
        <div class="forbidden-actions">
          <a-button type="primary" @click="router.push('/issues')">工单列表</a-button>
          <a-button @click="router.back()">返回上一页</a-button>
        </div>
      </div>
    </div>

    <!-- 404 用户不存在 -->
    <div v-else-if="notFound" class="profile-not-found">
      <div class="not-found-content">
        <div class="not-found-icon">👤</div>
        <h2 class="not-found-title">用户不存在</h2>
        <p class="not-found-desc">未找到该用户，可能已被删除或 ID 无效。</p>
        <div class="not-found-actions">
          <a-button type="primary" @click="router.push('/issues')">工单列表</a-button>
          <a-button @click="router.back()">返回上一页</a-button>
        </div>
      </div>
    </div>

    <!-- 正常展示 -->
    <template v-else-if="profile">
      <!-- 头部区域 -->
      <div class="profile-header">
        <div class="profile-avatar" :style="{ background: avatarBg }">
          <img v-if="profile.avatarUrl" :src="profile.avatarUrl" :alt="profile.displayName" />
          <span v-else class="avatar-initial">{{ userInitial }}</span>
        </div>
        <div class="profile-header-info">
          <h1 class="profile-name">{{ profile.displayName }}</h1>
          <span class="profile-username">@{{ profile.username }}</span>
        </div>
        <!-- 管理员：编辑按钮 -->
        <a-button
          v-if="profile.adminView"
          type="outline"
          size="small"
          @click="goToAdminEdit"
        >
          <template #icon><icon-edit /></template>
          编辑
        </a-button>
      </div>

      <!-- 管理员额外信息 -->
      <div v-if="profile.adminView" class="profile-section admin-info-section">
        <h3 class="section-title">账户信息</h3>
        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">邮箱</span>
            <span class="info-value">{{ profile.email || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">注册日期</span>
            <span class="info-value">{{ formatDate(profile.createdAt) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">账户状态</span>
            <span class="info-value">
              <a-tag :color="statusColor">{{ statusLabel }}</a-tag>
            </span>
          </div>
        </div>
      </div>

      <!-- 管理员：全局角色 -->
      <div v-if="profile.adminView && profile.globalRoles?.length" class="profile-section">
        <h3 class="section-title">全局角色</h3>
        <div class="role-tags">
          <a-tag v-for="role in profile.globalRoles" :key="role.id" color="arcoblue">
            {{ role.name }}
          </a-tag>
        </div>
      </div>

      <!-- 共同项目 / 所有项目 -->
      <div class="profile-section">
        <h3 class="section-title">
          {{ profile.adminView ? '参与的项目' : '共同项目' }}
        </h3>
        <div v-if="profile.commonProjects.length > 0" class="project-list">
          <div
            v-for="project in profile.commonProjects"
            :key="project.projectId"
            class="project-item"
            @click="goToProject(project.projectKey)"
          >
            <span class="project-key">{{ project.projectKey }}</span>
            <span class="project-name">{{ project.projectName }}</span>
            <a-tag v-if="project.roleName" size="small" class="project-role">
              {{ project.roleName }}
            </a-tag>
          </div>
        </div>
        <div v-else class="empty-state">
          <span class="empty-text">暂无项目信息</span>
        </div>
      </div>

      <!-- 管理员：所有项目角色（如果和 commonProjects 不同） -->
      <div v-if="profile.adminView && profile.allProjectRoles?.length" class="profile-section">
        <h3 class="section-title">所有项目角色</h3>
        <a-table
          :data="profile.allProjectRoles"
          :pagination="false"
          :bordered="false"
          size="small"
          class="project-role-table"
        >
          <template #columns>
            <a-table-column title="项目" data-index="projectName">
              <template #cell="{ record }">
                <span class="table-project-key">{{ record.projectKey }}</span>
                {{ record.projectName }}
              </template>
            </a-table-column>
            <a-table-column title="角色" data-index="roleName" :width="140" />
          </template>
        </a-table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { userApi, type UserPublicProfileVO } from '@/api/user'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(true)
const forbidden = ref(false)
const notFound = ref(false)
const profile = ref<UserPublicProfileVO | null>(null)

const userInitial = computed(() => {
  return profile.value?.displayName?.[0]?.toUpperCase() || 'U'
})

const avatarBg = computed(() => {
  const colors = ['#5c6bc0', '#26a69a', '#ef5350', '#ab47bc', '#42a5f5', '#ff7043', '#66bb6a']
  const name = profile.value?.displayName || ''
  return colors[name.charCodeAt(0) % colors.length]
})

const statusColor = computed(() => {
  if (profile.value?.banStatus === 'banned') return 'red'
  if (profile.value?.status === 'active') return 'green'
  return 'gray'
})

const statusLabel = computed(() => {
  if (profile.value?.banStatus === 'banned') return '已禁用'
  if (profile.value?.status === 'active') return '正常'
  return profile.value?.status || '-'
})

function formatDate(dateStr?: string) {
  if (!dateStr) return '-'
  try {
    const d = new Date(dateStr)
    return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
  } catch {
    return dateStr
  }
}

function goToProject(projectKey: string) {
  router.push(`/projects/${projectKey}`)
}

function goToAdminEdit() {
  if (profile.value) {
    router.push(`/admin/users/${profile.value.id}`)
  }
}

async function loadProfile() {
  const userId = route.params.userId as string
  if (!userId) {
    notFound.value = true
    loading.value = false
    return
  }

  // 如果是自己的 ID，重定向到个人资料页
  const currentUserId = authStore.user?.userId
  if (currentUserId && currentUserId === userId) {
    router.replace('/settings/profile')
    return
  }

  try {
    const res = await userApi.getPublicProfile(userId)
    if (res.code === 0 && res.data) {
      profile.value = res.data
    } else {
      notFound.value = true
    }
  } catch (err: any) {
    const status = err?.response?.status
    const code = err?.response?.data?.code
    if (status === 403 || code === 40300) {
      forbidden.value = true
    } else if (status === 404 || code === 40400) {
      notFound.value = true
    } else {
      notFound.value = true
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.user-profile-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 32px 24px;
}

/* Loading */
.profile-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 80px 0;
}
.loading-text {
  font-size: 14px;
  color: var(--tf-text-secondary);
}

/* Forbidden / NotFound */
.profile-forbidden,
.profile-not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
}
.forbidden-content,
.not-found-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 400px;
}
.forbidden-icon,
.not-found-icon {
  font-size: 48px;
  margin-bottom: 20px;
}
.forbidden-title,
.not-found-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}
.forbidden-desc,
.not-found-desc {
  font-size: 14px;
  color: var(--tf-text-secondary);
  line-height: 1.5;
  margin: 0 0 24px;
}
.forbidden-actions,
.not-found-actions {
  display: flex;
  gap: 12px;
}

/* Header */
.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}
.profile-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}
.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-initial {
  font-size: 22px;
  font-weight: 600;
  color: #fff;
}
.profile-header-info {
  flex: 1;
  min-width: 0;
}
.profile-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
  line-height: 1.3;
}
.profile-username {
  font-size: 14px;
  color: var(--tf-text-tertiary);
}

/* Sections */
.profile-section {
  margin-bottom: 28px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--tf-border-light);
}

/* Admin info grid */
.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px 24px;
}
.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.info-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}
.info-value {
  font-size: 13px;
  color: var(--tf-text-primary);
}

/* Role tags */
.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* Project list */
.project-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.project-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 150ms;
}
.project-item:hover {
  background: var(--tf-bg-hover);
}
.project-key {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-accent);
  min-width: 48px;
}
.project-name {
  flex: 1;
  font-size: 13px;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.project-role {
  flex-shrink: 0;
}

/* Table */
.project-role-table {
  margin-top: 4px;
}
.table-project-key {
  font-weight: 600;
  color: var(--tf-accent);
  margin-right: 6px;
}

/* Empty */
.empty-state {
  padding: 24px;
  text-align: center;
}
.empty-text {
  font-size: 13px;
  color: var(--tf-text-tertiary);
}
</style>
