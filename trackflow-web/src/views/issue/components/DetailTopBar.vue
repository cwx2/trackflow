<template>
  <header class="detail-topbar">
    <div class="topbar-left">
      <button class="icon-btn" @click="$router.back()" title="返回">
        <icon-left :size="16" />
      </button>
      <nav class="breadcrumb">
        <a v-if="fromQueryName" class="crumb" @click="navigateBack">{{ fromQueryName }}</a>
        <a v-else class="crumb" @click="$router.push('/issues')">{{ projectName }}</a>
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

      <!-- 切换属性面板（只读模式下单独展示，始终可见） -->
      <button v-if="readonly" class="icon-btn" @click="$emit('toggle-sidebar')" title="切换属性面板">
        <icon-menu :size="16" />
      </button>

      <!-- 更多操作下拉菜单（包含快捷动作）——仅对有写权限的用户显示 -->
      <a-dropdown v-if="!readonly" trigger="click" position="br">
        <button class="icon-btn" title="更多操作">
          <icon-more :size="16" />
        </button>
        <template #content>
          <!-- 快捷动作（工作流动作） -->
          <template v-if="canQuickActions && quickActions.length > 0">
            <a-doption
              v-for="action in quickActions"
              :key="action.actionKey"
              :disabled="executingKey === action.actionKey"
              @click="handleQuickAction(action)"
            >
              <template #icon>
                <icon-thunderbolt :size="14" />
              </template>
              {{ action.label }}
            </a-doption>
            <a-divider class="dropdown-divider" />
          </template>
          <!-- 基础操作 -->
          <a-doption @click="$emit('toggle-sidebar')">
            <template #icon>
              <icon-menu :size="14" />
            </template>
            切换属性面板
          </a-doption>
        </template>
      </a-dropdown>
    </div>

    <!-- 快捷动作对话框（form 类型） -->
    <QuickActionDialog
      :visible="dialogVisible"
      :definition="selectedAction"
      :issue-id="issueId"
      :project-id="projectId"
      @update:visible="dialogVisible = $event"
      @executed="onDialogExecuted"
    />
  </header>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { IconLeft, IconRight, IconCopy, IconPlus, IconMore, IconThumbUp, IconStar, IconStarFill, IconLock, IconMenu, IconThunderbolt } from '@arco-design/web-vue/es/icon'
import { issueVoteApi, issueWatcherApi, quickActionApi } from '@/api'
import type { IssueVoteStatusVO } from '@/api/issueVote'
import type { IssueWatcherStatusVO } from '@/api/issueWatcher'
import type { QuickActionDefinitionVO } from '@/api/quickAction'
import { Message } from '@arco-design/web-vue'
import QuickActionDialog from './QuickActionDialog.vue'

const router = useRouter()

const props = defineProps<{
  issueId: string
  projectId: string
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
  /** 是否可以执行快捷动作 */
  canQuickActions?: boolean
  /** 是否为只读模式（观察者等无写权限用户），隐藏写操作按钮 */
  readonly?: boolean
  /** 来源 Saved Query 的 ID（用于面包屑返回） */
  fromQueryId?: string
  /** 来源 Saved Query 的名称（显示在面包屑中） */
  fromQueryName?: string
}>()

const emit = defineEmits<{
  prev: []
  next: []
  copy: []
  create: []
  'toggle-sidebar': []
  'quick-action-executed': []
}>()

// ===== Navigate back to source (Saved Query or issue list) =====
function navigateBack() {
  // Navigate to issue list; the query will be restored from localStorage (tf_last_active_query_id)
  router.push('/issues')
}

// ===== 快捷动作 =====
const quickActions = ref<QuickActionDefinitionVO[]>([])
const dialogVisible = ref(false)
const selectedAction = ref<QuickActionDefinitionVO | null>(null)
const executingKey = ref('')

async function loadQuickActions() {
  if (!props.issueId || !props.canQuickActions) {
    quickActions.value = []
    return
  }
  try {
    const res = await quickActionApi.getAvailableActions(props.issueId)
    quickActions.value = res.data || []
  } catch (e) {
    console.error('加载快捷动作失败', e)
    quickActions.value = []
  }
}

function handleQuickAction(action: QuickActionDefinitionVO) {
  if (action.actionType === 'rule') {
    executeRuleAction(action)
  } else {
    openDialog(action)
  }
}

function openDialog(action: QuickActionDefinitionVO) {
  selectedAction.value = action
  dialogVisible.value = true
}

async function executeRuleAction(action: QuickActionDefinitionVO) {
  executingKey.value = action.actionKey
  try {
    const res = await quickActionApi.executeRule(props.issueId, action.actionKey)
    if (res.data?.success) {
      Message.success(`「${action.label}」已执行`)
    }
    emit('quick-action-executed')
    // 重新加载可用动作（状态可能已变化）
    loadQuickActions()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '执行失败')
  } finally {
    executingKey.value = ''
  }
}

function onDialogExecuted() {
  emit('quick-action-executed')
  // 重新加载可用动作
  loadQuickActions()
}

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
    loadQuickActions()
  }
}, { immediate: true })

watch(() => props.canQuickActions, () => {
  loadQuickActions()
})
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
.watch-btn.active { color: var(--tf-star); }
.watch-btn.active:hover { color: var(--tf-star); }

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

/* 下拉菜单分隔线 */
.dropdown-divider {
  margin: 4px 0;
}
</style>
