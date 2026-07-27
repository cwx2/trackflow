<template>
  <div class="issue-create-page">
    <IssueCreatePanel
      ref="createPanelRef"
      :visible="true"
      :is-full-page="true"
      :draft-id="draftIdFromQuery"
      @update:visible="onClose"
      @created="onCreated"
      @cancel-with-data="onCancelWithData"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { Modal, Message } from '@arco-design/web-vue'
import IssueCreatePanel from './IssueCreatePanel.vue'
import { useDrafts } from './composables/useDrafts'

const router = useRouter()
const route = useRoute()
const createPanelRef = ref<InstanceType<typeof IssueCreatePanel> | null>(null)
const { saveDraft } = useDrafts()

/** 从路由查询参数读取 draftId，支持从弹窗全屏跳转时恢复数据 */
const draftIdFromQuery = computed(() => {
  const v = route.query.draftId
  return v ? String(v) : null
})

function onClose() {
  router.back()
}

function onCreated() {
  router.back()
}

function onCancelWithData(formData: any) {
  if (formData && (formData.title?.trim() || formData.description?.trim())) {
    saveDraft(formData)
    Message.info('已保存为草稿')
  }
}

// Vue Router 路由守卫：离开页面时检查脏数据
onBeforeRouteLeave((_to, _from, next) => {
  const panel = createPanelRef.value
  if (panel && panel.isDirty) {
    Modal.confirm({
      title: '保存为草稿？',
      content: '当前表单中有未保存的内容。是否保存为草稿？',
      okText: '保存草稿',
      cancelText: '放弃更改',
      simple: false,
      onOk: () => {
        // The panel's close handler will emit cancel-with-data
        next()
      },
      onCancel: () => {
        next()
      }
    })
  } else {
    next()
  }
})
</script>

<style scoped>
.issue-create-page {
  height: 100%;
}
</style>
