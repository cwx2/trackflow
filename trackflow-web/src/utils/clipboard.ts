/**
 * utils/clipboard.ts — 剪贴板工具
 *
 * 统一替代 9 处散落的 navigator.clipboard.writeText(...) 内联调用，
 * 提供统一的错误处理和可选的成功/失败 Toast 提示。
 *
 * 依赖：@arco-design/web-vue 的 Message 组件（全局注册）
 */
import { Message } from '@arco-design/web-vue'

export interface CopyOptions {
  /** 复制成功后显示的 Toast 文字，不传则不提示 */
  successMessage?: string
  /** 复制失败后显示的 Toast 文字，不传则显示默认失败提示 */
  errorMessage?: string
}

/**
 * 将文本写入剪贴板。
 *
 * @param text    要复制的文本
 * @param options 可选：成功/失败 Toast 提示
 * @returns       是否复制成功
 */
export async function copyToClipboard(
  text: string,
  options: CopyOptions = {}
): Promise<boolean> {
  const { successMessage, errorMessage } = options
  try {
    await navigator.clipboard.writeText(text)
    if (successMessage) {
      Message.success(successMessage)
    }
    return true
  } catch {
    const msg = errorMessage ?? '复制失败，请手动复制'
    Message.error(msg)
    return false
  }
}
