<template>
  <a-drawer
    :visible="visible"
    :width="420"
    :footer="false"
    unmount-on-close
    @cancel="handleClose"
  >
    <template #title>
      <div class="panel-title">
        <span>动作配置</span>
        <span class="transition-path">{{ oldStatusName }} → {{ newStatusName }}</span>
      </div>
    </template>

    <!-- 转换显示名称配置 -->
    <div class="transition-name-section">
      <div class="section-label">转换名称</div>
      <div class="name-input-row">
        <a-input
          v-model="editTransitionName"
          :max-length="100"
          placeholder="留空则显示目标状态名"
          allow-clear
          @press-enter="saveTransitionName"
        />
        <a-button
          type="primary"
          size="small"
          :loading="nameSaving"
          :disabled="nameSaving"
          @click="saveTransitionName"
        >保存</a-button>
      </div>
      <div class="name-hint">配置后，工单状态下拉将显示此名称而非目标状态名（如"开始处理"）</div>
    </div>

    <DataContainer
      :loading="loading"
      :is-empty="actions.length === 0"
      empty-title="暂无配置的动作"
      empty-description="为此转换路径添加自动化动作，如自动分配负责人。"
    >
      <!-- 动作列表 -->
      <div class="action-list">
        <div
          v-for="action in actions"
          :key="action.id"
          class="action-item"
          :class="{ 'path-invalid': action.pathValid === false }"
        >
          <div class="action-main">
            <div class="action-header">
              <a-tag color="arcoblue" size="small">{{ actionTypeLabel(action.actionType) }}</a-tag>
              <span class="strategy-desc">{{ strategyDescription(action.actionConfig, action.actionType) }}</span>
              <a-tooltip v-if="action.pathValid === false" content="该动作绑定的转换路径已被删除，动作不会触发">
                <icon-exclamation-circle-fill class="path-warning-icon" />
              </a-tooltip>
            </div>
            <div class="action-meta">
              <span class="sort-order">排序: {{ action.sortOrder }}</span>
              <span v-if="action.pathValid === false" class="path-invalid-label">路径已禁用</span>
              <span v-if="action.actionConfig.fallback_strategy" class="fallback-info">
                回退: {{ strategyLabel(action.actionConfig.fallback_strategy) }}
              </span>
            </div>
          </div>
          <div class="action-ops">
            <a-switch
              :model-value="action.enabled"
              size="small"
              @change="handleToggle(action)"
            />
            <a-button
              type="text"
              size="mini"
              @click="handleEdit(action)"
            >
              编辑
            </a-button>
            <a-popconfirm
              content="确定删除此动作配置？"
              @ok="handleDelete(action)"
            >
              <a-button type="text" size="mini" status="danger">
                删除
              </a-button>
            </a-popconfirm>
          </div>
        </div>
      </div>

    </DataContainer>

    <!-- 底部操作 -->
    <div class="panel-footer">
      <a-button type="primary" long @click="handleAdd">
        <template #icon><icon-plus /></template>
        新增动作
      </a-button>
    </div>

    <!-- 表单弹窗 -->
    <TransitionActionForm
      v-model:visible="formVisible"
      :action="editingAction"
      :project-id="projectId"
      :old-status-id="oldStatusId"
      :new-status-id="newStatusId"
      :issue-type="issueType"
      @saved="onFormSaved"
    />
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconPlus, IconThunderbolt, IconExclamationCircleFill } from '@arco-design/web-vue/es/icon'
import { transitionActionApi, workflowApi } from '@/api'
import type { TransitionActionVO } from '@/api/transitionAction'
import TransitionActionForm from './TransitionActionForm.vue'
import DataContainer from '@/components/base/DataContainer.vue'

const props = defineProps<{
  visible: boolean
  projectId: string
  oldStatusId: string
  newStatusId: string
  oldStatusName: string
  newStatusName: string
  issueType?: string
  transitionId?: string
  transitionName?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'refresh'): void
  (e: 'name-updated', transitionId: string, newName: string | null): void
}>()

const loading = ref(false)
const actions = ref<TransitionActionVO[]>([])
const formVisible = ref(false)
const editingAction = ref<TransitionActionVO | null>(null)

// 转换名称编辑
const editTransitionName = ref('')
const nameSaving = ref(false)

watch(() => props.visible, (val) => {
  if (val) {
    editTransitionName.value = props.transitionName || ''
    loadActions()
  }
})

async function saveTransitionName() {
  if (!props.transitionId) return
  const newName = editTransitionName.value.trim() || null
  nameSaving.value = true
  try {
    await workflowApi.updateTransitionName(props.transitionId, newName)
    Message.success(newName ? '转换名称已保存' : '转换名称已清除')
    emit('name-updated', props.transitionId, newName)
  } catch {
    Message.error('保存转换名称失败')
  } finally {
    nameSaving.value = false
  }
}

async function loadActions() {
  loading.value = true
  try {
    const res = await transitionActionApi.list(props.projectId, {
      oldStatusId: Number(props.oldStatusId),
      newStatusId: Number(props.newStatusId)
    })
    actions.value = res.data || []
  } catch {
    actions.value = []
    Message.error('加载动作列表失败')
  } finally {
    loading.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
}

function handleAdd() {
  editingAction.value = null
  formVisible.value = true
}

function handleEdit(action: TransitionActionVO) {
  editingAction.value = action
  formVisible.value = true
}

async function handleToggle(action: TransitionActionVO) {
  try {
    await transitionActionApi.toggleEnabled(action.id)
    action.enabled = !action.enabled
    Message.success(action.enabled ? '已启用' : '已禁用')
    emit('refresh')
  } catch {
    Message.error('操作失败')
  }
}

async function handleDelete(action: TransitionActionVO) {
  try {
    await transitionActionApi.delete(action.id)
    Message.success('已删除')
    await loadActions()
    emit('refresh')
  } catch {
    Message.error('删除失败')
  }
}

function onFormSaved() {
  loadActions()
  emit('refresh')
}

// === 显示辅助 ===

function actionTypeLabel(type: string): string {
  const map: Record<string, string> = {
    auto_assign: '自动分配',
    add_comment: '添加评论',
    add_tag: '添加标签',
    require_field: '必填字段'
  }
  return map[type] || type
}

function strategyLabel(strategy: string): string {
  const map: Record<string, string> = {
    specific_user: '指定用户',
    role_based: '按角色分配',
    previous_assignee: '回退前负责人',
    reporter: '报告人',
    project_lead: '项目负责人'
  }
  return map[strategy] || strategy
}

function strategyDescription(config: TransitionActionVO['actionConfig'], actionType?: string): string {
  // 根据动作类型展示对应配置信息
  if (actionType === 'add_comment') {
    const template = config.comment_template || ''
    return template.length > 30 ? template.substring(0, 30) + '...' : (template || '默认模板')
  }
  if (actionType === 'add_tag') {
    return config.tag_name ? `标签: ${config.tag_name}` : '未指定标签'
  }
  if (actionType === 'require_field') {
    return config.required_field_name || (config.required_field_id ? `字段 #${config.required_field_id}` : '未指定字段')
  }

  // auto_assign 分配策略描述
  switch (config.strategy) {
    case 'specific_user':
      return '指定用户'
    case 'role_based':
      if (config.mode === 'round_robin') return '按角色轮转分配'
      if (config.mode === 'least_loaded') return '按角色最少负载分配'
      if (config.mode === 'weighted_round_robin') return '按角色加权轮转'
      return '按角色分配'
    case 'previous_assignee':
      return '回退前负责人'
    case 'reporter':
      return '分配给报告人'
    case 'project_lead':
      return '分配给项目负责人'
    default:
      return config.strategy || ''
  }
}
</script>

<style scoped>
.panel-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.panel-title span:first-child {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-bright, var(--color-text-1));
}

.transition-path {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-secondary, var(--color-text-3));
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.transition-name-section {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border-2, rgba(255,255,255,0.08));
}

.transition-name-section .section-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary, var(--color-text-2));
  margin-bottom: 6px;
}

.transition-name-section .name-input-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.transition-name-section .name-hint {
  font-size: 11px;
  color: var(--text-tertiary, var(--color-text-4));
  margin-top: 4px;
}

.action-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-radius: 6px;
  background: var(--bg-secondary, var(--color-fill-2));
  transition: background 150ms;
}

.action-item:hover {
  background: var(--bg-tertiary, var(--color-fill-3));
}

.action-item.path-invalid {
  border-left: 3px solid var(--color-warning-6, #ff7d00);
  opacity: 0.75;
}

.path-warning-icon {
  color: var(--color-warning-6, #ff7d00);
  font-size: 14px;
  flex-shrink: 0;
}

.path-invalid-label {
  color: var(--color-warning-6, #ff7d00);
  font-weight: 500;
}

.action-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.action-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.strategy-desc {
  font-size: 13px;
  color: var(--text-primary, var(--color-text-1));
}

.action-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11px;
  color: var(--text-muted, var(--color-text-4));
}

.action-ops {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.panel-footer {
  position: sticky;
  bottom: 0;
  padding: 16px 0;
  background: var(--bg-primary, var(--color-bg-2));
  border-top: 1px solid var(--border-color, var(--color-border-2));
  margin-top: 16px;
}
</style>
