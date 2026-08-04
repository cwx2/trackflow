<template>
  <a-modal
    :visible="visible"
    title="选择可见用户组"
    :width="420"
    :mask-closable="false"
    @cancel="onCancel"
    @ok="onConfirm"
  >
    <template #title>
      <div class="privacy-modal-title">
        <icon-lock class="privacy-modal-title-icon" />
        <span>私有附件 — 选择可见用户组</span>
      </div>
    </template>

    <div class="privacy-modal-body">
      <p class="privacy-modal-desc">
        仅被选中的用户组成员能查看此附件。请至少选择一个用户组。
      </p>

      <a-spin :loading="loading" style="width: 100%">
        <div v-if="groups.length === 0 && !loading" class="privacy-modal-empty">
          <span>暂无可用用户组</span>
        </div>
        <a-checkbox-group v-else v-model="selectedGroupIds" direction="vertical" class="privacy-modal-group-list">
          <a-checkbox v-for="group in groups" :key="group.id" :value="group.id" class="privacy-modal-group-item">
            {{ group.name }}
          </a-checkbox>
        </a-checkbox-group>
      </a-spin>
    </div>

    <template #footer>
      <a-button @click="onCancel">取消</a-button>
      <a-button type="primary" :disabled="selectedGroupIds.length === 0" @click="onConfirm">
        确认上传
      </a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { IconLock } from '@arco-design/web-vue/es/icon'
import { groupApi } from '@/api'
import type { GroupSimpleVO } from '@/api/types'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'confirm': [groupIds: string[]]
}>()

const loading = ref(false)
const groups = ref<GroupSimpleVO[]>([])
const selectedGroupIds = ref<string[]>([])

watch(() => props.visible, async (val) => {
  if (val) {
    selectedGroupIds.value = []
    await loadGroups()
  }
})

async function loadGroups() {
  loading.value = true
  try {
    const res = await groupApi.listSimple()
    groups.value = res.data || []
  } catch {
    groups.value = []
  } finally {
    loading.value = false
  }
}

function onCancel() {
  emit('update:visible', false)
}

function onConfirm() {
  if (selectedGroupIds.value.length === 0) return
  emit('confirm', [...selectedGroupIds.value])
  emit('update:visible', false)
}
</script>

<style scoped>
.privacy-modal-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.privacy-modal-title-icon {
  color: var(--tf-text-tertiary, var(--color-text-3));
  font-size: 16px;
}

.privacy-modal-body {
  min-height: 120px;
}

.privacy-modal-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--tf-text-secondary, var(--color-text-2));
  line-height: 1.5;
}

.privacy-modal-empty {
  padding: 24px;
  text-align: center;
  color: var(--tf-text-tertiary, var(--color-text-3));
  font-size: 13px;
}

.privacy-modal-group-list {
  width: 100%;
  max-height: 280px;
  overflow-y: auto;
}

.privacy-modal-group-item {
  padding: 8px 4px;
  border-radius: 4px;
  transition: background-color 150ms;
}

.privacy-modal-group-item:hover {
  background: var(--tf-bg-hover, var(--color-fill-2));
}
</style>
