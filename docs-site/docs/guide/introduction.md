# 什么是 TrackFlow

TrackFlow 是一个面向研发团队的**开源项目与工单管理系统**，交互设计参考 [YouTrack](https://www.jetbrains.com/youtrack/)，适合中小型团队私有化部署替代 Jira / YouTrack。

## 核心定位

| 对比项 | TrackFlow | Jira | YouTrack |
|--------|-----------|------|----------|
| 开源 | ✅ MIT | ❌ 商业 | ❌ 商业 |
| 私有化部署 | ✅ 一键 Docker | ✅ 复杂 | ✅ 复杂 |
| YouTrack 风格 | ✅ | ❌ | ✅ |
| 自动化引擎 | ✅ 可视化 | ✅ | ✅ |
| 免费 | ✅ 完全免费 | ❌ | ❌ |

## 技术栈

**后端**
- Java 21 + Spring Boot 4.0
- PostgreSQL 15 + Redis 7
- Keycloak 24（OIDC 认证）
- MinIO（文件存储）
- Flyway（数据库版本管理）

**前端**
- Vue 3 + TypeScript + Vite
- Arco Design Vue（UI 组件库）
- Tiptap（富文本编辑器）
- ECharts（图表）
- LogicFlow（工作流/自动化画布）

## 主要功能模块

- **工单管理**：创建、编辑、筛选、批量操作、附件、评论、标签、链接
- **查询面板**：YouTrack 风格的左侧面板，结构化查询语法
- **项目管理**：多项目、成员管理、自定义工作流
- **看板**：拖拽式 Kanban，自定义列和泳道
- **Sprint**：Scrum 迭代管理，燃尽图
- **时间跟踪**：工时登记、计时器、时间表
- **自动化**：可视化流程图，15+ 节点类型
- **工作流**：可视化状态机，转换规则和动作
- **报表与仪表盘**：内置报表 + 拖拽仪表盘
- **自定义字段**：10+ 种字段类型，条件显示，字段权限
- **系统管理**：用户、角色、用户组、审计日志、Webhook
