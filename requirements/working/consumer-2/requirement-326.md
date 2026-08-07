# REQ-326：「全页创建工单」路由仍以弹窗形式展示，不是真正的全页视图

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-326 |
| 标题 | 「全页创建工单」路由仍以弹窗形式展示，不是真正的全页视图 |
| 类型 | UI差异 |
| 严重程度 | P2 |
| 发现方式 | 用户反馈 |
| 发现日期 | 2026-08-07 |
| 关联模块 | Issue 创建全页视图（IssueCreateView.vue） |
| 状态 | 已修复 |

## 1. 问题描述

点击创建工单弹窗右上角的「全屏」图标后，页面路由跳转到 `/issues/create`（`IssueCreateView.vue`），但看到的仍然是一个弹窗浮层——左侧导航栏存在，弹窗遮罩覆盖了整个页面背景，创建表单居中浮动显示，与普通弹窗模式没有本质区别。

## 2. 根本原因

`IssueCreatePanel.vue` 整个组件就是一个 `<a-modal>`，`isFullPage` prop 只控制「全屏图标是否显示」，并不改变渲染方式。`IssueCreateView.vue` 将这个 Modal 组件以 `:visible="true"` 挂载到空页面里，结果是：路由变了，但视觉上还是弹窗 + 遮罩，背景是暗色蒙版而不是系统主内容区。

## 3. YouTrack 标准行为

YouTrack 的全页创建工单是真正的页面级视图：
- 左侧导航栏正常可见
- 顶部面包屑显示当前位置
- 创建表单铺满内容区（左侧表单主体 + 右侧属性面板），无遮罩无浮层
- 浏览器地址栏 URL 为 `/newIssue` 或项目专属路径
- 可以直接通过 URL 访问，支持书签收藏

## 4. 期望结果

`/issues/create` 路由渲染的创建工单页面应为标准的全页布局：
- 左侧系统导航栏保持可见
- 创建表单区域（标题、描述、附件、Link issue）作为主内容区铺满剩余空间
- 右侧属性面板（项目、类型、优先级等）作为侧边栏
- 无弹窗遮罩，无居中浮动
- 底部操作按钮（创建工单、取消）固定在底部或随页面流排列

实现上需要将 `IssueCreatePanel` 的弹窗容器（`<a-modal>`）抽离，核心表单内容单独作为页面组件渲染，或在 `IssueCreateView` 中直接内嵌表单内容而不依赖 Modal 包裹。

## 5. 验收标准

- [ ] 访问 `/issues/create` 时，左侧导航栏和顶部布局正常显示，无遮罩覆盖
- [ ] 创建表单铺满内容区，不以弹窗方式悬浮显示
- [ ] 从弹窗点全屏图标跳转后，视觉上明显是「页面」而非「弹窗」
- [ ] 全页模式下的功能与弹窗模式完全一致（草稿、模板、自定义字段等）
- [ ] 弹窗模式（从列表页点「创建」）保持原有行为不受影响

## 6. 备注

- 这是一个较大的重构，需要把 `IssueCreatePanel` 的内容层与 `<a-modal>` 包裹层分离
- 可以先评估成本，如果重构成本过高，短期可以让全屏图标直接移除或禁用，避免给用户造成"假全屏"的误导

## 自动化状态

fix_status: DONE
fix_commit: 4578e12
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-07 12:15

### 本次改动摘要
- 改动：`trackflow-web/src/views/issue/IssueCreatePanel.vue` — 移除了全页模式下阻止渲染的 `v-if="!isFullPage"`，改为通过 Arco Design modal 的条件 props（`mask=false`, `render-to-body=false`, 自定义 `modal-class`）和 CSS 覆盖样式将 modal 转为静态内联页面布局。全屏按钮在全页模式下隐藏。添加了 63 行全局 CSS 让 modal wrapper 和 modal body 变为 `position: static`、无阴影、无遮罩、无圆角、满宽显示。

### 本次变更文件清单
- `trackflow-web/src/views/issue/IssueCreatePanel.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 直接访问 `/issues/create` 路由 → 页面应渲染为正常布局（左侧导航栏可见，无遮罩覆盖，表单铺满内容区）
  2. 从 Issue 列表页打开创建弹窗（弹窗模式） → 弹窗应正常显示（有遮罩、居中浮动、可关闭）
  3. 弹窗模式下点击全屏按钮 → 应跳转到 `/issues/create` 并以全页布局显示
  4. 全页模式下填写表单并提交 → 功能应与弹窗模式一致
- **边界场景**：
  - 全页模式下不应出现全屏按钮（避免循环跳转）
  - 全页模式下点「取消」应该 router.back()
  - 弹窗模式不受影响（mask 正常、居中正常、关闭按钮可用）
- **建议测试账号**：testuser（超级管理员，确保有创建权限）
- **注意事项**：关注是否有残留的 modal mask 元素阻塞页面交互

### 审核重点（给 code-review 会话）
- **重点关注文件**：`IssueCreatePanel.vue`（template 第 1-12 行 modal props，全局 style 第 2546-2612 行）
- **潜在风险点**：CSS `!important` 使用较多（因需覆盖 Arco Design 内部样式），需确认不影响弹窗模式；`render-to-body=false` 在 Arco 中的兼容性
- **已知遗留项**：无

======================

## 修复记录

**修复日期**：2026-08-07
**修复人**：AI Agent（auto 模式）

### 根因分析
`IssueCreatePanel.vue` 在 template 中有 `v-if="!isFullPage"`，当 `isFullPage=true` 时整个 `<a-modal>` 不渲染，导致全页模式下什么都不显示。原有设计意图是在全页模式用 `v-else` 渲染替代内容，但 `v-else` 分支从未实现。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `trackflow-web/src/views/issue/IssueCreatePanel.vue` | 移除 `v-if`；通过条件 props + CSS 覆盖让 modal 在全页模式以内联方式渲染（无 mask、无 fixed 定位、无阴影）|

### 影响范围
- `/issues/create` 路由的全页创建工单视图（修复核心问题）
- 弹窗模式不受影响（条件 props 在非 fullpage 时保持原有行为）
