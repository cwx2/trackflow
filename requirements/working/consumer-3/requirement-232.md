# REQ-232：IssueService.listByQuery 和 QueryExecutor 存在两套重复的 SQL 查询构建逻辑

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-232 |
| 标题 | IssueService.listByQuery 和 QueryExecutor 存在两套重复的 Issue 查询构建逻辑 |
| 类型 | 架构缺陷 |
| 严重程度 | P1 |
| 发现方式 | 代码审核后续技术审计 |
| 发现日期 | 2026-08-05 |
| 关联模块 | issue/service / query/engine |
| 影响层级 | Service |
| 状态 | 已修复 |

## 1. 问题概述

**一句话**：系统中有两套完全独立的 Issue 查询 SQL 构建逻辑——`IssueService.listByQuery()`（约 300 行）和 `QueryExecutor.buildWrapper()`（846 行），二者都在构建针对 `issue` 表的动态查询，但互相不感知，导致相同的筛选条件在两处分别维护，行为容易不一致。

**技术根因**：`IssueService` 面向内部 API（前端直接请求），`QueryExecutor` 面向保存的查询面板（Saved Query）。二者都需要筛选工单，但各自实现，没有共用基础。

## 2. 重叠逻辑对比

| 筛选能力 | IssueService.listByQuery | QueryExecutor |
|---------|--------------------------|---------------|
| 按状态筛选 | ✅ `applyFilter("status_id")` | ✅ `case "status"` |
| 按优先级筛选 | ✅ `applyFilter("priority")` | ✅ `case "priority"` |
| 按负责人筛选 | ✅ `applyFilter("assignee_id")` | ✅ `case "assignee"` |
| 按 Sprint 筛选 | ✅ `applyFilter("sprint_id")` | ✅ `case "sprint"` |
| 按类型筛选 | ✅ `applyFilter("issue_type")` | ✅ `case "type"` |
| 关键词搜索 | ✅ `applyKeywordFilter()` | ✅ `case "keyword"` |
| 标签筛选 | ✅ EXISTS 子查询 | ✅ EXISTS 子查询 |
| 逾期筛选 | ✅ `due_date < today` | ✅ `case "overdue"` |
| 优先级语义排序 | ✅ CASE WHEN CRITICAL=1 | ✅ CASE WHEN CRITICAL=1（重复！） |
| 已解决/未解决 | ✅ hideResolved/onlyResolved | ✅ open/closed operator |
| 项目权限过滤 | ✅ `getAccessibleProjectIds()` | ✅ `case "project"` |

**问题**：优先级排序的 CASE 表达式在两处各自硬编码，如果将来新增优先级值（如 "Blocker"），需要在两处同步修改。

## 3. 改进方案

### 设计模式：Query Object + Builder 抽取

**方案 A（推荐）：IssueService 委托给 QueryExecutor**

将 `IssueService.listByQuery()` 的逻辑逐步迁移，使之能够将 `IssueQuery` 参数转换为 `QueryExecutor` 的 filters 格式，统一走同一套构建逻辑：

```java
// IssueService.listByQuery() 简化后
public Page<Issue> listByQuery(IssueQuery query) {
    List<Map<String, Object>> filters = issueQueryAdapter.toFilters(query);
    List<Map<String, String>> sorts = issueQueryAdapter.toSorts(query);
    return queryExecutor.execute(filters, (int)query.getPage(), (int)query.getPageSize(), sorts);
}
```

新建 `IssueQueryAdapter.java`，负责 `IssueQuery` → `QueryExecutor` filters 格式的转换。

**方案 B（轻量）：抽取公共 IssuePriorityOrderHelper**

至少把重复的优先级排序 CASE 表达式提取为公共方法：

```java
@Component
public class IssuePriorityOrderHelper {
    public static final String PRIORITY_ORDER_EXPR =
        "CASE LOWER(priority) WHEN 'critical' THEN 1 WHEN 'high' THEN 2 WHEN 'normal' THEN 3 WHEN 'low' THEN 4 ELSE 5 END";

    public void applyPrioritySort(QueryWrapper<Issue> wrapper, boolean desc) {
        if (desc) wrapper.last("ORDER BY " + PRIORITY_ORDER_EXPR + " DESC, updated_at DESC");
        else wrapper.last("ORDER BY " + PRIORITY_ORDER_EXPR + " ASC, updated_at DESC");
    }
}
```

### 实现建议

短期先做方案 B（1~2h），消除硬编码重复；
长期规划方案 A（1~2天），统一查询构建路径。

### 实现复杂度评估

| 方案 | 改动文件数 | 工作量 |
|------|----------|--------|
| 方案 B（优先级排序提取） | 3 个 | 小（1~2h） |
| 方案 A（完整合并） | 8~12 个 | 大（1~2天） |

## 7. 验收标准

- [ ] 优先级排序 CASE 表达式只在一处定义
- [ ] 修改优先级语义排序只需改一个地方
- [ ] 两套查询路径的排序结果一致

## 8. 关联问题

- REQ-226（IssueService 拆分）相关
- REQ-231（IssueVOAssembler）配套


## 自动化状态

fix_status: DONE
fix_commit: 985afb1
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 20:00

### 本次改动摘要
- 改动1：新建 `IssuePriorityHelper.java` — 全局唯一的优先级排序 CASE 表达式常量 + 便捷排序方法。使用 `LOWER()` 确保大小写不敏感。
- 改动2：`IssueService.java` — 将 `listByQuery()` 中硬编码的 CASE 表达式替换为 `IssuePriorityHelper.PRIORITY_ORDER_EXPR`。
- 改动3：`QueryExecutor.java` — 将 `buildWrapper()` 排序处理中硬编码的 CASE 表达式替换为 `IssuePriorityHelper.PRIORITY_ORDER_EXPR`。
- 改动4：`DashboardService.java` — 将 `getAssignedToMe()` 中硬编码的 CASE 表达式替换为 `IssuePriorityHelper.applyPrioritySortWithLimit()` 调用。同时修复了原代码没有 LOWER() 导致大小写不一致的潜在 bug。

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/issue/util/IssuePriorityHelper.java`
- `trackflow-server/src/main/java/com/trackflow/issue/service/IssueService.java`
- `trackflow-server/src/main/java/com/trackflow/query/engine/QueryExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/dashboard/service/DashboardService.java`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 打开任意项目的 Issue 列表 → 按优先级排序（点击 Priority 列头）→ 验证 Critical > High > Normal > Low 顺序正确
  2. 以 testuser 登录 → 打开 Dashboard → 检查"分配给我的工单"卡片 → 验证按优先级排序展示
  3. 以 testuser 登录 → 打开 Saved Query → 使用包含 priority 排序的保存查询 → 验证排序结果正确
- **边界场景**：
  - 数据库中存在混合大小写的 priority 值（如 `normal` 和 `Normal`），排序应将它们视为相同优先级
- **建议测试账号**：testuser（有工单分配）
- **注意事项**：本次改动纯后端重构，前端无改动。排序行为应与改动前一致（IssueService 和 QueryExecutor 之前已用 LOWER()），DashboardService 现在修复了大小写问题可能导致排序微调。

### 审核重点（给 code-review 会话）
- **重点关注文件**：IssuePriorityHelper.java（新文件，核心常量定义）
- **潜在风险点**：DashboardService 中 `.last()` 调用时机——确保 `wrapper.last()` 不会被后续操作覆盖
- **已知遗留项**：需求方案 A（将 IssueService 的查询委托给 QueryExecutor）未在本轮实施，属于更大范围的架构统一，留待后续

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
系统中有 3 处独立硬编码了优先级排序的 CASE WHEN 表达式，且 DashboardService 中的版本缺少 LOWER() 函数调用，当数据库中存在混合大小写的 priority 值时会排序错误。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `issue/util/IssuePriorityHelper.java` | 新建工具类，定义 PRIORITY_ORDER_EXPR 常量和 applyPrioritySort/applyPrioritySortWithLimit/applyPriorityOrderBy 三个便捷方法 |
| `issue/service/IssueService.java` | 删除本地 caseExpr 变量，改用 IssuePriorityHelper.PRIORITY_ORDER_EXPR |
| `query/engine/QueryExecutor.java` | 删除本地 caseExpr 变量，改用 IssuePriorityHelper.PRIORITY_ORDER_EXPR |
| `dashboard/service/DashboardService.java` | 删除硬编码 .last() 排序，改用 IssuePriorityHelper.applyPrioritySortWithLimit()，同时修复了缺少 LOWER() 的 bug |

### 影响范围
- Issue 列表页排序（IssueService）
- Saved Query 查询排序（QueryExecutor）
- Dashboard "分配给我" 卡片排序（DashboardService）
