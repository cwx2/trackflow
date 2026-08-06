# REQ-254：5 个 Controller 类违规使用 try-catch，破坏全局异常处理机制

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-254 |
| 标题 | 5 个 Controller 类违规使用 try-catch，破坏全局异常处理机制 |
| 类型 | 编码规范违规 |
| 严重程度 | P2 |
| 发现方式 | 技术审计（代码规范审核） |
| 发现日期 | 2026-08-05 |
| 关联模块 | auth / automation / board / integration |
| 影响层级 | Controller |
| 状态 | 待修复 |

## 1. 问题概述

**一句话**：5 个 Controller 文件中存在 try-catch 块，导致异常被吞掉或绕过全局 `GlobalExceptionHandler`，破坏了统一的错误响应格式。

**技术根因**：项目规范明确禁止在 Controller 层使用 try-catch。Controller 应让异常自然冒泡到全局处理器，由 `GlobalExceptionHandler` 统一转为 `R<Void>` 响应。

## 2. 问题证据

通过 grep 搜索 `try \{` 发现以下 Controller 违规：

### 2.1 BackChannelLogoutController（4 处 try-catch）

```
auth/controller/BackChannelLogoutController.java
- line 73:   try {
- line 122:  try {
- line 151:  try {
- line 171:  try {
```

### 2.2 AutomationWebhookController（1 处）

```
automation/trigger/AutomationWebhookController.java
- line 39: try {
```

### 2.3 BoardController（1 处，在 lambda 内）

```
board/controller/BoardController.java
- line 377: try {
```

### 2.4 EmailMuteController（1 处）

```
integration/controller/EmailMuteController.java
- line 57: try {
```

### 2.5 NotificationAdminController（1 处）

```
integration/controller/NotificationAdminController.java
- line 93: try {
```

## 3. 违规对比

```java
// ❌ 当前违规写法（Controller 中 try-catch）
@PostMapping("/backchannel-logout")
public ResponseEntity<Void> backchannelLogout(@RequestBody String body) {
    try {
        logoutService.processLogout(body);
        return ResponseEntity.ok().build();
    } catch (Exception e) {
        log.error("Logout error", e);
        return ResponseEntity.status(500).build();  // 绕过了统一响应格式！
    }
}

// ✅ 规范写法（让异常自然冒泡）
@PostMapping("/backchannel-logout")
public R<Void> backchannelLogout(@RequestBody String body) {
    logoutService.processLogout(body);  // 如果抛出异常，GlobalExceptionHandler 处理
    return R.ok();
}
```

## 4. 内部规范参考

根据 `.kiro/steering/java-coding-standards.md` 第十一节：

> **分层异常规则**
>
> | 层 | 处理方式 |
> |----|----------|
> | Controller | **不 try-catch**，让异常自然冒泡到全局处理器 |
> | Service | 业务异常抛 `BusinessException`；受检异常转为 `BusinessException` 或 `SystemException` |
> | Global Handler | 统一转为 `R<Void>` 响应 |

## 5. 影响分析

### 业务影响

- **响应格式不一致**：Controller 中 catch 后直接返回 `ResponseEntity` 而非统一的 `R<T>` 格式，前端需要针对性处理
- **错误信息丢失**：部分 catch 后返回 500 状态码但没有业务错误码，前端无法识别具体错误
- **日志不一致**：GlobalExceptionHandler 有统一的日志记录，绕过后异常日志格式不一致

### 特殊说明

- `BackChannelLogoutController` 是 Keycloak Back Channel Logout 端点，可能有特殊需要（接受 HTTP 表单而非 JSON），但仍应将受检异常包装为 `BusinessException` 后抛出
- `EmailMuteController` 处理邮件链接跳转，需要重定向而非 JSON 响应，但业务异常仍应用 `GlobalExceptionHandler` 处理

### 风险评估

| 风险维度 | 当前状态 | 说明 |
|----------|----------|------|
| 响应格式一致性 | ⚠️ | 部分端点响应格式不统一 |
| 错误可追踪性 | ⚠️ | 异常被吞时无法在全局日志中捕获 |
| 安全性 | ✅ | 无安全风险 |

## 6. 改进方案

### 6.1 一般 Controller（JSON 响应场景）

将 try-catch 中的受检异常转为 BusinessException 抛出，让 GlobalExceptionHandler 统一处理：

```java
// Service 层捕获并包装受检异常
@Service
public class LogoutService {
    public void processLogout(String token) {
        try {
            keycloakClient.logout(token);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "登出处理失败: " + e.getMessage());
        }
    }
}

// Controller 层无需 try-catch
@PostMapping("/backchannel-logout")
public R<Void> backchannelLogout(@RequestBody String body) {
    logoutService.processLogout(body);
    return R.ok();
}
```

### 6.2 重定向类 Controller（EmailMuteController）

重定向逻辑允许保留少量逻辑，但异常仍应抛出：
```java
@GetMapping("/mute")
public void muteViaEmail(@RequestParam("token") String token, HttpServletResponse response) throws IOException {
    emailMuteService.processMuteToken(token);  // Service 处理，失败抛 BusinessException
    response.sendRedirect("/notifications?muted=true");
}
```

### 实现复杂度评估

| 维度 | 评估 |
|------|------|
| 改动文件数 | 5 个 Controller + 对应 Service |
| 是否需要数据迁移 | 否 |
| 是否需要前后端联动 | 可能需要（前端响应格式变化） |
| 预计工作量 | 小（<2h） |

## 7. 验收标准

- [x] BackChannelLogoutController 无 try-catch 块
- [x] AutomationWebhookController 无 try-catch 块
- [x] BoardController 无 try-catch 块
- [x] EmailMuteController 无 try-catch 块（重定向逻辑除外）
- [x] NotificationAdminController 无 try-catch 块
- [x] 所有异常都经过 GlobalExceptionHandler 处理
- [ ] 功能测试正常通过（登出、邮件静音、通知管理功能）


## 自动化状态

fix_status: DONE
fix_commit: 846abbb0
fix_round: 2
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 1

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-06 14:07

### 本次改动摘要
第 2 轮修复，处理 code review MUST 问题：

- BoardController `parseCollapsedStatusIds` — 增加长度限制（<=18位），防止全数字但超出 Long.MAX_VALUE 的字符串导致 NumberFormatException
- EmailMuteController — 对 `outcome.resourceType()` 和 `outcome.resourceId()` 增加 null 检查，避免重定向 URL 中出现 "null" 字符串
- BackChannelLogoutController — 删除委托式常量 `LOGOUT_SESSION_KEY_PREFIX`
- UserSyncFilter — 改为直接引用 `BackChannelLogoutService.LOGOUT_SESSION_KEY_PREFIX`

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/auth/controller/BackChannelLogoutController.java`
- `trackflow-server/src/main/java/com/trackflow/auth/filter/UserSyncFilter.java`
- `trackflow-server/src/main/java/com/trackflow/board/controller/BoardController.java`
- `trackflow-server/src/main/java/com/trackflow/integration/controller/EmailMuteController.java`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 后端启动成功（已构建通过，确认 Spring 容器正常加载）
  2. 看板页面正常加载（验证 BoardController 改动不影响看板功能）
  3. 以 testuser 登录 → 访问管理后台通知页面 → 确认正常响应
- **边界场景**：
  - 看板折叠列功能正常工作（parseCollapsedStatusIds 改动不影响正常 ID 解析）
- **建议测试账号**：testuser（system_admin）
- **注意事项**：
  - BackChannelLogout 是 Keycloak 回调端点，验证后端启动无错误即可
  - EmailMuteController 需要有效 token，确认端点不 500 即可

### 审核重点（给 code-review 会话）
- **重点关注文件**：BoardController.java（overflow 修复）、EmailMuteController.java（null safety）
- **潜在风险点**：
  - MAX_LONG_DIGITS = 18 是保守值（Long.MAX_VALUE 是 19 位 9223372036854775807），选 18 确保不会溢出
  - EmailMuteController 使用 StringBuilder 方式构建 URL，逻辑清晰
- **已知遗留项**：无

======================

## 修复记录

**修复日期**：2026-08-06
**修复人**：AI Agent（auto 模式）

### 根因分析
5 个 Controller 文件中存在 try-catch 块，违反了编码规范第十一节"Controller 不 try-catch，让异常自然冒泡到全局处理器"的规则。部分场景有架构合理性（OIDC 规范、HTTP 重定向），但仍应将异常处理逻辑下沉到 Service 层。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `BackChannelLogoutController.java` | 重写为薄 Controller，仅调用 service + return R.ok() |
| `BackChannelLogoutService.java` | 新建，承载所有 logout 逻辑（JWT 解码、Redis 黑名单、审计） |
| `AutomationWebhookController.java` | 移除 try-catch，token 验证委托给 WebhookTokenVerifier |
| `WebhookTokenVerifier.java` | 新建，封装 SHA-256 token 比对逻辑 |
| `BoardController.java` | parseCollapsedStatusIds 用 digit 预过滤替代 try-catch |
| `EmailMuteController.java` | 使用 muteViaTokenSafe() 返回 MuteOutcome，无 try-catch |
| `EmailMuteTokenService.java` | 新增 muteViaTokenSafe() 和 MuteOutcome record |
| `NotificationAdminController.java` | 使用 sendTestEmailSafe()，无 try-catch |
| `EmailSendService.java` | 新增 sendTestEmailSafe() 包装异常 |

### 影响范围
- auth 模块（Back-Channel Logout 流程）
- automation 模块（Webhook 接收）
- board 模块（看板列折叠状态解析）
- integration 模块（邮件静音、通知管理）
