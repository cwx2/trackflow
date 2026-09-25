# TrackFlow

> 参考 YouTrack 交互风格设计的开源项目管理系统，基于 Spring Boot 4 + Vue 3 构建。

---

## 简介

TrackFlow 是一个面向研发团队的开源项目与工单管理系统，交互设计参考 YouTrack，支持 Scrum/看板工作流、自定义字段、自动化规则、报表与仪表盘等核心功能。适合中小型团队替代 Jira / YouTrack 私有化部署。

---

## 功能特性

### 工单管理
- 多项目工单（Issue）创建、编辑、归档，支持父子级结构
- 自定义字段（文本、日期、枚举、多选、用户、版本、周期等 10+ 种类型）
- 工单链接（关联、阻塞、重复等关系）、投票、关注
- 附件上传与管理（MinIO 存储，支持 50MB 上限）
- 富文本评论（Tiptap 编辑器，支持 @ 提及）
- 工单标签与颜色分类
- 工单可见性控制（公开 / 仅成员）

### 工作流引擎
- 可视化工作流设计器（LogicFlow 画布）
- 自定义状态与转换规则，支持转换条件（角色/负责人限制）
- 转换动作：自动更改字段值、发送通知等
- 转换时强制要求填写评论

### Scrum / 看板
- Sprint 创建、启动、归档，多 Sprint 支持
- 看板：自定义列（绑定状态）、泳道（按负责人/优先级/史诗）
- 卡片配置：显示字段、颜色、WIP 限制
- 积压（Backlog）管理

### 查询面板（Saved Queries）
- YouTrack 风格的左侧 Saved Query 面板
- 内置系统查询（我的工单、我的 Bug、本周已完成等）
- 支持结构化查询语法（`assignee: me status: In Progress`）
- 查询收藏与共享

### 自定义仪表盘
- 拖拽布局（grid-layout-plus）
- 10+ 种 Widget：工单列表、燃尽图、累积流图、饼图、团队成员、日历等
- 仪表盘共享与收藏
- 系统默认仪表盘

### 报表
- 预置报表：燃尽图、速度图、累积流、时间跟踪报表
- 报表共享与收藏
- 报表结果缓存

### 时间跟踪
- 工单工时登记（手动 / 计时器）
- 时间维度报表
- 工时查看权限控制

### 自动化（Automation）
- 基于触发器的自动化规则（工单创建/更新/状态变更/定时）
- 可视化自动化流设计器
- 内置自动化模板
- 执行历史与日志

### 通知系统
- 站内通知 + 邮件通知
- 通知偏好精细化配置（按事件类型 × 项目维度）
- 通知聚合（5 分钟窗口合并发送）
- 通知静音与取消订阅

### 系统管理
- 多项目管理，项目可见性控制（公开/私有）
- 基于角色的权限体系（RBAC）：`system_admin`、`project_admin`、`tech_lead`、`developer`、`tester`、`product_manager`、`observer`
- 用户组管理
- API Key 管理（支持 `tf_` 前缀 API Key 认证）
- 审计日志（操作记录可追溯）
- 系统设置（保留策略、颜色色板等）

---

## 技术栈

### 后端
| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 (Eclipse Temurin) | LTS |
| Spring Boot | 4.0.2 | 基于 Spring Framework 7 |
| MyBatis-Plus | 3.5.16 | ORM，使用 boot4-starter |
| PostgreSQL | 15 | 主数据库 |
| Redis | 7 | 权限缓存、通知聚合 |
| MinIO | latest | 附件文件存储 |
| Keycloak | 24 | OIDC 认证 / 用户管理 |
| Flyway | 10.21 | 数据库版本管理（300+ 迁移脚本）|
| MapStruct | 1.6.3 | DTO ↔ Entity 转换 |
| Knife4j | 4.5 | OpenAPI 3 文档 |

### 前端
| 技术 | 版本 | 说明 |
|------|------|------|
| Vue 3 | 3.5 | Composition API + `<script setup>` |
| TypeScript | 5.6 | 全量类型覆盖 |
| Arco Design Vue | 2.56 | UI 组件库 |
| Vite | 6 | 构建工具 |
| Pinia | 2.2 | 状态管理 |
| Tiptap | 3 | 富文本编辑器 |
| ECharts | 6 | 报表图表 |
| LogicFlow | 2 | 工作流 / 自动化画布 |

---

## 快速开始

### 前置条件

- Docker & Docker Compose
- JDK 21（[Eclipse Temurin](https://adoptium.net/)）
- Node.js 18+
- Maven 3.9+

### 1. 克隆仓库

```bash
git clone https://github.com/cwx2/trackflow.git
cd trackflow
```

### 2. 启动基础设施

```bash
cd trackflow-server
docker compose up -d
```

启动后服务清单：

| 服务 | 地址 | 说明 |
|------|------|------|
| PostgreSQL | localhost:5432 | 主数据库 |
| Redis | localhost:16379 | 缓存 |
| MinIO | localhost:9000 / 9001 | 文件存储 |
| Keycloak | localhost:8080 | 认证服务 |

### 3. 启动后端

```bash
cd trackflow-server
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway 会在首次启动时自动执行所有数据库迁移脚本，完成建表和初始数据填充。

后端 API 地址：`http://localhost:8090`  
API 文档（Knife4j）：`http://localhost:8090/doc.html`

### 4. 启动前端

```bash
cd trackflow-web
npm install
npm run dev
```

前端地址：`http://localhost:3000`

### 5. 登录

使用内置测试账号登录：

| 账号 | 密码 | 角色 |
|------|------|------|
| testuser | test123 | 系统管理员 |
| lina | test123 | 团队负责人 |
| zhangwei | test123 | 技术负责人 |
| wangqiang | test123 | 开发人员 |
| sunlei | test123 | 产品经理 |
| zhaojing | test123 | 测试人员 |

---

## 项目结构

```
trackflow/
├── trackflow-server/          # Spring Boot 后端
│   ├── src/main/java/com/trackflow/
│   │   ├── auth/              # 认证：Keycloak OIDC + API Key
│   │   ├── automation/        # 自动化规则引擎
│   │   ├── board/             # 看板（列、泳道、卡片配置）
│   │   ├── customfield/       # 自定义字段
│   │   ├── dashboard/         # 仪表盘与 Widget
│   │   ├── integration/       # Webhook、邮件通知
│   │   ├── issue/             # 工单核心（CRUD、评论、附件、活动）
│   │   ├── project/           # 项目管理
│   │   ├── query/             # 保存查询与筛选引擎
│   │   ├── report/            # 报表
│   │   ├── rule/              # 工作流规则引擎
│   │   ├── sprint/            # Sprint 管理
│   │   ├── system/            # 用户、角色、组织、API Key
│   │   ├── timeentry/         # 时间跟踪
│   │   └── workflow/          # 工作流定义与转换
│   ├── src/main/resources/
│   │   ├── db/migration/      # Flyway SQL（V1 ~ V300+）
│   │   └── application*.yml   # 配置文件
│   └── docker-compose.yml     # 本地开发基础设施
│
└── trackflow-web/             # Vue 3 前端
    └── src/
        ├── api/               # API 封装层
        ├── components/        # 通用组件
        ├── views/             # 页面视图
        │   ├── issue/         # 工单列表与详情
        │   ├── board/         # 看板
        │   ├── sprint/        # Sprint 管理
        │   ├── report/        # 报表
        │   ├── automation/    # 自动化配置
        │   ├── dashboard/     # 仪表盘
        │   └── admin/         # 管理后台
        └── stores/            # Pinia 状态
```

---

## 配置说明

开发环境配置文件 `trackflow-server/src/main/resources/application-dev.yml` 包含所有本地默认值，无需额外配置即可运行。

生产环境所有敏感配置通过环境变量注入：

| 环境变量 | 说明 |
|----------|------|
| `DB_URL` | PostgreSQL 连接字符串 |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 |
| `KEYCLOAK_ISSUER_URI` / `KEYCLOAK_JWKS_URI` | Keycloak 地址 |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO 存储 |
| `TRACKFLOW_CORS_ORIGINS` | 允许的前端域名（逗号分隔） |
| `TRACKFLOW_ENCRYPTION_KEY` | AES-256 加密密钥（Base64） |

---

## 权限模型

TrackFlow 使用双层 RBAC：

- **全局角色**：`system_admin`（系统管理员）
- **项目角色**：`project_admin`、`tech_lead`、`developer`、`product_manager`、`tester`、`observer`

所有 API 端点通过 `@PreAuthorize` 注解 + 自定义 `PermissionEvaluator` 实现细粒度权限控制。权限数据缓存于 Redis（TTL 5 分钟）。

---

## 开发指南

### 数据库迁移

```bash
# 新增迁移脚本（接着最大编号往后加）
# 文件名格式：V{N}__description.sql（双下划线）
touch trackflow-server/src/main/resources/db/migration/V999__my_change.sql
# 重启后端，Flyway 自动执行
```

### 构建

```bash
# 后端构建
cd trackflow-server && ./mvnw clean package -DskipTests

# 前端构建
cd trackflow-web && npm run build
```

---

## License

[MIT](LICENSE)

---

## 致谢

- 交互设计参考 [YouTrack](https://www.jetbrains.com/youtrack/)
- 后端业务流程参考 [OpenProject](https://github.com/opf/openproject)
- UI 组件：[Arco Design Vue](https://arco.design/vue)
- 工作流画布：[LogicFlow](https://github.com/didi/LogicFlow)
