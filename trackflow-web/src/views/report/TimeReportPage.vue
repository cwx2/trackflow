<template>
  <div class="time-report-page">
    <TimeReportTab :projects="projects" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import TimeReportTab from './TimeReportTab.vue'
import { projectApi } from '@/api'
import type { ProjectVO } from '@/api/types'

const projects = ref<ProjectVO[]>([])

onMounted(async () => {
  try {
    const res = await projectApi.list({ page: 1, pageSize: 100 })
    projects.value = res.data?.list || []
  } catch {
    // non-critical
  }
})
</script>

<style scoped>
.time-report-page {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}
</style>
