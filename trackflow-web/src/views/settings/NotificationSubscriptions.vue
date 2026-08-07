<template>
  <div class="subscriptions-section">
    <div class="section-header">
      <div>
        <h3 class="section-title">订阅规则</h3>
        <p class="section-desc">基于标签或保存搜索创建订阅，匹配的工单有变更时自动通知你。</p>
      </div>
      <button class="add-btn" @click="showAddDialog = true">
        <icon-plus :size="14" />
        新建订阅
      </button>
    </div>

    <!-- 订阅列表 -->
    <div v-if="loading" class="sub-loading">
      <a-spin size="small" />
      <span>加载中...</span>
    </div>

    <div v-else-if="subscriptions.length === 0" class="sub-empty">
      <span class="sub-empty-icon">📋</span>
      <span class="sub-empty-text">暂无订阅规则</span>
      <span class="sub-empty-hint">点击「新建订阅」添加基于标签或保存搜索的通知规则</span>
    </div>

    <div v-else class="sub-list">
      <div v-for="sub in subscriptions" :key="sub.id" class="sub-item">
        <div class="sub-item-left">
          <span class="sub-item-icon">{{ getSourceIcon(sub) }}</span>
          <div class="sub-item-info">
            <span class="sub-item-name">{{ sub.name }}</span>
            <span class="sub-item-source">{{ sub.sourceName }}</span>
          </div>
        </div>
        <div class="sub-item-right">
          <div class="sub-item-events">
            <span
              v-for="evt in eventKeys"
              :key="evt.key"
              class="event-tag"
              :class="{ active: sub.events[evt.key] }"
              :title="evt.label"
              @click="toggleEvent(sub, evt.key)"
            >
              {{ evt.short }}
            </span>
          </div>
          <button
            v-if="!sub.isDefault"
            class="sub-item-delete"
            title="删除订阅"
            @click="handleDelete(sub)"
          >
            <icon-close :size="12" />
          </button>
        </div>
      </div>
    </div>

    <!-- 新建订阅弹窗 -->
    <a-modal
      v-model:visible="showAddDialog"
      title="新建订阅"
      :width="440"
      :ok-loading="creating"
      ok-text="创建订阅"
      @ok="handleCreate"
      @cancel="resetForm"
    >
      <div class="add-form">
        <div class="form-field">
          <label class="form-label">订阅来源</label>
          <a-radio-group v-model="addForm.sourceType" size="small">
            <a-radio value="tag">标签</a-radio>
            <a-radio value="saved_query">保存搜索</a-radio>
            <a-radio value="project">项目</a-radio>
          </a-radio-group>
        </div>

        <div class="form-field">
          <label class="form-label">
            {{ addForm.sourceType === 'tag' ? '选择标签' : addForm.sourceType === 'saved_query' ? '选择保存搜索' : '选择项目' }}
          </label>
          <a-select
            v-model="addForm.sourceId"
            :placeholder="addForm.sourceType === 'tag' ? '选择一个标签' : addForm.sourceType === 'saved_query' ? '选择一个保存搜索' : '选择一个项目'"
            allow-search
            size="small"
          >
            <a-option
              v-for="opt in sourceOptions"
              :key="opt.id"
              :value="opt.id"
            >
              {{ opt.name }}
            </a-option>
          </a-select>
        </div>

        <div class="form-field">
          <label class="form-label">触发事件</label>
          <div class="event-checkboxes">
            <a-checkbox v-model="addForm.events.onCreated">工单被创建</a-checkbox>
            <a-checkbox v-model="addForm.events.onUpdated">工单有更新</a-checkbox>
            <a-checkbox v-model="addForm.events.onResolved">工单被解决</a-checkbox>
            <a-checkbox v-model="addForm.events.onCommented">新增评论</a-checkbox>
            <a-checkbox v-model="addForm.events.onVoted">收到投票</a-checkbox>
            <a-checkbox v-model="addForm.events.onSpentTime">工时变更</a-checkbox>
            <a-checkbox v-model="addForm.events.onTagAdded">标签被添加</a-checkbox>
            <a-checkbox v-model="addForm.events.onTagRemoved">标签被移除</a-checkbox>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { notificationSubscriptionApi } from '@/api'
import type { NotificationSubscriptionVO, SubscriptionEventsVO } from '@/api/notificationSubscription'
import { tagApi, queryApi, projectApi } from '@/api'

const loading = ref(true)
const creating = ref(false)
const showAddDialog = ref(false)
const subscriptions = ref<NotificationSubscriptionVO[]>([])

// Source options for the add dialog
const tags = ref<Array<{ id: string; name: string }>>([])
const savedQueries = ref<Array<{ id: string; name: string }>>([])
const projects = ref<Array<{ id: string; name: string }>>([])

const addForm = reactive({
  sourceType: 'tag' as 'tag' | 'saved_query' | 'project',
  sourceId: '' as string,
  events: {
    onCreated: true,
    onUpdated: true,
    onResolved: true,
    onCommented: true,
    onTagAdded: true,
    onTagRemoved: true,
    onVoted: true,
    onSpentTime: true
  }
})

const eventKeys: Array<{ key: keyof SubscriptionEventsVO; label: string; short: string }> = [
  { key: 'onCreated', label: '工单被创建', short: '创建' },
  { key: 'onUpdated', label: '工单有更新', short: '更新' },
  { key: 'onResolved', label: '工单被解决', short: '解决' },
  { key: 'onCommented', label: '新增评论', short: '评论' },
  { key: 'onVoted', label: '收到投票', short: '投票' },
  { key: 'onSpentTime', label: '工时变更', short: '工时' },
  { key: 'onTagAdded', label: '标签被添加', short: '+标签' },
  { key: 'onTagRemoved', label: '标签被移除', short: '-标签' }
]

const sourceOptions = computed(() => {
  if (addForm.sourceType === 'tag') return tags.value
  if (addForm.sourceType === 'saved_query') return savedQueries.value
  return projects.value
})

onMounted(async () => {
  await loadSubscriptions()
  await loadSourceOptions()
})

watch(() => addForm.sourceType, () => {
  addForm.sourceId = ''
})

async function loadSubscriptions() {
  loading.value = true
  try {
    const res = await notificationSubscriptionApi.list()
    if (res.code === 0 && res.data) {
      subscriptions.value = res.data
    }
  } catch (e) {
    console.error('[NotificationSub] 加载订阅列表失败:', e)
  } finally {
    loading.value = false
  }
}

async function loadSourceOptions() {
  try {
    // Load projects
    const projectRes = await projectApi.list()
    if (projectRes.code === 0 && projectRes.data) {
      const projectList = Array.isArray(projectRes.data) ? projectRes.data : (projectRes.data as any).list || []
      projects.value = projectList.map((p: any) => ({ id: String(p.id), name: p.name || p.key }))

      // Load tags from all projects
      const allTags: Array<{ id: string; name: string }> = []
      for (const p of projectList.slice(0, 10)) { // limit to 10 projects for performance
        try {
          const tagRes = await tagApi.listProjectTags(String(p.id))
          if (tagRes.code === 0 && tagRes.data) {
            for (const t of tagRes.data) {
              // Deduplicate by id
              if (!allTags.some(existing => existing.id === String(t.id))) {
                allTags.push({ id: String(t.id), name: `${t.name} (${p.name || p.key})` })
              }
            }
          }
        } catch (e) {
          console.error(`[NotificationSub] 加载项目标签失败:`, e)
        }
      }
      tags.value = allTags
    }
  } catch (e) {
    console.error('[NotificationSub] 加载来源选项失败:', e)
  }
  try {
    // Load saved queries from panel
    const queryRes = await queryApi.getPanel()
    if (queryRes.code === 0 && queryRes.data) {
      const panel = queryRes.data
      const allQueries: Array<{ id: string; name: string }> = []
      if (panel.queries) {
        for (const q of panel.queries) {
          allQueries.push({ id: String(q.id), name: q.name })
        }
      }
      if (panel.pinned) {
        for (const q of panel.pinned) {
          if (!allQueries.some(existing => existing.id === String(q.id))) {
            allQueries.push({ id: String(q.id), name: q.name })
          }
        }
      }
      savedQueries.value = allQueries
    }
  } catch (e) {
    console.error('[NotificationSub] 加载保存查询失败:', e)
  }
}

function getSourceIcon(sub: NotificationSubscriptionVO): string {
  if (sub.sourceType === 'builtin') return '⭐'
  if (sub.sourceType === 'tag') return '🏷️'
  if (sub.sourceType === 'project') return '📁'
  return '🔍'
}

async function toggleEvent(sub: NotificationSubscriptionVO, eventKey: keyof SubscriptionEventsVO) {
  const newValue = !sub.events[eventKey]
  // Optimistic update
  sub.events[eventKey] = newValue

  try {
    const res = await notificationSubscriptionApi.update(sub.id, {
      events: { [eventKey]: newValue }
    })
    if (res.code !== 0) {
      sub.events[eventKey] = !newValue // revert
      Message.error('更新失败')
    }
  } catch {
    sub.events[eventKey] = !newValue // revert
    Message.error('更新失败')
  }
}

async function handleCreate() {
  if (!addForm.sourceId) {
    Message.warning('请选择订阅来源')
    return
  }
  creating.value = true
  try {
    const res = await notificationSubscriptionApi.create({
      sourceType: addForm.sourceType,
      sourceId: addForm.sourceId,
      events: { ...addForm.events }
    })
    if (res.code === 0) {
      Message.success('订阅创建成功')
      showAddDialog.value = false
      resetForm()
      await loadSubscriptions()
    } else {
      Message.error(res.message || '创建失败')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

function handleDelete(sub: NotificationSubscriptionVO) {
  const { confirmDelete } = useConfirmDelete()
  confirmDelete({
    itemName: `订阅「${sub.name}」`,
    onConfirm: async () => {
      try {
        const res = await notificationSubscriptionApi.delete(sub.id)
        if (res.code === 0) {
          subscriptions.value = subscriptions.value.filter(s => s.id !== sub.id)
          Message.success('已删除')
        }
      } catch {
        Message.error('删除失败')
      }
    }
  })
}

function resetForm() {
  addForm.sourceType = 'tag'
  addForm.sourceId = ''
  addForm.events = {
    onCreated: true,
    onUpdated: true,
    onResolved: true,
    onCommented: true,
    onTagAdded: true,
    onTagRemoved: true
  }
}
</script>

<style scoped>
.subscriptions-section {
  margin-bottom: 32px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 4px;
}

.section-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

.add-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--tf-border);
  background: transparent;
  border-radius: 6px;
  font-size: 12px;
  color: var(--tf-text-secondary);
  cursor: pointer;
  transition: all 150ms;
  white-space: nowrap;
}

.add-btn:hover {
  background: var(--tf-bg-hover);
  border-color: var(--tf-accent);
  color: var(--tf-accent);
}

/* Loading & Empty */
.sub-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.sub-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 24px;
  text-align: center;
}

.sub-empty-icon {
  font-size: 24px;
  opacity: 0.5;
}

.sub-empty-text {
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.sub-empty-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

/* List */
.sub-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--tf-border-light);
  border-radius: 6px;
  overflow: hidden;
}

.sub-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  transition: background 150ms;
}

.sub-item:hover {
  background: var(--tf-bg-hover);
}

.sub-item + .sub-item {
  border-top: 1px solid var(--tf-border-subtle, rgba(255,255,255,0.04));
}

.sub-item-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.sub-item-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.sub-item-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.sub-item-name {
  font-size: 13px;
  color: var(--tf-text-primary);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-item-source {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-item-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.sub-item-events {
  display: flex;
  gap: 4px;
}

.event-tag {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 10px;
  cursor: pointer;
  transition: all 150ms;
  user-select: none;
  background: var(--tf-bg-surface, #22252a);
  color: var(--tf-text-tertiary);
  border: 1px solid var(--tf-border-light);
}

.event-tag.active {
  background: var(--tf-accent-subtle, rgba(88, 166, 255, 0.1));
  color: var(--tf-accent);
  border-color: var(--tf-accent-border, rgba(88, 166, 255, 0.3));
}

.event-tag:hover {
  border-color: var(--tf-accent);
}

.sub-item-delete {
  padding: 4px;
  border: none;
  background: transparent;
  color: var(--tf-text-tertiary);
  cursor: pointer;
  border-radius: 4px;
  transition: all 150ms;
  display: flex;
  align-items: center;
}

.sub-item-delete:hover {
  background: rgba(248, 81, 73, 0.1);
  color: var(--tf-error, #f85149);
}

/* Add Form */
.add-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
}

.event-checkboxes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
</style>
