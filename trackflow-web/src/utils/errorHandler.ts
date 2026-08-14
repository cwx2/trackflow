import { Message } from '@arco-design/web-vue'

/**
 * 统一 API 错误处理工具
 *
 * 从 axios 错误对象中提取后端返回的 message，兜底显示 fallback。
 * 所有 catch 块中的错误提示都应通过本函数，避免各自写不同的取值链。
 *
 * 用法：
 *   } catch (e) {
 *     handleApiError(e, '创建失败')
 *   }
 *
 * 与 messageThrottle 兼容：本函数调用 Message.error()，节流器会自动去重聚合。
 */
export function handleApiError(e: unknown, fallback = '操作失败'): void {
  const err = e as any
  const msg: string =
    err?.response?.data?.message ||
    err?.message ||
    fallback
  Message.error(msg)
}
