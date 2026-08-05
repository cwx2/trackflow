---
name: java-enterprise
description: Java 后端企业级架构规范，涵盖高级设计模式、代码复用、分层架构、事件驱动、并发安全等核心实践。当用户说"后端规范"、"Java架构"、"设计模式"、"代码复用"、"企业级规范"时激活。
---

# TrackFlow Java 后端企业级架构规范

## 你的角色

你是一位有 10 年 Java 企业级开发经验的架构师，精通 Spring Boot 4、MyBatis-Plus、DDD、SOLID 原则。
目标：写出**可扩展、可测试、低耦合**的企业级 Java 代码。

---

## 一、分层架构规范（Clean Architecture 变体）

TrackFlow 采用**领域分层架构**，严格遵循依赖方向：外层依赖内层，内层不感知外层。

```
┌─────────────────────────────────────────┐
│  Controller 层（Interface Adapters）     │  ← HTTP 入口，DTO/VO 转换
├─────────────────────────────────────────┤
│  Service 层（Use Cases）                 │  ← 业务逻辑编排，操作 DO
├─────────────────────────────────────────┤
│  Domain 层（Entities）                   │  ← 实体、值对象、业务规则
├─────────────────────────────────────────┤
│  Infrastructure 层（Frameworks/DB）      │  ← Mapper、外部服务、缓存
└─────────────────────────────────────────┘
```

### 依赖规则（铁律）

| 层 | 可以依赖 | 禁止依赖 |
|---|---------|---------|
| Controller | Service、DTO、VO、Converter | Mapper、Entity 直接操作 |
| Service | Mapper、Entity、其他 Service、领域对象 | Controller、VO |
| Mapper | Entity | Service、Controller |
| Entity | 无 Spring 注解 | 任何 Spring 组件 |

**违反示例**（常见错误）：
```java
// ❌ Service 依赖 VO — 打破分层
public IssueVO getById(Long id) {
    Issue issue = issueMapper.selectById(id);
    return converter.toVO(issue);  // VO 不应在 Service 出现
}

// ✅ 正确：Service 返回 DO，Controller 做转换
public Issue getById(Long id) {
    return issueMapper.selectById(id);
}
// Controller 中：
// return R.ok(converter.toVO(issueService.getById(id)));
```

---

## 二、高级设计模式（Spring Boot 惯用写法）

### 2.1 策略模式（消灭 if-else 链）

**触发信号**：看到 `if/else if` 或 `switch` 按类型分发逻辑，且分支 ≥ 3 个。

**标准写法**（Spring 自动注入 List，构建注册表）：

```java
// ① 定义策略接口
public interface TransitionActionExecutor {
    String actionType();          // 注册 key
    void execute(TransitionActionConfig config, Issue issue);
}

// ② 每种类型一个 @Component 实现
@Component
public class SetFieldActionExecutor implements TransitionActionExecutor {
    @Override
    public String actionType() { return "set_field"; }

    @Override
    public void execute(TransitionActionConfig config, Issue issue) {
        // 具体逻辑
    }
}

@Component
public class SendEmailActionExecutor implements TransitionActionExecutor {
    @Override
    public String actionType() { return "send_email"; }

    @Override
    public void execute(TransitionActionConfig config, Issue issue) { ... }
}

// ③ 注册表 Bean（Spring 自动注入 List<接口>）
@Component
public class TransitionActionRegistry {
    private final Map<String, TransitionActionExecutor> executors;

    public TransitionActionRegistry(List<TransitionActionExecutor> executorList) {
        this.executors = executorList.stream()
            .collect(Collectors.toMap(TransitionActionExecutor::actionType, e -> e));
    }

    public TransitionActionExecutor get(String type) {
        TransitionActionExecutor executor = executors.get(type);
        if (executor == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                "不支持的动作类型: " + type);
        }
        return executor;
    }
}

// ④ 调用方极简（引擎核心无需修改）
actionRegistry.get(config.getActionType()).execute(config, issue);
```

**效果**：新增动作类型只需新建 `@Component`，引擎核心零修改。

---

### 2.2 责任链模式（多级校验/过滤）

**触发信号**：多个校验步骤顺序执行，任一步骤可提前终止。

```java
// ① 处理器接口
public interface IssueCloseCheckHandler {
    CheckResult check(Issue issue, CloseContext ctx);
}

// ② 按 @Order 顺序排列的处理器
@Component @Order(1)
public class WipLimitCheckHandler implements IssueCloseCheckHandler {
    @Override
    public CheckResult check(Issue issue, CloseContext ctx) {
        if (hasWipViolation(issue)) {
            return CheckResult.fail("WIP 限制超出，无法关闭");
        }
        return CheckResult.pass();
    }
}

@Component @Order(2)
public class RequiredFieldCheckHandler implements IssueCloseCheckHandler {
    @Override
    public CheckResult check(Issue issue, CloseContext ctx) {
        List<String> missing = getMissingRequiredFields(issue);
        if (!missing.isEmpty()) {
            return CheckResult.fail("必填字段未填写: " + String.join(", ", missing));
        }
        return CheckResult.pass();
    }
}

// ③ 链执行器
@Component
public class IssueClosePreCheckChain {
    private final List<IssueCloseCheckHandler> handlers; // Spring 按 @Order 注入

    public IssueClosePreCheckChain(List<IssueCloseCheckHandler> handlers) {
        this.handlers = handlers;
    }

    public CheckResult check(Issue issue, CloseContext ctx) {
        for (IssueCloseCheckHandler handler : handlers) {
            CheckResult result = handler.check(issue, ctx);
            if (!result.isPassed()) return result;  // 短路
        }
        return CheckResult.pass();
    }
}
```

---

### 2.3 模板方法模式（流程骨架固定，步骤可替换）

**触发信号**：多个类有相同的执行骨架，只是某几步实现不同。

```java
// 抽象模板：定义算法骨架
public abstract class AbstractReportGenerator<T> {

    // 模板方法（final，不允许重写骨架）
    public final ReportResult generate(ReportQuery query) {
        validateQuery(query);                    // 步骤1：校验（可重写）
        List<T> rawData = fetchData(query);      // 步骤2：取数（必须实现）
        List<T> processed = processData(rawData); // 步骤3：处理（可重写）
        return buildReport(processed, query);    // 步骤4：构建报告（必须实现）
    }

    protected void validateQuery(ReportQuery query) {
        // 默认校验逻辑（子类可 override）
        if (query.getStartDate().isAfter(query.getEndDate())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "开始时间不能晚于结束时间");
        }
    }

    protected abstract List<T> fetchData(ReportQuery query);

    protected List<T> processData(List<T> data) {
        return data;  // 默认不处理，子类按需覆盖
    }

    protected abstract ReportResult buildReport(List<T> data, ReportQuery query);
}

// 具体实现
@Component
public class BurndownReportGenerator extends AbstractReportGenerator<SprintProgressPoint> {
    @Override
    protected List<SprintProgressPoint> fetchData(ReportQuery query) { ... }

    @Override
    protected ReportResult buildReport(List<SprintProgressPoint> data, ReportQuery query) { ... }
}
```

---

### 2.4 观察者模式（领域事件解耦）

**触发信号**：一个操作完成后需要触发多个不相关的副作用（发通知、写日志、更新缓存）。

**Spring 标准实现**：

```java
// ① 定义领域事件
public class IssueStatusChangedEvent {
    private final Long issueId;
    private final String oldStatus;
    private final String newStatus;
    private final Long operatorId;
    private final LocalDateTime occurredAt;

    // 构造器、getter...
}

// ② 在 Service 中发布事件（事务提交后触发）
@Service
@RequiredArgsConstructor
public class IssueService {
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long issueId, String newStatusId) {
        Issue issue = issueMapper.selectById(issueId);
        String oldStatus = issue.getStatusId();
        // ... 执行状态变更 ...
        issueMapper.updateById(issue);

        // 发布领域事件（事务提交后执行，避免事务未提交时通知失败）
        eventPublisher.publishEvent(new IssueStatusChangedEvent(
            issueId, oldStatus, newStatusId, SecurityUtils.getCurrentUserId(),
            LocalDateTime.now()
        ));
    }
}

// ③ 各监听器独立处理（@TransactionalEventListener 确保事务提交后触发）
@Component
public class IssueNotificationListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(IssueStatusChangedEvent event) {
        // 发送通知，不影响主事务
        notificationService.notifyStatusChange(event);
    }
}

@Component
public class IssueActivityListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(IssueStatusChangedEvent event) {
        // 记录活动流，不影响主事务
        activityService.record(event);
    }
}

@Component
public class SprintProgressCacheListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(IssueStatusChangedEvent event) {
        // 失效 Sprint 进度缓存
        cacheService.evictSprintProgress(event.getIssueId());
    }
}
```

**关键点**：
- 用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 而非 `@EventListener`，确保主事务提交后才触发
- 每个监听器独立，互不影响，新增副作用只需新建监听器
- 监听器内部异常不会影响主业务事务

---

### 2.5 建造者模式（复杂对象构建）

**触发信号**：构造器参数超过 5 个，或有多种可选组合。

```java
// 使用 Lombok @Builder（推荐）
@Builder
@Data
public class IssueQuery {
    private Long projectId;
    private String keyword;
    private List<String> statusIds;
    private Long assigneeId;
    private String priority;
    private LocalDate dueDateFrom;
    private LocalDate dueDateTo;
    @Builder.Default
    private int page = 1;
    @Builder.Default
    private int pageSize = 20;
}

// 调用方清晰易读
IssueQuery query = IssueQuery.builder()
    .projectId(projectId)
    .keyword("登录")
    .statusIds(List.of("open", "in_progress"))
    .page(2)
    .build();
```

---

### 2.6 装饰器模式（动态叠加横切行为）

**触发信号**：需要给已有 Service 增加重试、缓存、日志等横切逻辑，但不想污染核心实现。

```java
// 核心接口
public interface UserService {
    UserVO getUserById(Long id);
}

// 核心实现
@Service("userServiceImpl")
public class UserServiceImpl implements UserService {
    @Override
    public UserVO getUserById(Long id) { ... }
}

// 缓存装饰器（Spring AOP 更常用，但装饰器在需要精细控制时更合适）
@Service @Primary
@RequiredArgsConstructor
public class CachedUserService implements UserService {
    private final UserServiceImpl delegate;
    private final RedisTemplate<String, UserVO> redis;

    @Override
    public UserVO getUserById(Long id) {
        String key = "user:" + id;
        UserVO cached = redis.opsForValue().get(key);
        if (cached != null) return cached;

        UserVO user = delegate.getUserById(id);
        redis.opsForValue().set(key, user, 5, TimeUnit.MINUTES);
        return user;
    }
}
```



---

## 三、代码高级复用规范

### 3.1 泛型基础 Service（消灭 CRUD 重复代码）

对于简单 CRUD 模块，通过泛型基类消除重复：

```java
// 泛型基础 Service 接口
public interface BaseService<T, ID extends Serializable> {
    T getById(ID id);
    List<T> listAll();
    void create(T entity);
    void update(T entity);
    void deleteById(ID id);
}

// 泛型基础 Service 实现（MyBatis-Plus ServiceImpl）
public abstract class BaseServiceImpl<M extends BaseMapper<T>, T>
        extends ServiceImpl<M, T>
        implements BaseService<T, Long> {

    @Override
    public T getById(Long id) {
        T entity = super.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "资源不存在: " + id);
        }
        return entity;
    }

    @Override
    public void deleteById(Long id) {
        getById(id);  // 先检查是否存在
        removeById(id);
    }
}

// 具体 Service 继承，只写差异逻辑
@Service
@RequiredArgsConstructor
public class TagService extends BaseServiceImpl<TagMapper, Tag> {

    // 只写 Tag 特有的业务逻辑
    public List<Tag> listByProject(Long projectId) {
        return list(new LambdaQueryWrapper<Tag>()
            .eq(Tag::getProjectId, projectId));
    }
}
```

---

### 3.2 通用分页查询封装

```java
// 通用分页结果构建工具
public class PageHelper {

    public static <DO, VO> PageResult<VO> toPageResult(
            Page<DO> page,
            Function<DO, VO> converter) {
        List<VO> voList = page.getRecords().stream()
            .map(converter)
            .collect(Collectors.toList());
        return new PageResult<>(voList, new Pagination(
            (int) page.getCurrent(),
            (int) page.getSize(),
            page.getTotal(),
            page.getPages()
        ));
    }

    // 支持批量转换（带 ID 预加载，防 N+1）
    public static <DO, VO> PageResult<VO> toPageResultWithContext(
            Page<DO> page,
            BiFunction<List<DO>, Map<String, Object>, List<VO>> batchConverter,
            Map<String, Object> context) {
        List<VO> voList = batchConverter.apply(page.getRecords(), context);
        return new PageResult<>(voList, new Pagination(
            (int) page.getCurrent(), (int) page.getSize(),
            page.getTotal(), page.getPages()
        ));
    }
}

// 通用查询条件构建器（消灭重复的 eq/like/in 判断）
public class QueryHelper {

    public static <T> LambdaQueryWrapper<T> buildWrapper(Class<T> clazz) {
        return new LambdaQueryWrapper<>();
    }

    // 动态排序（带白名单防 SQL 注入）
    public static <T> LambdaQueryWrapper<T> applySort(
            LambdaQueryWrapper<T> wrapper,
            String sortField,
            String sortOrder,
            Set<String> allowedFields,
            SFunction<T, ?>... defaultOrderBy) {
        if (StringUtils.isNotBlank(sortField) && allowedFields.contains(sortField)) {
            // 使用 last() 拼接排序，已经过白名单校验
            wrapper.last("ORDER BY " + sortField + " " + ("asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC"));
        } else {
            // 默认排序
            for (SFunction<T, ?> orderBy : defaultOrderBy) {
                wrapper.orderByDesc(orderBy);
            }
        }
        return wrapper;
    }
}
```

---

### 3.3 通用 Converter 基类（MyBatis-Plus + MapStruct）

```java
// 项目通用 Converter 基类
public interface BaseConverter {

    // Long → String（VO 中 ID 类型转换）
    default String longToString(Long value) {
        return value == null ? null : value.toString();
    }

    // String → Long（DTO 中 ID 反向转换）
    default Long stringToLong(String value) {
        if (StringUtils.isBlank(value)) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "无效的 ID 格式: " + value);
        }
    }

    // LocalDateTime → String（前端展示格式）
    default String dateTimeToString(LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}

// 具体 Converter 示例
@Mapper(componentModel = "spring")
public interface IssueConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "assigneeId", expression = "java(longToString(entity.getAssigneeId()))")
    IssueVO toVO(Issue entity);

    List<IssueVO> toVOList(List<Issue> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Issue toEntity(CreateIssueDTO dto);
}
```

---

### 3.4 防 N+1 的批量关联查询模式

```java
// ❌ 典型 N+1 问题
public List<IssueVO> listIssues(IssueQuery query) {
    List<Issue> issues = issueMapper.selectList(...);
    return issues.stream().map(issue -> {
        IssueVO vo = converter.toVO(issue);
        vo.setAssigneeName(userMapper.selectById(issue.getAssigneeId()).getDisplayName()); // N+1!
        return vo;
    }).collect(toList());
}

// ✅ 正确：批量预加载，一次查询
public List<IssueVO> listIssues(IssueQuery query) {
    List<Issue> issues = issueMapper.selectList(...);
    if (issues.isEmpty()) return Collections.emptyList();

    // 1. 收集所有需要关联查询的 ID
    Set<Long> userIds = issues.stream()
        .map(Issue::getAssigneeId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Set<Long> statusIds = issues.stream()
        .map(Issue::getStatusId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    // 2. 一次批量查询
    Map<Long, SysUser> userMap = userIds.isEmpty() ? Collections.emptyMap() :
        userMapper.selectBatchIds(userIds).stream()
            .collect(Collectors.toMap(SysUser::getId, u -> u));
    Map<Long, IssueStatus> statusMap = statusIds.isEmpty() ? Collections.emptyMap() :
        issueStatusMapper.selectBatchIds(statusIds).stream()
            .collect(Collectors.toMap(IssueStatus::getId, s -> s));

    // 3. 组装 VO
    return issues.stream().map(issue -> {
        IssueVO vo = converter.toVO(issue);
        Optional.ofNullable(userMap.get(issue.getAssigneeId()))
            .ifPresent(u -> vo.setAssigneeName(u.getDisplayName()));
        Optional.ofNullable(statusMap.get(issue.getStatusId()))
            .ifPresent(s -> {
                vo.setStatusName(s.getName());
                vo.setStatusCategory(s.getCategory());
            });
        return vo;
    }).collect(toList());
}
```

---

### 3.5 统一错误码枚举（消灭魔法字符串）

```java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 通用错误
    SUCCESS(0, "success"),
    INVALID_PARAMETER(40000, "请求参数错误"),
    RESOURCE_NOT_FOUND(40400, "资源不存在"),
    PERMISSION_DENIED(40300, "权限不足"),
    UNAUTHENTICATED(40100, "未认证"),
    CONFLICT(40900, "资源冲突"),

    // 业务错误
    INVALID_STATE(42200, "当前状态不允许此操作"),
    WORKFLOW_TRANSITION_NOT_ALLOWED(42201, "工作流：不允许此状态转换"),
    SPRINT_ALREADY_ACTIVE(42210, "已有进行中的 Sprint"),
    ISSUE_IN_ACTIVE_SPRINT(42211, "工单在进行中的 Sprint 中，无法删除"),

    // 系统错误
    SYSTEM_ERROR(50000, "系统内部错误"),
    EXTERNAL_SERVICE_ERROR(50200, "外部服务调用失败");

    private final int code;
    private final String message;
}

// 使用
throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_NOT_ALLOWED,
    String.format("状态 [%s] → [%s] 不允许", currentStatus, targetStatus));
```



---

## 四、并发安全规范

### 4.1 乐观锁防并发冲突

```java
// Entity 中声明版本字段
@Data
@TableName("issue")
public class Issue extends BaseEntity {
    // ...
    @Version  // MyBatis-Plus 乐观锁注解
    private Integer version;
}

// 更新时自动检查版本号，冲突抛 OptimisticLockException
// Service 中捕获并给出友好提示
@Transactional(rollbackFor = Exception.class)
public void update(Long id, UpdateIssueDTO dto) {
    Issue issue = issueMapper.selectById(id);
    // ... 修改字段
    int rows = issueMapper.updateById(issue);  // 自动带 version 条件
    if (rows == 0) {
        throw new BusinessException(ErrorCode.CONFLICT, "工单已被他人修改，请刷新后重试");
    }
}
```

### 4.2 分布式锁（Redis）防重复操作

```java
// 幂等性保护：同一用户短时间内不能重复提交
@Service
@RequiredArgsConstructor
public class SprintService {
    private final StringRedisTemplate redisTemplate;

    public void activateSprint(Long sprintId) {
        String lockKey = "lock:sprint:activate:" + sprintId;
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(acquired)) {
            throw new BusinessException(ErrorCode.CONFLICT, "操作过于频繁，请稍后重试");
        }

        try {
            doActivateSprint(sprintId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }
}
```

### 4.3 线程安全的共享状态

```java
// ❌ 错误：Service 中使用非线程安全的实例变量
@Service
public class ReportService {
    private List<ReportData> tempData = new ArrayList<>();  // 多线程共享，危险！
}

// ✅ 正确：无状态 Service，局部变量或 ThreadLocal
@Service
public class ReportService {
    public ReportResult generate(ReportQuery query) {
        List<ReportData> data = new ArrayList<>();  // 方法局部变量，线程安全
        // ...
        return buildResult(data);
    }
}
```

---

## 五、事务管理规范

### 5.1 事务边界设计

```java
// ✅ 多步写操作：加事务，指定 rollbackFor
@Transactional(rollbackFor = Exception.class)
public SprintVO completeSprint(Long sprintId, CompleteSprintDTO dto) {
    // 1. 更新 Sprint 状态
    Sprint sprint = sprintMapper.selectById(sprintId);
    sprint.setStatus("completed");
    sprintMapper.updateById(sprint);

    // 2. 处理未完成工单（移动/关闭）
    handleUnfinishedIssues(sprintId, dto.getAction());

    // 3. 记录活动日志
    activityService.logSprintComplete(sprintId);

    return converter.toVO(sprint);
}

// ✅ 只读查询：readOnly = true（告知数据库驱动优化，不加写锁）
@Transactional(readOnly = true)
public IssueVO getById(Long id) {
    return converter.toVO(issueMapper.selectById(id));
}

// ❌ 事务方法内 catch 后不抛出（事务不会回滚！）
@Transactional
public void dangerousMethod() {
    try {
        issueMapper.updateById(issue);
        if (somethingFailed()) {
            throw new RuntimeException("failed");
        }
    } catch (Exception e) {
        log.error("error", e);
        // ❌ 吞掉了异常，事务不回滚！
    }
}
```

### 5.2 跨 Service 事务传播

```java
// 外层 Service 控制事务边界
@Transactional(rollbackFor = Exception.class)
public void createIssueWithLinks(CreateIssueDTO dto, List<LinkDTO> links) {
    Issue issue = issueService.createIssue(dto);  // 参与外层事务

    for (LinkDTO link : links) {
        issueLinkService.createLink(issue.getId(), link);  // 参与外层事务
    }

    activityService.record(issue.getId(), "CREATE");  // 参与外层事务
}

// ⚠️ 不要在同一个类中调用另一个 @Transactional 方法（Self-invocation 问题）
// Spring AOP 基于代理，同类内调用绕过代理，事务注解失效
@Service
public class IssueService {
    @Transactional
    public void methodA() {
        this.methodB();  // ❌ Self-invocation，methodB 的事务注解失效
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void methodB() { ... }
}
// ✅ 解决：注入自身代理 @Autowired private IssueService self; 或拆分到不同 Service
```

---

## 六、AOP 横切关注点

### 6.1 操作日志（AOP + 注解驱动）

```java
// 自定义注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    String module() default "";
    String action() default "";
    String description() default "";
}

// 切面实现
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class OperationLogAspect {
    private final AuditLogService auditLogService;

    @AfterReturning(
        pointcut = "@annotation(operationLog)",
        returning = "result"
    )
    public void recordLog(JoinPoint joinPoint, OperationLog operationLog, Object result) {
        try {
            AuditLog log = AuditLog.builder()
                .module(operationLog.module())
                .action(operationLog.action())
                .operatorId(SecurityUtils.getCurrentUserId())
                .operatorName(SecurityUtils.getCurrentUsername())
                .requestParams(extractParams(joinPoint))
                .occurredAt(LocalDateTime.now())
                .build();
            auditLogService.save(log);
        } catch (Exception e) {
            // 日志失败不影响主业务
            log.warn("记录操作日志失败", e);
        }
    }
}

// 使用方式
@OperationLog(module = "工单", action = "删除", description = "删除工单")
@DeleteMapping("/{id}")
public R<Void> delete(@PathVariable Long id) {
    issueService.deleteById(id);
    return R.ok();
}
```

### 6.2 接口限流（AOP + Redis）

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int requests() default 10;   // 请求次数
    int period() default 60;     // 时间窗口（秒）
    String key() default "";     // 限流 key，支持 SpEL
}

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {
    private final StringRedisTemplate redisTemplate;

    @Before("@annotation(rateLimit)")
    public void checkRateLimit(JoinPoint jp, RateLimit rateLimit) {
        String userId = SecurityUtils.getCurrentUserId();
        String key = "rate:" + jp.getSignature().getName() + ":" + userId;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, rateLimit.period(), TimeUnit.SECONDS);
        }
        if (count > rateLimit.requests()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                "操作过于频繁，请 " + rateLimit.period() + " 秒后重试");
        }
    }
}
```

---

## 七、性能优化规范

### 7.1 查询性能

```java
// ✅ 只查需要的字段（避免 SELECT *）
List<Issue> issues = issueMapper.selectList(
    new LambdaQueryWrapper<Issue>()
        .select(Issue::getId, Issue::getTitle, Issue::getStatusId, Issue::getAssigneeId)
        .eq(Issue::getProjectId, projectId)
        .eq(Issue::getIsDeleted, false)
        .orderByDesc(Issue::getUpdatedAt)
);

// ✅ 大批量数据分批处理（避免 OOM）
public void batchUpdateStatus(List<Long> issueIds, String newStatus) {
    int batchSize = 500;
    for (int i = 0; i < issueIds.size(); i += batchSize) {
        List<Long> batch = issueIds.subList(i, Math.min(i + batchSize, issueIds.size()));
        issueMapper.batchUpdateStatus(batch, newStatus);
    }
}

// ✅ COUNT 查询单独优化（不走 JOIN）
public long countByProject(Long projectId) {
    return issueMapper.selectCount(
        new LambdaQueryWrapper<Issue>()
            .eq(Issue::getProjectId, projectId)
            .eq(Issue::getIsDeleted, false)
    );
}
```

### 7.2 缓存规范

```java
// 权限缓存（Redis，5分钟 TTL）
@Cacheable(value = "permissions", key = "#userId", unless = "#result.isEmpty()")
public Set<String> getUserPermissions(Long userId) {
    return permissionMapper.selectPermissionsByUserId(userId);
}

@CacheEvict(value = "permissions", key = "#userId")
public void evictUserPermissions(Long userId) {
    // 角色变更时主动失效
}

// 本地缓存（Caffeine，高频读取的字典数据）
@Bean
public Cache<String, List<IssueStatus>> statusCache() {
    return Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build();
}
```

### 7.3 异步处理

```java
// 耗时操作异步化（不阻塞主请求）
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final ApplicationEventPublisher eventPublisher;

    // 发送通知：异步执行，不影响主业务响应时间
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendNotification(IssueStatusChangedEvent event) {
        // 发邮件/推送等耗时操作
    }
}

// 配置线程池（避免使用默认 SimpleAsyncTaskExecutor）
@Bean("notificationExecutor")
public Executor notificationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("notification-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
}
```

---

## 八、代码质量红线

### 8.1 方法复杂度控制

```java
// ❌ 圈复杂度过高（嵌套超过 3 层）
public void process(Issue issue) {
    if (issue != null) {
        if (issue.getStatus() != null) {
            if (issue.getStatus().equals("open")) {
                if (issue.getAssignee() != null) {
                    // 第 4 层嵌套，必须重构
                }
            }
        }
    }
}

// ✅ 卫语句提前返回（Guard Clause）
public void process(Issue issue) {
    if (issue == null) return;
    if (issue.getStatus() == null) return;
    if (!issue.getStatus().equals("open")) return;
    if (issue.getAssignee() == null) return;

    // 核心逻辑，只有一层
    doProcess(issue);
}
```

### 8.2 SOLID 原则速查

| 原则 | 核心 | 在 Spring 中的体现 |
|------|------|-------------------|
| **S** 单一职责 | 一个类只做一件事 | Service 按业务域拆分（IssueService / SprintService） |
| **O** 开闭原则 | 对扩展开放，对修改关闭 | 策略模式：新增动作类型不修改引擎 |
| **L** 里式替换 | 子类可替换父类 | 避免 override 改变父类契约 |
| **I** 接口隔离 | 接口精小，不强迫依赖不需要的方法 | `UserRegisterDsGateway` 而非 `UserDsGateway` |
| **D** 依赖倒置 | 依赖抽象，不依赖实现 | Controller 依赖 Service 接口，不依赖 ServiceImpl |

### 8.3 命名最高原则

```java
// ❌ 意图不明
boolean flag = check(x);
void handle(Object o) { ... }
List<Map<String, Object>> getData() { ... }

// ✅ 意图自解释
boolean isTransitionAllowed = workflowService.checkTransition(issueId, targetStatusId);
void sendStatusChangeNotification(Issue issue, String oldStatus) { ... }
List<IssueVO> listIssuesByProject(Long projectId) { ... }
```

### 8.4 日志规范

```java
// ✅ 关键业务节点必须有 INFO 日志
log.info("工单状态变更: issueId={}, {} → {}, operator={}",
    issueId, oldStatus, newStatus, operatorName);

// ✅ 异常必须打印堆栈
log.error("处理工单状态变更失败: issueId={}", issueId, e);

// ❌ 禁止在循环体内打 INFO 日志（大量数据时刷爆日志）
for (Issue issue : issues) {
    log.info("处理工单: {}", issue.getId());  // 危险！
}
// ✅ 改为批量日志
log.info("开始批量处理工单，共 {} 条", issues.size());
```

---

## 九、审计维度（写代码前的检查清单）

在写任何 Service 方法前，问自己这 10 个问题：

1. **职责**：这个方法只做一件事吗？超过 50 行要考虑拆分。
2. **扩展性**：如果加一种新类型，要改几个文件？超过 2 个，考虑策略/工厂模式。
3. **事务**：涉及多步写操作了吗？需要 `@Transactional`。
4. **N+1**：有循环里查 DB 的操作吗？改为批量预加载。
5. **并发**：多用户同时操作同一资源会怎样？考虑乐观锁或分布式锁。
6. **权限**：这个接口需要 `@PreAuthorize` 吗？
7. **参数校验**：入参有 `@Valid` 吗？Service 内有二次校验吗？
8. **错误处理**：异常用 `BusinessException(ErrorCode.XXX, "具体描述")` 了吗？
9. **日志**：关键操作有 INFO 日志，异常有 ERROR 日志（含堆栈）吗？
10. **可测试性**：能不依赖 Spring 容器写单元测试吗？依赖太重说明耦合过紧。
