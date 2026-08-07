/**
 * useConfirmDelete — 统一删除确认对话框 composable
 *
 * 提供两个函数：
 * - confirmDelete：普通删除确认（无数据影响）
 * - confirmDangerDelete：危险删除确认（有数据影响，显示醒目警告）
 *
 * @since 1.0
 */
import { Modal } from '@arco-design/web-vue'

export interface ConfirmDeleteOptions {
  /** 被删除的资源描述，如「用户组「测试组」」 */
  itemName: string
  /** 确认按钮文字，默认「删除」 */
  confirmText?: string
  /** 取消按钮文字，默认「取消」 */
  cancelText?: string
  /** 确认后执行的回调 */
  onConfirm: () => void | Promise<void>
}

export interface ConfirmDangerDeleteOptions extends ConfirmDeleteOptions {
  /** 影响说明，如「被 42 个工单使用，删除后数据将永久丢失」 */
  impactDescription: string
}

export function useConfirmDelete() {
  /**
   * 普通删除确认（无数据影响）
   *
   * 使用 Modal.warning，标题格式：「确认删除{itemName}」
   * 按钮：danger 样式的「删除」 + 「取消」
   */
  function confirmDelete(options: ConfirmDeleteOptions): void {
    Modal.warning({
      title: `确认删除${options.itemName}`,
      content: '此操作不可撤销。',
      okText: options.confirmText ?? '删除',
      cancelText: options.cancelText ?? '取消',
      hideCancel: false,
      okButtonProps: { status: 'danger' },
      onOk: options.onConfirm
    })
  }

  /**
   * 危险删除确认（有数据影响，显示橙色/红色警告）
   *
   * 使用 Modal.error，标题：「⚠️ 删除将导致数据丢失」
   * 显示影响说明，按钮文字可自定义
   */
  function confirmDangerDelete(options: ConfirmDangerDeleteOptions): void {
    Modal.error({
      title: '⚠️ 删除将导致数据丢失',
      content: `${options.itemName}当前${options.impactDescription}。此操作不可撤销。`,
      okText: options.confirmText ?? '确认删除',
      cancelText: options.cancelText ?? '取消',
      hideCancel: false,
      okButtonProps: { status: 'danger' },
      onOk: options.onConfirm
    })
  }

  return { confirmDelete, confirmDangerDelete }
}
