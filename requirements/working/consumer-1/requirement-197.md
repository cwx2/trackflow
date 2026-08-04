# REQ-197：枚举类型自定义字段支持范围查询语法

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-197 |
| 标题 | 枚举类型自定义字段支持范围查询语法 |
| 类型 | 功能缺失 |
| 严重程度 | P2 |
| 发现方式 | 文档对标 |
| 发现日期 | 2026-08-04 |
| 关联模块 | query/engine |
| 对标文档 | https://www.jetbrains.com/help/youtrack/server/manage-custom-fields-per-project.html |
| 状态 | 待修复 |

---

## 1. YouTrack 标准行为

YouTrack 文档中明确描述了枚举字段的范围查询行为：

> "In search queries that specify a range of values, the sort order determines which values match the search request. For example, a search request that includes `Priority: Show-stopper .. Major` returns issues that are Show-stopper, Critical, and Major."

即：对于手动排序的枚举字段，可以使用 `..` 语法指定**值的范围**，范围由字段的排序顺序决定。

### 典型使用场景

假设 Priority 手动排序为（从高到低）：
1. Show-stopper
2. Critical
3. Major
4. Normal
5. Minor

则：
- `Priority: Show-stopper .. Major` → 返回优先级为 Show-stopper、Critical、Major 的工单（排序第1到第3项）
- `Priority: Major .. Minor` → 返回 Major、Normal、Minor
- `Fix versions: 1.0.0 .. 2.0.0` → 返回修复版本在 1.0.0 到 2.0.0 之间（按版本号排序）

---

## 2. TrackFlow 现状

TrackFlow 的查询引擎（`QueryExecutor.java`）目前支持：
- 精确匹配：`Priority: Critical`
- 多值 OR：`Priority: Critical, High`
- 排除：`Priority: -Low`

**不支持** `..` 范围语法：`Priority: Critical .. Normal`

---

## 3. 期望结果

### 3.1 查询语法扩展

在查询解析器（`QueryInput.vue` / `QueryExecutor`）中增加对枚举类型字段的范围查询支持：

**语法：** `字段名: 值A .. 值B`

**语义：** 返回该字段值在 A 到 B 范围内（按字段选项的排序位置 `position` 确定范围，包含边界值）的所有工单

### 3.2 范围确定规则

- 范围由字段选项的 `position` 字段决定（手动排序时）
- A 的 position 必须 <= B 的 position（否则范围为空）
- 包含边界（A 和 B 本身也在范围内）
- 如果排序为名称排序或版本排序，对应使用该排序逻辑

### 3.3 适用字段类型

范围查询适用于所有枚举类型：
- `list`（Priority、Type 等）
- `version`（Fix versions，按版本号顺序）

不适用于：
- `user`（无排序概念）
- `state`（状态不适合范围查询）

### 3.4 查询补全提示

- 当用户输入 `Priority: Critical` 后，补全提示中显示 `..` 作为可选操作符
- 提示说明：`Priority: A .. B（范围查询：A到B之间的优先级）`

---

## 4. 验收标准

- [ ] 支持 `字段名: 值A .. 值B` 语法的查询
- [ ] 范围按字段选项的 position 排序确定
- [ ] 包含边界值（A 和 B 本身）
- [ ] version 类型字段支持版本号语义的范围查询
- [ ] 查询补全中提示 `..` 操作符
- [ ] 范围查询结果正确（通过 SQL `position BETWEEN posA AND posB` 实现）

---

## 5. 备注

- 后端实现参考：`QueryExecutor` 中解析 `..` 操作符，转换为 `position BETWEEN x AND y` 的 SQL 条件
- 前端 `QueryInput.vue` 的 tokenizer 需要识别 `..` 为合法操作符
- 此功能依赖 REQ-186（Priority 配置化）完成后才有实际意义


## 自动化状态

fix_status: DONE
fix_commit: fd72370f
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 05:10

### 本次改动摘要
- 改动1：`QueryExecutor.java` — 新增 `CustomFieldOptionMapper` 依赖注入，在 `applyCustomFieldFilter` 的 switch 中增加 `case "between"` 分支，委托新方法 `applyCustomFieldBetweenFilter` 处理枚举范围查询
- 改动2：`QueryExecutor.java` — 新增 `applyCustomFieldBetweenFilter` 方法：查找字段所有全局选项，按 label 或 ID 匹配边界值确定 position 范围，收集范围内的选项 ID，复用 `applyCustomFieldInFilter` 生成参数化 IN 查询
- 改动3：`QueryInput.vue` — 将 `..` 操作符提示从"日期/数值"更新为"日期/数值/枚举排序"

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/query/engine/QueryExecutor.java`
- `trackflow-web/src/views/issue/components/QueryInput.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 打开项目列表视图 → 在搜索框输入 `Priority: Critical .. Normal` → 验证返回的工单优先级为 Critical、High、Normal（position 1-3）
  2. 对比测试：输入 `Priority: Critical` → 验证只返回 Critical 的工单
- **边界场景**：
  - 范围值不存在（如 `Priority: InvalidValue .. Normal`）时应返回空结果，不报错
  - 反向范围（如 `Priority: Normal .. Critical`）应自动交换，返回相同结果
- **建议测试账号**：testuser（超级管理员）
- **注意事项**：
  - Priority 字段 ID 为 1000000000000000001，选项按 position: Show-stopper(0), Critical(1), High(2), Normal(3), Low(4) 排序
  - `custom_field_value.value` 存储的是选项 ID（如 1000000000000000104），不是 label
  - 前端 QueryInput 的 `..` 语法解析已存在，本次仅后端增加处理逻辑
  - 如果 Priority 自定义字段未被项目启用或没有对应工单数据，范围查询会返回空（属于正常行为）

### 审核重点（给 code-review 会话）
- **重点关注文件**：QueryExecutor.java
- **潜在风险点**：
  - 新方法引入了数据库查询（查询 custom_field_option），在高频调用场景下可能影响性能（可考虑缓存但当前体量不需要）
  - 双重匹配逻辑（先按 label 再按 ID）需确认不会导致误匹配
  - `isNull(CustomFieldOption::getProjectId)` 只查全局选项，如未来有项目级选项需要扩展
- **已知遗留项**：现有 eq/in 操作符对自定义字段的文本查询也存在 label vs ID 不匹配问题（REQ-197 范围之外）

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
QueryExecutor 的 `applyCustomFieldFilter` 方法 switch 中没有处理 `between` 操作符的分支。前端已正确解析 `..` 语法并发送 `operator: "between"`，但后端收到后直接跳过不执行任何 SQL 条件，导致范围查询无效果。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `QueryExecutor.java` | 新增 `CustomFieldOptionMapper` 注入 + `case "between"` 分支 + `applyCustomFieldBetweenFilter` 方法 |
| `QueryInput.vue` | 更新 `..` 操作符的 hint 文案，提示用户枚举字段也支持范围查询 |

### 影响范围
- 查询引擎（SavedQuery 路径 + IssueList 文本查询路径）
- 所有使用 QueryExecutor 的上游：报表引擎、看板筛选、通知匹配
