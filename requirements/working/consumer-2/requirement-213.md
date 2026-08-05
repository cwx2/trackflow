# REQ-213：状态转换卫士条件（Guard）后端执行逻辑未实现

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-213 |
| 标题 | 状态转换卫士条件（Guard）后端执行逻辑未实现 |
| 类型 | 功能缺失 |
| 严重程度 | P0 |
| 发现方式 | YouTrack 功能对标 |
| 发现日期 | 2026-08-05 |
| 关联模块 | Workflow / WorkflowService / TransitionGuardPanel |
| 对标文档 | YouTrack: workflow-constructor-state-machines.html |
| 状态 | 已修复 |

---

## 1. 问题描述

TrackFlow 状态转换矩阵页面（`/workflow`）已支持在格子上配置卫士条件（橙点），前端 UI `TransitionGuardPanel.vue` 允许管理员配置以下卫士条件：
- 字段满足某条件（如 Priority = Critical）
- 字段不为空（如 Resolution 不为空）

但是，**后端 `WorkflowService.getAvailableTransitions()` 并未读取和执行这些卫士条件**。

导致的问题：
1. 管理员配置了卫士条件，保存进数据库，但对实际状态流转没有任何限制效果
2. `workflow_transition_guard` 表中的数据从未被使用
3. 用户即使不满足卫士条件，依然可以进行状态变更

---

## 2. YouTrack 标准行为

YouTrack 的卫士条件会影响 `getAvailableTransitions()` 的返回结果：
- 满足卫士条件的转换 → 出现在下拉列表中
- 不满足卫士条件的转换 → 不出现（或显示为灰色+hover 提示）

此外 `applyTransition()` 也会再次校验，防止绕过前端直接调用 API。

---

## 3. 技术方案

### 后端（WorkflowService.java）

**Step 1：在 `getAvailableTransitions()` 中执行卫士条件过滤**

```java
public List<WorkflowTransitionVO> getAvailableTransitions(Long issueId, Long roleId) {
    // 1. 获取所有允许的转换（现有逻辑）
    List<WorkflowTransition> transitions = transitionMapper.findByIssueAndRole(...);
    
    // 2. 加载工单当前状态用于卫士条件评估
    Issue issue = issueMapper.selectById(issueId);
    
    // 3. 对每个转换，评估其卫士条件
    return transitions.stream()
        .filter(t -> evaluateGuards(t.getId(), issue))
        .map(converter::toVO)
        .collect(toList());
}

private boolean evaluateGuards(Long transitionId, Issue issue) {
    List<WorkflowTransitionGuard> guards = guardMapper.selectByTransitionId(transitionId);
    if (guards.isEmpty()) return true; // 无卫士条件，默认允许
    
    for (WorkflowTransitionGuard guard : guards) {
        if (!evalGuard(guard, issue)) return false;
    }
    return true;
}
```

**Step 2：在 `applyTransition()` 中二次校验（防绕过）**

```java
public void applyTransition(Long issueId, Long targetStatusId, Long operatorId) {
    // 现有逻辑：检查转换是否存在
    // ...
    
    // 新增：执行卫士条件校验
    Issue issue = issueMapper.selectById(issueId);
    WorkflowTransition transition = findTransition(issue, targetStatusId, operatorId);
    if (!evaluateGuards(transition.getId(), issue)) {
        String errorMsg = getGuardErrorMessage(transition.getId(), issue);
        throw new BusinessException(ErrorCode.WORKFLOW_GUARD_FAILED, errorMsg);
    }
    
    // 原有执行逻辑继续...
}
```

**Step 3：evalGuard 实现各种卫士条件类型**

```java
private boolean evalGuard(WorkflowTransitionGuard guard, Issue issue) {
    JsonNode config = parseJson(guard.getConfig());
    return switch (guard.getConditionType()) {
        case "field_check" -> evalFieldCheck(config, issue);
        case "field_not_empty" -> evalFieldNotEmpty(config, issue);
        case "links_resolved" -> evalLinksResolved(issue.getId(), config);
        default -> true;
    };
}
```

其中：
- `field_check`：字段值满足指定条件（equals/not_equals/is_set/not_set）
- `field_not_empty`：指定字段不为空
- `links_resolved`：所有子任务（subtask of 关联）处于已解决状态

**Step 4：在 `getAvailableTransitions()` API 返回中附带 guard 错误提示**

```json
// 当卫士不满足时，在 VO 中包含错误信息
{
  "statusId": "123",
  "statusName": "已完成",
  "available": false,
  "guardErrorMessage": "请先完成所有子任务"
}
```

前端根据 `available` 字段决定是否将该选项置灰并显示 tooltip。

### 数据库

`workflow_transition_guard` 表已存在（REQ-203-1 阶段已建），无需新建。

确认 `WorkflowTransitionGuardMapper` 中有 `selectByTransitionId(Long transitionId)` 方法。

---

## 4. 验收标准

- [ ] `getAvailableTransitions()` 会根据卫士条件过滤不可用的转换
- [ ] 不满足卫士条件的转换不出现在状态下拉选项中，或显示为灰色+提示
- [ ] `applyTransition()` API 在卫士条件不满足时返回业务错误
- [ ] 直接调用 API 绕过前端也会被卫士条件拦截（防绕过）
- [ ] `field_not_empty` 卫士：Resolution 为空时无法关闭工单
- [ ] `links_resolved` 卫士：子任务未完成时无法关闭父工单

## 5. 测试案例

### TC-WF-GUARD-001：字段必填卫士
**步骤**：在"进行中 → 已完成"转换上配置卫士 field_not_empty(resolution)
1. Resolution 为空时，工单详情页状态下拉中"已完成"不可点（或不显示）
2. 填写 Resolution 后，"已完成"可以选择
3. 直接 POST `/api/v1/issues/{id}/transitions`，body={targetStatusId:已完成}，不填 Resolution → 返回业务错误

**预期**：卫士条件在前端过滤 + 后端拦截两层都生效

### TC-WF-GUARD-002：子任务全完成卫士
**步骤**：在"进行中 → 已关闭"转换上配置卫士 links_resolved(subtask of)
1. 父工单 A 有两个未完成子任务 → "已关闭"不可选
2. 子任务全部改为"已完成" → "已关闭"可选
3. 成功关闭父工单 A

**预期**：子任务未全部完成时无法关闭父工单

## 自动化状态

fix_status: DONE
fix_commit: 75f6a54e
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 14:56

### 本次改动摘要
- 改动1：`TransitionGuardService.java` — 实现 `links_resolved` 和 `children_resolved` 高级守卫条件类型。原来 `conditionType` 字段的条件一律降级为"允许"，现在正确评估：`links_resolved` 查询 issue_link 表检查关联工单是否都已关闭，`children_resolved` 利用 Issue 的 childCount/childClosedCount 派生字段快速判断子工单是否全部完成。同时添加了 `getFailureReason()` 方法提供人类可读的守卫失败消息。
- 改动2：`UpdateTransitionConditionsDTO.java` — 移除字段的 `@NotNull` 校验（高级条件不需要 field/operator），新增 `conditionType` 和 `linkType` 字段。
- 改动3：`ErrorCode.java` — 新增 `WORKFLOW_GUARD_FAILED(40306, 403)` 错误码。
- 改动4：`TransitionGuardPanel.vue` — 添加条件模式选择器（字段条件 / 关联工单已关闭 / 子工单已关闭），支持配置 `links_resolved` 和 `children_resolved` 条件类型。
- 改动5：`workflow.ts` — 扩展 `TransitionConditionItem` 类型支持 `conditionType` 和 `linkType` 可选字段。

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/common/exception/ErrorCode.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/dto/UpdateTransitionConditionsDTO.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/TransitionGuardService.java`
- `trackflow-web/src/api/workflow.ts`
- `trackflow-web/src/views/admin/TransitionGuardPanel.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 打开工作流管理页面 → 右键某个转换格子 → 打开守卫条件面板 → 能看到条件模式选择器（字段条件/关联工单已关闭/子工单已关闭）
  2. 配置一个"字段条件"类型守卫（如 assignee_id is_not_empty）→ 保存 → 重新打开面板确认条件持久化
  3. 配置一个"子工单已关闭"类型守卫 → 保存 → 验证带有未完成子工单的父工单在状态下拉中看不到该目标状态
  4. 直接调用 API `POST /api/v1/issues/{id}/transitions` 尝试绕过守卫 → 应返回 403 拒绝
- **边界场景**：
  - 清除所有守卫条件后保存，转换应恢复为无限制
  - 无子工单的工单应不受 children_resolved 守卫影响
- **建议测试账号**：系统管理员 testuser（有工作流编辑权限）
- **注意事项**：本次改动不影响已有无条件的工作流转换（空条件 `{}` 等同于无守卫）

### 审核重点（给 code-review 会话）
- **重点关注文件**：TransitionGuardService.java（核心逻辑）、UpdateTransitionConditionsDTO.java（@NotNull 移除）
- **潜在风险点**：
  - `evalLinksResolved` 做了 DB 查询（selectBatchIds），在高频调用场景下可能有性能影响。但由于 getAvailableTransitions 已在最高优先级层过滤（通常只有 2-5 个候选目标），查询量可控。
  - `getClosedStatusIds()` 每次调用都查 DB，未做缓存（IssueStatus 表很小，通常 < 20 行）。如果性能敏感可后续加 Caffeine 本地缓存。
- **已知遗留项**：`WORKFLOW_GUARD_FAILED` 错误码已定义但尚未在 Controller 中使用（当前 isTransitionAllowed 返回 false 时统一抛 WORKFLOW_TRANSITION_DENIED）。后续可在需要更精确错误消息时引入。

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
需求描述的"守卫条件后端执行逻辑未实现"不完全准确。实际情况是：
1. 基础守卫框架（字段条件评估 + getAvailableTransitions 过滤 + isTransitionAllowed 防绕过）**已完整实现**
2. 真正缺失的是 `links_resolved` 高级条件类型——`conditionType` 字段的条件被降级为始终允许（`return true`）
3. 前端 TransitionGuardPanel 未暴露高级条件类型的配置入口

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `TransitionGuardService.java` | 实现 `evalAdvancedCondition()` 分发逻辑，新增 `evalLinksResolved()` 和 `evalChildrenResolved()` 方法，替换原来的降级逻辑。添加 `getFailureReason()` 方法。 |
| `UpdateTransitionConditionsDTO.java` | 移除 field/operator 的 @NotNull 校验，新增 conditionType/linkType 字段支持高级条件 |
| `ErrorCode.java` | 新增 WORKFLOW_GUARD_FAILED 错误码 |
| `TransitionGuardPanel.vue` | 添加条件模式选择器（field/links_resolved/children_resolved），支持配置和解析高级条件 |
| `workflow.ts` | 扩展 TransitionConditionItem 类型 |

### 影响范围
- 工作流模块：TransitionGuardService 的守卫评估逻辑
- 前端工作流编辑器：守卫条件配置面板
