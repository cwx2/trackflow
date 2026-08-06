# REQ-253：workflow/service/action 目录下 14 个类违规使用 @Autowired 字段注入

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-253 |
| 标题 | workflow/service/action 目录下 14 个类违规使用 @Autowired 字段注入 |
| 类型 | 编码规范违规 |
| 严重程度 | P2 |
| 发现方式 | 技术审计（代码规范审核） |
| 发现日期 | 2026-08-05 |
| 关联模块 | workflow |
| 影响层级 | Service |
| 状态 | 待修复 |

## 1. 问题概述

**一句话**：workflow 模块的动作执行器类（XxxActionExecutor）普遍使用 `@Autowired` 字段注入，违反项目规范要求的构造器注入原则，导致类的依赖关系不透明，难以测试。

**技术根因**：开发者习惯性使用了 `@Autowired` 字段注入，未遵循项目规范中"Bean 注入必须使用构造器注入（推荐 Lombok @RequiredArgsConstructor）"的要求。

## 2. 问题证据

通过 grep 搜索 `@Autowired` 关键字，发现以下文件违规：

```
// workflow/service/action/CopyIssueActionExecutor.java（5处）
@Autowired
private IssueService issueService;
@Autowired
private IssueMapper issueMapper;
// ...

// workflow/service/action/CreateIssueActionExecutor.java（4处）
@Autowired
private IssueService issueService;
// ...

// workflow/service/action/SetFieldActionExecutor.java（4处）
@Autowired
private CustomFieldService customFieldService;
// ...

// workflow/service/action/WorkflowActionSupport.java（4处）
@Autowired
private IssueService issueService;
// ...

// 以及以下文件各 1 处：
// workflow/service/action/AddCommentActionExecutor.java
// workflow/service/action/AddTagActionExecutor.java
// workflow/service/action/AddVoteActionExecutor.java
// workflow/service/action/AddWorkItemActionExecutor.java
// workflow/service/action/LinkIssueActionExecutor.java
// workflow/service/action/MoveToProjectActionExecutor.java
// workflow/service/action/RemoveTagActionExecutor.java
// workflow/service/action/RemoveVoteActionExecutor.java
// workflow/service/action/SendEmailActionExecutor.java
// workflow/converter/TransitionActionConverter.java
```

共涉及 **14 个文件，27 处 @Autowired 字段注入**。

## 3. 违规对比

```java
// ❌ 当前违规写法
@Component
public class AddTagActionExecutor {
    @Autowired
    private IssueTagService issueTagService;  // 字段注入
    
    public void execute(TransitionActionConfig config, Issue issue) {
        issueTagService.addTagToIssue(issue.getId(), ...);
    }
}

// ✅ 规范写法（构造器注入 + Lombok）
@Component
@RequiredArgsConstructor
public class AddTagActionExecutor {
    private final IssueTagService issueTagService;  // final 字段，构造器注入
    
    public void execute(TransitionActionConfig config, Issue issue) {
        issueTagService.addTagToIssue(issue.getId(), ...);
    }
}
```

## 4. 内部规范参考

根据 `.kiro/steering/java-coding-standards.md` 第十八节：

> ```java
> // ✅ 正确：构造器注入（推荐，Lombok @RequiredArgsConstructor）
> @RequiredArgsConstructor
> @Service
> public class IssueService {
>     private final IssueMapper issueMapper;
> }
> 
> // ❌ 错误：字段注入
> @Autowired
> private IssueMapper issueMapper;
> ```

## 5. 影响分析

### 业务影响

- **可测试性**：字段注入的类无法通过构造器传入 Mock 对象，单元测试必须使用 Spring 容器或反射
- **依赖透明性**：字段注入不在构造器中声明依赖，依赖关系对调用者不透明
- **NPE 风险**：字段注入在非 Spring 环境中会产生 NullPointerException（如测试中手动 new 对象）

### 风险评估

| 风险维度 | 当前状态 | 说明 |
|----------|----------|------|
| 数据一致性 | ✅ | 功能运行正常 |
| 并发安全 | ✅ | 无并发风险 |
| 性能影响 | ✅ | 无性能影响 |
| 可测试性 | ⚠️ | 单元测试编写困难 |

## 6. 改进方案

### 批量改造步骤

1. 删除所有 `@Autowired` 注解
2. 将字段改为 `private final`
3. 添加 `@RequiredArgsConstructor` 到类注解
4. 如果类已有 `@Component`/`@Service` 则不需要额外操作

```java
// 改造示例：SetFieldActionExecutor.java
@Component
@RequiredArgsConstructor  // 新增
public class SetFieldActionExecutor implements TransitionActionExecutor {
    // 删除 @Autowired，改为 final
    private final IssueService issueService;
    private final CustomFieldService customFieldService;
    private final IssueMapper issueMapper;
    private final WorkflowRuleConditionMatcher ruleMatcher;
    
    // 方法保持不变
}
```

### 实现复杂度评估

| 维度 | 评估 |
|------|------|
| 改动文件数 | 14 个文件 |
| 是否需要数据迁移 | 否 |
| 是否需要前后端联动 | 否 |
| 预计工作量 | 小（<2h，纯机械性修改） |

## 7. 验收标准

- [ ] `workflow/service/action/` 目录下所有类不包含 `@Autowired` 注解
- [ ] 所有注入字段改为 `private final`
- [ ] 所有类添加 `@RequiredArgsConstructor` 注解
- [ ] 项目编译无错误
- [ ] 运行时功能正常（现有集成测试通过）

## 8. 关联问题

- REQ-239：IssueController.transitStatus 含业务逻辑（workflow 模块相关）

## 自动化状态

fix_status: DONE
fix_commit: 43d7106b
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-06 13:40

### 本次改动摘要
将 workflow 模块 17 个文件中的 `@Autowired` 字段注入改为构造器注入，符合项目编码规范。

- WorkflowActionSupport.java — 基类：4 个 `@Autowired` 字段改为 `protected final` + 显式构造器
- 13 个子类 ActionExecutor — 各自声明显式构造器，将基类依赖通过 `super(...)` 传递，自身依赖声明为 `private final`
- TransitionActionConverter.java — 保留 `@Autowired`（MapStruct 抽象类约束，已添加注释说明）
- ShowAlertActionExecutor/RequireFieldActionExecutor/UpdateDescriptionActionExecutor/UpdateSummaryActionExecutor — 无自身依赖，仅添加转发构造器

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/WorkflowActionSupport.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/AddCommentActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/AddTagActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/AddVoteActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/AddWorkItemActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/CopyIssueActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/CreateIssueActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/LinkIssueActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/MoveToProjectActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/RemoveTagActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/RemoveVoteActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/RequireFieldActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/SendEmailActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/SetFieldActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/ShowAlertActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/UpdateDescriptionActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/service/action/UpdateSummaryActionExecutor.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/converter/TransitionActionConverter.java`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 后端能正常启动（Spring 容器成功注入所有 ActionExecutor bean）
  2. 工作流状态转换正常工作（触发任一 transition action 不报错）
  3. 以 testuser 登录 → 打开项目 DE4 → 对一个 Issue 执行状态转换 → 确认转换成功
- **边界场景**：
  - 所有 ActionExecutor 类型均在 WorkflowActionRegistry 注册成功（启动日志可见）
- **建议测试账号**：testuser（超级管理员）
- **注意事项**：本次为纯重构，不改变任何业务逻辑，只是 DI 方式变化。如果后端启动成功并能执行状态转换，即可确认修复正确。

### 审核重点（给 code-review 会话）
- **重点关注文件**：WorkflowActionSupport.java（基类构造器设计）、CopyIssueActionExecutor.java（最多依赖的子类）
- **潜在风险点**：TransitionActionConverter 保留了 @Autowired，需确认注释说明充分
- **已知遗留项**：TransitionActionConverter 因 MapStruct 约束无法改为构造器注入，已在代码中添加注释说明原因

======================

## 修复记录

**修复日期**：2026-08-06
**修复人**：AI Agent（auto 模式）

### 根因分析
开发者习惯性使用 `@Autowired` 字段注入，未遵循项目规范中"Bean 注入必须使用构造器注入（推荐 Lombok @RequiredArgsConstructor）"的要求。由于存在继承关系（WorkflowActionSupport 基类），不能直接使用 `@RequiredArgsConstructor`，需要显式编写构造器。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `WorkflowActionSupport.java` | 4 个 `@Autowired` 字段 → `protected final` + 4 参数构造器 |
| 13 个 ActionExecutor 子类 | 删除 `@Autowired`，字段改为 `private final`，添加显式构造器调用 `super(...)` |
| `TransitionActionConverter.java` | 保留 `@Autowired`（MapStruct 限制），添加注释说明 |

### 影响范围
- workflow 模块的所有动作执行器类
- 不影响任何业务逻辑、API 接口、数据库操作
