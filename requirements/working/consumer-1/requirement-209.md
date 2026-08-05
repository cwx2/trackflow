# REQ-209：工作流规则引擎缺少附件、链接、工时事件触发器

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-209 |
| 标题 | 工作流规则引擎缺少附件、链接、工时事件触发器 |
| 类型 | 功能缺失 |
| 严重程度 | P1 |
| 发现方式 | YouTrack 功能对标 |
| 发现日期 | 2026-08-05 |
| 关联模块 | Workflow / WorkflowRuleEngine / IssueService |
| 对标文档 | YouTrack: workflow-constructor-conditions.html |
| 状态 | 待修复 |

---

## 1. 问题描述

TrackFlow 工作流规则引擎目前只支持以下触发事件：
- `issue_created`（工单创建）
- `field_changed`（字段变更）
- `comment_added`（评论添加）
- On-schedule（定时）
- Action Rule（用户命令）

YouTrack 还支持以下事件触发，TrackFlow 完全缺失：

| 事件 | 说明 |
|------|------|
| 附件被添加（Attachment Is Added） | 有人上传了附件 |
| 附件被移除（Attachment Is Removed） | 附件被删除 |
| 工单链接被添加（Issue Link Is Added） | 两个工单建立了关联 |
| 工单链接被移除（Issue Link Is Removed） | 关联被解除 |
| 工时被添加（Work Item Is Added） | 有人记录了工时 |
| 工时被删除（Work Item Is Deleted） | 工时记录被删除 |
| 工单变为已解决（Issue Becomes Resolved） | 状态变更为"已解决类"状态 |
| 工单变为未解决（Issue Becomes Unresolved） | 状态从"已解决类"变回未解决 |

---

## 2. 典型使用场景

- **附件添加时自动打标签**："has-attachment" 标签，方便列表筛选
- **建立 parent-for 关联时自动给父工单添加评论**：通知父工单关注者
- **记录工时时自动更新字段**：如 % 完成度
- **工单变为已解决时通知报告人**：通过评论或标签

---

## 3. 技术方案

### 后端

**Step 1：发布事件**

各 Service 在对应操作完成后发布领域事件，仿照 `WorkflowRuleEvent.CommentAdded`：

```java
// 在 WorkflowRuleEvent.java 中新增：
public record AttachmentAdded(Long issueId, Long projectId, Long attachmentId) {}
public record AttachmentRemoved(Long issueId, Long projectId, Long attachmentId) {}
public record IssueLinkAdded(Long issueId, Long projectId, String linkType, Long targetIssueId) {}
public record IssueLinkRemoved(Long issueId, Long projectId, String linkType, Long targetIssueId) {}
public record WorkItemAdded(Long issueId, Long projectId, Long workItemId) {}
public record WorkItemDeleted(Long issueId, Long projectId, Long workItemId) {}
public record IssueResolved(Long issueId, Long projectId) {}     // 已有 IssueCreated，参考格式
public record IssueUnresolved(Long issueId, Long projectId) {}
```

在对应 Service 的操作方法末尾 `eventPublisher.publishEvent(...)` 发布事件：
- `IssueAttachmentService.uploadAttachment()` → 发布 `AttachmentAdded`
- `IssueAttachmentService.deleteAttachment()` → 发布 `AttachmentRemoved`
- `IssueService.linkIssue()` → 发布 `IssueLinkAdded`
- `IssueService.unlinkIssue()` → 发布 `IssueLinkRemoved`
- `WorkItemService.addWorkItem()` → 发布 `WorkItemAdded`
- `WorkItemService.deleteWorkItem()` → 发布 `WorkItemDeleted`
- `IssueService.applyTransition()` 中，若新状态为已解决类 → 发布 `IssueResolved`
- `IssueService.applyTransition()` 中，若旧状态为已解决类且新状态不是 → 发布 `IssueUnresolved`

**Step 2：在 WorkflowRuleEventListener 监听新事件**

```java
@EventListener
@Async
public void onAttachmentAdded(WorkflowRuleEvent.AttachmentAdded e) {
    engine.fireOnEvent(e.issueId(), e.projectId(), "attachment_added");
}
// 同样处理其他事件...
```

**Step 3：在 WorkflowRuleEngine 新增 fireOnEvent 方法**

```java
public void fireOnEvent(Long issueId, Long projectId, String triggerEvent) {
    List<WorkflowRule> rules = ruleMapper.findEnabledRules(projectId, triggerEvent);
    if (rules.isEmpty()) return;
    Issue issue = issueMapper.selectById(issueId);
    if (issue == null || issue.getDeletedAt() != null) return;
    evaluateAndExecute(rules, issue);
}
```

**Step 4：数据库**

`workflow_rule` 表的 `trigger_event` 字段 CHECK 约束需要扩展，允许新的事件类型值：
```sql
-- V260_workflow_trigger_events.sql（或追加到下一个迁移）
ALTER TABLE workflow_rule DROP CONSTRAINT IF EXISTS chk_workflow_rule_trigger_event;
ALTER TABLE workflow_rule ADD CONSTRAINT chk_workflow_rule_trigger_event
    CHECK (trigger_event IN (
        'issue_created', 'field_changed', 'comment_added',
        'attachment_added', 'attachment_removed',
        'link_added', 'link_removed',
        'work_item_added', 'work_item_deleted',
        'issue_resolved', 'issue_unresolved'
    ));
```

### 前端（WorkflowRulePanel.vue）

触发事件下拉列表新增选项：
```vue
<a-option value="attachment_added">附件被添加时</a-option>
<a-option value="attachment_removed">附件被移除时</a-option>
<a-option value="link_added">关联工单被添加时</a-option>
<a-option value="link_removed">关联工单被移除时</a-option>
<a-option value="work_item_added">工时被记录时</a-option>
<a-option value="work_item_deleted">工时被删除时</a-option>
<a-option value="issue_resolved">工单变为已解决时</a-option>
<a-option value="issue_unresolved">工单变为未解决时</a-option>
```

---

## 4. 验收标准

- [ ] 上传附件后，`attachment_added` 规则触发
- [ ] 删除附件后，`attachment_removed` 规则触发
- [ ] 建立工单关联后，`link_added` 规则触发
- [ ] 工单状态变为已解决类时，`issue_resolved` 规则触发
- [ ] 工单从已解决变回未解决时，`issue_unresolved` 规则触发
- [ ] 前端规则配置页面可以选择以上所有新触发事件

## 5. 测试案例

### TC-WF-ATTACH-001：附件添加触发
**步骤**：创建规则 trigger=attachment_added，动作=打标签 has-attachment
1. 以 wangqiang 打开工单，上传一个附件
2. 检查工单标签

**预期**：工单自动添加 has-attachment 标签

### TC-WF-RESOLVED-001：工单解决触发
**步骤**：创建规则 trigger=issue_resolved，动作=添加评论"工单已解决，请确认"
1. 以 wangqiang 将工单状态改为"已完成"（已解决类状态）
2. 检查评论区

**预期**：自动添加评论"工单已解决，请确认"

## 自动化状态

fix_status: DONE
fix_commit: ab9dd4e5
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 13:25

### 本次改动摘要
为工作流规则引擎新增 8 种事件触发器，使规则可由附件添加/移除、关联工单添加/移除、工时记录添加/删除、工单解决/重新打开等操作触发。

- 改动1：`WorkflowRuleEvent.java` — 新增 8 个 sealed record 类型（AttachmentAdded/Removed, LinkAdded/Removed, WorkItemAdded/Deleted, IssueResolved/Unresolved）
- 改动2：`WorkflowRuleEngine.java` — 新增通用 `fireOnEvent(issueId, projectId, triggerEvent)` 方法，加载对应 triggerEvent 的规则并执行
- 改动3：`WorkflowRuleEventListener.java` — 新增 8 个 @TransactionalEventListener 方法，委托给 fireOnEvent
- 改动4：`IssueService.java` — uploadAttachment 末尾发布 AttachmentAdded 事件；deleteAttachment 发布 AttachmentRemoved；状态转换代码中检测 isClosed 变化发布 IssueResolved/IssueUnresolved
- 改动5：`IssueLinkService.java` — createIssueLink 末尾发布双向 LinkAdded；deleteIssueLink 发布双向 LinkRemoved
- 改动6：`TimeEntryService.java` — create 方法末尾发布 WorkItemAdded；delete 方法发布 WorkItemDeleted
- 改动7：`WorkflowRulePanel.vue` — 触发事件下拉新增所有选项 + eventLabel/eventColor 扩展
- 改动8：`ProjectSettingsWorkflow.vue` — 同上

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/common/event/WorkflowRuleEvent.java`
- `trackflow-server/src/main/java/com/trackflow/issue/service/IssueLinkService.java`
- `trackflow-server/src/main/java/com/trackflow/issue/service/IssueService.java`
- `trackflow-server/src/main/java/com/trackflow/timeentry/service/TimeEntryService.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/WorkflowRuleEngine.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/WorkflowRuleEventListener.java`
- `trackflow-web/src/views/admin/WorkflowRulePanel.vue`
- `trackflow-web/src/views/project/settings/ProjectSettingsWorkflow.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 进入全局管理 → 工作流规则 → 创建新规则 → 触发事件下拉应包含 11 个选项（issue_created, field_changed, comment_added, attachment_added, attachment_removed, link_added, link_removed, work_item_added, work_item_deleted, issue_resolved, issue_unresolved）
  2. 以 testuser 登录 → 项目设置 → 工作流规则 → 创建 on_change 规则 → 触发事件下拉同样包含 11 个选项
  3. 创建一条 trigger=attachment_added 的规则（动作=打标签），验证保存成功
  4. 创建一条 trigger=issue_resolved 的规则（动作=添加评论），验证保存成功
- **边界场景**：
  - 选择 attachment_added 等新事件时，"监听字段" 下拉应不显示（只有 field_changed 才需要 triggerField）
- **建议测试账号**：超级管理员 testuser
- **注意事项**：此功能不需要 DB 迁移（trigger_event 字段是 VARCHAR(50) 无 CHECK 约束），后端无需重启即可验证前端下拉。后端事件发布逻辑需后端重启验证。

### 审核重点（给 code-review 会话）
- **重点关注文件**：WorkflowRuleEvent.java（sealed interface 扩展）, IssueService.java（新增事件发布位置正确性）, WorkflowRuleEngine.java（新方法签名合理性）
- **潜在风险点**：IssueLinkService 双向发布事件时，如果 source 和 target 属于不同项目，每个发布的 projectId 是否正确（已用各自的 getProjectId()）
- **已知遗留项**：无

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
工作流规则引擎仅支持 issue_created、field_changed、comment_added 三种触发事件。YouTrack 支持更多事件类型（附件、关联、工时、解决/未解决），TrackFlow 完全缺失这些触发器。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `WorkflowRuleEvent.java` | 新增 8 个 sealed record 事件类型 |
| `WorkflowRuleEngine.java` | 新增通用 fireOnEvent 方法，通过 triggerEvent 字符串查询匹配规则并执行 |
| `WorkflowRuleEventListener.java` | 新增 8 个异步事件监听器方法 |
| `IssueService.java` | uploadAttachment/deleteAttachment 发布附件事件；状态转换中检测 isClosed 变化发布 resolved/unresolved |
| `IssueLinkService.java` | createIssueLink/deleteIssueLink 发布双向链接事件 |
| `TimeEntryService.java` | create/delete 方法发布工时事件 |
| `WorkflowRulePanel.vue` | 触发事件下拉新增 11 个选项，标签和颜色映射扩展 |
| `ProjectSettingsWorkflow.vue` | 同上 |

### 影响范围
- 工作流规则引擎（新增事件类型支持）
- 工单附件上传/删除流程（新增事件发布）
- 工单关联创建/删除流程（新增事件发布）
- 工时记录创建/删除流程（新增事件发布）
- 工单状态转换流程（新增 resolved/unresolved 事件发布）
- 前端规则配置页面（全局管理 + 项目设置两处）
