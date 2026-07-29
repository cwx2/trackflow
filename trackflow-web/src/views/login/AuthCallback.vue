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
  const state = params.get('state')

  if (!code || !state) {
    console.error('Auth callback missing required parameters (code or state)')
    router.push('/login')
    return
  }

  try {
    await authStore.handleCallback(code, state)
    // 登录成功后跳回原目标页面（如果有保存的 returnUrl）
    const returnUrl = sessionStorage.getItem('tf_return_url')
    sessionStorage.removeItem('tf_return_url')
    if (returnUrl && returnUrl.startsWith('/')) {
      router.push(returnUrl)
    } else {
      router.push('/issues')
    }
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
