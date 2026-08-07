# REQ-330：状态机画布初始加载时节点跑到左上角，未自动居中

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-330 |
| 标题 | 状态机画布初始加载时节点跑到左上角，未自动居中 |
| 类型 | Bug |
| 严重程度 | P1 |
| 发现方式 | 用户反馈 |
| 发现日期 | 2026-08-07 |
| 关联模块 | 工作流编辑器 / 状态机画布（WorkflowCanvasView.vue） |
| 状态 | 已修复 |

## 1. 问题描述

进入工作流编辑器的「状态机画布」Tab 后，画布中的状态节点全部堆叠在左上角，大片画布区域空白，需要用户手动点击「适配视图」按钮才能将节点居中显示。

预期行为：初始加载完成后，节点应自动居中显示在画布可视区域内。

## 2. 问题位置

`WorkflowCanvasView.vue` 的 `renderCanvas()` 函数，在节点渲染完成后调用 `fitView()`：

```javascript
// 有持久化位置时
nextTick(() => lf?.fitView())

// 无持久化位置时（走 autoLayout）
nextTick(() => lf?.fitView())
```

`fitView()` 在 `nextTick` 时机调用，但画布容器 DOM 的实际宽高可能尚未稳定（父容器 flex 布局、Tab 切换动画、异步数据加载等因素），导致 LogicFlow 用错误的容器尺寸计算 viewport，将所有节点"适配"到了左上角。

## 3. 期望结果

初次进入状态机画布时，节点自动居中显示在可视区域，不需要用户手动操作。画布底部工具栏的「适配视图」按钮效果与初始进入时一致。

修复思路：延迟 `fitView()` 的调用时机（如使用 `setTimeout` 替代 `nextTick`，或在容器尺寸确定后再调用），确保画布容器 DOM 完全渲染后再执行适配计算。

## 4. 验收标准

- [ ] 首次点击「状态机画布」Tab，节点自动居中显示，不偏移到左上角
- [ ] 切换筛选条件（如切换角色）重新渲染后，节点同样自动居中
- [ ] 手动拖动节点后，再次进入页面恢复到上次位置并居中
- [ ] 底部「适配视图」按钮功能保持正常

## 自动化状态

fix_status: DONE
fix_commit: 98b60ba
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-07 12:54

### 本次改动摘要
- 改动1：`trackflow-web/src/views/admin/WorkflowCanvasView.vue` — 将 `fitView()` 的调用从 `nextTick()` 替换为 `deferredFitView()` 方法。该方法通过 `requestAnimationFrame` 确保浏览器完成 layout 后再执行 fitView。当容器尺寸为 0（被 `v-show` 隐藏时）则通过 `ResizeObserver` 监听首次获得有效尺寸时自动触发 `lf.resize()` + `lf.fitView()`。

### 本次变更文件清单
- `trackflow-web/src/views/admin/WorkflowCanvasView.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 进入管理后台 → 工作流编辑器 → 点击「状态机画布」Tab → 验证节点自动居中显示（不在左上角）
  2. 在画布 Tab 下切换角色筛选条件 → 验证节点重新渲染后居中显示
  3. 点击底部工具栏「适应视图」按钮 → 验证节点正确居中
- **边界场景**：
  - 画布 Tab 是默认 Tab（非首次激活），节点应正常显示
  - 多次来回切换 matrix/canvas Tab，节点位置不丢失
- **建议测试账号**：testuser（超级管理员，有工作流管理权限）
- **注意事项**：本次改动仅影响画布渲染时 fitView 的调用时机，不影响节点拖拽、连线创建等交互功能

### 审核重点（给 code-review 会话）
- **重点关注文件**：WorkflowCanvasView.vue
- **潜在风险点**：ResizeObserver 回调中的 `lf` 引用是否可能在组件卸载后仍被调用（已通过 onBeforeUnmount 中 disconnect 防护）
- **已知遗留项**：无

======================

## 修复记录

**修复日期**：2026-08-07
**修复人**：AI Agent（auto 模式）

### 根因分析
`renderCanvas()` 在节点渲染完成后通过 `nextTick(() => lf?.fitView())` 执行视图适配。但在以下场景中容器 DOM 尺寸尚未稳定：
1. 组件挂载时画布 Tab 被 `v-show` 隐藏（`display: none`），容器 `clientWidth/clientHeight` 为 0
2. Tab 切换动画 / flex 布局重新计算尚未完成
LogicFlow 在容器尺寸为 0 时计算 viewport 导致所有节点被"适配"到左上角。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `trackflow-web/src/views/admin/WorkflowCanvasView.vue` | 新增 `deferredFitView()` 方法：当容器有有效尺寸时用 `requestAnimationFrame` 延迟执行 `fitView()`；当容器尺寸为 0 时通过 `ResizeObserver` 监听首次有效尺寸后再执行 `lf.resize()` + `lf.fitView()`。替换了 `renderCanvas()` 和 `autoLayout()` 中的 `nextTick(() => lf?.fitView())` 调用。在 `onBeforeUnmount` 中清理 `ResizeObserver`。 |

### 影响范围
- 工作流编辑器的状态机画布视图初始加载和筛选切换时的节点居中行为
