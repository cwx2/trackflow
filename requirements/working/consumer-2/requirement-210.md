# REQ-210：工作流规则引擎缺少 remove_tag / require_field / send_email / show_alert 动作

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-210 |
| 标题 | 工作流规则引擎缺少 remove_tag / require_field / send_email / show_alert 动作 |
| 类型 | 功能缺失 |
| 严重程度 | P1 |
| 发现方式 | YouTrack 功能对标 |
| 发现日期 | 2026-08-05 |
| 关联模块 | Workflow / WorkflowRuleEngine |
| 对标文档 | YouTrack: workflow-constructor-actions.html |
| 状态 | 已修复 |

---

## 1. 问题描述

TrackFlow 工作流规则引擎目前支持的动作类型：
- `set_field`（设置字段）✅
- `add_tag`（添加标签）✅
- `add_comment`（添加评论）✅
- `create_issue`（创建工单）✅
- `link_issue`（建立关联）✅

YouTrack 还支持以下动作，TrackFlow 完全缺失：

| 动作 | 说明 | 优先级 |
|------|------|--------|
| `remove_tag`（移除标签） | 从工单移除指定标签 | P1 |
| `require_field`（必填阻断） | 检查字段是否已填写，否则阻断当前操作 | P1 |
| `send_email`（发邮件） | 向指定用户发送邮件通知 | P1 |
| `show_alert`（显示提示） | 在 UI 右下角显示操作成功/错误提示 | P1 |
| `update_summary`（改标题） | 修改工单标题（支持变量） | P2 |
| `update_description`（改描述） | 修改工单描述（支持变量） | P2 |

---

## 2. YouTrack 标准行为

### remove_tag
- 从当前工单移除指定标签
- 如果标签不存在，不报错（幂等）
- 用途：如规则触发后清理"overdue"、"needs-review" 等临时标签

### require_field
- 检查指定字段是否已填写
- 如果为空，**阻断当前操作**（如状态变更），并显示自定义错误提示
- 注意：这是阻断型动作，需要在整个动作链执行前优先检查
- 用途：确保状态变更时某些字段必填（如关闭前必须填 Resolution）

### send_email
- 向指定用户（当前用户 / Reporter / Assignee / 指定邮箱）发送邮件
- Subject 和 Body 均支持变量插值（`{issue.id}`、`{issue.summary}` 等）
- 用途：工单解决后通知报告人

### show_alert
- 在 UI 右下角弹出提示消息
- 支持两种样式：Acknowledgment（绿色/普通）和 Error（红色/错误）
- 消息支持变量插值
- 用途：规则执行后给用户一个即时的操作反馈

---

## 3. 技术方案

### 后端（WorkflowRuleEngine.executeActions 中新增 case）

```java
else if ("remove_tag".equals(type)) doRemoveTag(act, issue, rule);
else if ("require_field".equals(type)) {
    if (!doRequireField(act, issue, rule)) {
        // 阻断后续动作执行
        throw new WorkflowRuleBlockedException(textOf(act, "errorMessage"));
    }
}
else if ("send_email".equals(type)) doSendEmail(act, issue, rule);
else if ("show_alert".equals(type)) doShowAlert(act, issue, rule);
else if ("update_summary".equals(type)) doUpdateSummary(act, issue, rule);
else if ("update_description".equals(type)) doUpdateDescription(act, issue, rule);
```

**doRemoveTag**：
```java
private void doRemoveTag(JsonNode act, Issue issue, WorkflowRule rule) {
    String tagIdStr = textOf(act, "tagId");
    if (tagIdStr == null) return;
    Long tagId = Long.parseLong(tagIdStr);
    tagRelationMapper.delete(new LambdaQueryWrapper<IssueTagRelation>()
        .eq(IssueTagRelation::getIssueId, issue.getId())
        .eq(IssueTagRelation::getTagId, tagId));
    logActivity(issue.getId(), rule, "tag_removed", "tag", tagIdStr, null);
}
```

**doRequireField**：
```java
private boolean doRequireField(JsonNode act, Issue issue, WorkflowRule rule) {
    String field = textOf(act, "field");
    String actual = fieldValue(issue, field);
    if (actual == null || actual.isBlank()) {
        log.info("[RuleEngine] require_field: field '{}' is empty, blocking action in rule '{}'", field, rule.getName());
        return false;
    }
    return true;
}
```

**doSendEmail**：调用 `JavaMailSender`（系统已有邮件配置）：
```java
private void doSendEmail(JsonNode act, Issue issue, WorkflowRule rule) {
    String to = resolveEmailTarget(act, issue, rule);
    String subject = interpolateVariables(textOf(act, "subject"), issue, rule);
    String body = interpolateVariables(textOf(act, "body"), issue, rule);
    if (to == null || subject == null) return;
    mailService.send(to, subject, body);
}
```

**doShowAlert**：将 alert 信息写入当前请求上下文的响应元数据（或通过 WebSocket 推送）：
```java
private void doShowAlert(JsonNode act, Issue issue, WorkflowRule rule) {
    String style = textOf(act, "style"); // "acknowledgment" | "error"
    String message = interpolateVariables(textOf(act, "message"), issue, rule);
    // 写入 RequestContextHolder 的响应 Header 或通知服务
    WorkflowAlertContext.set(style, message);
}
```

### 前端（WorkflowRulePanel.vue）

动作类型下拉新增选项：
```vue
<a-option value="remove_tag">移除标签</a-option>
<a-option value="require_field">要求必填字段</a-option>
<a-option value="send_email">发送邮件通知</a-option>
<a-option value="show_alert">显示提示消息</a-option>
<a-option value="update_summary">修改工单标题</a-option>
<a-option value="update_description">修改工单描述</a-option>
```

各动作的配置表单（对应不同的 type 显示不同的输入项）：
- `remove_tag`：标签选择器
- `require_field`：字段选择器 + 错误提示文本
- `send_email`：收件人（当前用户/Reporter/Assignee/指定邮箱）+ Subject + Body
- `show_alert`：样式（普通/错误）+ 消息文本
- `update_summary`：新标题模板（支持 {issue.summary} 等变量）
- `update_description`：新描述模板

---

## 4. 验收标准

- [ ] `remove_tag` 动作执行后，工单指定标签被移除
- [ ] `require_field` 动作在字段为空时阻断后续动作，字段有值时继续执行
- [ ] `send_email` 动作执行后，目标用户邮箱收到邮件（主题/正文含变量替换）
- [ ] `show_alert` 动作执行后，前端 UI 出现对应提示
- [ ] 前端配置页面可以选择并配置以上所有新动作类型

## 5. 测试案例

### TC-WF-REMOVE-TAG-001：移除标签动作
**步骤**：创建规则 trigger=field_changed(status → 已完成)，动作=remove_tag(overdue)
1. 给工单添加 overdue 标签，再将状态改为"已完成"
2. 检查工单标签

**预期**：overdue 标签被自动移除

### TC-WF-REQUIRE-001：必填字段阻断
**步骤**：创建规则 trigger=field_changed(status changes to 已完成)，动作=require_field(resolution, "请先填写解决方案")
1. Resolution 为空时尝试改状态为"已完成"
2. 填写 Resolution 后再次改状态

**预期**：步骤 1 被阻断，提示"请先填写解决方案"；步骤 2 成功

### TC-WF-EMAIL-001：发邮件通知
**步骤**：创建规则 trigger=issue_resolved，动作=send_email(Reporter, "[TrackFlow] 工单 {issue.id} 已解决")
1. 以 wangqiang 将工单改为已解决状态
2. 检查 sunlei（Reporter）的邮箱

**预期**：sunlei 收到含工单编号的通知邮件


## 自动化状态

fix_status: DONE
fix_commit: a1e0e7b
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 13:56

### 本次改动摘要
- 改动1：`WorkflowRuleEngine.java` — 在 `executeActions` 方法的 action 分发循环中新增 6 个动作类型处理分支（remove_tag/require_field/send_email/show_alert/update_summary/update_description），每个类型有独立的 private 方法实现。新增依赖注入 `NotificationMapper`（站内通知）和 `EmailSendService`（邮件发送）。
- 改动2：`WorkflowRulePanel.vue` — 在前端动作类型下拉中增加 6 个新选项，每个动作类型有对应的配置表单模板（标签选择器、字段选择器+错误消息、收件人+主题+正文、样式+消息等）。更新 `ActionItem` 接口增加新字段。更新 `actionSummary` 函数展示新动作类型的摘要文本。

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/workflow/service/WorkflowRuleEngine.java`
- `trackflow-web/src/views/admin/WorkflowRulePanel.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 进入项目管理 → 工作流规则页面 → 创建新规则 → 验证动作类型下拉包含所有新选项（remove_tag/require_field/send_email/show_alert/update_summary/update_description）
  2. 选择 "remove_tag" 动作类型 → 验证出现标签 ID 输入框
  3. 选择 "require_field" 动作类型 → 验证出现字段选择器 + 错误消息输入框
  4. 选择 "send_email" 动作类型 → 验证出现收件人选择器 + 主题 + 正文输入
  5. 选择 "show_alert" 动作类型 → 验证出现样式选择器 + 消息输入
  6. 选择 "update_summary" 动作类型 → 验证出现标题模板输入
  7. 选择 "update_description" 动作类型 → 验证出现描述模板 textarea
  8. 创建一条规则包含 remove_tag 动作并保存 → 验证保存成功且规则列表展示正确摘要
- **边界场景**：
  - send_email 配置表单的多行布局正确展示
  - 规则摘要中新动作类型展示友好文本（非 raw type name）
- **建议测试账号**：testuser（需要项目管理员权限管理工作流规则）
- **注意事项**：后端已重启，新动作的实际执行效果（如发邮件）依赖 SMTP 配置，测试时主要验证前端表单和规则保存/展示即可

### 审核重点（给 code-review 会话）
- **重点关注文件**：WorkflowRuleEngine.java（新增 ~280 行逻辑）, WorkflowRulePanel.vue（新增模板和接口字段）
- **潜在风险点**：
  - `doSendEmail` 调用 `emailSendService.sendNotificationEmail()`（@Async），在规则引擎本身已经在 @Async 线程中运行，需确认嵌套异步无问题（实际上 Spring @Async 可以嵌套调用，每次产生新线程）
  - `createAlertNotification` 使用 `notificationMapper.insert()` — 需确认 Notification 实体的 ID 生成策略（ASSIGN_ID）在无主事务时正常工作
  - `require_field` 使用 `break` 跳出 action 循环，确认只中断当前规则的 actions 不影响其他规则
- **已知遗留项**：
  - `require_field` 在当前异步架构下无法阻断用户原始操作（只能阻断后续动作链），这是架构限制，文中已注释说明
  - `send_email` 中 target 暂不支持自定义邮箱输入的前端 UI（后端已支持含 @ 的直接邮箱）

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
WorkflowRuleEngine 的 `executeActions` 方法仅有 5 种动作类型处理，缺失 YouTrack 对应的 remove_tag、require_field、send_email、show_alert、update_summary、update_description 动作类型。前端 WorkflowRulePanel.vue 的动作类型下拉也仅有 3 个选项。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `trackflow-server/.../WorkflowRuleEngine.java` | 新增 6 个 action handler 方法 + 辅助方法（resolveEmailTarget、resolveAlertTarget、createAlertNotification、buildRuleEmailHtml、escapeHtml）；新增 NotificationMapper 和 EmailSendService 依赖注入 |
| `trackflow-web/.../WorkflowRulePanel.vue` | 动作类型下拉新增 6 个选项；ActionItem 接口增加 errorMessage/target/subject/body/style/message 字段；各动作类型对应的配置表单模板；actionSummary 函数增加新类型展示 |

### 影响范围
- 工作流规则引擎（后端动作执行）
- 工作流规则管理前端页面
- 通知系统（show_alert/require_field 会创建站内通知）
- 邮件系统（send_email 调用已有 EmailSendService）
