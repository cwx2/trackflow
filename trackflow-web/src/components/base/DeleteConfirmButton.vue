<template>
  <button
    class="delete-confirm-btn"
    :class="{ 'is-deleting': deleting, [size]: true }"
    :disabled="disabled || deleting"
    @click.stop="handleClick"
  >
    <slot>
      <span v-if="deleting" class="delete-spinner"></span>
      <span v-else>{{ buttonText }}</span>
    </slot>
  </button>
</template>

<script setup lang="ts">
/**
 * DeleteConfirmButton — 带确认弹窗的删除按钮
 *
 * 用法：
 * <DeleteConfirmButton
 *   :delete-fn="() => userApi.delete(row.id)"
 *   confirm-title="确认删除"
 *   confirm-content="删除后不可恢复，确定删除？"
 *   @deleted="refresh()"
 * />
 */
import { ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'

const props = withDefaults(defineProps<{
  /** 执行删除的异步函数 */
  deleteFn: () => Promise<any>
  /** 确认弹窗标题 */
  confirmTitle?: string
  /** 确认弹窗内容 */
  confirmContent?: string
  /** 按钮文案 */
  buttonText?: string
  /** 删除成功提示 */
  successMessage?: string
  /** 按钮尺寸 */
  size?: 'small' | 'medium'
  /** 是否禁用 */
  disabled?: boolean
}>(), {
  confirmTitle: '确认删除',
  confirmContent: '删除后无法恢复，确定要删除吗？',
  buttonText: '删除',
  successMessage: '删除成功',
  size: 'small',
  disabled: false
})

const emit = defineEmits<{
  (e: 'deleted'): void
  (e: 'error', error: Error): void
}>()

const deleting = ref(false)

function handleClick() {
  const { confirmDelete } = useConfirmDelete()
  confirmDelete({
    itemName: '',
    confirmText: '确认删除',
    onConfirm: async () => {
      deleting.value = true
      try {
        await props.deleteFn()
        Message.success(props.successMessage)
        emit('deleted')
      } catch (e: any) {
        const msg = e?.response?.data?.message || e?.message || '删除失败'
        Message.error(msg)
        emit('error', e)
      } finally {
        deleting.value = false
      }
    }
  })
}
</script>

<style scoped>
.delete-confirm-btn {
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 4px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--tf-error, #f85149);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  white-space: nowrap;
}

.delete-confirm-btn:hover:not(:disabled) {
  background: rgba(248, 81, 73, 0.1);
}

.delete-confirm-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.delete-confirm-btn.medium {
  padding: 6px 14px;
  font-size: 13px;
}

.delete-spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 1.5px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: dspin 0.6s linear infinite;
}

@keyframes dspin {
  to { transform: rotate(360deg); }
}
</style>
