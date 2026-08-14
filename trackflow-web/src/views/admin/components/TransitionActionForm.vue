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
        <a-select v-model="form.actionType" placeholder="选择动作类型" @change="onActionTypeChange">
          <a-option value="auto_assign">自动分配负责人</a-option>
          <a-option value="add_comment">自动添加评论</a-option>
          <a-option value="add_tag">自动添加标签</a-option>
          <a-option value="require_field">要求必填字段</a-option>
        </a-select>
      </a-form-item>

      <!-- ===== auto_assign 专用字段 ===== -->
      <template v-if="form.actionType === 'auto_assign'">
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
      </template>

      <!-- ===== add_comment 专用字段 ===== -->
      <template v-if="form.actionType === 'add_comment'">
        <a-form-item field="commentTemplate" label="评论内容">
          <a-textarea
            v-model="form.commentTemplate"
            placeholder="输入评论模板文本，支持占位符：{issue_key}, {old_status}, {new_status}"
            :auto-size="{ minRows: 3, maxRows: 8 }"
          />
          <template #extra>
            <span style="font-size: 11px; color: var(--color-text-4)">
              留空则使用默认模板：「状态已从「旧状态」变更为「新状态」。」
            </span>
          </template>
        </a-form-item>
      </template>

      <!-- ===== add_tag 专用字段 ===== -->
      <template v-if="form.actionType === 'add_tag'">
        <a-form-item field="tagName" label="标签名称">
          <a-input
            v-model="form.tagName"
            placeholder="输入标签名（如项目中不存在将自动创建）"
          />
          <template #extra>
            <span style="font-size: 11px; color: var(--color-text-4)">
              当状态转换发生时，自动为工单添加此标签。如果标签不存在会自动创建。
            </span>
          </template>
        </a-form-item>
      </template>

      <!-- ===== require_field 专用字段 ===== -->
      <template v-if="form.actionType === 'require_field'">
        <a-form-item field="requiredFieldId" label="必填字段">
          <a-select
            v-model="form.requiredFieldId"
            placeholder="选择必须填写的字段"
            :loading="fieldsLoading"
          >
            <a-option
              v-for="f in customFields"
              :key="f.id"
              :value="Number(f.id)"
              :label="f.name"
            >
              {{ f.name }}
              <span style="font-size: 11px; color: var(--color-text-4); margin-left: 4px">
                ({{ f.fieldType }})
              </span>
            </a-option>
          </a-select>
          <template #extra>
            <span style="font-size: 11px; color: var(--color-text-4)">
              转换时如果此字段为空，将阻止状态变更并显示警告。
            </span>
          </template>
        </a-form-item>

        <a-form-item field="warningMessage" label="警告消息（可选）">
          <a-input
            v-model="form.warningMessage"
            placeholder="请先填写「{field_name}」字段"
          />
        </a-form-item>
      </template>

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
import { handleApiError } from '@/utils/errorHandler'
import type { FormInstance } from '@arco-design/web-vue'
import { transitionActionApi, projectApi, workflowApi, customFieldApi } from '@/api'
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
const fieldsLoading = ref(false)
const members = ref<ProjectMemberVO[]>([])
const roles = ref<RoleVO[]>([])
const customFields = ref<Array<{ id: string; name: string; fieldType: string }>>([])

const form = reactive({
  actionType: 'auto_assign' as string,
  // auto_assign fields
  strategy: '' as string,
  userId: undefined as number | undefined,
  roleId: undefined as number | undefined,
  mode: 'round_robin' as string,
  fallbackStrategy: undefined as string | undefined,
  // add_comment fields
  commentTemplate: '' as string,
  // add_tag fields
  tagName: '' as string,
  // require_field fields
  requiredFieldId: undefined as number | undefined,
  warningMessage: '' as string,
  // common
  enabled: true
})

/** 加权轮转模式下的权重配置：key = userId, value = weight */
const weightMap = reactive<Record<string, number>>({})
/** 当前选中角色的成员列表（用于加权配置 UI） */
const roleMembers = ref<ProjectMemberVO[]>([])

const formRules = computed(() => {
  const rules: Record<string, any[]> = {
    actionType: [{ required: true, message: '请选择动作类型' }]
  }

  if (form.actionType === 'auto_assign') {
    rules.strategy = [{ required: true, message: '请选择分配策略' }]
    if (form.strategy === 'specific_user') {
      rules.userId = [{ required: true, message: '请选择用户' }]
    }
    if (form.strategy === 'role_based') {
      rules.roleId = [{ required: true, message: '请选择角色' }]
      rules.mode = [{ required: true, message: '请选择分配模式' }]
    }
  } else if (form.actionType === 'add_tag') {
    rules.tagName = [{ required: true, message: '请输入标签名称' }]
  } else if (form.actionType === 'require_field') {
    rules.requiredFieldId = [{ required: true, message: '请选择必填字段' }]
  }

  return rules
})

const fallbackOptions = computed(() => {
  const all = [
    { value: 'specific_user', label: '指定用户' },
    { value: 'role_based', label: '按角色分配' },
    { value: 'previous_assignee', label: '回退前负责人' },
    { value: 'reporter', label: '报告人' },
    { value: 'project_lead', label: '项目负责人' }
  ]
  return all.filter(o => o.value !== form.strategy)
})

watch(() => props.visible, (val) => {
  if (val) {
    initForm()
    loadMembers()
    loadRoles()
    if (form.actionType === 'require_field') {
      loadCustomFields()
    }
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

function onActionTypeChange() {
  // 切换动作类型时加载必要数据
  if (form.actionType === 'require_field' && customFields.value.length === 0) {
    loadCustomFields()
  }
}

function initForm() {
  // 清除权重配置
  Object.keys(weightMap).forEach(k => delete weightMap[k])
  roleMembers.value = []

  if (props.action) {
    const config = props.action.actionConfig
    form.actionType = props.action.actionType
    form.enabled = props.action.enabled

    if (props.action.actionType === 'auto_assign') {
      form.strategy = config.strategy || ''
      form.userId = config.user_id
      form.roleId = config.role_id
      form.mode = config.mode || 'round_robin'
      form.fallbackStrategy = config.fallback_strategy
      if ((config as any).weights) {
        Object.assign(weightMap, (config as any).weights)
      }
      if (config.strategy === 'role_based' && config.role_id) {
        loadRoleMembers(config.role_id)
      }
    } else if (props.action.actionType === 'add_comment') {
      form.commentTemplate = config.comment_template || ''
    } else if (props.action.actionType === 'add_tag') {
      form.tagName = config.tag_name || ''
    } else if (props.action.actionType === 'require_field') {
      form.requiredFieldId = config.required_field_id
      form.warningMessage = config.warning_message || ''
      loadCustomFields()
    }
  } else {
    form.actionType = 'auto_assign'
    form.strategy = ''
    form.userId = undefined
    form.roleId = undefined
    form.mode = 'round_robin'
    form.fallbackStrategy = undefined
    form.commentTemplate = ''
    form.tagName = ''
    form.requiredFieldId = undefined
    form.warningMessage = ''
    form.enabled = true
  }
}

function onStrategyChange() {
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

async function loadRoleMembers(roleId?: number) {
  if (!roleId || !props.projectId || props.projectId === '0') {
    roleMembers.value = []
    return
  }
  try {
    const res = await projectApi.listMembers(props.projectId)
    const allMembers: ProjectMemberVO[] = res.data || []
    roleMembers.value = allMembers.filter((m: any) => {
      if (m.roleIds && m.roleIds.length > 0) {
        return m.roleIds.includes(String(roleId))
      }
      return String(m.roleId) === String(roleId)
    })
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

async function loadCustomFields() {
  if (!props.projectId || props.projectId === '0') return
  fieldsLoading.value = true
  try {
    const res = await customFieldApi.listByProject(props.projectId)
    customFields.value = (res.data || []).map((f: any) => ({
      id: f.id,
      name: f.name,
      fieldType: f.fieldFormat || f.fieldType || 'text'
    }))
  } catch {
    customFields.value = []
  } finally {
    fieldsLoading.value = false
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

  // 构建 actionConfig 根据 actionType
  const actionConfig: Record<string, any> = {}

  if (form.actionType === 'auto_assign') {
    actionConfig.strategy = form.strategy
    if (form.strategy === 'specific_user') {
      actionConfig.user_id = form.userId
    }
    if (form.strategy === 'role_based') {
      actionConfig.role_id = form.roleId
      actionConfig.mode = form.mode
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
  } else if (form.actionType === 'add_comment') {
    if (form.commentTemplate.trim()) {
      actionConfig.comment_template = form.commentTemplate.trim()
    }
  } else if (form.actionType === 'add_tag') {
    actionConfig.tag_name = form.tagName.trim()
  } else if (form.actionType === 'require_field') {
    actionConfig.required_field_id = form.requiredFieldId
    if (form.warningMessage.trim()) {
      actionConfig.warning_message = form.warningMessage.trim()
    }
  }

  submitting.value = true
  try {
    if (props.action) {
      await transitionActionApi.update(props.action.id, {
        actionType: form.actionType,
        actionConfig,
        enabled: form.enabled
      })
      Message.success('动作已更新')
    } else {
      await transitionActionApi.create({
        projectId: Number(props.projectId),
        issueType: props.issueType || '*',
        triggerType: 'transition',
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
  } catch (e) {
    handleApiError(e, '操作失败')
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
