<script setup lang="ts">
import { ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { handleApiError } from '@/utils/errorHandler'
import { reportApi } from '@/api/report'
import { ShareTargetsModal } from '@/components/base'
import type { ShareTargetItem } from '@/components/base'

interface Props {
  visible: boolean
  reportId: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:visible': [val: boolean]
  saved: []
}>()

const initialTargets = ref<ShareTargetItem[]>([])
const saving = ref(false)

const reportPermissionOptions = [
  { value: 'view', label: '可查看' },
  { value: 'edit', label: '可编辑' },
]

const reportHint = `
  <p><strong>可查看</strong>：可以查看报表数据和刷新，不能修改配置</p>
  <p><strong>可编辑</strong>：可以查看报表数据，且能修改报表配置</p>
`

watch(
  () => props.visible,
  async (val) => {
    if (val && props.reportId) {
      try {
        const res = await reportApi.getShares(props.reportId)
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
    await reportApi.setShares(props.reportId, {
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
    title="共享报表"
    :permission-options="reportPermissionOptions"
    :hint-content="reportHint"
    :initial-targets="initialTargets"
    :saving="saving"
    @update:visible="$emit('update:visible', $event)"
    @save="handleSave"
  />
</template>
