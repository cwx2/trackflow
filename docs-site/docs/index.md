---
layout: home

hero:
  name: "TrackFlow"
  text: "开源项目管理系统"
  tagline: 参考 YouTrack 交互风格，私有化部署，一条命令启动
  actions:
    - theme: brand
      text: 快速开始
      link: /guide/getting-started
    - theme: alt
      text: 功能文档
      link: /features/issues
    - theme: alt
      text: GitHub
      link: https://github.com/cwx2/trackflow

features:
  - icon: 🎯
    title: YouTrack 风格
    details: 左侧 Saved Query 面板 + 右侧工单列表，支持结构化查询语法，熟悉 YouTrack 的用户零学习成本上手。

  - icon: 🔧
    title: 完整工单管理
    details: 支持父子工单、自定义字段（10+ 种类型）、工单链接、附件、富文本评论、标签、投票、工单可见性控制。

  - icon: 📋
    title: Scrum / 看板
    details: Sprint 全生命周期管理，可视化看板支持自定义列、泳道（按负责人/优先级/史诗）和 WIP 限制。

  - icon: ⚙️
    title: 可视化工作流
    details: 拖拽式工作流状态机设计器，自定义转换条件（角色/负责人限制），支持转换时强制填写评论。

  - icon: 🤖
    title: 自动化引擎
    details: 可视化流程图编辑器，支持 15+ 种节点类型（条件、循环、HTTP 请求、AI Agent、角色代理等），内置模板库。

  - icon: 📊
    title: 报表与仪表盘
    details: 燃尽图、速度图、累积流图、时间跟踪报表，拖拽式仪表盘支持 10+ 种 Widget，可共享和收藏。

  - icon: 🔐
    title: 细粒度权限
    details: 双层 RBAC 权限体系，全局角色 + 项目角色，@PreAuthorize 注解级别的 API 访问控制。

  - icon: 🚀
    title: 一键部署
    details: Docker Compose 一条命令启动全部服务（前端 + 后端 + PostgreSQL + Redis + MinIO + Keycloak），生产就绪。
---
