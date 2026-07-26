<template>
  <header class="detail-topbar">
    <div class="topbar-left">
      <button class="icon-btn" @click="$router.back()" title="返回">
        <icon-left :size="16" />
      </button>
      <nav class="breadcrumb">
        <a class="crumb" @click="$router.push('/issues')">{{ projectName }}</a>
        <span class="sep">/</span>
        <span class="crumb-current">{{ issueKey }}</span>
        <!-- 受限工单锁定图标（对标 YouTrack "Visible to" 锁定标识） -->
        <a-tooltip v-if="isRestricted" content="此工单为受限访问，仅部分用户可见">
          <icon-lock class="restricted-icon" :size="13" />
        </a-tooltip>
      </nav>
      <span class="meta">
        创建者 <b>{{ createdBy }}</b> · {{ createdAgo }}
        &nbsp;·&nbsp; 更新者 <b>{{ updatedBy }}</b> · {{ updatedAgo }}
      </span>
    </div>
    <div class="topbar-right">
      <!-- Vote 按钮 -->
      <a-tooltip :content="voteStatus.voted ? '取消投票' : '为此工单投票'">
        <button
          class="icon-btn vote-btn"
          :class="{ active: voteStatus.voted }"
          @click="handleToggleVote"
          :disabled="voteLoading"
        >
          <icon-thumb-up :size="14" />
          <span v-if="voteStatus.voteCount > 0" class="badge-count">{{ voteStatus.voteCount }}</span>
        </button>
      </a-tooltip>

      <!-- Watch/Star 按钮 -->
      <a-tooltip :content="watcherStatus.watching ? '取消关注' : '关注此工单'">
        <button
          class="icon-btn watch-btn"
          :class="{ active: watcherStatus.watching }"
          @click="handleToggleWatch"
          :disabled="watchLoading"
        >
          <icon-star-fill v-if="watcherStatus.watching" :size="14" />
          <icon-star v-else :size="14" />
          <span v-if="watcherStatus.watcherCount > 0" class="badge-count">{{ watcherStatus.watcherCount }}</span>
        </button>
      </a-tooltip>

      <!-- 前后导航 -->
      <div class="nav-group" v-if="(total ?? 0) > 0">
        <button class="icon-btn" :disabled="(index ?? 0) <= 1" @click="$emit('prev')"><icon-left :size="12" /></button>
        <span class="nav-pos">{{ index }} / {{ total }}</span>
        <button class="icon-btn" :disabled="(index ?? 0) >= (total ?? 0)" @click="$emit('next')"><icon-right :size="12" /></button>
      </div>
      <button class="icon-btn" @click="$emit('copy')" title="复制"><icon-copy :size="16" /></button>
      <button v-if="showCreate" class="icon-btn" @click="$emit('create')" title="创建工单"><icon-plus :size="16" /></button>
      <button class="icon-btn" @click="$emit('toggle-sidebar')" title="面板"><icon-menu :size="16" /></button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { IconLeft, IconRight, IconCopy, IconPlus, IconMenu, IconThumbUp, IconStar, IconStarFill, IconLock } from '@arco-design/web-vue/es/icon'
import { issueVoteApi, issueWatcherApi } from '@/api'
import type { IssueVoteStatusVO } from '@/api/issueVote'
import type { IssueWatcherStatusVO } from '@/api/issueWatcher'
import { Message } from '@arco-design/web-vue'

const props = defineProps<{
  issueId: string
  projectName: string
  issueKey: string
  createdBy: string
  updatedBy: string
  createdAgo: string
  updatedAgo: string
  index?: number
  total?: number
  showCreate?: boolean
  /** 是否为受限访问工单（显示锁定图标） */
  isRestricted?: boolean
}>()

defineEmits<{
  prev: []
  next: []
  copy: []
  create: []
  'toggle-sidebar': []
}>()

// ===== Vote 状态 =====
const voteStatus = ref<IssueVoteStatusVO>({ voted: false, voteCount: 0 })
const voteLoading = ref(false)

async function loadVoteStatus() {
  if (!props.issueId) return
  try {
    const res = await issueVoteApi.getStatus(props.issueId)
    if (res.code === 0 && res.data) {
      voteStatus.value = res.data
    }
  } catch (e) {
    // 静默失败
  }
}

async function handleToggleVote() {
  if (voteLoading.value || !props.issueId) return
  voteLoading.value = true
  try {
    const res = voteStatus.value.voted
      ? await issueVoteApi.unvote(props.issueId)
      : await issueVoteApi.vote(props.issueId)
    if (res.code === 0 && res.data) {
      voteStatus.value = res.data
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    voteLoading.value = false
  }
}

// ===== Watcher 状态 =====
const watcherStatus = ref<IssueWatcherStatusVO>({ watching: false, watcherCount: 0 })
const watchLoading = ref(false)

async function loadWatcherStatus() {
  if (!props.issueId) return
  try {
    const res = await issueWatcherApi.getStatus(props.issueId)
    if (res.code === 0 && res.data) {
      watcherStatus.value = res.data
    }
  } catch (e) {
    // 静默失败
  }
}

async function handleToggleWatch() {
  if (watchLoading.value || !props.issueId) return
  watchLoading.value = true
  try {
    const res = watcherStatus.value.watching
      ? await issueWatcherApi.unwatch(props.issueId)
      : await issueWatcherApi.watch(props.issueId)
    if (res.code === 0 && res.data) {
      watcherStatus.value = res.data
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    watchLoading.value = false
  }
}

// ===== 加载状态 =====
watch(() => props.issueId, (newId) => {
  if (newId) {
    loadVoteStatus()
    loadWatcherStatus()
  }
}, { immediate: true })
</script>

<style scoped>
.detail-topbar {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid var(--tf-border);
  flex-shrink: 0;
  background: var(--tf-bg-elevated);
}

.topbar-left, .topbar-right { display: flex; align-items: center; gap: 8px; }
.topbar-left { min-width: 0; overflow: hidden; }
.topbar-right { gap: 4px; flex-shrink: 0; }

.icon-btn {
  background: none;
  border: none;
  color: var(--tf-text-secondary);
  cursor: pointer;
  padding: 6px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  transition: color 150ms, background 150ms;
}
.icon-btn:hover:not(:disabled) { background: var(--tf-bg-hover); color: var(--tf-text-primary); }
.icon-btn:disabled { opacity: 0.3; cursor: default; }

/* Vote 按钮激活态 */
.vote-btn.active { color: var(--tf-accent); }
.vote-btn.active:hover { color: var(--tf-accent); }

/* Watch/Star 按钮激活态 */
.watch-btn.active { color: #f0a020; }
.watch-btn.active:hover { color: #f0a020; }

.badge-count {
  font-size: 11px;
  line-height: 1;
  font-weight: 500;
}

.breadcrumb { display: flex; align-items: center; gap: 4px; font-size: 13px; }
.crumb {
  color: var(--tf-text-secondary);
  cursor: pointer;
  transition: color 150ms;
}
.crumb:hover { color: var(--tf-accent); }
.sep { color: var(--tf-text-muted); font-size: 12px; }
.crumb-current { color: var(--tf-text-primary); font-weight: 500; }

.meta { font-size: 11px; color: var(--tf-text-muted); margin-left: 8px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.meta b { color: var(--tf-text-secondary); font-weight: 500; }

.nav-group { display: flex; align-items: center; gap: 4px; margin-right: 8px; }
.nav-pos { font-size: 11px; color: var(--tf-text-muted); min-width: 36px; text-align: center; }

/* 受限访问锁定图标 */
.restricted-icon {
  color: var(--tf-text-tertiary);
  vertical-align: middle;
  margin-left: 2px;
  cursor: default;
}
</style>
