<template>
  <div class="issue-create-page">
    <IssueCreatePanel
      :visible="true"
      @update:visible="onClose"
      @created="onCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import IssueCreatePanel from './IssueCreatePanel.vue'

const router = useRouter()

function onClose() {
  router.back()
}

function onCreated() {
  router.back()
}
</script>

<style scoped>
.issue-create-page {
  height: 100%;
}
</style>
