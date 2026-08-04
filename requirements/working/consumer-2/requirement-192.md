# REQ-192：状态（State）字段应统一接入自定义字段管理体系

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-192 |
| 标题 | 状态（State）字段应统一接入自定义字段管理体系 |
| 类型 | 架构差距 |
| 严重程度 | P1 |
| 发现方式 | 文档对标 + 代码分析 |
| 发现日期 | 2026-08-04 |
| 关联模块 | issue、customfield、project/settings、workflow |
| 对标文档 | https://www.jetbrains.com/help/youtrack/server/default-custom-fields.html https://www.jetbrains.com/help/youtrack/server/supported-custom-field-types.html |
| 状态 | 已修复 |

---

## 1. YouTrack 标准行为

YouTrack 中 State 是 **`state` 类型自定义字段**，是所有新建项目默认附加的核心字段：

> "State: Used to manage the lifecycle of an issue. The set of values that are used for the State field has an additional Resolved property."

### 1.1 `state` 类型的特殊之处

`state` 是 `enum` 的特殊子类型，每个值除了名称/颜色/描述外，还有：

| 属性 | 类型 | 说明 |
|------|------|------|
| `isResolved` | Boolean | **最关键属性**：决定该状态是否视为"已解决"。已解决的工单在列表中显示为删除线+变灰，影响 Sprint 统计、报表等所有地方 |

### 1.2 多 state 字段的注意事项

YouTrack 文档特别警告：
> "We strongly advise against using more than one state field in a project. Your issues are not resolved until **all** state-type fields are assigned resolved values."

即：工单被视为"已解决"需要所有 state 字段的值都是 resolved 状态。

### 1.3 项目层面的配置能力

State 字段和其他枚举字段一样，在项目设置中可配置：
- 各项目可创建独立的状态值集合
- 可增删状态值（每个值设置名称、颜色、描述、isResolved）
- 可拖拽调整状态顺序
- 可设置默认状态值
- 状态之间的转换规则通过工作流（Workflow）管理
- Default 模板的默认 state 值：
  - 未解决：Submitted / Open / In Progress / To be discussed / Reopened
  - 已解决：Can't Reproduce / Duplicate / Fixed / Won't fix / Incomplete / Obsolete / Verified
- Scrum 模板默认 state 值：
  - 未解决：Open / In Progress / To Verify
  - 已解决：Done / Duplicate

---

## 2. TrackFlow 当前实现

### 2.1 存储方式

状态通过 `issue_status` 表单独管理，`issue.statusId` 是外键：

```java
private Long statusId;  // 外键 → issue_status.id
```

`issue_status` 表有 `isResolved`/`isClosed` 等属性，功能上等价。

### 2.2 现有能力

- `issue_status` 表存储状态定义（name、color、category、isResolved）
- 工作流系统（`workflow_transition`）管理状态之间的转换规则
- 项目设置 → 工作流 页面可以配置状态和转换

### 2.3 问题所在

`issue_status` 是**独立于自定义字段体系**的：
- 状态字段不出现在"自定义字段管理"界面
- 无法对状态字段使用 CF 体系的通用能力：
  - 条件显示（State 字段在某条件下才显示）
  - 组级可见性（只有特定角色可以修改状态）
  - 字段顺序调整（在工单侧边栏中的显示位置）
  - 别名（用于搜索命令简写）
- 管理入口分散：状态在"工作流"页面，自定义字段在另一个页面，不统一

---

## 3. 期望结果

### 3.1 核心目标

将 State 字段**注册到自定义字段体系的管理界面**，实现统一入口，同时保留现有工作流引擎的转换逻辑。

**注意**：不需要将 `issue_status` 物理迁移到 `custom_field_value` 表，只需在管理界面层面统一展示和配置。

### 3.2 具体要求

1. **在自定义字段体系中注册 State 字段**
   - `custom_field_definition` 中创建 State 字段定义（`fieldFormat=state`）
   - 标记为"内置字段"（不可删除、不可修改类型）
   - 实际数据仍存储在 `issue_status` 表（不物理迁移）

2. **`state` 类型字段支持**
   - `custom_field_option` 或桥接表中，state 类型的值有 `isResolved` 属性
   - UI 中创建/编辑 state 类型值时显示 `isResolved` 开关

3. **项目层面配置（优先级最高的部分）**
   - 项目设置 → 自定义字段中可以看到 State 字段
   - 可设置组级可见性（哪些角色可以修改状态）
   - 可设置字段在侧边栏中的显示顺序
   - 可设置别名（用于搜索查询，如 `#State: Open` → `#s: Open`）

4. **保持现有工作流引擎不变**
   - 状态值的增删、转换规则继续由"项目设置 → 工作流"管理
   - 不破坏现有工作流引擎

---

## 4. 验收标准

- [ ] 自定义字段管理界面中可以看到 State 字段（标注为内置字段）
- [ ] State 字段支持设置组级可见性（限定哪些角色能改状态）
- [ ] State 字段在工单侧边栏中的显示顺序可以调整
- [ ] 新增 `state` 类型支持，创建 state 类型字段时可为每个值设置 `isResolved`
- [ ] 现有工作流功能不受影响

---

## 5. 备注

- 这是三个 P1 需求里最复杂的，涉及工作流引擎，需要仔细设计桥接层
- 短期实现目标是"管理界面统一入口"，不要求物理迁移数据存储
- `isResolved` 逻辑继续由 `issue_status.isResolved` 字段驱动，自定义字段体系只是透传展示


## 自动化状态

fix_status: DONE
fix_commit: 8bdc725
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 02:10

### 本次改动摘要
将 State（状态）字段注册到 TrackFlow 的自定义字段管理体系中，使其在管理界面统一展示，同时保留现有工作流引擎不变。

- V253 迁移脚本：添加 `is_resolved` 列到 `custom_field_option`，更新 CHECK 约束加入 `state` 类型，注册 State 内置字段（ID=1000000000000000004），从 `issue_status` 同步选项到 `custom_field_option`，将字段附加到所有现有项目
- `CustomFieldOption` 实体：新增 `isResolved` 字段
- `CustomFieldOptionVO`：新增 `isResolved` 字段
- `CreateCustomFieldDTO.OptionItem` / `UpdateCustomFieldDTO.OptionItem`：新增 `isResolved` 字段
- `CustomFieldService`：`BUILTIN_FIELD_IDS` 新增 ID 4（State），`STATE_FIELD_ID` 常量，`listEnumFields()` 使用 `IN ('list','state')` 查询，创建/更新逻辑用 `isEnumLikeFormat` 判断
- `CustomFieldOptionService`：新增 `isEnumLikeFormat()` 静态方法，所有原来的 `"list".equals` 替换为此方法，选项更新/插入支持 `isResolved`
- `CustomFieldDisplayService`：选项预加载和解析均支持 state 类型
- `CustomFieldValidationEngine`：`SUPPORTED_FORMATS` 和 switch 都增加 state
- `CustomFieldSortHelper`：`SORTABLE_FIELD_FORMATS` 和排序 SQL 都增加 state
- `CustomFieldReplacementService`/`CustomFieldValueService`：`isEnumLikeFormat` 替换
- 前端 `types.ts`：`CustomFieldOptionVO` 新增 `isResolved` 可选字段
- 前端 `CustomFieldManage.vue`：`fieldTypeOptions` 新增 state 类型，选项列表显示/编辑模板支持 state 格式，选项 isResolved checkbox
- 前端 `ProjectSettingsCustomFields.vue`：`fieldTypeMap` 新增 state/period，条件字段/默认值选择器支持 state

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/customfield/dto/CreateCustomFieldDTO.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/dto/UpdateCustomFieldDTO.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/entity/CustomFieldOption.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldDisplayService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldOptionService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldReplacementService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldSortHelper.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldValidationEngine.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldValueService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/vo/CustomFieldOptionVO.java`
- `trackflow-server/src/main/resources/db/migration/V253__register_state_custom_field.sql`
- `trackflow-web/src/api/types.ts`
- `trackflow-web/src/views/admin/CustomFieldManage.vue`
- `trackflow-web/src/views/project/settings/ProjectSettingsCustomFields.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 管理后台 → 自定义字段管理 → 验证 "State" 字段出现在列表中，标注"内置"标签，类型显示"状态(State)"
  2. 点击 State 字段 → 验证侧边栏/抽屉显示选项列表（Open, In Progress, Code Review, Testing, Done 等），每个选项有颜色和"已解决"勾选状态
  3. 进入项目设置 → 自定义字段 → 验证 State 字段出现在字段列表中，可查看/配置可见性和显示顺序
  4. 验证 State 字段不可删除（内置字段保护）
- **边界场景**：
  - State 字段的"已解决"状态应与 issue_status.is_closed 值一致（Done/Cancelled 等应为 true）
  - 内置字段不可改名验证
- **建议测试账号**：超级管理员 testuser
- **注意事项**：需要重启后端以执行 V253 迁移脚本

### 审核重点（给 code-review 会话）
- **重点关注文件**：CustomFieldOptionService.java（isEnumLikeFormat helper + isResolved 处理）、V253 迁移脚本（数据同步逻辑）
- **潜在风险点**：state 类型字段的 custom_field_value 数据仍通过 issue.status_id 管理，不会写入 custom_field_value 表；确保不会有逻辑试图通过 CF value service 写入 state 字段值
- **已知遗留项**：State 字段的实际状态值变更仍通过工作流引擎（issue.status_id），CF 体系仅做管理入口统一展示；未来可能需要添加 State 字段在工单侧边栏与工作流的桥接交互

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
State（状态）字段独立于自定义字段体系管理，管理入口分散（状态在"工作流"页面，自定义字段在另一个页面）。需要在 custom_field_definition 中注册 State 为内置字段，使其出现在统一的自定义字段管理界面中。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `V253__register_state_custom_field.sql` | 新增迁移：添加 is_resolved 列、更新 CHECK 约束、注册 State 字段、同步 issue_status 选项、附加到所有项目 |
| `CustomFieldOption.java` | 新增 isResolved 字段 |
| `CustomFieldOptionVO.java` | 新增 isResolved 字段 |
| `CreateCustomFieldDTO.java` / `UpdateCustomFieldDTO.java` | OptionItem 新增 isResolved |
| `CustomFieldService.java` | BUILTIN_FIELD_IDS 新增 ID 4，STATE_FIELD_ID 常量，listEnumFields 支持 state |
| `CustomFieldOptionService.java` | 新增 isEnumLikeFormat() 静态方法，替换所有 "list".equals 检查，选项 CRUD 支持 isResolved |
| `CustomFieldDisplayService.java` | 选项预加载和解析支持 state 类型 |
| `CustomFieldValidationEngine.java` | SUPPORTED_FORMATS 和 switch 增加 state |
| `CustomFieldSortHelper.java` | SORTABLE_FIELD_FORMATS 和排序 SQL 增加 state |
| `CustomFieldReplacementService.java` / `CustomFieldValueService.java` | isEnumLikeFormat 替换 |
| `types.ts` | CustomFieldOptionVO 新增 isResolved |
| `CustomFieldManage.vue` | fieldTypeOptions 新增 state，选项列表/编辑支持 state + isResolved |
| `ProjectSettingsCustomFields.vue` | fieldTypeMap 新增 state/period，条件字段支持 state |

### 影响范围
- 自定义字段管理页面（管理后台）
- 项目设置 → 自定义字段页面
- 所有使用 CustomFieldOptionService 的选项操作接口
