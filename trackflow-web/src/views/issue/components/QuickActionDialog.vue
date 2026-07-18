<template>
  <a-modal
    :visible="visible"
    :title="definition?.label || '快捷动作'"
    :width="560"
    :footer="false"
    :mask-closable="true"
    @cancel="close"
  >
    <div class="quick-action-form">
      <!-- 动态表单字段 -->
      <a-form ref="formRef" :model="formData" layout="vertical">
        <a-form-item
          v-for="field in formFields"
          :key="field.key"
          :label="field.label"
          :field="field.key"
          :rules="field.required ? [{ required: true, message: `${field.label} 不能为空` }] : undefined"
        >
          <!-- Radio -->
          <a-radio-group
            v-if="field.type === 'radio'"
            v-model="formData[field.key]"
            direction="vertical"
          >
            <a-radio
              v-for="opt in getOptions(field)"
              :key="opt"
              :value="opt"
            >{{ opt }}</a-radio>
          </a-radio-group>

          <!-- Checkbox -->
          <a-checkbox-group
            v-if="field.type === 'checkbox'"
            v-model="formData[field.key]"
          >
            <a-checkbox
              v-for="opt in getOptions(field)"
              :key="opt"
              :value="opt"
            >{{ opt }}</a-checkbox>
          </a-checkbox-group>

          <!-- Select -->
          <a-select
            v-if="field.type === 'select'"
            v-model="formData[field.key]"
            :placeholder="field.placeholder || `选择${field.label}`"
            allow-clear
          >
            <a-option v-for="opt in getOptions(field)" :key="opt" :value="opt">{{ opt }}</a-option>
          </a-select>

          <!-- Input -->
          <a-input
            v-if="field.type === 'input'"
            v-model="formData[field.key]"
            :placeholder="field.placeholder || ''"
          />

          <!-- Textarea -->
          <a-textarea
            v-if="field.type === 'textarea'"
            v-model="formData[field.key]"
            :placeholder="field.placeholder || ''"
            :auto-size="{ minRows: 3, maxRows: 8 }"
          />
        </a-form-item>
      </a-form>
    </div>

    <!-- 底部操作按钮 -->
    <div class="quick-action-footer">
      <a-button @click="close">取消</a-button>
      <a-button
        v-for="action in actionButtons"
        :key="action.key"
        :type="action.type === 'primary' ? 'primary' : undefined"
        :loading="executing && executingAction === action.key"
        :disabled="executing"
        @click="executeAction(action.key)"
      >{{ action.label }}</a-button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { quickActionApi } from '@/api'
import type {
  QuickActionDefinitionVO,
  QuickActionFormField,
  QuickActionButton,
  MailTemplateVO,
} from '@/api/quickAction'

const props = defineProps<{
  visible: boolean
  definition: QuickActionDefinitionVO | null
  issueId: string
  projectId: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'executed': []
}>()

const formRef = ref()
const formData = ref<Record<string, any>>({})
const executing = ref(false)
const executingAction = ref('')
const mailTemplates = ref<MailTemplateVO[]>([])

// 解析表单字段配置
const formFields = computed<QuickActionFormField[]>(() => {
  if (!props.definition) return []
  try {
    return JSON.parse(props.definition.formSchema)
  } catch {
    return []
  }
})

// 解析操作按钮
const actionButtons = computed<QuickActionButton[]>(() => {
  if (!props.definition) return []
  try {
    return JSON.parse(props.definition.actions)
  } catch {
    return []
  }
})

// 获取字段选项（支持动态加载）
function getOptions(field: QuickActionFormField): string[] {
  if (field.optionsSource === 'mail_templates') {
    return mailTemplates.value.map(t => t.name)
  }
  return field.options || []
}

// Dialog 打开时初始化表单
watch(() => props.visible, async (val) => {
  if (val && props.definition) {
    // 初始化表单默认值
    const data: Record<string, any> = {}
    for (const field of formFields.value) {
      if (field.defaultValue !== undefined) {
        data[field.key] = field.defaultValue
      } else if (field.type === 'checkbox') {
        data[field.key] = []
      } else {
        data[field.key] = undefined
      }
    }
    formData.value = data

    // 加载邮件模板（如果有字段需要）
    const needsMailTemplates = formFields.value.some(f => f.optionsSource === 'mail_templates')
    if (needsMailTemplates) {
      try {
        const res = await quickActionApi.getMailTemplates(
          props.issueId, props.definition.actionKey, props.projectId
        )
        mailTemplates.value = res.data || []
      } catch (e) {
        console.error('加载邮件模板失败', e)
      }
    }
  }
})

function close() {
  emit('update:visible', false)
}

async function executeAction(actionKey: string) {
  // 表单校验
  const errors = await formRef.value?.validate()
  if (errors) return

  executing.value = true
  executingAction.value = actionKey

  try {
    // 找到选中的邮件模板 ID
    let mailTemplateId: string | undefined
    if (actionKey === 'comment_and_mail') {
      const selectedTemplateName = formData.value['mail_template']
      if (selectedTemplateName) {
        const matched = mailTemplates.value.find(t => t.name === selectedTemplateName)
        mailTemplateId = matched?.id
      }
    }

    const res = await quickActionApi.execute(props.issueId, props.definition!.actionKey, {
      formData: JSON.stringify(formData.value),
      resultType: actionKey,
      mailTemplateId,
    })

    if (res.data?.success) {
      const msg = actionKey === 'comment_and_mail'
        ? (res.data.mailSent ? '已评论并发送邮件' : '已评论（邮件发送失败）')
        : '已评论'
      Message.success(msg)

      if (res.data.statusAfter) {
        Message.info(`状态已变更为 ${res.data.statusAfter}`)
      }
    }

    close()
    emit('executed')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '执行失败')
  } finally {
    executing.value = false
    executingAction.value = ''
  }
}
</script>

<style scoped>
.quick-action-form {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 4px;
}

.quick-action-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--tf-border);
  margin-top: 16px;
}
</style>
