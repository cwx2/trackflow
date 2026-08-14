# REQ-677：看板视图「仅我的」按钮点击后状态切换为 active 但工单卡片未被过滤，开发人员无法快速聚焦个人工作

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-677 |
| 标题 | 看板视图「仅我的」按钮切换为 active 状态但未实际过滤工单 |
| 类型 | 功能缺失 |
| 严重程度 | P1 |
| 发现方式 | 开发人员工作流走查 |
| 发现日期 | 2026-08-14 |
| 关联模块 | 看板（Kanban Board） |
| 状态 | 修复中 |

## 1. 期望行为

在看板视图中，点击「👤 仅我的」按钮后：
- 看板应仅显示分配给当前登录用户（负责人=我）的工单卡片
- 顶部工单分布统计图应更新为仅包含我的工单
- 各列的工单计数和预估工时应重新计算
- 按钮激活时有视觉高亮，再次点击取消筛选恢复全部

## 2. TrackFlow 当前实现

以开发人员 `liuyang`（刘洋）登录，进入 FE1 看板视图：
- 看板展示所有 18 个工单（8 待处理 + 2 进行中 + 3 代码审查 + 0 测试中 + 2 待修复 + 3 已完成）
- 点击「👤 仅我的」按钮后：
  - 按钮变为 `[active]` 状态（有视觉变化）
  - **但看板上所有工单卡片均未被过滤** — 仍然显示全部 18 个工单
  - 包括不属于刘洋的工单如 FE1-56（张伟）、FE1-52（未分配）、FE1-44（王强）等仍然可见
  - 顶部分布图和各列工单计数也未变化

**参考截图**（修复时必看）：
- ![看板仅我的按钮无效](file:///D:/project/YT/test/tf-dev-kanban-only-mine-no-effect.png) — 点击"仅我的"后按钮 active 但工单未过滤

## 3. 差异分析

| 维度 | 期望 | 当前 | 差异 |
|------|------|------|------|
| 按钮状态 | active 时触发过滤 | active 状态切换正常 | 仅 UI 状态变了 |
| 工单过滤 | 仅显示负责人=当前用户 | 显示所有工单 | 过滤逻辑未执行 |
| 统计更新 | 重算分布和计数 | 保持全量数据 | 统计未跟随筛选 |
| 预估工时 | 仅计算我的工单 | 仍为全部（124h） | 数据不准确 |

## 4. 期望结果

点击「仅我的」后：
1. 仅保留 `assignee === currentUser` 的工单卡片
2. 各列标题计数更新为筛选后数量
3. 顶部工单分布图重算
4. 预估工时总计重算
5. 再次点击取消筛选恢复全部显示

## 5. 验收标准

- [ ] 点击「仅我的」后看板仅显示当前用户负责的工单
- [ ] 各列工单计数与实际过滤后卡片数量一致
- [ ] 顶部分布图按筛选后数据重新渲染
- [ ] 预估工时统计仅计算筛选后工单
- [ ] 取消「仅我的」后恢复全部工单展示
- [ ] 与「负责人」下拉筛选器不冲突

## 6. 备注

- 这是开发人员每日工作中最核心的操作之一：在看板上快速定位自己的工单
- 按钮 UI 状态切换正常说明前端事件绑定没问题，但数据过滤逻辑缺失或未正确触发
- REQ-564 之前被 rejected，但那是描述为"功能不存在"，现在情况不同——按钮存在且有 active 状态，只是过滤逻辑无效

---

## 审核记录

**审核日期**：2026-08-14
**审核结论**：✅ 通过

### YouTrack 对标验证

| 维度 | 结果 |
|------|------|
| 文档确认 | ✅ YouTrack agile-board-tips-and-tricks 文档明确描述了看板过滤功能（"Filter and Focus"章节），用户可通过 `for: Username` 查询语法过滤看板卡片按负责人 |
| 截图确认 | ✅ `![YouTrack 看板过滤截图](file:///D:/project/YT/test/review-yt-677-1.png)` 中展示了按负责人过滤看板卡片的功能 |
| 行为一致 | ✅ YouTrack 支持按负责人过滤看板工单卡片，TrackFlow 的「仅我的」按钮是该功能的用户友好实现 |

### TrackFlow 验证

| 维度 | 结果 |
|------|------|
| 问题存在 | ✅ 代码验证确认按钮 UI 状态切换正常（assigneeFilter 切换为 'me'），但过滤逻辑可能未正确传递到实际渲染 |
| 代码确认 | ✅ `useKanbanBoardImpl.ts` 中 `toggleMyIssues()` 函数和 `effectiveAssigneeId` computed 属性逻辑存在，`useBoardData.ts` 中 API 请求也传递了 `assigneeId` 参数，但实际效果未生效——属于功能存在但有 Bug 的情况 |

### 重复性检查

- REQ-564（rejected）描述了类似问题但措辞为"功能不存在"。REQ-677 情况不同：按钮已存在且有 active 状态切换，只是过滤逻辑未生效。按独立验证原则，代码确认了问题确实存在。

**通过理由**：YouTrack 明确支持看板按负责人过滤，TrackFlow 已实现了按钮 UI 和后端参数传递，但实际过滤未生效，属于 P1 级别的功能 Bug。
**前置依赖**：无


## 自动化状态

fix_status: DONE
fix_commit: 74fa4378
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-14 09:15

### 本次改动摘要
看板「仅我的」按钮过滤失败的根因是 `authStore.user.userId`（数据库用户ID）在某些场景下未加载：
- `userId` 仅在 PKCE 登录流程中通过 `/me` 接口获取并存入 localStorage
- 页面刷新时从 localStorage 恢复，但如果 localStorage 被清除则 `userId` 缺失
- 路由守卫不保证 `userId` 存在，导致 `effectiveAssigneeId` computed 返回 `undefined`
- API 请求不带 `assigneeId` 参数 → 后端返回全量工单 → 过滤未生效

修复方案：
- 改动1：`stores/auth.ts` — 新增 `ensureUserId()` 方法，若 userId 缺失则调用 `/me` 获取
- 改动2：`router/index.ts` — 在路由守卫中对已认证用户调用 `ensureUserId()`，保证任何页面加载前 userId 已就绪
- 改动3：`useKanbanBoardImpl.ts` — `toggleMyIssues()` 改为 async，点击时若 userId 仍缺失则主动获取后重试

### 本次变更文件清单
- `trackflow-web/src/stores/auth.ts`
- `trackflow-web/src/router/index.ts`
- `trackflow-web/src/views/board/composables/useKanbanBoardImpl.ts`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 liuyang 登录 → 打开看板页 `/boards?project=FE1` → 点击「👤 仅我的」→ 验证看板仅显示刘洋的工单（约 5 张卡片，分布在代码审查和已完成列）
  2. 再次点击「👤 仅我的」→ 验证取消过滤恢复全部工单（18张左右）
  3. URL 应正确反映 `&assignee=me`（激活时）和无 assignee 参数（取消时）
- **边界场景**：
  - 激活过滤后刷新页面 → 过滤应保持（从 URL/localStorage 恢复）
  - 分布图和列计数应与过滤后的工单数量一致
- **建议测试账号**：开发人员 liuyang（有 FE1 项目中的工单）
- **注意事项**：需确保 FE1 项目存在且有多个负责人的工单

### 审核重点（给 code-review 会话）
- **重点关注文件**：auth.ts（ensureUserId 方法）、router/index.ts（守卫调用时机）
- **潜在风险点**：每次导航都调用 ensureUserId() — 内部有 `if (user.value.userId) return` 短路检查，正常情况下不会发额外请求
- **已知遗留项**：无

======================

## 修复记录

**修复日期**：2026-08-14
**修复人**：AI Agent（auto 模式）

### 根因分析
看板「仅我的」过滤依赖 `authStore.user.userId`（数据库用户ID，非 Keycloak subject）。该值仅在 PKCE 登录流程中通过 `/api/v1/auth/me` 接口获取。页面刷新时依赖 localStorage 恢复。若 localStorage 被清理、或浏览器隐私模式、或首次跨设备登录后直接打开看板 URL，`userId` 可能为 undefined，导致 `effectiveAssigneeId` computed 返回 undefined，API 请求无 assigneeId 参数，后端返回全量工单。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `trackflow-web/src/stores/auth.ts` | 新增 `ensureUserId()` 方法：检查 userId 是否存在，不存在则调用 `/me` 获取 |
| `trackflow-web/src/router/index.ts` | 路由守卫中对已认证用户调用 `await authStore.ensureUserId()`，保证 userId 在任何页面渲染前就绪 |
| `trackflow-web/src/views/board/composables/useKanbanBoardImpl.ts` | `toggleMyIssues()` 改为 async，点击时若 userId 缺失主动调用 `ensureUserId()` 后重试 |

### 影响范围
- 看板「仅我的」过滤功能
- 所有依赖 `authStore.user.userId` 的功能（WebSocket 事件过滤、工单详情权限判断）间接受益
- 路由守卫增加一次轻量检查（有缓存短路，正常场景无额外网络请求）
