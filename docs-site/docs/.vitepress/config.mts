import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'TrackFlow',
  description: '开源项目管理系统 - 参考 YouTrack 交互风格，基于 Spring Boot 4 + Vue 3',
  base: '/trackflow/',

  head: [
    ['meta', { name: 'theme-color', content: '#3b82f6' }],
    ['meta', { name: 'og:type', content: 'website' }],
    ['meta', { name: 'og:title', content: 'TrackFlow 文档' }],
    ['meta', { name: 'og:description', content: '开源项目管理系统，参考 YouTrack 交互风格' }],
  ],

  themeConfig: {
    logo: '/logo.svg',
    siteTitle: 'TrackFlow',

    nav: [
      { text: '快速开始', link: '/guide/getting-started' },
      { text: '功能文档', link: '/features/issues' },
      { text: '管理配置', link: '/admin/overview' },
      { text: 'GitHub', link: 'https://github.com/cwx2/trackflow', target: '_blank' },
    ],

    sidebar: {
      '/guide/': [
        {
          text: '介绍',
          items: [
            { text: '什么是 TrackFlow', link: '/guide/introduction' },
            { text: '快速开始', link: '/guide/getting-started' },
            { text: '生产部署', link: '/guide/deployment' },
            { text: '权限模型', link: '/guide/permissions' },
          ],
        },
      ],
      '/features/': [
        {
          text: '核心功能',
          items: [
            { text: '工单管理', link: '/features/issues' },
            { text: '查询面板', link: '/features/query-panel' },
            { text: '项目管理', link: '/features/projects' },
            { text: '看板', link: '/features/board' },
            { text: 'Sprint 迭代', link: '/features/sprint' },
            { text: '时间跟踪', link: '/features/time-tracking' },
          ],
        },
        {
          text: '高级功能',
          items: [
            { text: '自动化', link: '/features/automation' },
            { text: '报表与仪表盘', link: '/features/reports' },
            { text: '自定义字段', link: '/features/custom-fields' },
            { text: '工作流', link: '/features/workflow' },
            { text: '通知', link: '/features/notifications' },
          ],
        },
      ],
      '/admin/': [
        {
          text: '系统管理',
          items: [
            { text: '概览', link: '/admin/overview' },
            { text: '用户与角色', link: '/admin/users-roles' },
            { text: '组织管理', link: '/admin/organization' },
            { text: '用户组', link: '/admin/groups' },
            { text: '自定义字段管理', link: '/admin/custom-fields' },
            { text: '工作流管理', link: '/admin/workflow' },
            { text: '工单属性', link: '/admin/work-item-attributes' },
            { text: 'Webhook 集成', link: '/admin/webhooks' },
            { text: '通知管理', link: '/admin/notifications' },
            { text: '审计日志', link: '/admin/audit-log' },
            { text: 'API Key', link: '/admin/api-keys' },
          ],
        },
      ],
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/cwx2/trackflow' },
    ],

    footer: {
      message: 'Released under the MIT License.',
      copyright: 'Copyright © 2026 TrackFlow Contributors',
    },

    search: {
      provider: 'local',
    },

    editLink: {
      pattern: 'https://github.com/cwx2/trackflow/edit/main/docs-site/docs/:path',
      text: '在 GitHub 上编辑此页',
    },

    lastUpdated: {
      text: '最后更新于',
    },
  },

  markdown: {
    lineNumbers: true,
  },
})
