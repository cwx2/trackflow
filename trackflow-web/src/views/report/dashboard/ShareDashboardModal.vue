<script setup lang="ts">
import { ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { handleApiError } from '@/utils/errorHandler'
import { customDashboardApi } from '@/api'
import { ShareTargetsModal } from '@/components/base'
import type { ShareTargetItem } from '@/components/base'

interface Props {
  visible: boolean
  dashboardId: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:visible': [val: boolean]
  saved: []
}>()

const initialTargets = ref<ShareTargetItem[]>([])
const saving = ref(false)

const dashboardPermissionOptions = [
  { value: 'view', label: '可查看' },
  { value: 'edit', label: '可编辑' },
]

watch(
  () => props.visible,
  async (val) => {
    if (val && props.dashboardId) {
      try {
        const res = await customDashboardApi.getShares(props.dashboardId)
        const shares = res.data || []
        initialTargets.value = shares.map(s => ({
          key: `${s.targetType}:${s.targetId}`,
          type: s.targetType as 'user' | 'group',
          id: Number(s.targetId),
          name: s.targetName,
          permission: s.permission,
        }))
      } catch {
        initialTargets.value = []
      }
    }
  },
)

async function handleSave(targets: ShareTargetItem[]) {
  saving.value = true
  try {
    await customDashboardApi.setShares(props.dashboardId, {
      targets: targets.map(t => ({
        targetType: t.type,
        targetId: t.id,
        permission: t.permission as 'view' | 'edit',
      })),
    })
    Message.success('共享设置已保存')
    emit('update:visible', false)
    emit('saved')
  } catch (e) {
    handleApiError(e, '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ShareTargetsModal
    :visible="visible"
    title="共享仪表盘"
    :permission-options="dashboardPermissionOptions"
    :initial-targets="initialTargets"
    :saving="saving"
    @update:visible="$emit('update:visible', $event)"
    @save="handleSave"
  />
</template>
