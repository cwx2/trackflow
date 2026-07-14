import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ArcoVue from '@arco-design/web-vue'
import '@arco-design/web-vue/dist/arco.css'
import './styles/variables.css'
import './styles/components.css'
import App from './App.vue'
import router from './router'
import { vPermission } from './directives/permission'

// 初始化主题（必须在 mount 之前）
import './composables/useTheme'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ArcoVue)
app.directive('permission', vPermission)

app.mount('#app')
