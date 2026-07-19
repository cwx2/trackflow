<template>
  <div v-if="actions.length > 0" class="quick-action-bar">
    <!-- 前 3 个按钮直接显示 -->
    <a-button
      v-for="action in visibleActions"
      :key="action.actionKey"
      size="small"
      class="qa-btn"
      :loading="executingKey === action.actionKey"
      @click="handleClick(action)"
    >
      {{ action.label }}
    </a-button>

    <!-- 超出 3 个折叠到下拉 -->
    <a-dropdown v-if="overflowActions.length > 0" :trigger="['click']">
      <a-button size="small" class="qa-btn qa-btn-more">
        更多
        <template #icon><icon-down /></template>
      </a-button>
      <template #content>
        <a-doption
          v-for="action in overflowActions"
          :key="action.actionKey"
          @click="handleClick(action)"
        >{{ action.label }}</a-doption>
      </template>
    </a-dropdown>

    <!-- 弹窗（仅 form 类型使用） -->
    <QuickActionDialog
      :visible="dialogVisible"
      :definition="selectedAction"
      :issue-id="issueId"
      :project-id="projectId"
      @update:visible="dialogVisible = $event"
      @executed="$emit('executed')"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconDown } from '@arco-design/web-vue/es/icon'
import { quickActionApi } from '@/api'
import type { QuickActionDefinitionVO } from '@/api/quickAction'
import QuickActionDialog from './QuickActionDialog.vue'

const props = defineProps<{
  issueId: string
  projectId: string
}>()

const emit = defineEmits<{
  executed: []
}>()

const actions = ref<QuickActionDefinitionVO[]>([])
const dialogVisible = ref(false)
const selectedAction = ref<QuickActionDefinitionVO | null>(null)
const executingKey = ref('')

const MAX_VISIBLE = 3

const visibleActions = computed(() => actions.value.slice(0, MAX_VISIBLE))
const overflowActions = computed(() => actions.value.slice(MAX_VISIBLE))

async function loadActions() {
  if (!props.issueId) return
  try {
    const res = await quickActionApi.getAvailableActions(props.issueId)
    actions.value = res.data || []
  } catch (e) {
    console.error('加载快捷动作失败', e)
    actions.value = []
  }
}

function handleClick(action: QuickActionDefinitionVO) {
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
    emit('executed')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '执行失败')
  } finally {
    executingKey.value = ''
  }
}

// 加载动作列表
onMounted(loadActions)
watch(() => props.issueId, loadActions)
</script>

<style scoped>
.quick-action-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
}

.qa-btn {
  font-size: 12px;
  border-color: var(--tf-border);
  color: var(--tf-text-secondary);
  transition: all 0.15s;
}

.qa-btn:hover {
  border-color: var(--tf-accent);
  color: var(--tf-accent);
}

.qa-btn-more {
  display: flex;
  align-items: center;
  gap: 2px;
}
</style>
