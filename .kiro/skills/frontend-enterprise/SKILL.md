---
name: frontend-enterprise
description: Vue 3 企业级前端开发规范。涵盖通用组件封装、Composable 设计、性能优化、全局封装（请求/错误/权限）、状态管理、TypeScript 强类型等核心实践。当用户说"前端规范"、"组件封装"、"前端性能"、"Vue 企业级"时激活。
---

# TrackFlow 前端企业级开发规范

## 你的角色

你是一位有 8 年 Vue 生产经验的高级前端架构师，熟悉 Vue 3 Composition API、TypeScript 严格模式、Arco Design Vue、Vite 工程化。
你的目标：写出**可维护、可测试、高性能**的企业级前端代码，让团队成员能快速理解和接手。

---

## 一、项目目录结构规范

```
src/
├── api/                    # 接口层
│   ├── index.ts            # 统一出口
│   ├── request.ts          # axios 实例 + 拦截器
│   ├── types.ts            # 所有后端 VO 类型定义
│   ├── issue.ts            # Issue 模块 API
│   └── ...                 # 其他模块
├── composables/            # 可复用逻辑（以 use 开头）
│   ├── useRequest.ts       # 通用异步请求封装
│   ├── usePagedList.ts     # 分页列表复用逻辑
│   ├── useForm.ts          # 表单提交复用逻辑
│   └── usePermission.ts    # 权限判断
├── components/             # 通用业务组件
│   ├── base/               # 基础原子组件（跨业务复用）
│   ├── business/           # 业务组合组件（单业务域）
│   └── layout/             # 布局组件
├── stores/                 # Pinia 状态（只放跨页面共享状态）
│   ├── user.ts
│   ├── permission.ts
│   └── theme.ts
├── router/
│   ├── index.ts
│   ├── guards.ts           # 路由守卫（鉴权、页面标题）
│   └── modules/            # 按模块拆分路由
├── styles/
│   ├── variables.css       # CSS 变量（--tf-* 主题变量）
│   ├── reset.css
│   └── global.css
├── utils/                  # 纯函数工具（无副作用）
│   ├── format.ts           # 日期/数字格式化
│   ├── validate.ts         # 校验函数
│   └── storage.ts          # localStorage 封装
└── views/                  # 页面组件（只做组合，不写业务逻辑）
    ├── issue/
    ├── project/
    └── ...
```

**关键原则**：
- `views/` 只做组合，业务逻辑抽到 `composables/`
- `components/` 不直接调 API，数据由父组件传入
- `utils/` 是纯函数，不依赖 Vue 响应式
- `stores/` 只放**跨页面**需要共享的状态



## 二、全局请求封装（request.ts）

企业级项目的请求层必须统一处理：Token 刷新、错误码映射、loading 状态、取消重复请求。

```typescript
// src/api/request.ts
import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { Message } from '@arco-design/web-vue'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// ── 请求拦截器 ──
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// ── 响应拦截器 ──
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 业务错误（HTTP 200 但 code !== 0）
    if (res.code !== 0) {
      Message.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res  // 直接返回 R<T>，组件拿到的就是 { code, data, message }
  },
  async (error) => {
    const status = error.response?.status
    if (status === 401) {
      // Token 过期：尝试刷新，失败则跳登录
      const refreshed = await tryRefreshToken()
      if (refreshed) return request(error.config)
      useUserStore().logout()
      router.push('/login')
      return Promise.reject(error)
    }
    if (status === 403) {
      Message.error('无权限执行此操作')
    } else if (status === 404) {
      Message.error('资源不存在')
    } else if (status >= 500) {
      Message.error('服务器繁忙，请稍后重试')
    } else {
      Message.error(error.response?.data?.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

async function tryRefreshToken(): Promise<boolean> {
  try {
    const refreshToken = localStorage.getItem('refresh_token')
    if (!refreshToken) return false
    const res = await axios.post('/auth/refresh', { refreshToken })
    localStorage.setItem('access_token', res.data.data.accessToken)
    return true
  } catch {
    return false
  }
}

export default request
```

### 防重复请求（幂等保护）

对于提交类操作，用 `AbortController` 取消上一个未完成请求：

```typescript
// composables/useAbortable.ts
export function useAbortable() {
  const controller = ref<AbortController | null>(null)

  function abort() {
    controller.value?.abort()
    controller.value = new AbortController()
    return controller.value.signal
  }

  onUnmounted(() => controller.value?.abort())
  return { abort }
}

// 使用
const { abort } = useAbortable()
const res = await issueApi.search(keyword, { signal: abort() })
```

---

## 三、API 模块标准写法

```typescript
// src/api/issue.ts
import request from './request'
import type { R, PageResult, IssueVO, IssueDetailVO, CreateIssueDTO } from './types'

export const issueApi = {
  // 列表（GET + 查询参数）
  list(params: IssueQuery) {
    return request.get<any, R<PageResult<IssueVO>>>('/issues', { params })
  },
  // 详情
  getById(id: string) {
    return request.get<any, R<IssueDetailVO>>(`/issues/${id}`)
  },
  // 创建（POST + body）
  create(data: CreateIssueDTO) {
    return request.post<any, R<IssueVO>>('/issues', data)
  },
  // 部分更新（PATCH）
  update(id: string, data: Partial<UpdateIssueDTO>) {
    return request.patch<any, R<IssueVO>>(`/issues/${id}`, data)
  },
  // 删除
  delete(id: string) {
    return request.delete<any, R<void>>(`/issues/${id}`)
  },
  // 状态变更（操作型接口）
  transition(id: string, statusId: string) {
    return request.post<any, R<IssueVO>>(`/issues/${id}/transitions`, { statusId })
  },
}
```

**规则**：
- 每个模块一个文件，导出一个 `xxxApi` 对象
- 返回类型用泛型标注 `R<T>`，不用 `any`
- URL 参数（路径变量）直接拼接，查询参数放 `params`，body 放 `data`
- 所有模块在 `src/api/index.ts` 统一 `export { issueApi } from './issue'`



## 四、Composable 设计规范

Composable 是 Vue 3 最核心的复用单元。**识别信号**：多个组件都写了类似的 `loading/data/error + fetch` 逻辑，必须提取。

### 4.1 通用异步请求 composable

```typescript
// composables/useRequest.ts
import { ref, type Ref } from 'vue'
import { Message } from '@arco-design/web-vue'

interface UseRequestOptions<T> {
  immediate?: boolean          // 是否立即执行，默认 false
  initialData?: T              // 初始值
  onSuccess?: (data: T) => void
  onError?: (e: Error) => void
  successMsg?: string          // 成功提示（可选）
}

export function useRequest<T>(
  fn: () => Promise<{ code: number; data: T; message?: string }>,
  options: UseRequestOptions<T> = {}
) {
  const data: Ref<T | undefined> = ref(options.initialData as T)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function execute() {
    loading.value = true
    error.value = null
    try {
      const res = await fn()
      data.value = res.data
      if (options.successMsg) Message.success(options.successMsg)
      options.onSuccess?.(res.data)
    } catch (e: any) {
      error.value = e.message ?? '操作失败'
      options.onError?.(e)
      // 注意：Message.error 已在 request.ts 拦截器里处理，这里不重复
    } finally {
      loading.value = false
    }
  }

  if (options.immediate) execute()

  return { data, loading, error, execute }
}
```

### 4.2 分页列表 composable（最常用）

```typescript
// composables/usePagedList.ts
import { ref, reactive, watch, type Ref } from 'vue'
import { useRequest } from './useRequest'

interface PageParams {
  page: number
  pageSize: number
  [key: string]: any
}

export function usePagedList<T, P extends Record<string, any>>(
  fetchFn: (params: PageParams & P) => Promise<any>,
  initialFilters?: Partial<P>
) {
  const list = ref<T[]>([])
  const total = ref(0)
  const pagination = reactive({ page: 1, pageSize: 20 })
  const filters = reactive<Partial<P>>({ ...initialFilters })

  const { loading, execute: _fetch } = useRequest(
    async () => {
      const res = await fetchFn({ ...pagination, ...filters } as any)
      list.value = res.data?.list ?? []
      total.value = res.data?.pagination?.total ?? 0
      return res
    },
    { immediate: true }
  )

  // 切换筛选条件时重置到第一页
  watch(filters, () => {
    pagination.page = 1
    _fetch()
  })

  function refresh() { _fetch() }
  function onPageChange(page: number) {
    pagination.page = page
    _fetch()
  }
  function onPageSizeChange(pageSize: number) {
    pagination.page = 1
    pagination.pageSize = pageSize
    _fetch()
  }

  return { list, total, loading, pagination, filters, refresh, onPageChange, onPageSizeChange }
}

// ── 使用示例 ──
// const { list, loading, total, pagination, filters, onPageChange } = usePagedList(
//   issueApi.list,
//   { status: 'open', projectId: props.projectId }
// )
```

### 4.3 表单提交 composable

```typescript
// composables/useForm.ts
import { ref, reactive } from 'vue'
import type { FormInstance } from '@arco-design/web-vue'

export function useForm<T extends Record<string, any>>(
  initialValues: T,
  submitFn: (data: T) => Promise<any>,
  options: { successMsg?: string; resetAfterSubmit?: boolean } = {}
) {
  const formRef = ref<FormInstance>()
  const formData = reactive<T>({ ...initialValues })
  const submitting = ref(false)

  async function handleSubmit() {
    const valid = await formRef.value?.validate()
    if (valid) return  // Arco validate 有错时返回错误对象（truthy）

    submitting.value = true
    try {
      await submitFn({ ...formData } as T)
      if (options.resetAfterSubmit) {
        Object.assign(formData, initialValues)
        formRef.value?.resetFields()
      }
    } finally {
      submitting.value = false
    }
  }

  function resetForm() {
    Object.assign(formData, initialValues)
    formRef.value?.resetFields()
  }

  return { formRef, formData, submitting, handleSubmit, resetForm }
}
```

### 4.4 Composable 设计规则

| 规则 | 说明 |
|------|------|
| 必须以 `use` 开头 | `useIssueList`、`useForm`，不要 `issueHelper` |
| 接受响应式参数 | 参数用 `Ref<T>` 或 `ComputedRef<T>`，不接受原始值 |
| 内部副作用自清理 | `addEventListener`、`setInterval` 必须在 composable 内部 `onUnmounted` 清理 |
| 返回稳定对象 | 解构不破坏响应性：`return { data, loading, execute }`（ref/reactive 可安全解构） |
| 3 个组件重复就提取 | 同一数据获取模式出现 3 次 → 必须提取 composable |



## 五、通用组件封装规范

### 5.1 组件分层

| 层级 | 位置 | 特征 | 示例 |
|------|------|------|------|
| 原子组件 | `components/base/` | 无业务语义，纯 UI | `BaseTag`、`BaseAvatar`、`BaseSkeleton` |
| 业务组件 | `components/business/` | 有业务语义，可能调 API | `IssueStatusBadge`、`UserAvatar`、`PrioritySelect` |
| 页面组件 | `views/` | 组合多个业务组件，有路由 | `IssueListView`、`IssueDetailView` |

### 5.2 组件 Props/Emits 强类型模板

```vue
<!-- components/business/IssueStatusBadge.vue -->
<template>
  <a-tag :color="STATUS_COLORS[status.category]">
    <template #icon>
      <icon-check-circle v-if="status.category === 'done'" />
      <icon-clock-circle v-else />
    </template>
    {{ status.name }}
  </a-tag>
</template>

<script setup lang="ts">
import type { IssueStatusVO } from '@/api/types'

// ✅ Props 必须有 TypeScript interface
interface Props {
  status: IssueStatusVO
  size?: 'small' | 'medium' | 'large'
  clickable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  size: 'medium',
  clickable: false,
})

// ✅ Emits 必须有类型签名
const emit = defineEmits<{
  click: [status: IssueStatusVO]
}>()

const STATUS_COLORS: Record<string, string> = {
  todo: 'gray',
  in_progress: 'blue',
  done: 'green',
  cancelled: 'red',
}

function handleClick() {
  if (props.clickable) emit('click', props.status)
}
</script>
```

### 5.3 带加载/空状态/错误状态的标准容器组件

企业级组件必须处理三态（loading / empty / error），不能只写 happy path：

```vue
<!-- components/base/DataContainer.vue -->
<template>
  <!-- 加载中 -->
  <a-spin v-if="loading" class="data-container__spin" />

  <!-- 错误 -->
  <a-result v-else-if="error" status="error" :title="error">
    <template #extra>
      <a-button @click="emit('retry')">重试</a-button>
    </template>
  </a-result>

  <!-- 空状态 -->
  <a-empty v-else-if="isEmpty">
    <template #description>
      <span>{{ emptyText }}</span>
    </template>
    <a-button v-if="createAction" type="primary" @click="emit('create')">
      {{ createAction }}
    </a-button>
  </a-empty>

  <!-- 正常内容 -->
  <slot v-else />
</template>

<script setup lang="ts">
interface Props {
  loading?: boolean
  error?: string | null
  isEmpty?: boolean
  emptyText?: string
  createAction?: string   // 非空则显示创建按钮
}

withDefaults(defineProps<Props>(), {
  loading: false,
  error: null,
  isEmpty: false,
  emptyText: '暂无数据',
})

const emit = defineEmits<{
  retry: []
  create: []
}>()
</script>

<style scoped>
.data-container__spin {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}
</style>
```

**使用**：
```vue
<DataContainer
  :loading="loading"
  :error="error"
  :is-empty="list.length === 0"
  empty-text="当前项目暂无工单"
  create-action="创建工单"
  @create="showCreateModal = true"
  @retry="refresh()"
>
  <IssueTable :issues="list" />
</DataContainer>
```

### 5.4 通用确认删除组件

```vue
<!-- components/base/DeleteConfirmButton.vue -->
<template>
  <a-popconfirm
    :content="content"
    type="warning"
    ok-text="确认删除"
    ok-status="danger"
    @ok="handleDelete"
  >
    <a-button
      type="text"
      status="danger"
      size="mini"
      :loading="deleting"
    >
      <template #icon><icon-delete /></template>
    </a-button>
  </a-popconfirm>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Message } from '@arco-design/web-vue'

interface Props {
  content?: string
  deleteFn: () => Promise<any>
}

const props = withDefaults(defineProps<Props>(), {
  content: '确认删除该条记录？此操作不可撤销。',
})

const emit = defineEmits<{ deleted: [] }>()
const deleting = ref(false)

async function handleDelete() {
  deleting.value = true
  try {
    await props.deleteFn()
    Message.success('删除成功')
    emit('deleted')
  } finally {
    deleting.value = false
  }
}
</script>
```



## 六、全局权限封装

### 6.1 权限 composable

```typescript
// composables/usePermission.ts
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'

export function usePermission() {
  const userStore = useUserStore()

  // 检查项目级权限
  function can(projectId: string, permission: string): boolean {
    if (userStore.isSystemAdmin) return true
    return userStore.permissions[projectId]?.includes(permission) ?? false
  }

  // 检查全局权限
  function canGlobal(permission: string): boolean {
    return userStore.globalPermissions.includes(permission) || userStore.isSystemAdmin
  }

  return { can, canGlobal, isSystemAdmin: computed(() => userStore.isSystemAdmin) }
}
```

### 6.2 权限指令 v-permission

```typescript
// directives/permission.ts
import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

// 用法：v-permission="'issue:delete'"（全局）
//       v-permission="{ projectId, permission: 'issue:delete' }"（项目级）
export const vPermission: Directive = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const value = binding.value

    let hasPermission: boolean
    if (typeof value === 'string') {
      hasPermission = userStore.globalPermissions.includes(value) || userStore.isSystemAdmin
    } else {
      hasPermission = userStore.isSystemAdmin ||
        (userStore.permissions[value.projectId]?.includes(value.permission) ?? false)
    }

    if (!hasPermission) {
      el.style.display = 'none'  // 隐藏而非移除，避免布局抖动
      el.setAttribute('aria-hidden', 'true')
    }
  }
}

// 注册（main.ts）：app.directive('permission', vPermission)
```

### 6.3 路由守卫

```typescript
// router/guards.ts
import type { Router } from 'vue-router'
import { useUserStore } from '@/stores/user'

export function setupRouterGuards(router: Router) {
  router.beforeEach(async (to) => {
    const userStore = useUserStore()

    // 白名单（无需登录）
    if (to.meta.public) return true

    // 未登录 → 跳登录
    if (!userStore.isLoggedIn) return { path: '/login', query: { redirect: to.fullPath } }

    // 首次加载权限
    if (!userStore.permissionsLoaded) {
      await userStore.loadPermissions()
    }

    // 页面级权限校验
    if (to.meta.permission && !userStore.globalPermissions.includes(to.meta.permission as string)) {
      return { path: '/403' }
    }

    return true
  })

  // 设置页面标题
  router.afterEach((to) => {
    document.title = to.meta.title ? `${to.meta.title} - TrackFlow` : 'TrackFlow'
  })
}
```

---

## 七、性能优化规范

### 7.1 路由懒加载（必须）

```typescript
// router/modules/issue.ts
export default [
  {
    path: '/issues',
    component: () => import('@/views/issue/IssueListView.vue'),  // ✅ 懒加载
    meta: { title: '工单列表' }
  },
  {
    path: '/issues/:id',
    component: () => import('@/views/issue/IssueDetailView.vue'),
    meta: { title: '工单详情' }
  }
]
// ❌ 禁止：import IssueListView from '@/views/issue/IssueListView.vue'（打包进主 bundle）
```

### 7.2 大型组件异步加载

```typescript
// 超过 100KB 的组件（如富文本编辑器）
const RichEditor = defineAsyncComponent({
  loader: () => import('@/components/business/RichEditor.vue'),
  loadingComponent: EditorSkeleton,   // 加载中占位
  errorComponent: EditorError,        // 加载失败
  delay: 200,                          // 200ms 内加载完不显示 loading
  timeout: 10000,
})
```

### 7.3 长列表虚拟化（超过 200 条必须）

```vue
<!-- 使用 @vueuse/core 的 useVirtualList -->
<template>
  <div ref="containerRef" class="issue-list" style="height: 600px; overflow-y: auto;">
    <div :style="{ height: `${totalHeight}px`, position: 'relative' }">
      <div
        v-for="item in visibleItems"
        :key="item.data.id"
        :style="{ position: 'absolute', top: `${item.index * ITEM_HEIGHT}px`, width: '100%' }"
      >
        <IssueRow :issue="item.data" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useVirtualList } from '@vueuse/core'

const ITEM_HEIGHT = 48

const { list: visibleItems, containerProps, totalHeight } = useVirtualList(
  issues,
  { itemHeight: ITEM_HEIGHT, overscan: 5 }
)
</script>
```

### 7.4 computed 缓存 vs watch 副作用（高频错误）

```typescript
// ❌ 用 watch 计算派生数据（每次都重算，无缓存）
watch(issues, (list) => {
  openCount.value = list.filter(i => i.status.category === 'todo').length
  doneCount.value = list.filter(i => i.status.category === 'done').length
})

// ✅ 派生数据用 computed（有缓存，依赖不变不重算）
const openCount = computed(() => issues.value.filter(i => i.status.category === 'todo').length)
const doneCount = computed(() => issues.value.filter(i => i.status.category === 'done').length)

// ✅ watch 只用于副作用
watch(selectedIssueId, (id) => {
  if (id) fetchIssueDetail(id)  // 调 API = 副作用，正确用 watch
})
```

### 7.5 内存泄漏防护（每次写组件必查）

```typescript
// ❌ 忘记清理，组件卸载后继续监听
onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  resizeObserver = new ResizeObserver(updateLayout)
  resizeObserver.observe(containerRef.value!)
  timerId = setInterval(refreshData, 30000)
})
// 没有 onUnmounted！

// ✅ 配套清理
const cleanup: (() => void)[] = []

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  cleanup.push(() => window.removeEventListener('keydown', handleKeydown))

  const observer = new ResizeObserver(updateLayout)
  observer.observe(containerRef.value!)
  cleanup.push(() => observer.disconnect())

  const timerId = setInterval(refreshData, 30000)
  cleanup.push(() => clearInterval(timerId))
})

onUnmounted(() => cleanup.forEach(fn => fn()))

// 或用 VueUse（推荐，自动清理）：
useEventListener(window, 'keydown', handleKeydown)
useResizeObserver(containerRef, updateLayout)
useIntervalFn(refreshData, 30000)
```



## 八、Pinia 状态管理规范

### 8.1 标准 Store 写法（Setup Store 风格）

```typescript
// stores/user.ts
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api'
import type { UserInfo } from '@/api/types'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  // ── state ──
  const userInfo = ref<UserInfo | null>(null)
  const permissions = ref<Record<string, string[]>>({})   // projectId → permissions[]
  const globalPermissions = ref<string[]>([])
  const permissionsLoaded = ref(false)

  // ── getters ──
  const isLoggedIn = computed(() => !!userInfo.value)
  const isSystemAdmin = computed(() =>
    globalPermissions.value.includes('system:admin')
  )
  const displayName = computed(() => userInfo.value?.displayName ?? '')

  // ── actions ──
  async function loadUserInfo() {
    const res = await authApi.me()
    userInfo.value = res.data
  }

  async function loadPermissions() {
    const res = await authApi.permissions()
    globalPermissions.value = res.data.global ?? []
    permissions.value = res.data.project ?? {}
    permissionsLoaded.value = true
  }

  function logout() {
    userInfo.value = null
    permissions.value = {}
    globalPermissions.value = []
    permissionsLoaded.value = false
    localStorage.removeItem('access_token')
    router.push('/login')
  }

  return {
    userInfo, permissions, globalPermissions, permissionsLoaded,
    isLoggedIn, isSystemAdmin, displayName,
    loadUserInfo, loadPermissions, logout,
  }
})
```

### 8.2 什么该放 Store，什么不该

| 状态类型 | 存放位置 | 原因 |
|----------|----------|------|
| 当前登录用户信息 | ✅ Pinia store | 全局需要，多页面复用 |
| 全局权限列表 | ✅ Pinia store | 路由守卫和组件都需要 |
| 全局主题/语言 | ✅ Pinia store | 影响整个应用 |
| 列表的 loading 状态 | ❌ 组件内 ref | 只在当前组件用 |
| 弹窗的 visible | ❌ 组件内 ref | 生命周期随组件 |
| 当前页的筛选条件 | ❌ composable | 跟随页面组件，不需要全局 |
| 工单列表数据 | ❌ composable | 实时性高，不需要全局缓存 |

**规则**：只有**跨页面共享且需要持久**的状态才放 Pinia。

---

## 九、TypeScript 强类型规范

### 9.1 后端 VO 对应前端 interface

```typescript
// src/api/types.ts

// 基础包装类型
export interface R<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  list: T[]
  pagination: {
    page: number
    pageSize: number
    total: number
    totalPages: number
  }
}

// 后端 VO 对应（ID 必须是 string）
export interface IssueVO {
  id: string           // ✅ Long → string
  issueKey: string
  title: string
  status: IssueStatusVO
  priority: string
  assigneeId: string | null
  assigneeName: string | null
  projectId: string
  sprintId: string | null
  createdAt: string    // ISO 8601
  updatedAt: string
}

// 查询参数（对应后端 Query 对象）
export interface IssueQuery {
  projectId?: string
  status?: string
  assigneeId?: string
  keyword?: string
  page?: number
  pageSize?: number
}
```

### 9.2 禁止 any 的替代写法

```typescript
// ❌ any 泛滥
const handleUpdate = (data: any) => { ... }
const processResult = (res: any) => res.data.list

// ✅ 具体类型
const handleUpdate = (data: Partial<UpdateIssueDTO>) => { ... }
const processResult = (res: R<PageResult<IssueVO>>) => res.data.list

// ✅ 实在不知道类型时用 unknown 而非 any（更安全）
function parseError(e: unknown): string {
  if (e instanceof Error) return e.message
  if (typeof e === 'string') return e
  return '未知错误'
}
```

---

## 十、企业级反模式速查

| 反模式 | 严重程度 | 正确做法 |
|--------|---------|---------|
| 组件内直接 `axios.get('/api/...')` | 🔴 必须改 | 从 `@/api/xxx` 导入，统一走拦截器 |
| `any` 类型在新代码里超过 3 处 | 🟡 建议改 | 定义具体 interface，实在不行用 `unknown` |
| `watch` 计算派生数据（应用 computed） | 🟡 建议改 | 有缓存需求的衍生值必须用 `computed` |
| `addEventListener` 没有配套 `removeEventListener` | 🔴 必须改 | 在 `onUnmounted` 清理，或用 VueUse |
| `v-for` 用 index 做 `:key` | 🟡 建议改 | 用稳定唯一 ID（如 `item.id`） |
| 直接 mutate props | 🔴 必须改 | emit 事件让父组件更新，或用 `computed + setter` |
| `console.log` 提交到代码库 | 🟡 建议改 | 提交前清理，用 eslint no-console 规则 |
| 空 catch 块吞异常 | 🔴 必须改 | 至少 `Message.error()` + `console.error(e)` |
| 同一 API 调用复制在 3+ 组件 | 🟡 建议改 | 提取 composable |
| 弹窗/抽屉没有加载和错误状态 | 🟡 建议改 | 用 `DataContainer` 或三态模板 |
| 列表超 200 条不分页/不虚拟化 | 🟡 建议改 | 后端分页 + 前端虚拟滚动 |
| 路由组件不懒加载 | 🟡 建议改 | `() => import(...)` |
| 大型弹窗每次都渲染（v-if vs v-show） | ⚠️ 注意 | 初始化开销大的用 `v-if`，频繁切换的用 `v-show` |
| 图片不压缩直接用原图 | ⚠️ 注意 | 用 `vite-plugin-imagemin` 或 CDN 压缩 |
| CSS 颜色硬编码 `#1a1a1a` | ⚠️ 注意 | 用 `var(--tf-text-primary)` 等 CSS 变量 |

---

## 十一、工作流程

每次开发新功能前：

1. **确认是否已有类似 composable** — 查 `src/composables/`，避免重复造轮子
2. **确认是否已有类似组件** — 查 `src/components/`，优先复用
3. **确认类型定义** — `src/api/types.ts` 是否已有对应 VO，没有则先补类型
4. **确认 API 模块** — `src/api/` 是否已有对应接口，没有则先补 API
5. **写 composable 先于 view** — 先写数据逻辑，再写模板
6. **三态都处理** — loading / empty / error 缺一不可
7. **检查内存泄漏** — 有 `addEventListener`/`setInterval`/`WebSocket` 的，确认有清理

