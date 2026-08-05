# TrackFlow 项目开发指南

> 此文件由 Claude Code 在每次会话启动时自动加载。
> 内容整合自 `.kiro/steering/`，与 Kiro 保持一致的上下文。

---

## 一、项目定位

TrackFlow 是一款**企业级项目与任务管理系统**，目标完全替代 JetBrains YouTrack，面向 10-200 人技术团队提供专业的工单管理、工作流自动化和团队协作能力。

### 功能目标
- 覆盖 YouTrack 80% 核心能力
- 前端体验达到 Linear 流畅度
- 后端架构达到 OpenProject 可扩展性
- 支持完全私有化部署（Docker 一键启动）

### 核心对标产品

| 产品 | 借鉴维度 |
|------|----------|
| **JetBrains YouTrack** | 产品功能对标（UI 交互风格、工作流设计、左侧 Saved Query 面板 + 右侧列表布局） |
| **OpenProject** | 后端架构参考（数据模型、工作流引擎、Journal 活动记录、Relation 关联体系、权限模型） |
| **Linear** | 现代 UI 美学参考（极简动效、暗色主题、键盘操作） |
| **GitHub Issues** | 亮色/暗色双主题配色方案参考 |

---

## 二、技术栈

### 后端

| 层级 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Java | 21 LTS | Eclipse Temurin |
| 框架 | Spring Boot | 4.0.2 | Spring Framework 7 |
| ORM | MyBatis-Plus | 3.5.16 | boot4-starter |
| 数据库 | PostgreSQL | 15 | JSONB 支持自定义字段 |
| 缓存 | Redis | 7 | 权限缓存、会话 |
| 文件存储 | MinIO | latest | S3 兼容对象存储 |
| 认证 | Keycloak | 24 | OIDC/OAuth2 |
| 安全 | Spring Security | 7 | 自定义 PermissionEvaluator |
| 数据库迁移 | Flyway | 10.21 | 版本化 SQL 脚本 |
| API 文档 | Knife4j | 4.5 | OpenAPI 3 |
| 对象映射 | MapStruct | 1.6.3 | 编译时代码生成 |

### 前端

| 层级 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | Vue 3 | 3.5+ | Composition API + `<script setup>` |
| 语言 | TypeScript | 5.6+ | 严格模式 |
| UI 组件库 | Arco Design Vue | 2.56+ | 暗色/亮色主题 |
| 构建 | Vite | 6+ | 极快的 HMR |
| 路由 | Vue Router | 4.4+ | — |
| 状态管理 | Pinia | 2.2+ | 仅需跨页面共享时使用 |
| 富文本编辑器 | Tiptap (ProseMirror) | latest | WYSIWYG + Markdown 切换 |
| Markdown 渲染 | markdown-it + DOMPurify | — | 安全渲染 |
| HTTP 客户端 | Axios | 1.7+ | 拦截器统一处理 |

### 基础设施端口

| 服务 | 端口 | 用途 |
|------|------|------|
| TrackFlow 后端 | 8090 | REST API |
| Vue 前端 | 3000 | 开发服务器 |
| PostgreSQL | 5432 | 主数据库 |
| Redis | 16379 | 缓存 |
| MinIO | 9000/9001 | 文件存储 |
| Keycloak | 8080 | 身份认证 |

### Spring Boot 4.0 注意事项

1. **Jackson 3.x**：包名从 `com.fasterxml.jackson` 变为 `tools.jackson.core`。项目中同时存在 Jackson 2.x（第三方库依赖）和 Jackson 3.x（Spring Boot 核心），需要手动注册 `com.fasterxml.jackson.databind.ObjectMapper` bean 供 MyBatis-Plus 等使用。
2. **MyBatis-Plus**：必须使用 `mybatis-plus-spring-boot4-starter`（不是 boot3），版本 3.5.16+。
3. **Flyway 自动配置**：需要额外依赖 `spring-boot-flyway` 模块。
4. **`@MapperScan`**：放在 `@Configuration` 类上，扫描路径 `com.trackflow.**.mapper`。

---

## 三、核心功能模块

### 已完成

| 模块 | 功能 | 对标 YouTrack |
|------|------|---------------|
| **工单管理** | CRUD、部分更新、软删除 | Single Issue View |
| **工单详情页** | 标题/描述编辑、标签、关联、附件、活动流、评论 | Issue Detail |
| **工作流引擎** | 状态定义(18种)、转换规则(role+type+project)、可用转换查询 | Workflow |
| **属性面板** | 右侧字段面板、内联编辑、下拉选择 | Field Panel |
| **富文本编辑** | Tiptap WYSIWYG + Markdown 切换 | Editor |
| **主题系统** | 暗色/亮色/护眼三套主题 | Dark Mode |
| **项目管理** | CRUD、成员管理、角色分配 | Projects |
| **Sprint 管理** | 创建/激活/完成 | Agile Board |
| **查询面板** | 保存搜索、分组、项目筛选 | Saved Queries |
| **权限体系** | 全局权限 + 项目级权限 + Redis 缓存 | Permissions |
| **认证集成** | Keycloak OIDC + API Key 双模式 | Auth |
| **文件存储** | MinIO 上传/下载/预览 | Attachments |

### 待实现

| 模块 | 功能 | 优先级 |
|------|------|--------|
| **列表行内编辑** | 列表中直接点击修改 State/Sprint/负责人 | P0 |
| **自定义字段** | 项目可自定义字段（文本/选择/日期/数字） | P0 |
| **看板视图** | 按状态分列拖拽 | P1 |
| **批量操作** | 多选 + 批量变更状态/负责人 | P1 |
| **通知系统** | 工单变更实时通知（WebSocket + 邮件） | P1 |
| **报表图表** | 燃尽图、工作量统计、状态分布 | P2 |
| **邮件收发** | 通过邮件创建/回复工单 | P2 |
| **数据导入** | YouTrack CSV/JSON 数据迁移 | P2 |
| **Webhook** | 事件推送到外部系统 | P2 |
| **全文搜索** | PostgreSQL GIN + tsvector | P2 |

---

## 四、甲方核心需求基线（最高优先级）

> 此部分来自甲方合同，是系统交付的硬性要求，优先级高于所有其他需求。

### 基础功能

| # | 功能 | 当前状态 |
|---|------|----------|
| 1 | 用户、组织、角色及权限管理 | ✅ 已完成 |
| 2 | Keycloak 统一登录和用户身份管理 | ✅ 已完成 |
| 3 | 项目创建、编辑、查询及管理 | ✅ 已完成（筛选增强 REQ-468） |
| 4 | Issue CRUD、分配、评论及状态流转 | ✅ 已完成 |
| 5 | 自定义字段扩展 | ✅ 已完成 |
| 6 | Sprint 创建、查看及管理 | ✅ 已完成 |
| 7 | 不同类型的报表 | 🔶 框架在，能力不足（REQ-467） |
| 8 | 可配置 Workflow | ✅ 已完成 |
| 9 | 工程师 Apply 及状态记录 | ❌ 未实现（REQ-457） |
| 10 | 邮件添加 Comment | ❌ 未实现（REQ-458） |
| 11 | SUG 系统对接 | ❌ 未实现（REQ-459） |
| 12 | 评论、附件、通知、搜索及操作记录 | ✅ 已完成 |
| 13 | YouTrack 核心数据迁移 | ❌ 未实现（REQ-460） |
| 14 | API/Webhook 供外部系统接入 | ✅ 后端完成（REQ-464） |

### 实施优先级

**P0（阻塞交付）**：REQ-468（筛选引擎）、REQ-467（报表引擎）、REQ-466（集成架构）、REQ-457（Apply）、REQ-460（数据迁移）

**P1（核心体验）**：REQ-458（邮件Comment）、REQ-459（SUG集成）、REQ-461（规则计分）、REQ-470（搜索标签）、REQ-469（所有项目入口）

**P2**：REQ-462（预置报表）、REQ-463（过期提醒）、REQ-465（交付文档）

**P3**：REQ-464（Webhook 前端页面）

---

## 五、项目结构

```
trackflow-server/src/main/java/com/trackflow/
├── common/            # 公共模块 (config/exception/filter/model/util)
│   ├── config/        # MyBatisPlus, Redis, Jackson, Knife4j
│   ├── exception/     # ErrorCode, BusinessException, GlobalExceptionHandler
│   ├── filter/        # TraceIdFilter
│   ├── model/         # Result<T>, PageResult<T>, BaseEntity
│   └── util/          # SecurityUtils, PageHelper
├── auth/              # 认证 (Keycloak OIDC + API Key + Spring Security)
├── system/            # 系统管理 (Organization, User, Role, ApiKey)
├── project/           # 项目管理 + 成员
├── issue/             # Issue CRUD + 评论 + 附件 + 活动记录
├── query/             # 保存查询 + 筛选引擎 (QueryExecutor)
├── workflow/          # 状态转换规则
├── sprint/            # Sprint 管理
├── report/            # 报表
└── integration/       # Webhook + 通知

trackflow-web/src/
├── api/               # 接口层（类型 + 模块化 API）
│   └── types.ts       # 所有接口类型定义
├── assets/            # 静态资源
├── router/            # 路由配置
├── stores/            # Pinia 状态管理
├── styles/            # 全局样式 + CSS 变量
└── views/             # 页面组件
    ├── layout/        # 布局组件
    ├── login/         # 登录
    ├── issue/         # Issue 相关页面
    ├── project/       # 项目相关页面
    ├── sprint/        # Sprint 页面
    ├── admin/         # 管理页面（用户/角色/组织/工作流）
    └── dashboard/     # 仪表盘
```

### 模块间依赖

```
common → (被所有模块依赖)
auth → system (UserSyncService 依赖 SysUserMapper)
project → auth (PermissionService)
issue → project (nextIssueSequence)
query → issue (QueryExecutor 查询 Issue 表)
workflow → issue, project (状态转换校验)
sprint → project
integration → issue (Webhook 触发)
```

---

## 六、架构设计原则

### 后端原则

1. **分层清晰**：Controller(VO/DTO) → Service(DO) → Mapper(DO)，严格遵循。Controller 只能调 Service，不能直接调 Mapper
2. **统一响应**：所有 API 返回 `R<T>` 结构（code/message/data/traceId）。`code = 0` 表示成功。旧的 `Result<T>` 已废弃
3. **工作流驱动**：状态转换必须通过 `workflow_transition` 表校验，不允许绕过
4. **活动记录**：所有字段变更自动记录到 `issue_activity` 表（参考 OpenProject Journal）
5. **权限校验**：每个写操作必须 `@PreAuthorize` 验证。权限数据缓存在 Redis（key: `perm:user:{userId}`，TTL 5分钟），角色变更时主动失效
6. **Long→String**：所有 VO 中的 ID 用 String 防止 JS 精度丢失。MapStruct Converter 通过 `longToString()` 自动转换
7. **构造器注入**：禁止 `@Autowired` 字段注入，使用 Lombok `@RequiredArgsConstructor`
8. **性能优先**：使用 JOIN 查询替代 N+1，MyBatis XML 自定义 SQL。单次 API < 500ms，单次 SQL < 100ms

### 角色分配原则

- **最小权限**：新用户通过 Keycloak 首次登录时，`UserSyncService` 仅创建 `sys_user` 记录，不自动分配任何全局角色（唯一例外：系统中无任何管理员时首位用户自动成为 system_admin）
- **显式授权**：角色必须由系统管理员通过用户管理界面主动分配

### 前端原则

1. **API 模块化**：每个后端模块对应 `src/api/{module}.ts`，统一从 `@/api` 导出。组件中不硬编码 URL
2. **类型安全**：所有接口类型定义在 `src/api/types.ts`。ID 字段为 `string` 类型
3. **主题变量**：所有颜色用 `--tf-*` CSS 变量，禁止硬编码
4. **组件优先**：能用 Arco Design Vue 现成组件就用，不手写 div 模拟
5. **响应处理**：`request.ts` 拦截器已做 `response.data` 解包，组件中 `await xxxApi.method()` 得到的是 `R<T>` 对象
6. **Mock + API 双模式**：后端不可用时自动 fallback 到 mock 数据

---

## 七、数据库设计要点

| 表 | 用途 | 关键字段 |
|----|------|----------|
| `sys_user` | 用户（Keycloak 同步） | keycloak_id, display_name |
| `project` | 项目 | key(唯一), issue_sequence |
| `project_member` | 项目成员 | project_id, user_id, role_id |
| `issue` | 工单主表 | issue_key, status_id, assignee_id, custom_fields(JSONB) |
| `issue_status` | 状态定义 | name, color, category, is_closed |
| `workflow_transition` | 工作流规则 | project_id, issue_type, role_id, old_status_id → new_status_id |
| `issue_comment` | 评论 | issue_id, user_id, content |
| `issue_activity` | 活动记录 | issue_id, action, field_name, old_value, new_value |
| `issue_attachment` | 附件 | file_path(MinIO), content_type |
| `issue_tag` | 标签定义（项目级） | project_id, name, color |
| `issue_tag_relation` | 工单-标签关联 | issue_id, tag_id |
| `issue_link` | 工单关联 | source_issue_id, target_issue_id, link_type |
| `sprint` | 迭代 | project_id, status(planned/active/completed) |
| `saved_query` | 保存的搜索 | filters(JSONB), user_id, shared |

---

## 八、Java 后端编码规范

### 分层领域模型

| 对象 | 命名规则 | 位置 | 职责 |
|------|----------|------|------|
| **DO (Entity)** | `{表名}` | `{module}/entity/` | 与数据库表一一对应 |
| **DTO** | `{动作}{实体}DTO` | `{module}/dto/` | 接收前端请求参数 |
| **VO** | `{实体}VO` / `{实体}{场景}VO` | `{module}/vo/` | 返回给前端，ID 为 String 类型 |
| **Query** | `{实体}Query` | `{module}/dto/` | 查询条件封装，继承 `PageQuery` |
| **Converter** | `{实体}Converter` | `{module}/converter/` | MapStruct 接口，DO↔VO/DTO 转换 |

### 禁止事项

- ❌ Controller 直接返回 Entity（DO）
- ❌ 使用 `Map<String, Object>` 作为 @RequestBody 或返回值
- ❌ VO/DTO 类中使用 `@TableId`、`@TableName` 等持久化注解
- ❌ Service 层依赖 VO 类
- ❌ 超过 2 个查询参数不封装 Query 对象
- ❌ Controller 中 try-catch（让异常自然冒泡到全局处理器）
- ❌ Controller 中加 @Transactional
- ❌ private 方法上加 @Transactional（AOP 代理无效）
- ❌ 事务方法内捕获异常后不重新抛出
- ❌ Controller 超过 20 行

### 统一响应 R\<T\>

```java
R.ok()                    // 无数据
R.ok(data)                // 有数据
R.fail(40400, "资源不存在")
R.fail(ErrorCode.RESOURCE_NOT_FOUND)
```

### PageQuery 分页

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class IssueQuery extends PageQuery {
    private Long projectId;
    private String keyword;
}
```

### MapStruct Converter

```java
@Mapper(componentModel = "spring")
public interface ProjectConverter extends BaseConverter {
    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    ProjectVO toVO(Project entity);
    List<ProjectVO> toVOList(List<Project> entities);
}
```

### 权限注解（强制）

所有 Controller 的公开方法必须有权限注解：
```java
@PreAuthorize("@perm.check(#projectId, 'issue:create')")
@NoAuthorizationRequired  // 不需要权限的明确标注
```

### SQL 注入防护

- 所有参数用 `#{param}`，禁止 `${param}`（除非有白名单校验）
- 动态排序字段必须白名单校验

### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| Controller | `{实体}Controller` | `IssueController` |
| Service | `{实体}Service` | `IssueService` |
| Mapper | `{实体}Mapper` | `IssueMapper` |
| Entity | 表名驼峰 | `Issue`、`SysUser` |
| VO | `{实体}VO` / `{实体}{场景}VO` | `IssueVO`、`IssueDetailVO` |
| DTO | `{动作}{实体}DTO` | `CreateIssueDTO` |

API URL 使用复数名词：`/api/v1/projects`，非 `/api/v1/project`

### PostgreSQL 类型映射

| PostgreSQL | Java Entity | Java VO |
|------------|-------------|---------|
| BIGSERIAL / BIGINT | Long | String |
| VARCHAR | String | String |
| TEXT | String | String |
| TIMESTAMP | LocalDateTime | LocalDateTime |
| BOOLEAN | Boolean | Boolean |
| JSONB | String + `@TableField(typeHandler = JsonbTypeHandler.class)` | String |

### 日志规范

- 使用 Lombok `@Slf4j` 或 `Logger log = LoggerFactory.getLogger(...)`
- 使用占位符：`log.info("用户 {} 创建工单", username)`，不用字符串拼接
- 禁止日志中打印密码、Token、密钥

### Flyway 迁移规范

- 文件命名：`V{n}__{description}.sql`（双下划线）
- 已执行的脚本绝对不能修改（checksum 校验）
- 每个脚本原子化（一个功能一个脚本）

---

## 九、前端编码规范

### API 管理

```
src/api/
├── index.ts        # 统一出口
├── request.ts      # axios 实例 + 拦截器
├── types.ts        # 所有接口类型定义
├── project.ts      # 项目模块 API
├── issue.ts        # Issue 模块 API
└── ...
```

每个后端模块对应一个 `src/api/{module}.ts`，所有 API 函数集中在一个 `{module}Api` 对象中导出：

```typescript
export const projectApi = {
  list(params?: { keyword?: string; page?: number; pageSize?: number }) {
    return request.get<any, R<PageResult<ProjectVO>>>('/projects', { params })
  },
}
```

### 组件规范

- 使用 `<script setup lang="ts">`
- 使用 Arco Design Vue 组件（已全局注册）
- 不使用原生 `alert()`、`confirm()` — 使用 `Message` / `Modal`
- 组件文件命名：PascalCase

### 组件优先原则

| 场景 | 使用 | 不要 |
|------|------|------|
| 数据表格 | `<a-table>` + columns + slots | 手写 div 表格 |
| 表单 | `<a-form>` + `<a-form-item>` | 手写 div + label |
| 弹窗 | `<a-modal>` / `<a-drawer>` | 手写遮罩层 |
| 下拉选择 | `<a-select>` / `<a-dropdown>` | 手写 dropdown |
| 加载状态 | `<a-spin>` / `<a-skeleton>` | 手写 loading |

---

## 十、UI 设计原则

### 8px 间距网格

| Token | 值 | 用途 |
|-------|------|------|
| `xs` | 4px | 图标与文字 |
| `sm` | 8px | 组件内部间距 |
| `md` | 12px | 相关元素之间 |
| `base` | 16px | 标准区块 padding |
| `lg` | 24px | Section 之间 |
| `xl` | 32px | 大模块分隔 |

### 三级视觉层级

| 层级 | 处理 | 示例 |
|------|------|------|
| Primary | 大号、加粗、深色/亮色 | 页面标题、主按钮 |
| Secondary | 中号、常规 | 描述文本、次要按钮 |
| Tertiary | 小号、轻色 | 时间戳、元数据 |

### 颜色系统

| 角色 | 暗色模式 | 亮色模式 |
|------|----------|----------|
| 背景 Body | Gray-900 (#1b1d21) | White (#fff) |
| 背景 Surface | Gray-800 (#22252a) | Gray-50 (#f6f8fa) |
| 主文字 | Gray-100 (#e6edf3) | Gray-900 (#1f2328) |
| 次文字 | Gray-400 (#9ca3af) | Gray-600 (#57606a) |
| 强调色 | Blue-400 (#58a6ff) | Blue-600 (#0969da) |

### 交互反馈

| 状态 | 表现 |
|------|------|
| Hover | 背景微亮，transition 150ms |
| Active | 背景再深一级 |
| Focus | 2px accent 色 outline |
| Disabled | opacity: 0.4, cursor: not-allowed |
| Loading | 半透明遮罩 + spinner 或 skeleton |

### 反模式（NEVER）

- ❌ 灰色文字放在彩色背景上
- ❌ 仅用颜色传达含义
- ❌ 纯黑背景或纯白文字
- ❌ 同一区域超过 3 种字号
- ❌ 没有 hover 效果的可点击元素
- ❌ 空列表只显示"暂无数据"（必须有引导 CTA）
- ❌ 按钮文案用"提交"（应说明具体动作）

---

## 十一、开发流程（强制）

1. **先看 YouTrack** — 确认要实现的功能在 YouTrack 中是什么样的交互
2. **再看 OpenProject** — 参考后端数据模型和 API 设计
3. **Mock 先行** — 前端用 mock 数据验证 UI 效果
4. **后端实现** — 按 Java 编码规范实现 Controller → Service → Mapper
5. **必须测试** — 调用 E2E 测试验证功能
6. **必须审核** — git commit 前调用 code-review
7. **性能优化** — 消除 N+1、添加索引、Redis 缓存

### 企业级实现标准

- **不允许简化实现**：每个功能必须达到企业级生产标准
- **功能流程零遗漏**：CRUD 功能必须包含确认弹窗、错误处理、乐观更新、并发控制
- **不得遗漏**：权限控制、审计日志、通知触发、双重校验、空状态/加载状态/错误状态、键盘快捷键

### 测试要求

每次完成功能开发后，必须对刚完成的功能进行端到端测试。测试不通过 → 修复 → 重测，直到通过。

### 审核要求

每次 git commit 业务代码之前，必须对 staged 文件进行审核。MUST 级问题必须修复后才能 commit。

---

## 十二、子代理间信息传递规范

每次调用子代理都是无状态的。主代理负责做"上下文桥接"，把必要的历史信息通过 prompt 传递给子代理：

### e2e-tester 重测

```
上轮测试结果：
- ✅ 用例1：xxx（已通过，跳过）
- ❌ 用例3：xxx（失败原因：yyy）

请仅验证用例3是否修复 + 回归检查用例1仍然正常
```

### code-reviewer 二审

```
上轮反馈及处理：
- MUST-1：✅ 已修复
- MUST-2：✅ 已修复

请验证 MUST-1/2 是否正确修复，无需重新做完整审核
```

### 反模式

- ❌ 每次都重写完整验收标准——浪费时间
- ❌ 修复后直接 commit 不重测——可能引入回归
- ❌ 二次审核时不说明上轮反馈——审核子代理会重复提相同问题

---

## 十三、测试账号

### 开发测试账号

| 用途 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| Keycloak 管理员 | admin | admin | 管理 realm、用户、client |
| 应用测试用户 | testuser | test123 | 登录 TrackFlow 系统 |
| PostgreSQL | trackflow | trackflow123 | 数据库连接 |
| MinIO | trackflow | trackflow123 | 文件存储管理控制台 http://localhost:9001 |

### 系统角色（7 种）

| ID | 名称 | Code | 类型 | 说明 |
|----|------|------|------|------|
| 1 | 系统管理员 | system_admin | global | 拥有所有权限（代码中 shortcut 判断） |
| 2 | 项目管理员 | project_admin | project | 项目内全部权限，管理成员/工作流/字段 |
| 3 | 开发人员 | developer | project | Issue CRUD + 状态变更 + Sprint 查看 |
| 4 | 测试人员 | tester | project | Issue 查看/评论/状态变更（Testing→Done） |
| 5 | 观察者 | observer | project | 只读 + 评论 |
| 6 | 产品经理 | product_manager | project | Issue 创建/编辑 + Sprint 管理 + 报表 |
| 7 | 技术负责人 | tech_lead | project | 开发人员权限 + 分配/删除 Issue + Sprint 管理 |

### 主要测试用户

所有测试用户密码统一：`test123`

| 用户名 | 姓名 | 全局角色 | DE4 角色 | FE1 角色 |
|--------|------|----------|----------|----------|
| testuser | Test User | system_admin | project_admin | project_admin |
| lina | 李娜 | system_admin | project_admin | — |
| zhangwei | 张伟 | — | tech_lead | developer |
| zhoujie | 周杰 | — | tech_lead | — |
| wangqiang | 王强 | — | developer | developer |
| liuyang | 刘洋 | — | developer | tech_lead |
| sunlei | 孙磊 | — | product_manager | product_manager |
| zhaojing | 赵静 | — | tester | tester |
| huanglei | 黄磊 | — | observer | observer |

### 权限验证典型场景

| 场景 | 推荐用户 | 原因 |
|------|----------|------|
| 超级管理员 | testuser | system:admin shortcut |
| 项目管理 | lina | project_admin |
| 技术负责人（分配+Sprint） | zhangwei | tech_lead |
| 普通开发 | wangqiang | developer |
| 产品经理（创建+Sprint） | sunlei | product_manager |
| 测试人员（仅状态变更） | zhaojing | tester |
| 观察者（只读+评论） | huanglei | observer |

### Keycloak 配置

- Realm: `trackflow`
- Client: `trackflow-frontend` (public, PKCE)
- 管理员控制台: http://localhost:8080 → `admin / admin`
- Token 有效期: 5 分钟（access_token），30 分钟（refresh_token）
- 所有用户已配置在 `trackflow-server/keycloak/trackflow-realm.json`

---

## 十四、开发环境

### 启动步骤

1. `cd trackflow-server && docker compose up -d` 启动基础设施
2. 确保其他项目没有占用 5432 端口
3. IDEA 打开项目，设置 Active Profile = `dev`
4. 运行 TrackFlowApplication，Flyway 自动建表
5. 前端：`cd trackflow-web && npm install && npm run dev`

### Keycloak 字段映射

| Keycloak 字段 | JWT Claim | 示例 |
|---------------|-----------|------|
| `firstName` | `given_name` | 名（如"静"） |
| `lastName` | `family_name` | 姓（如"赵"） |
| `username` | `preferred_username` | zhaojing |

**CJK 姓名处理**：不要直接使用 JWT `name` claim（格式为"名 姓"带空格）。从 `family_name` + `given_name` 拼接，无空格。

### 参考资源

- **YouTrack 文档**：`youtrack-docs/pages/{topic}.html.md`（离线 600+ 页）
- **OpenProject 源码**：`openproject/` 目录
- **需求文件**：`requirements/develop/` (develop/implement/rejected/review/working)

---

## 十五、环境注意事项与安全规则

### Shell 命令防阻塞（强制）

> 任何命令都不允许产生交互式等待。违反此规则会导致无人值守模式下进程永久卡死。

**绝对禁止**：
- `Test-Path` 的参数来自变量（变量为空时提示 `Path[0]:` 永久阻塞）
- `git commit` 不带 `-m`
- `git rebase -i`
- `npm init` 不带 `-y`
- `psql`/`redis-cli` 不带 `-c`
- `Set-Content`/`Add-Content` 修改源代码文件（变量为空时提示 `Value[0]:` 阻塞）
- 用 `;` 或 `|` 串接可能失败的多步操作
- `taskkill /F /IM chrome.exe`（会杀掉所有 worker 的 Chrome 实例）

### 文件修改必须用原生工具

禁止用 PowerShell 命令修改源代码文件。必须用 Read/Write/Edit/Grep 等原生工具。

### Java 编译流程

统一通过 `trackflow-test` MCP 的 `build_backend` 编译。禁止用 PowerShell 检查 class 文件（`Test-Path`、`Get-Item` 等）。

### 浏览器进程管理

并行模式下每个 worker 有独立的 Playwright 实例。绝对禁止 `taskkill /F /IM chrome.exe`。

### PowerShell 中文输出

PowerShell 控制台无法正确显示 UTF-8 中文。不要因为 PowerShell 输出乱码就判定"数据有问题"。验证 API 返回值必须通过 Playwright 或直接查数据库。

---

## 十六、数据库迁移规范

- 文件命名：`V{n}__{description}.sql`（双下划线）
- 放在 `src/main/resources/db/migration/`
- 启动时 Flyway 自动执行
- 不要修改已执行的脚本，只能新增
- 脚本中必须包含注释说明变更目的
- 索引命名：`idx_{表名}_{字段名}`

---

## 十七、API 规范

- 统一响应：`R<T>` 包装 `{code, message, data, traceId}`
- 分页：`PageResult<T>` 包含 `{list, pagination: {page, pageSize, total, totalPages}}`
- 错误码：40xxx 客户端错误，50xxx 服务端错误
- 认证：Bearer Token（Keycloak JWT 或 `tf_` 开头的 API Key）
- URL：复数名词、小写+连字符、层级不超过 3 层

### HTTP 方法语义

| 方法 | 语义 | 幂等 | 响应码 |
|------|------|------|--------|
| GET | 读取 | ✅ | 200 |
| POST | 创建 | ❌ | 201 |
| PUT | 全量替换 | ✅ | 200 |
| PATCH | 部分更新 | ❌ | 200 |
| DELETE | 删除 | ✅ | 204 或 200 |
