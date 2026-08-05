# REQ-208：工作流规则引擎缺少 OR / NOT 条件逻辑组合

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-208 |
| 标题 | 工作流规则引擎缺少 OR / NOT 条件逻辑组合 |
| 类型 | 功能缺失 |
| 严重程度 | P0 |
| 发现方式 | YouTrack 功能对标 |
| 发现日期 | 2026-08-05 |
| 关联模块 | Workflow / WorkflowRuleEngine |
| 对标文档 | YouTrack: workflow-constructor-building-blocks.html |
| 状态 | 待修复 |

---

## 1. 问题描述

TrackFlow 工作流规则引擎（`WorkflowRuleEngine.java`）的 `evaluateConditions()` 方法对规则的条件数组采用**隐式 AND 逻辑**——即所有条件必须同时满足才触发规则。

YouTrack Workflow Constructor 支持完整的逻辑组合块：
- **AND**：所有子条件全部满足（TrackFlow 已有）
- **OR**：任意一个子条件满足即可
- **NOT**：取反（子条件不满足时才通过）
- **IF...ELSE**：在动作区域中条件分支执行

缺少 OR 和 NOT 后，很多实际业务场景无法表达。例如：
- "工单类型是 Bug **或者** Priority 是 Critical 时，自动分配给张三"（OR）
- "工单类型是 Bug，**且** Assignee **未被手动修改**时，自动分配"（NOT）

---

## 2. YouTrack 标准行为

YouTrack 文档：`workflow-constructor-building-blocks.html`

条件块可以用 Building Blocks 包裹：
```
AND 块 {
  条件A
  条件B
}            → 条件A AND 条件B

OR 块 {
  条件A
  条件B
}            → 条件A OR 条件B

NOT 块 {
  条件A
}            → NOT 条件A
```

支持嵌套：
```
AND {
  条件A
  NOT {
    条件B
  }
  OR {
    条件C
    条件D
  }
}
→ 条件A AND (NOT 条件B) AND (条件C OR 条件D)
```

---

## 3. 技术方案

### 后端（WorkflowRuleEngine.java）

条件 JSON 改为递归结构，支持嵌套逻辑节点：

```json
// 单个条件（叶子节点）
{"type": "condition", "field": "issue_type", "operator": "equals", "value": "Bug"}

// AND 节点（目前隐式，改为显式可选）
{"type": "and", "conditions": [...]}

// OR 节点（新增）
{"type": "or", "conditions": [...]}

// NOT 节点（新增）
{"type": "not", "condition": {...}}
```

`evalCondition()` 改为递归方法：

```java
private boolean evalCondition(JsonNode cond, Issue issue, String changedField, String oldValue) {
    String type = textOf(cond, "type");
    if ("and".equals(type)) {
        for (JsonNode child : cond.get("conditions")) {
            if (!evalCondition(child, issue, changedField, oldValue)) return false;
        }
        return true;
    }
    if ("or".equals(type)) {
        for (JsonNode child : cond.get("conditions")) {
            if (evalCondition(child, issue, changedField, oldValue)) return true;
        }
        return false;
    }
    if ("not".equals(type)) {
        return !evalCondition(cond.get("condition"), issue, changedField, oldValue);
    }
    // 原有的叶子节点逻辑
    // ...
}
```

`evaluateConditions()` 方法改为：如果 conditionJson 是数组（向后兼容），则隐式 AND；如果是对象（新格式），则递归求值。

### 前端（WorkflowRulePanel.vue）

条件配置区域支持：
- "添加条件"按钮旁增加"添加条件组"下拉（AND 组 / OR 组）
- 条件组支持嵌套，每个条件组可添加子条件
- NOT 通过条件旁边的"取反"切换按钮实现
- 现有条件列表（平铺 AND 模式）保持向后兼容

---

## 4. 验收标准

- [ ] 后端 evalCondition 支持 AND / OR / NOT 递归节点
- [ ] 旧格式（数组）向后兼容，不影响已有规则
- [ ] 前端条件配置区域支持 OR 组和 NOT 条件
- [ ] 测试：Bug OR Priority=Critical → 触发规则（OR）
- [ ] 测试：Bug AND NOT(Assignee changes) → 仅在未手动改分配人时触发（NOT）
- [ ] 测试：嵌套逻辑 (A AND (B OR C)) 正确求值

## 5. 测试案例

### TC-WF-OR-001：OR 条件触发
**步骤**：创建规则，条件 = `OR(Type=Bug, Priority=Critical)`，动作 = 打标签 urgent
1. 创建 Task 工单，Priority=Normal → 不触发
2. 创建 Task 工单，Priority=Critical → 触发打标签
3. 创建 Bug 工单，Priority=Normal → 触发打标签

### TC-WF-NOT-001：NOT 条件
**步骤**：创建规则，条件 = `NOT(Assignee is_not_empty)`，动作 = 设置 Assignee=张伟
1. 创建工单未指定负责人 → Assignee 为空，NOT 条件满足 → 自动分配
2. 创建工单并指定 wangqiang → Assignee 不为空，NOT 条件不满足 → 不触发

### TC-WF-NEST-001：嵌套逻辑
**步骤**：条件 = `AND(Type=Bug, NOT(Assignee changes))`
1. 新建 Bug，不设负责人 → 触发
2. 新建 Bug，手动设置负责人 → 不触发（NOT 不满足）
3. 新建 Task → 不触发（外层 AND 不满足）

## 自动化状态

fix_status: DONE
fix_commit: 44b9e20
fix_round: 2
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 12:50

### 本次改动摘要
- 改动1：`WorkflowRuleEngine.java` — 新增 `evalConditionNode()` 递归方法，支持 `{"type":"and"/"or"/"not", ...}` 格式的条件节点。`evaluateConditions()` 和 `evaluateConditionsWithComment()` 均升级为：如果是数组则隐式 AND（旧格式兼容），如果是对象则递归求值。
- 改动2：`TransitionGuardService.java` — 同样新增 `evalConditionNode()` 递归方法。`evaluate()` 方法现在支持三种格式：`{conditions:[...]}` 旧格式、直接数组旧格式、以及 `{type:"and"/"or"/"not",...}` 新格式。
- 改动3：`ScheduledRuleService.java` — 新增 `evalConditionNode()` 递归方法。`findMatchingIssues()` 检测新格式时跳过 SQL 下推，全部在 Java 层递归评估。
- 改动4：`WorkflowRulePanel.vue` — 条件区域增加 AND/OR 顶层逻辑切换（radio group）、每个条件行增加 NOT 切换按钮（取反）。提交时根据是否使用 OR/NOT 决定序列化为旧格式（纯数组）或新格式（递归节点）。编辑回显时 `parseConditionJson()` 支持两种格式反序列化。`conditionSummary()` 更新为递归显示。
- 改动5（Round 2）：`WorkflowRuleService.java` — 修复 `validateJson()` 方法，从仅接受 JSON 数组改为同时接受数组（旧格式）和递归条件节点对象（`{type:"and"/"or"/"not"/"condition",...}`）。这是阻止新格式规则保存的根因。

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/workflow/service/WorkflowRuleEngine.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/TransitionGuardService.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/ScheduledRuleService.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/WorkflowRuleService.java` (Round 2: validateJson fix)
- `trackflow-web/src/views/admin/WorkflowRulePanel.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 打开任意项目 → 进入设置/工作流规则 → 创建一条使用 OR 逻辑的规则（如 Type=Bug OR Priority=Critical）→ 保存成功 → 列表中正确显示 OR 摘要
  2. 编辑刚创建的 OR 规则 → 确认回显正确（AND/OR radio 切换器选中 OR，条件列表正确）
  3. 创建一条含 NOT 的规则（如 NOT(Assignee 为空)）→ 保存成功 → 列表显示含 NOT 的摘要
  4. 验证已有规则（旧格式）仍正常显示和编辑（向后兼容）
- **边界场景**：
  - 无条件规则仍显示"无条件（始终触发）"
  - 只有一个条件时，AND/OR 选择不影响结果
- **建议测试账号**：testuser（超级管理员，有项目管理权限）
- **注意事项**：此功能仅影响工作流规则的条件配置 UI 和后端评估逻辑，不影响工单的创建/编辑等主流程

### 审核重点（给 code-review 会话）
- **重点关注文件**：WorkflowRuleEngine.java, TransitionGuardService.java, ScheduledRuleService.java（三处递归逻辑一致性）
- **潜在风险点**：递归深度无限制（理论上恶意用户可构造深度嵌套 JSON 导致栈溢出），生产环境可考虑加深度限制
- **已知遗留项**：ScheduledRuleService 对新格式条件跳过 SQL 下推，全部 Java 层评估，大数据量场景可能有性能影响（但条件复杂度通常很低）

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
工作流规则引擎 `evaluateConditions()` 方法对条件数组采用隐式 AND 逻辑，不支持 OR（任一满足）和 NOT（取反）组合。前端也只支持平铺条件列表。这导致无法表达"Bug 或 Critical 优先级"这样的分支逻辑。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `WorkflowRuleEngine.java` | 新增 `evalConditionNode()` + `evalConditionNodeWithComment()` 递归方法，`evaluateConditions` 和 `evaluateConditionsWithComment` 支持数组（旧）和对象（新）两种格式 |
| `TransitionGuardService.java` | 新增 `evalConditionNode()` 递归方法，`evaluate()` 支持新旧三种格式 |
| `ScheduledRuleService.java` | 新增 `evalConditionNode()`，`findMatchingIssues()` 检测新格式后在 Java 层递归评估 |
| `WorkflowRulePanel.vue` | 增加 AND/OR radio 切换、NOT 按钮、`buildConditionJson()` 序列化、`parseConditionJson()` 反序列化、`conditionSummary` 递归展示 |

### 影响范围
- 工作流规则引擎条件评估（on-change/on-create/on-comment/scheduled 四种触发类型）
- 工作流转换守卫条件评估
- 工作流规则管理前端页面（条件编辑区域）
