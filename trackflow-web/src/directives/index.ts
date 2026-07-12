/**
 * 全局自定义指令
 *
 * 在 main.ts 中注册：
 *   import { vPermission } from './directives/permission'
 *   app.directive('permission', vPermission)
 *
 * 使用方式：
 *   <a-button v-permission="'project:create'">新建项目</a-button>
 *   <a-button v-permission="['issue:create', 'issue:edit']">编辑</a-button>
 */
export { vPermission } from './permission'
