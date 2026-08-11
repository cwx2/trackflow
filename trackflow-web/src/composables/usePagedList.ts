/**
 * 分页列表 composable — 管理列表数据的分页、筛选、排序、刷新等通用逻辑
 *
 * 消除各管理页面中重复的 list/total/page/pageSize/loading/loadXxx 模式
 */
import { ref, shallowRef, reactive, computed, watch, type Ref, type UnwrapNestedRefs } from 'vue'

export interface Pagination {
  page: number
  pageSize: number
}

export interface UsePagedListOptions<P extends Record<string, any>> {
  /** 初始筛选条件 */
  initialFilters?: Partial<P>
  /** 每页条数，默认 20 */
  pageSize?: number
  /** 是否立即加载（默认 true） */
  immediate?: boolean
  /** 筛选变化时是否自动刷新（默认 false，需手动调用 refresh） */
  autoRefreshOnFilterChange?: boolean
}

export interface UsePagedListReturn<T, P extends Record<string, any>> {
  /** 当前页数据列表 */
  list: Ref<T[]>
  /** 总记录数 */
  total: Ref<number>
  /** 加载中状态 */
  loading: Ref<boolean>
  /** 请求错误信息 */
  error: Ref<string | null>
  /** 分页状态 */
  pagination: UnwrapNestedRefs<Pagination>
  /** 计算属性：总页数 */
  totalPages: Ref<number>
  /** 筛选条件（reactive 对象，直接修改属性即可） */
  filters: UnwrapNestedRefs<Partial<P>>
  /** 重新加载当前页 */
  refresh: () => Promise<void>
  /** 页码变更 */
  onPageChange: (page: number) => void
  /** 每页条数变更 */
  onPageSizeChange: (size: number) => void
  /** 重置筛选并回到第一页 */
  reset: (newFilters?: Partial<P>) => void
}

export function usePagedList<T, P extends Record<string, any> = Record<string, any>>(
  fetchFn: (params: Pagination & Partial<P>) => Promise<{
    code: number
    data: { list: T[]; pagination: { total: number; page: number; pageSize: number; totalPages: number } }
    message?: string
  }>,
  options: UsePagedListOptions<P> = {}
): UsePagedListReturn<T, P> {
  const {
    initialFilters = {} as Partial<P>,
    pageSize = 20,
    immediate = true,
    autoRefreshOnFilterChange = false
  } = options

  // list 用 shallowRef：每次加载替换整个数组引用，Vue 无需递归代理每条记录的字段
  const list = shallowRef<T[]>([]) as Ref<T[]>
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const pagination = reactive<Pagination>({ page: 1, pageSize })
  const filters = reactive<Partial<P>>({ ...initialFilters })

  const totalPages = computed(() => Math.ceil(total.value / pagination.pageSize) || 1)

  async function refresh() {
    loading.value = true
    error.value = null
    try {
      const params = { ...pagination, ...filters } as Pagination & Partial<P>
      const res = await fetchFn(params)
      if (res.code === 0 && res.data) {
        list.value = res.data.list ?? []
        total.value = res.data.pagination?.total ?? 0
      } else {
        error.value = res.message || '加载失败'
        list.value = []
        total.value = 0
      }
    } catch (e: any) {
      error.value = e?.response?.data?.message || e?.message || '网络请求失败'
      list.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  function onPageChange(page: number) {
    pagination.page = page
    refresh()
  }

  function onPageSizeChange(size: number) {
    pagination.page = 1
    pagination.pageSize = size
    refresh()
  }

  function reset(newFilters?: Partial<P>) {
    pagination.page = 1
    if (newFilters) {
      Object.assign(filters, newFilters)
    } else {
      Object.assign(filters, initialFilters)
    }
    refresh()
  }

  if (autoRefreshOnFilterChange) {
    watch(filters, () => {
      pagination.page = 1
      refresh()
    })
  }

  if (immediate) {
    refresh()
  }

  return {
    list,
    total,
    loading,
    error,
    pagination,
    totalPages,
    filters,
    refresh,
    onPageChange,
    onPageSizeChange,
    reset
  }
}
