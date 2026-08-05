/**
 * 通用异步请求 composable — 管理单次异步操作的 loading / data / error 三态
 */
import { ref, type Ref } from 'vue'

export interface UseRequestOptions<T> {
  /** 是否立即执行，默认 false */
  immediate?: boolean
  /** 初始数据 */
  initialData?: T
  /** 成功回调 */
  onSuccess?: (data: T) => void
  /** 错误回调 */
  onError?: (e: Error) => void
}

export interface UseRequestReturn<T> {
  data: Ref<T | undefined>
  loading: Ref<boolean>
  error: Ref<string | null>
  execute: () => Promise<void>
}

export function useRequest<T>(
  fn: () => Promise<{ code: number; data: T; message?: string }>,
  options: UseRequestOptions<T> = {}
): UseRequestReturn<T> {
  const data: Ref<T | undefined> = ref(options.initialData as T) as Ref<T | undefined>
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function execute() {
    loading.value = true
    error.value = null
    try {
      const res = await fn()
      if (res.code === 0) {
        data.value = res.data
        options.onSuccess?.(res.data)
      } else {
        error.value = res.message || '操作失败'
      }
    } catch (e: any) {
      error.value = e?.response?.data?.message || e?.message || '网络请求失败'
      options.onError?.(e)
    } finally {
      loading.value = false
    }
  }

  if (options.immediate) execute()

  return { data, loading, error, execute }
}
