<template>
  <a-modal
    :visible="visible"
    title="共享仪表盘"
    :width="560"
    @update:visible="$emit('update:visible', $event)"
    @ok="handleSave"
    :ok-loading="saving"
    ok-text="保存共享设置"
    cancel-text="取消"
  >
    <!-- 搜索添加区 -->
    <div class="share-search-section">
      <a-select
        v-model="searchValue"
        :loading="searching"
        placeholder="搜索用户或用户组..."
        allow-search
        allow-clear
        :filter-option="false"
        @search="handleSearch"
        @change="(v) => handleAddTarget(v as string | undefined)"
        class="share-search-select"
      >
        <a-option v-for="opt in searchOptions" :key="opt.key" :value="opt.key">
          <div class="search-option">
            <span class="option-icon">
              <icon-user v-if="opt.type === 'user'" />
              <icon-user-group v-else />
            </span>
            <span class="option-name">{{ opt.name }}</span>
            <span class="option-type">{{ opt.type === 'user' ? '用户' : '用户组' }}</span>
          </div>
        </a-option>
      </a-select>
    </div>

    <!-- 已共享列表 -->
    <div class="share-list-section">
      <div class="share-list-header">
        <span class="share-list-title">已共享给</span>
        <span class="share-list-count" v-if="shareTargets.length > 0">
          {{ shareTargets.length }} {{ shareTargets.length === 1 ? '个对象' : '个对象' }}
        </span>
      </div>

      <div v-if="shareTargets.length === 0" class="share-empty">
        <span class="empty-hint">尚未共享给任何人。在上方搜索并添加用户或用户组。</span>
      </div>

      <div v-else class="share-items">
        <div v-for="(target, index) in shareTargets" :key="target.key" class="share-item">
          <div class="share-item-info">
            <span class="share-item-icon">
              <icon-user v-if="target.type === 'user'" />
              <icon-user-group v-else />
            </span>
            <span class="share-item-name">{{ target.name }}</span>
          </div>
          <div class="share-item-actions">
            <a-select
              v-model="target.permission"
              size="small"
              :style="{ width: '90px' }"
            >
              <a-option value="view">只读</a-option>
              <a-option value="edit">可编辑</a-option>
            </a-select>
            <a-button type="text" size="small" class="remove-btn" @click="removeTarget(index)">
              <template #icon><icon-close /></template>
            </a-button>
          </div>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconClose } from '@arco-design/web-vue/es/icon'
import { customDashboardApi } from '@/api'
import { userApi } from '@/api'
import { groupApi } from '@/api/group'
import type { DashboardShareVO, ShareTarget } from '@/api/customDashboard'

interface Props {
  visible: boolean
  dashboardId: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:visible': [val: boolean]
  saved: []
}>()

// ─── 搜索状态 ─────────────────────────────────────────

interface SearchOption {
  key: string  // "user:123" or "group:456"
  type: 'user' | 'group'
  id: number
  name: string
}

const searchValue = ref<string | undefined>(undefined)
const searching = ref(false)
const searchOptions = ref<SearchOption[]>([])
let searchTimer: ReturnType<typeof setTimeout> | null = null

// ─── 共享目标列表 ─────────────────────────────────────────

interface ShareTargetItem {
  key: string
  type: 'user' | 'group'
  id: number
  name: string
  permission: 'view' | 'edit'
}

const shareTargets = ref<ShareTargetItem[]>([])
const saving = ref(false)
const loadingShares = ref(false)

// ─── 加载已有共享 ─────────────────────────────────────────

watch(() => props.visible, async (val) => {
  if (val && props.dashboardId) {
    await loadExistingShares()
  }
})

async function loadExistingShares() {
  loadingShares.value = true
  try {
    const res = await customDashboardApi.getShares(props.dashboardId)
    const shares: DashboardShareVO[] = res.data || []
    shareTargets.value = shares.map(s => ({
      key: `${s.targetType}:${s.targetId}`,
      type: s.targetType,
      id: Number(s.targetId),
      name: s.targetName,
      permission: s.permission
    }))
  } catch (e: any) {
    // 可能是非 owner 无权查看
    shareTargets.value = []
  } finally {
    loadingShares.value = false
  }
}

// ─── 搜索逻辑 ─────────────────────────────────────────

function handleSearch(keyword: string) {
  if (searchTimer) clearTimeout(searchTimer)
  if (!keyword || keyword.length < 1) {
    searchOptions.value = []
    return
  }
  searchTimer = setTimeout(async () => {
    searching.value = true
    try {
      const options: SearchOption[] = []

      // 并行搜索用户和用户组
      const [usersRes, groupsRes] = await Promise.all([
        userApi.list({ keyword, page: 1, pageSize: 10 }),
        groupApi.list({ keyword, page: 1, pageSize: 10 })
      ])

      const users = usersRes.data?.list || []
      for (const u of users) {
        options.push({
          key: `user:${u.id}`,
          type: 'user',
          id: Number(u.id),
          name: u.displayName || u.username
        })
      }

      const groups = groupsRes.data?.list || []
      for (const g of groups) {
        options.push({
          key: `group:${g.id}`,
          type: 'group',
          id: Number(g.id),
          name: g.name
        })
      }

      // 过滤已经添加的
      const existingKeys = new Set(shareTargets.value.map(t => t.key))
      searchOptions.value = options.filter(o => !existingKeys.has(o.key))
    } catch {
      searchOptions.value = []
    } finally {
      searching.value = false
    }
  }, 300)
}

function handleAddTarget(key: string | undefined) {
  if (!key) return
  const option = searchOptions.value.find(o => o.key === key)
  if (!option) return

  // 避免重复
  if (shareTargets.value.some(t => t.key === option.key)) return

  shareTargets.value.push({
    key: option.key,
    type: option.type,
    id: option.id,
    name: option.name,
    permission: 'view'
  })

  // 清空搜索
  searchValue.value = undefined
  searchOptions.value = []
}

function removeTarget(index: number) {
  shareTargets.value.splice(index, 1)
}

// ─── 保存 ─────────────────────────────────────────

async function handleSave() {
  saving.value = true
  try {
    const targets: ShareTarget[] = shareTargets.value.map(t => ({
      targetType: t.type,
      targetId: t.id,
      permission: t.permission
    }))

    await customDashboardApi.setShares(props.dashboardId, { targets })
    Message.success('共享设置已保存')
    emit('update:visible', false)
    emit('saved')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.share-search-section {
  margin-bottom: 20px;
}

.share-search-select {
  width: 100%;
}

.search-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-icon {
  font-size: 14px;
}

.option-name {
  flex: 1;
  font-size: 13px;
  color: var(--tf-text-primary);
}

.option-type {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-elevated);
  padding: 1px 6px;
  border-radius: 3px;
}

.share-list-section {
  border-top: 1px solid var(--tf-border-light);
  padding-top: 16px;
}

.share-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.share-list-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.share-list-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.share-empty {
  padding: 24px 0;
  text-align: center;
}

.empty-hint {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.share-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}

.share-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--tf-bg-elevated);
  border-radius: 6px;
  transition: background-color 0.15s;
}

.share-item:hover {
  background: var(--tf-bg-hover);
}

.share-item-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.share-item-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.share-item-name {
  font-size: 13px;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-item-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.remove-btn {
  color: var(--tf-text-tertiary);
}

.remove-btn:hover {
  color: var(--tf-danger);
}
</style>
