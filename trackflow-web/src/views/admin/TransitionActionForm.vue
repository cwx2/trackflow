<template>
  <a-modal
    :visible="visible"
    :title="action ? '编辑动作' : '新增动作'"
    :width="520"
    unmount-on-close
    @cancel="handleClose"
    @ok="handleSubmit"
    :ok-loading="submitting"
    ok-text="保存"
    cancel-text="取消"
  >
    <a-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      layout="vertical"
      size="medium"
    >
      <!-- 动作类型 -->
      <a-form-item field="actionType" label="动作类型">
        <a-select v-model="form.actionType" placeholder="选择动作类型">
          <a-option value="auto_assign">自动分配负责人</a-option>
        </a-select>
      </a-form-item>

      <!-- 分配策略 -->
      <a-form-item field="strategy" label="分配策略">
        <a-select v-model="form.strategy" placeholder="选择分配策略" @change="onStrategyChange">
          <a-option value="specific_user">指定用户</a-option>
          <a-option value="role_based">按角色分配</a-option>
          <a-option value="previous_assignee">回退前负责人</a-option>
          <a-option value="reporter">分配给报告人</a-option>
          <a-option value="project_lead">分配给项目负责人</a-option>
        </a-select>
      </a-form-item>

      <!-- specific_user → 用户选择器 -->
      <a-form-item
        v-if="form.strategy === 'specific_user'"
        field="userId"
        label="指定用户"
      >
        <a-select
          v-model="form.userId"
          placeholder="搜索项目成员"
          :loading="membersLoading"
          allow-search
          :filter-option="filterMembers"
        >
          <a-option
            v-for="m in members"
            :key="m.userId"
            :value="Number(m.userId)"
            :label="m.displayName"
          >
            {{ m.displayName }}
            <span style="font-size: 11px; color: var(--color-text-4); margin-left: 4px">
              @{{ m.username }}
            </span>
          </a-option>
        </a-select>
      </a-form-item>

      <!-- role_based → 角色选择器 + 模式 -->
      <template v-if="form.strategy === 'role_based'">
        <a-form-item field="roleId" label="角色">
          <a-select v-model="form.roleId" placeholder="选择角色">
            <a-option
              v-for="role in roles"
              :key="role.id"
              :value="Number(role.id)"
              :label="role.name"
            >
              {{ role.name }}
            </a-option>
          </a-select>
        </a-form-item>

        <a-form-item field="mode" label="分配模式">
          <a-radio-group v-model="form.mode" type="button">
            <a-radio value="round_robin">轮转分配</a-radio>
            <a-radio value="least_loaded">最少负载</a-radio>
            <a-radio value="weighted_round_robin">加权轮转</a-radio>
          </a-radio-group>
        </a-form-item>

        <!-- 加权配置 -->
        <a-form-item v-if="form.mode === 'weighted_round_robin'" label="成员权重">
          <div class="weight-config">
            <div v-for="m in roleMembers" :key="m.userId" class="weight-row">
              <span class="weight-name">{{ m.displayName }}</span>
              <a-input-number
                v-model="weightMap[m.userId]"
                :min="1"
                :max="10"
                size="small"
                style="width: 80px"
              />
            </div>
            <div v-if="roleMembers.length === 0" class="weight-empty">
              请先选择角色以加载成员列表
            </div>
          </div>
          <template #extra>
            <span style="font-size: 11px; color: var(--color-text-4)">
              权重越高分配越多。例如权重 3:1 表示前者被分配的频率是后者的 3 倍
            </span>
          </template>
        </a-form-item>
      </template>

      <!-- 回退策略 -->
      <a-form-item field="fallbackStrategy" label="回退策略（可选）">
        <a-select
          v-model="form.fallbackStrategy"
          placeholder="主策略失败时使用"
          allow-clear
        >
          <a-option
            v-for="opt in fallbackOptions"
            :key="opt.value"
            :value="opt.value"
          >{{ opt.label }}</a-option>
        </a-select>
        <template #extra>
          <span style="font-size: 11px; color: var(--color-text-4)">
            当主策略无法找到合适的用户时，尝试使用回退策略
          </span>
        </template>
      </a-form-item>

      <!-- 启用开关 -->
      <a-form-item field="enabled" label="启用">
        <a-switch v-model="form.enabled" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import type { FormInstance } from '@arco-design/web-vue'
import { transitionActionApi, projectApi, workflowApi } from '@/api'
import type { TransitionActionVO } from '@/api/transitionAction'
import type { ProjectMemberVO, RoleVO } from '@/api/types'

const props = defineProps<{
  visible: boolean
  action: TransitionActionVO | null
  projectId: string
  oldStatusId: string
  newStatusId: string
  issueType?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'saved'): void
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const membersLoading = ref(false)
const members = ref<ProjectMemberVO[]>([])
const roles = ref<RoleVO[]>([])

const form = reactive({
  actionType: 'auto_assign',
  strategy: '' as string,
  userId: undefined as number | undefined,
  roleId: undefined as number | undefined,
  mode: 'round_robin' as string,
  fallbackStrategy: undefined as string | undefined,
  enabled: true
})

/** 加权轮转模式下的权重配置：key = userId, value = weight */
const weightMap = reactive<Record<string, number>>({})
/** 当前选中角色的成员列表（用于加权配置 UI） */
const roleMembers = ref<ProjectMemberVO[]>([])

const formRules = computed(() => ({
  actionType: [{ required: true, message: '请选择动作类型' }],
  strategy: [{ required: true, message: '请选择分配策略' }],
  userId: form.strategy === 'specific_user'
    ? [{ required: true, message: '请选择用户' }]
    : [],
  roleId: form.strategy === 'role_based'
    ? [{ required: true, message: '请选择角色' }]
    : [],
  mode: form.strategy === 'role_based'
    ? [{ required: true, message: '请选择分配模式' }]
    : []
}))

const fallbackOptions = computed(() => {
  const all = [
    { value: 'specific_user', label: '指定用户' },
    { value: 'role_based', label: '按角色分配' },
    { value: 'previous_assignee', label: '回退前负责人' },
    { value: 'reporter', label: '报告人' },
    { value: 'project_lead', label: '项目负责人' }
  ]
  // 排除当前选中的主策略
  return all.filter(o => o.value !== form.strategy)
})

watch(() => props.visible, (val) => {
  if (val) {
    initForm()
    loadMembers()
    loadRoles()
  }
})

// 监听 roleId 变化，加载角色成员用于加权配置
watch(() => form.roleId, (newRoleId) => {
  if (newRoleId && form.mode === 'weighted_round_robin') {
    loadRoleMembers(newRoleId)
  }
})

// 监听 mode 变化到 weighted 时也加载
watch(() => form.mode, (newMode) => {
  if (newMode === 'weighted_round_robin' && form.roleId) {
    loadRoleMembers(form.roleId)
  }
})

function initForm() {
  // 清除权重配置
  Object.keys(weightMap).forEach(k => delete weightMap[k])
  roleMembers.value = []

  if (props.action) {
    const config = props.action.actionConfig
    form.actionType = props.action.actionType
    form.strategy = config.strategy
    form.userId = config.user_id
    form.roleId = config.role_id
    form.mode = config.mode || 'round_robin'
    form.fallbackStrategy = config.fallback_strategy
    form.enabled = props.action.enabled
    // 恢复权重配置
    if ((config as any).weights) {
      Object.assign(weightMap, (config as any).weights)
    }
    // 如果是 role_based 且有 roleId，加载成员列表
    if (config.strategy === 'role_based' && config.role_id) {
      loadRoleMembers(config.role_id)
    }
  } else {
    form.actionType = 'auto_assign'
    form.strategy = ''
    form.userId = undefined
    form.roleId = undefined
    form.mode = 'round_robin'
    form.fallbackStrategy = undefined
    form.enabled = true
  }
}

function onStrategyChange() {
  // 清除不相关字段
  if (form.strategy !== 'specific_user') {
    form.userId = undefined
  }
  if (form.strategy !== 'role_based') {
    form.roleId = undefined
    form.mode = 'round_robin'
    Object.keys(weightMap).forEach(k => delete weightMap[k])
    roleMembers.value = []
  }
}

/** 当 roleId 变化时加载该角色的项目成员（用于加权配置） */
async function loadRoleMembers(roleId?: number) {
  if (!roleId || !props.projectId || props.projectId === '0') {
    roleMembers.value = []
    return
  }
  try {
    const res = await projectApi.listMembers(props.projectId)
    const allMembers: ProjectMemberVO[] = res.data || []
    roleMembers.value = allMembers.filter((m: any) => {
      // 多角色：检查 roleIds 数组是否包含目标角色
      if (m.roleIds && m.roleIds.length > 0) {
        return m.roleIds.includes(String(roleId))
      }
      // 兼容：单角色 fallback
      return String(m.roleId) === String(roleId)
    })
    // 初始化未配置的成员权重为 1
    for (const m of roleMembers.value) {
      if (!weightMap[m.userId]) {
        weightMap[m.userId] = 1
      }
    }
  } catch {
    roleMembers.value = []
  }
}

async function loadMembers() {
  if (!props.projectId || props.projectId === '0') return
  membersLoading.value = true
  try {
    const res = await projectApi.listMembers(props.projectId)
    members.value = res.data || []
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

async function loadRoles() {
  try {
    const res = await workflowApi.listProjectRoles()
    roles.value = res.data || []
  } catch {
    roles.value = []
  }
}

function filterMembers(inputValue: string, option: any) {
  const label = (option.label || '').toLowerCase()
  return label.includes(inputValue.toLowerCase())
}

function handleClose() {
  emit('update:visible', false)
}

async function handleSubmit() {
  const valid = await formRef.value?.validate()
  if (valid) return

  // 构建 actionConfig
  const actionConfig: Record<string, any> = {
    strategy: form.strategy
  }
  if (form.strategy === 'specific_user') {
    actionConfig.user_id = form.userId
  }
  if (form.strategy === 'role_based') {
    actionConfig.role_id = form.roleId
    actionConfig.mode = form.mode
    // 加权轮转时包含权重配置
    if (form.mode === 'weighted_round_robin' && Object.keys(weightMap).length > 0) {
      const weights: Record<string, number> = {}
      for (const [uid, w] of Object.entries(weightMap)) {
        if (w && w > 0) weights[uid] = w
      }
      if (Object.keys(weights).length > 0) {
        actionConfig.weights = weights
      }
    }
  }
  if (form.fallbackStrategy) {
    actionConfig.fallback_strategy = form.fallbackStrategy
  }

  submitting.value = true
  try {
    if (props.action) {
      // 编辑
      await transitionActionApi.update(props.action.id, {
        actionType: form.actionType,
        actionConfig,
        enabled: form.enabled
      })
      Message.success('动作已更新')
    } else {
      // 新建
      await transitionActionApi.create({
        projectId: Number(props.projectId),
        issueType: props.issueType || '*',
        oldStatusId: Number(props.oldStatusId),
        newStatusId: Number(props.newStatusId),
        actionType: form.actionType,
        actionConfig,
        enabled: form.enabled
      })
      Message.success('动作已创建')
    }
    emit('saved')
    handleClose()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.weight-config {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 0;
}

.weight-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  border-radius: 4px;
  background: var(--color-fill-1);
}

.weight-name {
  font-size: 13px;
  color: var(--color-text-1);
}

.weight-empty {
  font-size: 12px;
  color: var(--color-text-4);
  text-align: center;
  padding: 12px;
}
</style>
