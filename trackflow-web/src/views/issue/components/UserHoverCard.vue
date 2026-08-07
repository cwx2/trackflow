<template>
  <a-trigger
    v-if="userId"
    trigger="hover"
    position="bottom"
    :mouse-enter-delay="250"
    :mouse-leave-delay="150"
    :popup-offset="4"
    :content-style="{ padding: 0, background: 'none', border: 'none', boxShadow: 'none' }"
    animation-name="fade-in"
    auto-fit-position
    @popup-visible-change="onVisibleChange"
  >
    <span class="user-hover-target clickable">
      <slot />
    </span>
    <template #content>
      <div class="user-hover-card">
        <div v-if="loading" class="card-loading">
          <a-spin :size="16" />
        </div>
        <template v-else-if="userInfo">
          <div class="card-header">
            <UserAvatar :name="userInfo.displayName || ''" :avatar="userInfo.avatarUrl" :size="40" />
            <div class="card-info">
              <div class="card-name">{{ userInfo.displayName }}</div>
              <div class="card-username">@{{ userInfo.username }}</div>
            </div>
          </div>
          <div class="card-actions">
            <button class="card-action-btn" @click.stop="viewUserProfile">
              查看资料
            </button>
            <button class="card-action-btn" @click.stop="viewReportedIssues">
              查看报告的工单
            </button>
            <button class="card-action-btn" @click.stop="viewAssignedIssues">
              查看负责的工单
            </button>
          </div>
        </template>
        <div v-else class="card-error">
          无法加载用户信息
        </div>
      </div>
    </template>
  </a-trigger>
  <span v-else class="user-hover-target">
    <slot />
  </span>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { userApi, type UserSummaryVO } from '@/api/user'
import { UserAvatar } from '@/components/base'

const props = defineProps<{
  /** 用户 ID（数据库 ID），为空时不包裹 trigger */
  userId?: string
}>()

const router = useRouter()
const loading = ref(false)
const userInfo = ref<UserSummaryVO | null>(null)
const fetched = ref(false)

// 模块级缓存，避免同一页面同一用户重复请求
const userSummaryCache = new Map<string, UserSummaryVO>()

function onVisibleChange(visible: boolean) {
  if (visible && !fetched.value) {
    fetchUser()
  }
}

async function fetchUser() {
  if (!props.userId) return
  fetched.value = true

  const cached = userSummaryCache.get(props.userId)
  if (cached) {
    userInfo.value = cached
    return
  }

  loading.value = true
  try {
    const res = await userApi.getSummary(props.userId)
    if (res.code === 0 && res.data) {
      userInfo.value = res.data
      userSummaryCache.set(props.userId, res.data)
    }
  } catch {
    // Silent fail — card shows error state
  } finally {
    loading.value = false
  }
}

function viewReportedIssues() {
  if (!props.userId) return
  router.push({
    path: '/issues',
    query: {
      reporter: props.userId,
      reporterName: userInfo.value?.displayName || ''
    }
  })
}

function viewAssignedIssues() {
  if (!props.userId) return
  router.push({
    path: '/issues',
    query: {
      assignee: props.userId,
      assigneeName: userInfo.value?.displayName || ''
    }
  })
}

function viewUserProfile() {
  if (!props.userId) return
  router.push({ path: `/users/${props.userId}` })
}
</script>

<style scoped>
.user-hover-target {
  display: inline-flex;
  align-items: center;
  cursor: default;
}
.user-hover-target.clickable {
  cursor: pointer;
}

.user-hover-card {
  width: 240px;
  padding: 12px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}

.card-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 60px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-username {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}

.card-actions {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--tf-border-light);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.card-action-btn {
  width: 100%;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-accent);
  background: none;
  border: 1px solid var(--tf-accent);
  border-radius: 4px;
  cursor: pointer;
  transition: background 150ms, color 150ms;
  text-align: center;
}
.card-action-btn:hover {
  background: var(--tf-accent);
  color: #fff;
}

.card-error {
  font-size: 12px;
  color: var(--tf-text-muted);
  text-align: center;
  padding: 8px 0;
}
</style>
