# REQ-347-1：Token 过期后 WebSocket 不重新认证

## 基本信息

| 字段 | 内容 |
|------|------|
| 编号 | REQ-347-1 |
| 父需求 | REQ-347 |
| 标题 | Token 过期后 WebSocket 不重新认证 |
| 类型 | Bug 修复 |
| 严重程度 | P1 |
| 依赖 | 无 |

---

## 问题描述

Keycloak access token 有效期 5 分钟。WebSocket 连接建立时读取当前 token，之后不再更新。当 token 过期后：

1. 当前连接因为是 TCP 长连接，只要没有断线就仍然有效（服务端不会主动校验已建立连接的 token）
2. 但一旦连接意外断开触发重连，重连时 STOMP CONNECT 帧携带的还是**过期 token**，服务端 `WebSocketAuthInterceptor` 会拒绝，重连永远失败
3. `useWebSocket` 里有 `updateToken(newToken)` 方法，但**没有任何地方调用它**

```typescript
// useWebSocket.ts — updateToken 存在但从未被调用
function updateToken(newToken: string) {
  if (globalClient) {
    globalClient.connectHeaders = { token: newToken }  // 只更新 header
    // 已建立的连接不受影响，下次重连时才会用新 token
  }
}
```

```typescript
// authStore — token 刷新后没有通知 WebSocket
async function refreshToken() {
  // ...刷新 token...
  accessToken.value = newToken  // 更新了 store，但没通知 WebSocket
}
```

---

## 修复方案

### 前端：`src/composables/useWebSocket.ts`

在 `connect()` 函数中监听 `authStore.accessToken` 变化，token 变更时同步更新 connectHeaders：

```typescript
// 在 connect() 内部，globalClient 初始化之后添加：
import { watch } from 'vue'
import { useAuthStore } from '@/stores/auth'

// 监听 token 变化，实时更新 connectHeaders
const authStore = useAuthStore()
watch(
  () => authStore.accessToken,
  (newToken) => {
    if (newToken && globalClient) {
      globalClient.connectHeaders = { token: newToken }
      // 如果当前连接已断开（非主动 deactivate），用新 token 重连
      if (!globalClient.connected && !globalClient.active && connectionRefCount > 0) {
        globalClient.activate()
      }
    }
  }
)
```

**注意**：这个 watch 应该只初始化一次（用 `let tokenWatchStopper` 存储，避免重复注册）。

---

## 验收标准

1. 模拟场景：用户打开工单详情页，等待 token 过期（或手动让 token 失效），然后在另一个浏览器窗口操作同一个工单
2. **预期**：WebSocket 能在 token 刷新后正确用新 token 重连，不出现永久断线
3. `authStore.accessToken` 变化时，`globalClient.connectHeaders.token` 同步更新
4. 重连后订阅（`useIssueDetailSubscription`、`useIssueProjectSubscription`）自动恢复
5. 控制台不出现 `[WebSocket] STOMP error: Invalid token` 之类的重连失败日志

---

## 备注

- 当前连接不需要断开重建，只需保证下次重连时用新 token
- `@stomp/stompjs` 的 `connectHeaders` 是 plain object，直接赋值即可生效于下次 CONNECT 帧
- token watch 要在 `globalClient` 第一次创建时注册，避免重复注册

## 自动化状态

fix_status: DONE
fix_commit: 7fa13710
fix_round: 2
test_status: PENDING
test_round: 1
review_status: PENDING
review_round: 1

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-07 18:56

### 本次改动摘要
第2轮修复，解决 code review MUST 问题：token watcher 被绑定到首个调用组件的 effect scope，组件卸载后 watcher 静默失效。

- 改动1：`trackflow-web/src/composables/useWebSocket.ts` — 将 `tokenWatchStopper`（裸 watch 返回值）替换为 `tokenWatchScope`（detached effectScope）。使用 `effectScope(true)` 创建独立于任何组件生命周期的 scope，在其中注册 token watch。这样即使首个调用 connect() 的组件卸载，watcher 仍然存活，直到 disconnect() 引用计数归零时显式 `scope.stop()` 清理。
- 改动2：同文件 — 给 `updateToken()` 方法添加 `@deprecated` JSDoc 注释，说明其功能已被 watch 机制取代。

### 本次变更文件清单
- `trackflow-web/src/composables/useWebSocket.ts`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 打开任一工单详情页 → 确认 WebSocket 连接正常建立（控制台无 STOMP error）
  2. 验证 WebSocket 状态在页面加载后为 'connected'
  3. 打开另一个页面/标签操作同一工单 → 验证实时事件能正常接收
- **边界场景**：
  - 在详情页→列表页→详情页之间来回切换（模拟组件卸载/重挂载），确认 WebSocket 连接不丢失
  - Token 过期场景难以在 e2e 中 5 分钟内复现；核心验证是不引入回归
- **建议测试账号**：testuser（超级管理员）
- **注意事项**：本次改动仅影响前端 WebSocket composable，不涉及后端变更，无需重启后端

### 审核重点（给 code-review 会话）
- **重点关注文件**：`trackflow-web/src/composables/useWebSocket.ts`
- **上轮 MUST 问题处理**：
  - MUST-1（watch 绑定到组件 effect scope）：✅ 已修复，改用 `effectScope(true)` 创建 detached scope
- **上轮 SHOULD 建议处理**：
  - updateToken @deprecated 注释：✅ 已添加
- **潜在风险点**：无新增风险，逻辑与上轮一致，仅将 watcher 注册位置从组件 scope 移到独立 scope

======================

## 修复记录

**修复日期**：2026-08-07
**修复人**：AI Agent（auto 模式）

### 根因分析
`useWebSocket.ts` 中 `updateToken()` 方法虽然存在，但从未被任何代码调用。当 `authStore` 的 `refresh()` 方法刷新 token 后，`accessToken.value` 被更新，但 WebSocket 的 `globalClient.connectHeaders` 仍然持有旧 token。如果此时 WebSocket 连接意外断开并触发重连，STOMP CONNECT 帧携带过期 token，服务端拒绝认证，重连永远失败。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `trackflow-web/src/composables/useWebSocket.ts` | 新增模块级 `tokenWatchStopper` 变量；在 `connect()` 创建 `globalClient` 后注册 `watch(authStore.accessToken)` 同步 token 到 `connectHeaders`，断线时自动用新 token 重连；在 `disconnect()` 归零时清理 watcher |

### 影响范围
- WebSocket 连接管理（`useWebSocket` composable）
- 所有使用 WebSocket 订阅的页面（Issue 详情页、Issue 列表页）— 行为向后兼容，无破坏性变更

### 第 2 轮修复（code review MUST 反馈）

**问题**：watch() 绑定到首个调用组件的 effect scope，组件卸载后 watcher 静默失效，tokenWatchStopper 仍为非 null 阻止重新注册。

**修复**：
| 文件 | 改动说明 |
|------|----------|
| `trackflow-web/src/composables/useWebSocket.ts` | 将 `tokenWatchStopper` 替换为 `tokenWatchScope = effectScope(true)`（detached scope），watcher 注册在独立 scope 内不受组件卸载影响；disconnect 清理时调用 `scope.stop()`；给 `updateToken()` 加 `@deprecated` 注释 |


======================

## 代码审核报告（第 1 轮）

> 由 code-review 会话写入，供 fix 代理下轮修复时读取。
> 审核时间：2026-08-07 18:53

### 审核结论
🔴 需要修改后合并

### ❌ MUST 问题（必须修复，以下问题导致本轮 FAIL）

| # | 文件:行 | 问题描述 | 修复建议 |
|---|---------|---------|---------|
| 1 | useWebSocket.ts:118-131 | **watch 被绑定到首个调用 connect() 的组件的 effect scope**。当该组件卸载时，Vue 自动停止该 watcher，但 `tokenWatchStopper` 仍为非 null，导致后续不会注册新 watcher。场景：组件 A 调 connect() 创建 watcher → 组件 B 调 connect() 跳过 → 组件 A 卸载（Vue 自动 stop watcher）→ token 刷新时 watcher 已死，connectHeaders 不再更新，重连仍用过期 token。 | 使用 Vue 的 `effectScope()` 创建一个 **detached scope**，将 token watch 注册在这个独立 scope 内，使其不受任何组件卸载影响。修改如下：<br>1. 在文件顶部 import `effectScope` from 'vue'<br>2. 新增模块级变量 `let tokenWatchScope: ReturnType<typeof effectScope> \| null = null`<br>3. 在注册 watcher 时：`tokenWatchScope = effectScope(true)` (detached=true)，然后在 scope.run 内执行 watch<br>4. disconnect 清理时调用 `tokenWatchScope.stop()` 并置 null，移除 `tokenWatchStopper` 变量 |

### ❌ SHOULD 问题（建议修复）

| # | 文件:行 | 问题描述 | 修复建议 |
|---|---------|---------|---------|
| 无 | | | |

### ⚠️ 建议改进

1. **useWebSocket.ts:160-168** — `updateToken()` 方法功能已被 watch 机制完全覆盖，保留它可能让后续开发者困惑"应该调 updateToken 还是靠 watch 自动同步"。建议在方法上方加一行注释标注 `@deprecated` 说明该方法已被 watch 机制取代，仅保留向后兼容。

### 亮点

- 模块级 `tokenWatchStopper` 保证只注册一次，避免重复 watch 是正确思路
- disconnect 时 refcount 归零才清理 watcher，生命周期管理思路合理
- 重连时 `reconnectAttempts = 0` 重置计数——因为新 token 大概率能成功，避免浪费重连次数
- 重连条件 `!globalClient.connected && !globalClient.active` 双重检查避免重复 activate
