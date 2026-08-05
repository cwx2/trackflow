# REQ-214：初始状态标记功能缺少 UI 操作入口

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-214 |
| 标题 | 初始状态标记功能缺少 UI 操作入口（is_initial 字段有但无法配置） |
| 类型 | 功能缺失 |
| 严重程度 | P1 |
| 发现方式 | YouTrack 功能对标 |
| 发现日期 | 2026-08-05 |
| 关联模块 | Workflow / WorkflowEditor / WorkflowService |
| 对标文档 | YouTrack: workflow-constructor-state-machines.html |
| 状态 | 待修复 |

---

## 1. 问题描述

`workflow_transition` 表中已有 `is_initial BOOLEAN` 字段（REQ-203-1 阶段添加），表示某个状态是该 Issue Type 在该项目下新建工单时的默认初始状态。

但是：
1. **前端 WorkflowEditor 没有标记初始状态的 UI 入口**——用户无法通过界面设置初始状态
2. **后端 `IssueService.createIssue()` 没有读取初始状态**——创建工单时不使用此配置，而是用固定逻辑（取第一个 Open 类状态）
3. 结果：这个字段从来没被使用过

---

## 2. YouTrack 标准行为

YouTrack 状态机画布中，每个状态块有一个"Mark as initial"控件：
- 标记后，该状态显示特殊图标（如星形）
- 每种 Issue Type 只能有一个初始状态
- 创建工单时，新工单默认进入该类型的初始状态

---

## 3. 技术方案

### 前端（WorkflowEditor.vue / WorkflowRulePanel.vue）

在状态转换矩阵的行标题（状态名称）旁边，添加"设为初始状态"操作：
- 方式一：状态名旁的 ⭐ 图标（点击切换）
- 方式二：右键菜单 → "设为此类型初始状态"

逻辑：
- 同一 Issue Type + 同一项目，只能有一个初始状态
- 点击"设为初始状态"时，先清除同项目同类型下其他状态的 `is_initial=true`，再将当前状态的 `is_initial=true`
- 初始状态显示一个小星形/箭头标记

API 调用：
```
PATCH /api/v1/workflow/statuses/{statusId}/initial
Body: { projectId, issueType }
```

### 后端（WorkflowService + IssueService）

**Step 1：新增 API**

```java
@PatchMapping("/statuses/{statusId}/initial")
@PreAuthorize("@perm.check(#projectId, 'project:manage_workflow')")
public R<Void> setInitialStatus(
    @PathVariable Long statusId,
    @RequestParam Long projectId,
    @RequestParam(required = false) String issueType
) {
    workflowService.setInitialStatus(projectId, issueType, statusId);
    return R.ok();
}
```

**Step 2：WorkflowService.setInitialStatus()**

```java
@Transactional
public void setInitialStatus(Long projectId, String issueType, Long statusId) {
    // 清除同项目同类型的其他初始状态标记
    workflowTransitionMapper.clearInitialStatus(projectId, issueType);
    // 设置新的初始状态（通过某条转换或直接在 issue_status 扩展表中记录）
    workflowTransitionMapper.setInitialStatus(projectId, issueType, statusId);
}
```

> 注：`is_initial` 目前在 `workflow_transition` 表上，但初始状态是"状态本身"的属性而非"转换"的属性，建议改为在 `workflow_project_status`（项目-状态关联表）或专门的 `workflow_initial_status` 表中存储。评估现有数据模型决定方案。

**Step 3：IssueService.createIssue() 读取初始状态**

```java
// 现有逻辑（取第一个 Open 类状态）
IssueStatus defaultStatus = statusMapper.findFirstByCategory("in_progress");

// 改为：先查初始状态配置，找不到才 fallback
IssueStatus defaultStatus = workflowService.getInitialStatus(dto.getProjectId(), dto.getIssueType());
if (defaultStatus == null) {
    defaultStatus = statusMapper.findFirstByCategory("in_progress");
}
```

---

## 4. 验收标准

- [ ] 状态矩阵 UI 中每行状态旁有"设为初始状态"的操作入口
- [ ] 设置初始状态后，该状态显示特殊标记（如 ⭐）
- [ ] 同项目同 Issue Type 只能有一个初始状态（设置新的会清除旧的）
- [ ] 创建工单时，默认状态为配置的初始状态
- [ ] 未配置初始状态时，按原有逻辑 fallback（取第一个 Open 类状态）

## 5. 测试案例

### TC-WF-INITIAL-001：设置初始状态
**步骤**：以 lina 进入工作流编辑器，将"进行中"标记为初始状态
1. 验证"进行中"旁显示 ⭐
2. 以 wangqiang 创建新工单，不手动选择状态

**预期**：新工单默认状态为"进行中"

### TC-WF-INITIAL-002：切换初始状态
**步骤**：当前初始状态为"进行中"，改为将"待处理"设为初始状态
1. 点击"待处理"旁的"设为初始状态"

**预期**：
- "进行中"的 ⭐ 消失
- "待处理"出现 ⭐
- 后续新建工单默认状态为"待处理"

### TC-WF-INITIAL-003：未配置时的 fallback
**步骤**：清除所有初始状态配置后，创建工单

**预期**：使用系统 fallback 逻辑（取第一个 in_progress 类状态）


## 自动化状态

fix_status: DONE
fix_commit: 515b49a4
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 15:35

### 本次改动摘要
- 新建 `workflow_initial_status` 表（V261 迁移）— 存储每个(project_id, issue_type)组合的默认初始状态，替代原来语义不清的 `workflow_transition.is_initial` 字段
- 新建 `WorkflowInitialStatus` 实体 + Mapper + VO
- `WorkflowController` 增加 3 个 API：GET 列表、PUT 设置、DELETE 清除初始状态
- `WorkflowService` 增加 `setInitialStatus()`, `clearInitialStatus()`, `getInitialStatusId()`, `listInitialStatuses()` 方法
- `IssueService.create()` 改用 `workflowService.getInitialStatusId()` 优先查找初始状态，找不到再 fallback 到 `issue_status.is_default`
- `WorkflowRuleEngine.resolveInitialStatus()` 同步改为查 `workflow_initial_status` 表
- 前端 `workflow.ts` 新增 `listInitialStatuses`, `setInitialStatus`, `clearInitialStatus` API
- 前端 `types.ts` 新增 `WorkflowInitialStatusVO` 接口
- 前端 `WorkflowEditor.vue` 在矩阵行标题旁添加 ★ 星标图标，hover 显示，点击切换初始状态

### 本次变更文件清单
- `trackflow-server/src/main/resources/db/migration/V261__create_workflow_initial_status_table.sql`
- `trackflow-server/src/main/java/com/trackflow/workflow/entity/WorkflowInitialStatus.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/mapper/WorkflowInitialStatusMapper.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/vo/WorkflowInitialStatusVO.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/controller/WorkflowController.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/WorkflowService.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/WorkflowRuleEngine.java`
- `trackflow-server/src/main/java/com/trackflow/issue/service/IssueService.java`
- `trackflow-web/src/api/types.ts`
- `trackflow-web/src/api/workflow.ts`
- `trackflow-web/src/views/admin/WorkflowEditor.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 管理后台 → 工作流编辑器 → 选择项目（如 DE4）→ 选择角色 → 看到矩阵后，hover 行标题看到 ★ 星标 → 点击某个状态的 ★ → 星变为金色 → 刷新页面确认持久化
  2. 再点击另一个状态的 ★ → 原来的星消失，新的变金色（同一 project+issueType 只能一个初始状态）
  3. 点击已激活的星 → 星消失（清除初始状态配置）
  4. 以 wangqiang 登录 → 在已配置初始状态的项目中创建工单（不手动选状态）→ 验证新工单状态为配置的初始状态
- **边界场景**：
  - 全局工作流（project=0）也可以设置初始状态
  - 未配置时创建工单仍使用系统默认状态（is_default=true 的状态）
- **建议测试账号**：testuser（管理员，可操作工作流编辑器），wangqiang（开发人员，验证工单创建）
- **注意事项**：工作流编辑器页面路由为 `/workflow/editor`，需先选择角色才会显示矩阵

### 审核重点（给 code-review 会话）
- **重点关注文件**：WorkflowService.java（初始状态查找优先级逻辑），IssueService.java（create 方法状态解析变更）
- **潜在风险点**：WorkflowRuleEngine 注入了 WorkflowInitialStatusMapper（检查是否有循环依赖问题），IssueService 的状态解析优先级是否合理（workflow config > system default > user specified 是否正确——实际是 user specified 最高）
- **已知遗留项**：`workflow_transition.is_initial` 旧字段仍然存在但未使用（可在后续迁移中清理），前端星标在全局模式(project=0)下也可操作

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
`workflow_transition` 表有 `is_initial` 布尔字段但：
1. 前端工作流编辑器没有设置入口
2. 后端 `IssueService.create()` 不读取此字段
3. 语义不正确——初始状态是 (project, issueType) 维度的属性，不是某条转换规则的属性

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `V261__create_workflow_initial_status_table.sql` | 新建专用表，正确建模初始状态概念 |
| `WorkflowInitialStatus.java` | 实体类 |
| `WorkflowInitialStatusMapper.java` | MyBatis-Plus Mapper |
| `WorkflowInitialStatusVO.java` | 响应 VO |
| `WorkflowController.java` | 新增 3 个 REST API |
| `WorkflowService.java` | 新增 set/clear/get/list 方法 |
| `WorkflowRuleEngine.java` | resolveInitialStatus 改用新表 |
| `IssueService.java` | create() 优先使用工作流初始状态配置 |
| `types.ts` | 新增 WorkflowInitialStatusVO 类型 |
| `workflow.ts` | 新增 3 个 API 方法 |
| `WorkflowEditor.vue` | 添加 ★ 星标 UI 和交互逻辑 |

### 影响范围
- 工作流管理模块（新增初始状态配置能力）
- Issue 创建流程（状态解析优先级变更，向后兼容）
- WorkflowRuleEngine 自动创建工单时的状态解析
