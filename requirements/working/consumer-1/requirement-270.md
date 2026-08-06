# REQ-270：护眼模式下 Arco Design 组件颜色未跟随主题，与页面背景不协调

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-270 |
| 标题 | 护眼模式下 Arco Design 组件颜色未跟随主题，与页面背景不协调 |
| 类型 | UI差异 |
| 严重程度 | P1 |
| 发现方式 | 用户反馈 + 系统走查 |
| 发现日期 | 2026-08-06 |
| 关联模块 | 主题系统、自动化模块、工单列表、工作流、仪表盘、管理页面等 |
| 状态 | 待修复 |

## 1. 问题描述

切换到护眼模式（绿色主题）后，部分页面的 Arco Design 组件颜色没有跟随护眼主题变化，仍然使用暗色（dark）主题的颜色，导致页面整体颜色不协调、不统一。

**根本原因分析：**

在 `src/composables/useTheme.ts` 中，护眼模式（`green`）切换时，`arco-theme` 属性被强制设置为 `dark`：

```typescript
function applyTheme(theme: ThemeMode) {
  document.documentElement.setAttribute('data-theme', theme)
  if (theme === 'light') {
    document.body.removeAttribute('arco-theme')
  } else {
    document.body.setAttribute('arco-theme', 'dark')  // 护眼模式走这里，但用了 dark 主题
  }
  localStorage.setItem(STORAGE_KEY, theme)
}
```

这导致 Arco Design 的组件（如 Tag、Badge、Modal、Table 等）使用暗色主题的配色，而不是护眼绿色调的配色。

## 2. 受影响页面

### 已确认问题的页面（用户截图）

**自动化页面（/automation）：**
- 工作流列表中的"草稿"标签（Tag）颜色为橙色，与深绿背景不协调
- "未启动"运行状态标签颜色为蓝色，与深绿背景不协调
- "编辑"、"执行历史"、"删除"操作按钮颜色与主题不符

**工单列表页面（/issues）：**
- 状态标签（待处理/进行中/代码审查等）颜色与深绿背景不协调
- 优先级标签颜色与主题不符
- 类型标签（缺陷/需求/任务等）颜色与主题不符

### 需进一步排查的页面

以下页面也可能存在同样问题，需要逐一验证：
- 仪表盘（/dashboard）- 各种图表、Widget 组件颜色
- 工作流编辑器（/workflow）- 节点状态颜色、按钮颜色
- Sprint 规划（/sprint-planning）- 状态标签、按钮
- 报表（/reports）- 图表颜色
- 管理页面（/admin）- 用户状态标签、权限标签
- 工单详情页 - 状态流转按钮颜色
- Issue 创建弹窗 - 表单组件颜色

## 3. 截图

**护眼模式 - 工单列表**：
`![护眼模式工单列表](file:///D:/project/YT/test/tf-eyecare-1-issues.png)`

**护眼模式 - 自动化页面（状态标签颜色不协调）**：
`![护眼模式自动化页面](file:///D:/project/YT/test/tf-eyecare-2-automation.png)`

> ⚠️ 修复此需求的开发者必须用 `mcp_trackflow_test_view_screenshot` 查看以上截图，确认问题现象。

## 4. 期望结果

### 修复方案

**方案一（推荐）：为护眼模式创建专用 Arco Design CSS 变量覆盖**

在 `src/styles/variables.css` 中，在 `[data-theme="green"]` 块内添加 Arco Design 的 CSS 变量覆盖，使组件颜色与护眼主题协调：

```css
[data-theme="green"] {
  /* Arco Design 组件变量覆盖 - 护眼主题 */
  --color-primary-1: #1a2a1a;
  --color-primary-2: #1e2e1e;
  --color-primary-3: #243524;
  --color-primary-4: #2b3f2b;
  --color-primary-5: #3a5239;
  --color-primary-6: #7dcea0;
  --color-primary-7: #9eddb9;
  /* ... 其他颜色变量 */
}
```

**方案二（备用）：修改 useTheme.ts 的 arco-theme 设置逻辑**

在 `applyTheme` 函数中，为护眼模式单独处理 `arco-theme` 属性，或添加护眼主题对应的 CSS 变量前缀。

### 视觉要求

修复后，护眼模式下所有页面的 Arco Design 组件应具有：
- 背景色：使用绿色调（如 `#1e2a1e`、`#243524`、`#2b3f2b`）
- 文字色：使用淡绿色调（如 `#d4e7d0`、`#a3c49e`）
- 强调色：使用主题强调色（如 `#7dcea0`）
- 标签/Badge：颜色与整体主题协调，不出现刺眼的高饱和橙色/蓝色
- 按钮：primary 按钮使用护眼强调色 `#7dcea0`

## 5. 验收标准

- [ ] 切换到护眼模式后，自动化页面中的"草稿"、"未启动"等标签颜色与深绿背景协调
- [ ] 切换到护眼模式后，工单列表中的状态标签、优先级标签颜色与深绿背景协调
- [ ] 切换到护眼模式后，工作流编辑器页面颜色协调
- [ ] 切换到护眼模式后，仪表盘各 Widget 组件颜色协调
- [ ] 切换到护眼模式后，Sprint 规划页面颜色协调
- [ ] 切换到护眼模式后，管理页面颜色协调
- [ ] 护眼模式与暗色模式、亮色模式之间切换时，所有组件颜色均能正确跟随主题变化
- [ ] 三种主题各截图对比，视觉上均协调统一，无突兀的颜色突兀感

## 6. 备注

- 护眼模式的 CSS 变量已在 `src/styles/variables.css` 中定义了 `[data-theme="green"]` 块，但 Arco Design 组件的颜色变量（如 `--color-primary-*`）需要在该块中补充覆盖
- 修复时需要参考暗色主题中已有的 Arco Design 颜色变量，将其调整为绿色调版本
- 工作流编辑器有专用的 `--wf-*` 变量，已有护眼模式覆盖，可能不受本问题影响，需单独验证

## 自动化状态

fix_status: DONE
fix_commit: 98d1d6b
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-06 16:22

### 本次改动摘要
- 改动1：`trackflow-web/src/styles/variables.css` — 在护眼主题 `[data-theme="green"]` 块之后、全局重置之前，新增了一个完整的 Arco Design CSS 变量覆盖块。选择器为 `html[data-theme="green"] body[arco-theme='dark']`，该选择器特异性高于 Arco 原生的 `body[arco-theme='dark']`，因此能有效覆盖 Arco 暗色主题变量。覆盖内容包括：全部调色板（arcoblue/red/orange/blue/green/gray/gold/cyan/purple/orangered）、语义色映射（primary/success/danger/warning/link）、背景色（--color-bg-1~5）、文字色（--color-text-1~4）、填充色（--color-fill-1~4）、边框色、以及 Tooltip/Spin/Menu/Mask 等特殊组件颜色。所有颜色值都调整为降饱和度、偏绿色调版本，确保与深绿背景协调。

### 本次变更文件清单
- `trackflow-web/src/styles/variables.css`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 切换到护眼模式（点击主题切换按钮两次，从暗色→亮色→护眼） → 打开工单列表页面 → 验证状态标签、优先级标签颜色与深绿背景协调（不应出现高饱和度橙色/蓝色）
  2. 在护眼模式下 → 打开自动化页面（/automation）→ 验证"草稿"标签、运行状态标签颜色与深绿背景协调
  3. 在护眼模式下 → 切换到暗色模式 → 验证暗色模式组件颜色正常（未被影响）
  4. 在护眼模式下 → 切换到亮色模式 → 验证亮色模式组件颜色正常（未被影响）
- **边界场景**：
  - 三种主题来回切换时，颜色应即时跟随变化，无残留
- **建议测试账号**：超级管理员 testuser
- **注意事项**：本次改动是纯 CSS 变量覆盖，不涉及 JS 逻辑变更，无需重启后端。仅需前端页面刷新即可生效。

### 审核重点（给 code-review 会话）
- **重点关注文件**：`trackflow-web/src/styles/variables.css`
- **潜在风险点**：CSS 变量值是否合理、是否覆盖完整、选择器特异性是否正确
- **已知遗留项**：无

======================

## 修复记录

**修复日期**：2026-08-06
**修复人**：AI Agent（auto 模式）

### 根因分析
在 `useTheme.ts` 的 `applyTheme` 函数中，护眼模式（green）切换时，`arco-theme` 属性被强制设置为 `dark`（即 `document.body.setAttribute('arco-theme', 'dark')`）。这导致 Arco Design 组件使用暗色主题的 CSS 变量（灰色调背景、标准蓝色 primary、标准橙色 warning 等），而非与护眼绿色调协调的颜色。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `trackflow-web/src/styles/variables.css` | 在护眼主题块后新增 `html[data-theme="green"] body[arco-theme='dark']` 选择器块，全面覆盖 Arco Design 暗色主题的 CSS 变量（调色板、语义色、背景色、文字色、填充色、边框色等），使所有 Arco 组件在护眼模式下使用绿色调配色 |

### 影响范围
- 护眼模式下所有使用 Arco Design CSS 变量的组件（Tag、Badge、Table、Modal、Button、Input、Select 等）
- 不影响暗色模式和亮色模式
