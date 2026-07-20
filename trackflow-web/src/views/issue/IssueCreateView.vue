<template>
  <div class="issue-create-page">
    <IssueCreatePanel
      ref="createPanelRef"
      :visible="true"
      @update:visible="onClose"
      @created="onCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { Modal } from '@arco-design/web-vue'
import IssueCreatePanel from './IssueCreatePanel.vue'

const router = useRouter()
const createPanelRef = ref<InstanceType<typeof IssueCreatePanel> | null>(null)

function onClose() {
  router.back()
}

function onCreated() {
  router.back()
}

// Vue Router 路由守卫：离开页面时检查脏数据
onBeforeRouteLeave((_to, _from, next) => {
  const panel = createPanelRef.value
  if (panel && panel.isDirty) {
    Modal.confirm({
      title: '有未保存的更改',
      content: '当前表单中有未保存的内容，确定要离开吗？',
      okText: '放弃更改',
      cancelText: '继续编辑',
      simple: false,
      onOk: () => {
        next()
      },
      onCancel: () => {
        next(false)
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
