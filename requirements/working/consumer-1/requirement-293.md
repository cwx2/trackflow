# REQ-293：自定义字段缺少「构建号(build)」和「用户组(group)」两种类型

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-293 |
| 标题 | 自定义字段缺少「构建号(build)」和「用户组(group)」两种 YouTrack 标准类型 |
| 类型 | 功能缺失 |
| 严重程度 | P2 |
| 发现方式 | YouTrack 对标 |
| 发现日期 | 2026-08-06 |
| 关联模块 | 自定义字段模块（前端 + 后端） |
| 对标文档 | https://www.jetbrains.com/help/youtrack/server/2025.3/supported-custom-field-types.html |
| 状态 | 已修复 |

## 1. YouTrack 标准行为

YouTrack 自定义字段支持 14 种类型，其中 TrackFlow 尚未实现的有：

### `build` 类型
- 存储构建号（Build Number）列表，每个构建值有：
  - `name`：构建号名称（如 `2024.1.1234`）
  - `assembleDate`：构建生成日期（`date` 类型）
- 典型用途：`Fixed in build`（修复版本的构建号）、`Affected build`（受影响的构建号）
- 与 `version` 类型相似，但侧重 CI/CD 构建产物，无 startDate/released 概念
- 可通过 YouTrack REST API 或 TeamCity 集成自动追加新构建值

### `group` 类型
- 存储对系统用户组的引用
- 与 `user` 类型类似，但选择的是用户组而非单个用户
- 可用于「负责团队」「通知组」等场景
- 下拉值来自系统用户组列表，受 `Read Group` 权限控制
- 支持单值和多值

## 2. TrackFlow 当前实现

TrackFlow 前端 `fieldTypeOptions` 数组共 13 个类型，**没有 `build` 和 `group`**：

```typescript
const fieldTypeOptions = [
  { value: 'string', label: '文本(单行)' },
  { value: 'text', label: '文本(多行/Markdown)' },
  { value: 'int', label: '整数' },
  { value: 'float', label: '小数' },
  { value: 'date', label: '日期' },
  { value: 'datetime', label: '日期时间' },
  { value: 'bool', label: '布尔' },
  { value: 'list', label: '列表(枚举)' },
  { value: 'ownedField', label: '子系统(Owned Field)' },
  { value: 'state', label: '状态(State)' },
  { value: 'user', label: '用户' },
  { value: 'period', label: '时间周期' },
  { value: 'version', label: '版本(Version)' }
  // ❌ 缺少 build、group
]
```

后端 `CustomFieldHandlerRegistry.java` 中也未注册 `BuildFieldHandler` 和 `GroupFieldHandler`。

## 3. 差异分析

| 类型 | YouTrack | TrackFlow | 差距说明 |
|------|----------|-----------|----------|
| `build` | ✅ 支持，带 assembleDate | ❌ 缺失 | 与 CI/CD 集成的关键类型 |
| `group` | ✅ 支持，引用用户组 | ❌ 缺失 | 团队级字段分配 |

## 4. 期望结果

### `build` 类型实现

**后端：**
- 新建 `BuildFieldHandler.java` 注册到 `CustomFieldHandlerRegistry`
- `build` 类型的选项额外存储 `assembleDate`（日期类型）
- 工单字段值存储选中的构建值 ID

**前端（`CustomFieldManage.vue`）：**
- `fieldTypeOptions` 新增 `{ value: 'build', label: '构建号(Build)' }`
- `build` 类型选项编辑行，新增 `assembleDate` 日期选择器（可选填）
- 工单详情页字段面板：`build` 字段显示为下拉选择，选项格式为「构建号（日期）」

### `group` 类型实现

**后端：**
- 新建 `GroupFieldHandler.java`
- 字段值引用 `sys_user_group` 表（需确认是否存在用户组表，如不存在需评估）
- 权限：只有有 `Read Group` 权限的用户才能看到对应的组选项

**前端：**
- `fieldTypeOptions` 新增 `{ value: 'group', label: '用户组(Group)' }`
- 工单详情页字段面板：`group` 字段显示为用户组下拉选择器
- 支持单值和多值配置（与 `user` 类型保持一致）

## 5. 验收标准

### build 类型
- [ ] 创建字段时可选「构建号(Build)」类型
- [ ] 构建号选项可设置 `assembleDate`（生成日期，可选）
- [ ] 工单详情页 `build` 字段显示为下拉选择
- [ ] 构建值可归档，归档后不显示在下拉列表中

### group 类型
- [ ] 创建字段时可选「用户组(Group)」类型（前提：系统有用户组功能）
- [ ] 工单详情页 `group` 字段显示用户组下拉
- [ ] 支持单值/多值配置

## 6. 注意事项

- `group` 类型依赖系统用户组（Group）功能。**如果 TrackFlow 尚未实现用户组管理，`group` 类型应在用户组功能完成后再实现**，可先实现 `build` 类型。
- `build` 类型优先级高于 `group`，因为构建号是软件研发项目中最常见的跟踪维度之一。

## 7. 相关文件

**后端（新建）：**
- `trackflow-server/src/main/java/com/trackflow/customfield/handler/BuildFieldHandler.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/handler/GroupFieldHandler.java`

**前端（修改）：**
- `trackflow-web/src/views/admin/CustomFieldManage.vue`（fieldTypeOptions 新增选项）
- 工单详情页字段渲染（build/group 类型的 widget 组件）


## 自动化状态

fix_status: DONE
fix_commit: ac75e509
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-06 20:58

### 本次改动摘要
- 新建 `BuildFieldHandler.java` — 继承 ListFieldHandler，注册 fieldFormat="build"，typeLabel="构建号"
- 新建 `GroupFieldHandler.java` — 独立实现，校验 user_group 表存在性，通过 DisplayContext.groupNameMap 解析组名
- 扩展 `DisplayContext` record — 新增 groupNameMap 字段（3参数），保留 2 参数向后兼容
- `V269__add_assemble_date_to_custom_field_option.sql` — 为 custom_field_option 表加 assemble_date DATE 列
- Entity/VO/DTO 均增加 assembleDate 字段
- `CustomFieldOptionService` — isEnumLikeFormat 加入 "build"，addOptionInline 签名增加 assembleDate 参数，copy/update/insert 路径均传递 assembleDate
- `CustomFieldTypeConversionService` — 添加 build→list/string 和 group→(无转换) 规则
- `CustomFieldDisplayService` — buildSingleQueryContext 增加 group 分支查 user_group；新增 3 参数 resolveDisplayValue 重载
- 前端 `CustomFieldManage.vue` — fieldTypeOptions 加 build/group；选项条件加 build；build 选项行有 assembleDate 日期选择器和按构建日期排序按钮
- 前端 `FieldsInProjects.vue` — fieldTypeOptions + fieldTypeLabels 加 build/group
- 前端 `IssueCreatePanel.vue` — enum select 条件加 build；新增 group 类型 select 组件（加载 allUserGroups）
- 前端 `IssueDetailView.vue` — sidebar switch 加 build(select)/group(select)；加 allUserGroups 懒加载
- 前端 `QueryInput.vue` — filter options 条件加 build
- 前端 API types — CustomFieldOptionVO 加 assembleDate；create/update/addOption 方法签名扩展

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/customfield/handler/BuildFieldHandler.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/handler/GroupFieldHandler.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/handler/DisplayContext.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/entity/CustomFieldOption.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/vo/CustomFieldOptionVO.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/dto/AddOptionDTO.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/dto/CreateCustomFieldDTO.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/dto/UpdateCustomFieldDTO.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldOptionService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldDisplayService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldTypeConversionService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/controller/CustomFieldController.java`
- `trackflow-server/src/main/resources/db/migration/V269__add_assemble_date_to_custom_field_option.sql`
- `trackflow-web/src/api/customField.ts`
- `trackflow-web/src/api/types/customField.ts`
- `trackflow-web/src/views/admin/CustomFieldManage.vue`
- `trackflow-web/src/views/admin/FieldsInProjects.vue`
- `trackflow-web/src/views/issue/IssueCreatePanel.vue`
- `trackflow-web/src/views/issue/IssueDetailView.vue`
- `trackflow-web/src/views/issue/components/QueryInput.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 管理后台 → 自定义字段 → 新建字段 → 选择"构建号(Build)"类型 → 添加若干选项并设置 assembleDate → 保存成功
  2. 以 testuser 登录 → 管理后台 → 自定义字段 → 新建字段 → 选择"用户组(Group)"类型 → 保存成功
  3. 确认字段列表页 fieldTypeOptions 下拉中可见"构建号(Build)"和"用户组(Group)"
  4. 编辑已创建的 build 字段 → 选项行应出现"构建日期"日期选择器
  5. 编辑已创建的 build 字段 → "按构建日期"排序按钮可用
- **边界场景**：
  - build 选项不填 assembleDate 应该也能保存
  - group 类型在系统有用户组的前提下才有下拉选项
- **建议测试账号**：testuser（system_admin）
- **注意事项**：后端需重启以执行 V269 迁移脚本

### 审核重点（给 code-review 会话）
- **重点关注文件**：BuildFieldHandler.java, GroupFieldHandler.java, DisplayContext.java, CustomFieldOptionService.java
- **潜在风险点**：DisplayContext record 增加第 3 个字段，确认所有现有调用站仍编译通过（已提供 2 参数兼容 overload）
- **已知遗留项**：group 类型在批量展示场景（列表页）暂未预加载 groupNameMap，单条解析（活动日志）已支持。batch preload 可在 group 字段实际使用后再优化。

======================

## 修复记录

**修复日期**：2026-08-06
**修复人**：AI Agent（auto 模式）

### 根因分析
TrackFlow 自定义字段系统在对标 YouTrack 14 种标准类型时缺少 `build`（构建号）和 `group`（用户组）两种类型。架构上已有策略模式（Handler + Registry 自动发现），新增类型只需创建 @Component 实现即可注册。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `BuildFieldHandler.java` | 新建，继承 ListFieldHandler，fieldFormat="build" |
| `GroupFieldHandler.java` | 新建，独立实现，校验 user_group 表 |
| `V269__add_assemble_date_to_custom_field_option.sql` | 新增 assemble_date DATE 列 |
| `DisplayContext.java` | 增加 groupNameMap 字段和 3 参数工厂方法 |
| `CustomFieldOption.java` + VO + DTO | 增加 assembleDate 字段 |
| `CustomFieldOptionService.java` | isEnumLikeFormat 加 build，所有 option CRUD 路径传递 assembleDate |
| `CustomFieldTypeConversionService.java` | 添加 build/group 转换规则 |
| `CustomFieldDisplayService.java` | group 单条解析支持，resolveDisplayValue 兼容重载 |
| 前端 admin 管理页 | fieldTypeOptions 加两种类型，build 选项行加 assembleDate |
| 前端 Issue 创建/详情页 | build 加入 enum-like select 条件，group 加入独立 select 组件 |

### 影响范围
- 自定义字段管理页面（管理员）
- 工单创建面板
- 工单详情页侧边栏
- 查询筛选器
