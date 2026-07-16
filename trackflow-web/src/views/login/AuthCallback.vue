<template>
  <div class="callback-container">
    <a-spin :loading="true" tip="正在登录..." />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')

  if (!code) {
    router.push('/login')
    return
  }

  try {
    await authStore.handleCallback(code)
    router.push('/issues')
  } catch (error) {
    console.error('Auth callback failed:', error)
    router.push('/login')
  }
})
</script>

<style scoped>
.callback-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--tf-bg-body);
}
</style>
