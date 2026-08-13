<template>
  <div v-if="teamMembers.length > 0 && totalIssueCount > 0" class="widget-project-team">
    <!-- 汇总行 -->
    <div class="team-summary-row">
      <span class="summary-label">Total</span>
      <span class="summary-count">{{ totalIssueCount }}</span>
      <span class="summary-unit">工单</span>
    </div>

    <!-- 成员列表 -->
    <div class="team-member-list">
      <div
        v-for="member in teamMembers"
        :key="member.userId"
        class="team-member-row"
        @click="goToMemberIssues(member)"
      >
        <div class="member-left">
          <UserAvatar :name="member.displayName || member.username" :size="28" />
          <div class="member-info">
            <div class="member-name">{{ member.displayName || member.username }}</div>
            <div class="member-role">{{ member.roleName || '—' }}</div>
          </div>
        </div>

        <div class="member-right">
          <div class="member-stats">
            <span class="count-number" :class="{ 'has-issues': member.openIssueCount > 0 }">
              {{ member.openIssueCount }}
            </span>
            <span class="count-pct">{{ getMemberPct(member.openIssueCount) }}%</span>
          </div>
          <div class="member-bar-track">
            <div
              class="member-bar-fill"
              :style="{ width: getBarWidth(member.openIssueCount) + '%' }"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 有成员但全部工单数为 0：简洁空状态 -->
  <div v-else-if="teamMembers.length > 0 && totalIssueCount === 0" class="widget-no-issues">
    <icon-user-group :size="28" class="no-issues-icon" />
    <span class="no-issues-text">{{ teamMembers.length }} 位成员，暂无分配工单</span>
  </div>

  <div v-else class="widget-configure-hint">
    <icon-user-group :size="32" class="hint-icon" />
    <span class="hint-text">{{ !(config.projectId || dashboardProjectId) ? '点击「编辑配置」选择项目' : '该项目暂无成员' }}</span>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { IconUserGroup } from '@arco-design/web-vue/es/icon'
import { dashboardApi } from '@/api/dashboard'
import type { ProjectTeamMemberVO } from '@/api/dashboard'
import { UserAvatar } from '@/components/base'

const props = defineProps<{
  config: Record<string, any>
  /** 仪表盘所属项目 ID（来自 project_overview 仪表盘），用作隐式过滤条件 */
  dashboardProjectId?: string
}>()

const emit = defineEmits<{
  loaded: []
  error: [message: string]
  'permission-denied': []
}>()

const router = useRouter()
const teamMembers = ref<ProjectTeamMemberVO[]>([])

// 汇总：全部成员工单总数
const totalIssueCount = computed(() =>
  teamMembers.value.reduce((sum, m) => sum + (m.openIssueCount || 0), 0)
)

// 最大值：用于柱状条宽度计算（最多的人 = 100%）
const maxIssueCount = computed(() =>
  Math.max(...teamMembers.value.map(m => m.openIssueCount || 0), 1)
)

/**
 * 进度条宽度：按「当前成员 / 最多工单成员」比例计算
 */
function getBarWidth(count: number): number {
  return Math.round((count / maxIssueCount.value) * 100)
}

/**
 * 百分比：按「当前成员 / 全部成员总数」计算
 */
function getMemberPct(count: number): number {
  if (totalIssueCount.value === 0) return 0
  return Math.round((count / totalIssueCount.value) * 100)
}

/**
 * 跳转到工单列表，按成员筛选未关闭工单
 */
function goToMemberIssues(member: ProjectTeamMemberVO) {
  const name = member.displayName || member.username
  const query: Record<string, string> = {
    assignee: member.userId,
    label: `${name} 的未关闭工单`
  }
  const projectId = props.config.projectId || props.dashboardProjectId
  if (projectId) {
    query.projectId = projectId
  }
  router.push({ path: '/issues', query })
}

async function loadData(_force = false) {
  try {
    const projectId = props.config.projectId || props.dashboardProjectId
    if (!projectId) {
      teamMembers.value = []
      emit('loaded')
      return
    }
    const params: { projectId: string; limit?: number } = { projectId }
    if (props.config.limit && props.config.limit > 0) {
      params.limit = props.config.limit
    }
    const res = await dashboardApi.projectTeam(params, { _silent403: true })
    teamMembers.value = res.data || []
    emit('loaded')
  } catch (e: any) {
    const status = e.response?.status
    if (status === 403) {
      emit('permission-denied')
    } else {
      emit('error', e.response?.data?.message || '加载项目团队数据失败')
    }
  }
}

onMounted(() => {
  loadData()
})

defineExpose({ loadData })
</script>

<style scoped>
.widget-project-team {
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  max-height: 100%;
}

/* 汇总行 */
.team-summary-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 4px 8px 8px;
  border-bottom: 1px solid var(--tf-border-light, var(--color-border-2));
  margin-bottom: 4px;
}

.summary-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-secondary);
}

.summary-count {
  font-size: 20px;
  font-weight: 700;
  color: var(--tf-text-primary);
}

.summary-unit {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

/* 成员列表 */
.team-member-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.team-member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.team-member-row:hover {
  background: var(--tf-bg-hover);
}

/* 左侧：头像 + 信息 */
.member-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 130px;
  min-width: 0;
}

.member-info {
  flex: 1;
  min-width: 0;
}

.member-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-role {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 右侧：数量 + 百分比 + 进度条 */
.member-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.member-stats {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.count-number {
  font-size: 13px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  min-width: 20px;
  text-align: right;
}

.count-number.has-issues {
  color: var(--tf-accent);
}

.count-pct {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  min-width: 28px;
}

/* 进度条 */
.member-bar-track {
  height: 4px;
  background: var(--tf-bg-elevated, var(--color-fill-2));
  border-radius: 2px;
  overflow: hidden;
}

.member-bar-fill {
  height: 100%;
  background: var(--tf-accent, var(--color-primary-6));
  opacity: 0.7;
  border-radius: 2px;
  transition: width 0.4s ease;
}

/* 配置提示 */
.widget-configure-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 8px;
  padding: 12px;
}

.hint-icon {
  color: var(--tf-text-tertiary);
  opacity: 0.4;
}

.hint-text {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-align: center;
  line-height: 1.4;
}

/* 有成员但无工单的简洁空状态 */
.widget-no-issues {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 8px;
  padding: 12px;
}

.no-issues-icon {
  color: var(--tf-text-tertiary);
  opacity: 0.5;
}

.no-issues-text {
  font-size: 12px;
  color: var(--tf-text-secondary);
  text-align: center;
  line-height: 1.4;
}
</style>
