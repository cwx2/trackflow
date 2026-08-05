# TrackFlow 项目开发指南

> 此文件由 Claude Code 在每次会话启动时自动加载。内容取自 `.kiro/steering/`，保持与 Kiro 一致。

## 项目定位

TrackFlow 是一款**企业级项目与任务管理系统**，目标完全替代 JetBrains YouTrack，面向 10-200 人技术团队。

| 参考产品 | 借鉴维度 |
|----------|----------|
| **JetBrains YouTrack** | 产品功能对标（UI 交互风格、工作流设计、左侧 Saved Query 面板 + 右侧列表布局） |
| **OpenProject** | 后端架构参考（数据模型、工作流引擎、Journal 活动记录、权限模型） |
| **Linear** | 现代 UI 美学参考（极简动效、暗色主题、键盘操作） |
| **GitHub Issues** | 亮色/暗色双主题配色方案参考 |

## 技术栈

### 后端
- Java 21 (Eclipse Temurin) + Spring Boot 4.0.2 + MyBatis-Plus 3.5.16
- PostgreSQL 15 + Redis 7 + MinIO + Keycloak 24 (OIDC)
- Flyway 10.21 数据库迁移 + Knife4j 4.5 API 文档

### 前端
- Vue 3 + TypeScript 5.6+ + Arco Design Vue 2.56+ + Vite 6
- Pinia 2.2 + Vue Router 4.4 + Axios 1.7 + Tiptap 富文本

### 端口清单

| 服务 | 端口 |
|------|------|
| TrackFlow 后端 | 8090 |
| Vue 前端 | 3000 |
| PostgreSQL | 5432 |
| Redis | 16379 |
| MinIO | 9000/9001 |
| Keycloak | 8080 |

## 项目结构

```
trackflow-server/src/main/java/com/trackflow/
├── common/     # 公共模块 (config/exception/filter/model/util)
├── auth/       # 认证 (Keycloak OIDC + API Key + Spring Security)
├── system/     # 系统管理 (组织/用户/角色/API Key)
├── project/    # 项目管理 + 成员
├── issue/      # Issue CRUD + 评论 + 附件 + 活动记录
├── query/      # 保存查询 + 筛选引擎 (QueryExecutor)
├── workflow/   # 状态转换规则
├── sprint/     # Sprint 管理
├── report/     # 报表
└── integration/ # Webhook + 通知

trackflow-web/src/
├── api/        # 接口层（类型 + 模块化 API）
├── views/      # 页面组件 (layout/login/issue/project/sprint/admin/dashboard)
├── stores/     # Pinia
├── router/     # 路由
└── styles/     # 全局样式 + CSS 变量
```

## 架构设计原则

### 后端
1. **分层清晰**：Controller(VO/DTO) → Service(DO) → Mapper(DO)，严格遵循
2. **统一响应**：所有 API 返回 `R<T>` 结构（code/message/data/traceId）
3. **工作流驱动**：状态转换必须通过 `workflow_transition` 表校验
4. **活动记录**：所有字段变更自动记录到 `issue_activity` 表
5. **权限校验**：每个写操作必须 `@PreAuthorize` 验证项目级权限
6. **Long→String**：所有 VO 中的 ID 用 String 防止 JS 精度丢失

### 前端
1. **API 模块化**：每个后端模块对应 `src/api/{module}.ts`
2. **类型安全**：所有接口类型定义在 `src/api/types.ts`
3. **主题变量**：所有颜色用 `--tf-*` CSS 变量，禁止硬编码
4. **组件优先**：能用 Arco Design Vue 现成组件就用，不手写 div 模拟

## 模块间依赖关系

common → (被所有模块依赖)
auth → system
project → auth
issue → project
query → issue
workflow → issue, project
sprint → project
integration → issue

## 参考资源

- **YouTrack 文档**：`youtrack-docs/pages/{topic}.html.md`（离线 600+ 页）
- **OpenProject 源码**：`openproject/` 目录
- **需求文件**：`requirements/develop/` (develop/implement/rejected/review/working)

## 测试账号

所有测试用户密码：`test123`
Keycloak 管理员：`admin / admin`
PostgreSQL：`trackflow / trackflow123`

| 用户 | 全局角色 | 用途 |
|------|----------|------|
| testuser | system_admin | 超级管理员 |
| zhangwei | 无 (tech_lead) | 技术负责人 |
| wangqiang | 无 (developer) | 普通开发 |
| zhaojing | 无 (tester) | 测试人员 |
| huanglei | 无 (observer) | 最小权限 |

## 开发流程（强制）

1. **先看 YouTrack** — 确认真实交互
2. **再看 OpenProject** — 参考数据模型和 API
3. **实现功能** — 按规范编码
4. **必须测试** — E2E 测试通过
5. **必须审核** — Code review 通过
6. **提交推送** — git commit + push

## 关键约束

- 不允许简化实现，必须达到企业级生产标准
- 禁止 Controller 直接返回 Entity / 直接调用 Mapper
- 禁止 `@Autowired` 字段注入，必须构造器注入
- 禁止仅用颜色传达含义
- 禁止 PowerShell 修改源代码文件（用原生工具）
- 禁止 `Test-Path` 检查 class 文件（用 MCP 工具）
- 禁止 `taskkill /F /IM chrome.exe`（会破坏并行隔离）
