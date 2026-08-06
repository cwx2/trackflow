import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ArcoVue from '@arco-design/web-vue'
import ArcoVueIcon from '@arco-design/web-vue/es/icon'
import axios from 'axios'
import '@arco-design/web-vue/dist/arco.css'
import './styles/variables.css'
import './styles/components.css'
import App from './App.vue'
import router from './router'
import { vPermission } from './directives/permission'

// 初始化主题（必须在 mount 之前）
import './composables/useTheme'

// 注册内置 Widget 插件（必须在 mount 之前）
import './widgets/builtin'

const app = createApp(App)

/**
 * 全局错误处理器
 *
 * 捕获未处理的错误，防止应用崩溃。
 * 特别处理 axios.CanceledError（Token 过期时的请求取消），
 * 这些错误由 handleSessionExpired() 统一处理跳转，组件层不需要再处理。
 */
app.config.errorHandler = (err, _instance, _info) => {
  // axios.CanceledError: Token 过期导致请求被取消
  // 这是正常的会话过期流程，静默处理（handleSessionExpired 会跳转到登录页）
  if (axios.isCancel(err)) {
    // 不打印到 console，避免用户看到不必要的错误信息
    return
  }

  // 其他未捕获的错误，打印到控制台供调试
  console.error('[Vue Error]', err)
}

/**
 * 全局 Promise rejection 处理器
 *
 * 捕获未处理的 Promise rejection，防止浏览器控制台报错。
 * 同样静默处理 axios.CanceledError。
 */
window.addEventListener('unhandledrejection', (event) => {
  const reason = event.reason
  if (axios.isCancel(reason)) {
    // 阻止浏览器默认的错误报告行为
    event.preventDefault()
    return
  }
  // 其他 rejection 不处理，让浏览器正常报告
})

app.use(createPinia())
app.use(router)
app.use(ArcoVue)
app.use(ArcoVueIcon)
app.directive('permission', vPermission)

app.mount('#app')
