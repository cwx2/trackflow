# 角色权限速查表（严格对应后台权限矩阵）

此文件是所有角色工作流的权限参考基准。每个工作流文件必须严格按照此表验证权限边界。

---

## 完整权限矩阵

| 权限点 | system_admin | project_admin | tech_lead | developer | product_manager | tester | observer |
|--------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| system:admin（后台管理） | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| project:create（创建项目） | ✅(via admin) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| project:edit（编辑项目） | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| project:delete（删除项目） | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| project:view（查看项目） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| project:manage_members（管理成员） | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| project:manage_workflow（配置工作流） | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| project:manage_custom_fields（自定义字段） | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| issue:create（创建工单） | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| issue:view（查看工单） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| issue:edit（编辑工单内容） | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| issue:delete（删除工单） | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| issue:assign（分配负责人） | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| issue:change_status（修改状态） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| issue:comment（添加评论） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| issue:manage_attachments（管理附件） | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| sprint:create（创建Sprint） | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ |
| sprint:edit（编辑Sprint） | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ |
| sprint:delete（删除Sprint） | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| sprint:view（查看Sprint） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| query:create（创建Saved Query） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| query:share（分享Query给团队） | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ |
| query:manage_public（管理公共Query） | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| report:view（查看报表） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| report:create（创建报表） | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ |
| webhook:manage（管理Webhook） | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

---

## 用户账号与项目角色

密码统一：`test123`

### DE4 项目成员（12人）

| 用户名 | 姓名 | DE4角色 | 说明 |
|--------|------|---------|------|
| testuser | Test User | 项目管理员 | 同时是系统管理员 |
| lina | 李娜 | 项目管理员 | 同时是系统管理员 |
| zhangwei | 张伟 | 技术负责人 | FE1中是开发人员 |
| zhoujie | 周杰 | 技术负责人 | 仅在DE4 |
| wangqiang | 王强 | 开发人员 | FE1中也是开发人员 |
| liuyang | 刘洋 | 开发人员 | FE1中是技术负责人（跨项目权限差异） |
| sunlei | 孙磊 | 产品经理 | FE1中也是产品经理 |
| yangmin | 杨敏 | 产品经理 | 仅在DE4 |
| zhaojing | 赵静 | 测试人员 | FE1中也是测试人员 |
| chenfei | 陈飞 | 测试人员 | 仅在DE4 |
| huanglei | 黄磊 | 观察者 | FE1中也是观察者 |
| wumin | 吴敏 | 观察者 | 仅在DE4 |

### FE1 项目成员（7人）

| 用户名 | 姓名 | FE1角色 |
|--------|------|---------|
| testuser | Test User | 项目管理员 |
| liuyang | 刘洋 | 技术负责人 |
| zhangwei | 张伟 | 开发人员 |
| wangqiang | 王强 | 开发人员 |
| sunlei | 孙磊 | 产品经理 |
| zhaojing | 赵静 | 测试人员 |
| huanglei | 黄磊 | 观察者 |

---

## 关键权限边界（最容易出错的地方）

### 测试人员（tester）的限制
- ✅ 可以：查看工单、添加评论、修改状态（只能在工作流允许的转换范围内）
- ❌ 不能：创建工单、编辑工单内容、删除工单、分配负责人、管理附件、创建/编辑Sprint

### 开发人员（developer）的限制
- ✅ 可以：创建工单、编辑工单、修改状态、分配负责人、管理附件
- ❌ 不能：删除工单、创建Sprint、分享Query、管理项目/成员/工作流

### 产品经理（product_manager）的限制
- ✅ 可以：创建工单、编辑工单、修改状态、创建/编辑Sprint、创建报表、分享Query
- ❌ 不能：删除工单、分配负责人、删除Sprint、管理成员/工作流/自定义字段

### 观察者（observer）的限制
- ✅ 可以：查看所有内容、添加评论、创建个人Saved Query
- ❌ 不能：创建工单、编辑工单、修改状态、删除工单、分配负责人、管理附件、Sprint相关操作（除查看）、分享Query

### 技术负责人与开发人员的差异
- tech_lead 比 developer 多了：issue:delete、sprint:create、sprint:edit、query:share
- tech_lead 没有：project:manage_members、project:manage_workflow 等项目级管理权
