<template>
  <a-modal
    v-model:visible="visible"
    title="克隆看板"
    width="480px"
    :confirm-loading="loading"
    ok-text="克隆看板"
    cancel-text="取消"
    @ok="handleConfirm"
    @cancel="handleCancel"
  >
    <div class="clone-board-modal">
      <p class="clone-board-modal__desc">
        将以 <strong>{{ sourceBoardName }}</strong> 为模板创建新看板，
        复制所有列配置、卡片字段、泳道和图表设置。
      </p>

      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-form-item field="newName" label="新看板名称" required>
          <a-input
            v-model="form.newName"
            placeholder="例：我的看板"
            :max-length="100"
            show-word-limit
            @input="onNameInput"
          />
        </a-form-item>

        <a-form-item field="newKey" label="项目标识（Key）" required>
          <a-input
            v-model="form.newKey"
            placeholder="例：MYBOARD"
            :max-length="10"
            allow-clear
          >
            <template #extra>
              <span class="key-tip">2-10位大写字母和数字</span>
            </template>
          </a-input>
        </a-form-item>
      </a-form>

      <div class="clone-board-modal__info">
        <icon-info-circle class="info-icon" />
        <span>克隆后新看板访问权限将重置为默认（仅您可管理）。成员、工单、工作流不会被复制。</span>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { boardApi } from '@/api'
import { useRouter } from 'vue-router'

const props = defineProps<{
  modelValue: boolean
  /** 源看板所在项目 ID */
  sourceProjectId: string
  /** 源看板名称（用于展示） */
  sourceBoardName: string
  /** 源项目 Key（用于生成默认 Key） */
  sourceProjectKey: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'cloned': [projectId: string]
}>()

const router = useRouter()
const visible = ref(props.modelValue)
const loading = ref(false)
const formRef = ref()

const form = reactive({
  newName: '',
  newKey: ''
})

const rules = {
  newName: [
    { required: true, message: '请输入新看板名称' },
    { maxLength: 100, message: '名称不能超过100字' }
  ],
  newKey: [
    { required: true, message: '请输入项目标识' },
    { minLength: 1, message: '标识至少1位' },
    { maxLength: 10, message: '标识不超过10位' },
    {
      validator: (value: string, callback: (error?: string) => void) => {
        if (!/^[A-Z0-9]+$/.test(value)) {
          callback('只能包含大写字母和数字')
        } else {
          callback()
        }
      }
    }
  ]
}

// 当弹窗打开时初始化默认值
watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    // 默认名称：原看板名 + " (Copy)"
    form.newName = props.sourceBoardName ? `${props.sourceBoardName} (Copy)` : ''
    // 默认 Key：原 Key 后加 "C"（截断到10位），转大写
    const baseKey = (props.sourceProjectKey || '').toUpperCase().replace(/[^A-Z0-9]/g, '')
    const suffix = 'C'
    form.newKey = baseKey.length + suffix.length <= 10
      ? baseKey + suffix
      : baseKey.substring(0, 10 - suffix.length) + suffix
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

/** 名称变更时自动生成 Key（仅当 Key 未手动修改时） */
function onNameInput(value: string) {
  // 从名称中提取字母和数字，转大写，取前8位
  const autoKey = value.replace(/[^a-zA-Z0-9]/g, '').toUpperCase().substring(0, 8)
  if (autoKey) {
    form.newKey = autoKey
  }
}

async function handleConfirm() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const res = await boardApi.cloneBoard({
      sourceProjectId: props.sourceProjectId,
      newName: form.newName.trim(),
      newKey: form.newKey.trim()
    })

    if (res.code === 0) {
      Message.success('看板克隆成功')
      visible.value = false
      // 跳转到新看板
      emit('cloned', res.data.projectId)
      router.push({
        name: 'Boards',
        query: { projectId: res.data.projectId }
      })
    } else {
      Message.error(res.message || '克隆失败')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '克隆失败，请重试')
  } finally {
    loading.value = false
  }
}

function handleCancel() {
  visible.value = false
}
</script>

<style scoped>
.clone-board-modal {
  padding: 4px 0;
}

.clone-board-modal__desc {
  margin-bottom: 20px;
  color: var(--color-text-2, #9ca3af);
  font-size: 13px;
  line-height: 1.6;
}

.clone-board-modal__desc strong {
  color: var(--color-text-1, #e6edf3);
}

.clone-board-modal__info {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 12px;
  background: var(--color-fill-1, #2a2d33);
  border-radius: 6px;
  margin-top: 16px;
  font-size: 12px;
  color: var(--color-text-3, #6b7280);
  line-height: 1.5;
}

.info-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: rgb(var(--primary-6, 64 128 255));
}

.key-tip {
  font-size: 11px;
  color: var(--color-text-4, #4b5563);
}
</style>
