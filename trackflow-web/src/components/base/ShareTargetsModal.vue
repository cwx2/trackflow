<script setup lang="ts">
/**
 * ShareTargetsModal — 通用「共享给用户/用户组」弹窗
 *
 * 职责：
 * - 提供搜索用户/用户组、添加共享目标、设置权限、移除目标的 UI
 * - 不自己调用 API 保存，通过 emit('save', targets) 将结果交给父组件处理
 *
 * 对外接口：
 * - Props: visible, title, permissionOptions, hintContent, initialTargets, saving
 * - Emits: 'update:visible', 'save'(targets: ShareTargetItem[])
 */
import { ref, watch } from 'vue'
import { IconClose } from '@arco-design/web-vue/es/icon'
import { userApi } from '@/api'
import { groupApi } from '@/api/group'

export interface ShareTargetItem {
  key: string          // "user:123" or "group:456"
  type: 'user' | 'group'
  id: number
  name: string
  permission: string   // 'view' | 'edit' 等，由父组件 permissionOptions 决定
}

export interface PermissionOption {
  value: string
  label: string
}

interface Props {
  visible: boolean
  title?: string
  /** 权限选项，默认 [{value:'view',label:'可查看'},{value:'edit',label:'可编辑'}] */
  permissionOptions?: PermissionOption[]
  /** 弹窗底部说明，支持 HTML */
  hintContent?: string
  /** 初始共享目标（打开时回显） */
  initialTargets?: ShareTargetItem[]
  /** 保存加载中（父组件控制） */
  saving?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  title: '共享设置',
  permissionOptions: () => [
    { value: 'view', label: '可查看' },
    { value: 'edit', label: '可编辑' },
  ],
  hintContent: '',
  initialTargets: () => [],
  saving: false,
})

const emit = defineEmits<{
  'update:visible': [val: boolean]
  /** 点击确定时，把当前共享列表传给父组件，父组件负责调用 API */
  save: [targets: ShareTargetItem[]]
}>()

// ===== 搜索状态 =====

interface SearchOption {
  key: string
  type: 'user' | 'group'
  id: number
  name: string
}

const searchValue = ref<string | undefined>(undefined)
const searching = ref(false)
const searchOptions = ref<SearchOption[]>([])
let searchTimer: ReturnType<typeof setTimeout> | null = null

// ===== 共享目标列表 =====

const shareTargets = ref<ShareTargetItem[]>([])

// 每次打开时用 initialTargets 初始化
watch(
  () => props.visible,
  (val) => {
    if (val) {
      shareTargets.value = props.initialTargets.map(t => ({ ...t }))
      searchValue.value = undefined
      searchOptions.value = []
    }
  },
)

// ===== 搜索逻辑 =====

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
      const [usersRes, groupsRes] = await Promise.all([
        userApi.list({ keyword, page: 1, pageSize: 10 }),
        groupApi.list({ keyword, page: 1, pageSize: 10 }),
      ])
      for (const u of usersRes.data?.list || []) {
        options.push({ key: `user:${u.id}`, type: 'user', id: Number(u.id), name: u.displayName || u.username })
      }
      for (const g of groupsRes.data?.list || []) {
        options.push({ key: `group:${g.id}`, type: 'group', id: Number(g.id), name: g.name })
      }
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
  if (shareTargets.value.some(t => t.key === option.key)) return
  shareTargets.value.push({
    key: option.key,
    type: option.type,
    id: option.id,
    name: option.name,
    permission: props.permissionOptions[0]?.value ?? 'view',
  })
  searchValue.value = undefined
  searchOptions.value = []
}

function removeTarget(index: number) {
  shareTargets.value.splice(index, 1)
}

function handleOk() {
  emit('save', shareTargets.value.map(t => ({ ...t })))
}
</script>

<template>
  <a-modal
    :visible="visible"
    :title="title"
    :width="560"
    :ok-loading="saving"
    ok-text="保存共享设置"
    cancel-text="取消"
    @update:visible="$emit('update:visible', $event)"
    @ok="handleOk"
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
        class="share-search-select"
        @search="handleSearch"
        @change="(v) => handleAddTarget(v as string | undefined)"
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
        <span v-if="shareTargets.length > 0" class="share-list-count">{{ shareTargets.length }} 个对象</span>
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
            <a-select v-model="target.permission" size="small" :style="{ width: '100px' }">
              <a-option v-for="opt in permissionOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </a-option>
            </a-select>
            <a-button type="text" size="small" class="remove-btn" @click="removeTarget(index)">
              <template #icon><icon-close /></template>
            </a-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 可选说明 -->
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div v-if="hintContent" class="share-hint" v-html="hintContent" />
  </a-modal>
</template>

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
.share-hint {
  margin-top: 16px;
  padding: 12px;
  background: var(--tf-bg-elevated);
  border-radius: 6px;
  font-size: 12px;
  color: var(--tf-text-secondary);
  line-height: 1.6;
}
:deep(.share-hint p) {
  margin: 0;
}
:deep(.share-hint strong) {
  color: var(--tf-text-primary);
}
</style>
