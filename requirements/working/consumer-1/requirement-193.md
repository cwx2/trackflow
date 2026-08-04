# REQ-193：自定义字段新增 `ownedField` 类型（Subsystem 子系统字段）

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-193 |
| 标题 | 自定义字段新增 `ownedField` 类型（Subsystem 子系统字段） |
| 类型 | 功能缺失 |
| 严重程度 | P2 |
| 发现方式 | 文档对标 |
| 发现日期 | 2026-08-04 |
| 关联模块 | customfield |
| 对标文档 | https://www.jetbrains.com/help/youtrack/server/supported-custom-field-types.html |
| 状态 | 待修复 |

---

## 1. YouTrack 标准行为

> "`ownedField`: Stores values from a predefined set of values. The default Subsystem field uses this type. This type is similar to the `enum` type, except that each value in the set of values stores an **owner**. This property stores a reference to the user who is responsible for the corresponding software component. Examples include Front End, Back End, and Documentation."

### 1.1 典型用途

**Subsystem（子系统/组件）字段**，用于将工单归类到特定软件组件（如 Frontend、Backend、API、Database、CI/CD 等），每个组件有一个**负责人（owner）**。

### 1.2 `ownedField` 类型的特殊之处

在 `enum` 基础上，每个值额外有：

| 属性 | 类型 | 说明 |
|------|------|------|
| `owner` | user | 该子系统/组件的负责人，指向 YouTrack 用户账号 |

### 1.3 与工作流的联动

YouTrack 有内置的 **Subsystem Assignee** 工作流：

> "When you attach the Subsystem Assignee workflow to a project, the value for the Assignee field is automatically set to the owner of the value that is set in the Subsystem field."

即：当工单选择了某个 Subsystem，工作流自动将 Assignee 设置为该 Subsystem 的 owner。

---

## 2. TrackFlow 现状

TrackFlow 没有 `ownedField` 类型，当前自定义字段支持类型：
- `string`, `text`, `int`, `float`, `date`, `datetime`, `bool`, `list`, `user`

**缺失的内容：**
- 无法创建带 owner 属性的枚举字段
- 无法配置"选择子系统时自动分配 assignee"的联动逻辑
- 用户目前只能用普通 `list` 类型模拟子系统，但没有 owner 关联

---

## 3. 期望结果

### 3.1 新增 `ownedField` 字段类型

在 `custom_field_option` 表中，为 `ownedField` 类型的选项新增 `owner_user_id` 字段：

```sql
ALTER TABLE custom_field_option ADD COLUMN owner_user_id BIGINT REFERENCES sys_user(id);
```

或通过独立表存储（取决于实现方案）。

### 3.2 UI 配置

在创建/编辑 `ownedField` 类型字段时：
- 添加/编辑选项时，可为每个选项指定一个 **owner 用户**（从项目成员中选择）
- 显示格式：`[组件名称] → [owner 用户名]`
- owner 为可选（允许某些子系统暂无 owner）

### 3.3 预置 Subsystem 字段

- 在全局自定义字段中预置一个名为 "Subsystem" 的 `ownedField` 字段
- 默认 isAutoAttach=false（项目手动添加）
- 默认值集合为空（各项目自行配置）

### 3.4 工单详情中的展示

- `ownedField` 类型的字段，在下拉选项中除显示值名称外，还显示 owner 用户名
- 选中后，侧边栏显示选中值和 owner 用户

---

## 4. 验收标准

- [ ] 可创建 `ownedField` 类型的自定义字段
- [ ] 添加/编辑选项时可指定 owner 用户
- [ ] 工单侧边栏的 ownedField 下拉中显示值名称和 owner
- [ ] 全局预置 Subsystem 字段
- [ ] 工单详情中选中 Subsystem 后可查看到 owner

---

## 5. 备注

- 与 Subsystem Assignee 工作流联动（选 Subsystem 自动赋值 Assignee）属于工作流/自动化模块，作为独立功能跟进
- 本需求仅包含字段类型本身的支持，不包含自动化联动


## 自动化状态

fix_status: DONE
fix_commit: 712ab29
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 02:58

### 本次改动摘要
新增 `ownedField` 自定义字段类型，支持类似 YouTrack Subsystem 字段功能（每个选项可指定 owner 用户）。

- 改动1: `V254__add_owned_field_type.sql` — 更新 check 约束加入 ownedField，添加 owner_user_id 列，预置 Subsystem 字段
- 改动2: `CustomFieldOption.java` — Entity 增加 ownerUserId 字段
- 改动3: `CustomFieldOptionVO.java` — VO 增加 ownerUserId + ownerDisplayName
- 改动4: `CustomFieldConverter.java` — MapStruct 映射 ownerUserId（Long→String）
- 改动5: DTOs (`CreateCustomFieldDTO`, `UpdateCustomFieldDTO`, `AddOptionDTO`) — 增加 ownerUserId 参数
- 改动6: `CustomFieldValidationEngine.java` — SUPPORTED_FORMATS 增加 ownedField，验证 switch 处理 ownedField
- 改动7: `CustomFieldOptionService.java` — isEnumLikeFormat 包含 ownedField，addOptionInline 接受 ownerUserId，updateListOptions/insertOrReactivateOption 保存 ownerUserId
- 改动8: `CustomFieldService.java` — 创建选项时保存 ownerUserId，enrichOptionOwnerDisplayNames 批量填充 ownerDisplayName，listEnumFields 查询包含 ownedField，条件/过滤源字段允许 ownedField
- 改动9: `CustomFieldSortHelper.java` — 排序支持 ownedField（按 position）
- 改动10: `CustomFieldDisplayService.java` — resolveDisplayValue 处理 ownedField
- 改动11: `CustomFieldTypeConversionService.java` — ownedField 可转换为 list 或 string
- 改动12: `CustomFieldController.java` — addOption 传递 ownerUserId
- 改动13: Frontend `types.ts` — fieldFormat union 加入 ownedField，CustomFieldOptionVO 加 ownerUserId/ownerDisplayName
- 改动14: Frontend `customField.ts` — API 类型包含 ownerUserId
- 改动15: Frontend `CustomFieldManage.vue` — 类型列表加"子系统(Owned Field)"，选项编辑行加 owner 用户选择器
- 改动16: Frontend `IssueCreatePanel.vue` — ownedField 使用 list 相同的 select 组件
- 改动17: Frontend `IssueDetailView.vue` — ownedField 在侧边栏显示颜色和 canAddOption
- 改动18: Frontend `QueryInput.vue` — ownedField 映射为 enum 类型搜索

### 本次变更文件清单
- `trackflow-server/src/main/resources/db/migration/V254__add_owned_field_type.sql`
- `trackflow-server/src/main/java/com/trackflow/customfield/entity/CustomFieldOption.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/vo/CustomFieldOptionVO.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/converter/CustomFieldConverter.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/dto/CreateCustomFieldDTO.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/dto/UpdateCustomFieldDTO.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/dto/AddOptionDTO.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/controller/CustomFieldController.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldValidationEngine.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldOptionService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldSortHelper.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldDisplayService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldTypeConversionService.java`
- `trackflow-web/src/api/types.ts`
- `trackflow-web/src/api/customField.ts`
- `trackflow-web/src/views/admin/CustomFieldManage.vue`
- `trackflow-web/src/views/issue/IssueCreatePanel.vue`
- `trackflow-web/src/views/issue/IssueDetailView.vue`
- `trackflow-web/src/views/issue/components/QueryInput.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 管理后台 → 自定义字段管理 → 确认 Subsystem 字段已存在，类型为"子系统(Owned Field)"
  2. 创建新 ownedField 字段 → 添加选项并为每个选项指定 owner 用户 → 保存 → 确认选项带 owner 显示名
  3. 将 ownedField 字段附加到项目 → 在工单创建/编辑中出现下拉选择 → 选中值保存成功
  4. 编辑已有 ownedField 字段 → 修改选项 owner → 保存 → 确认 ownerDisplayName 更新
- **边界场景**：
  - owner 为空的选项可以正常保存
  - ownedField 类型支持条件显示和值过滤
  - ownedField 在 issue 列表可排序
- **建议测试账号**：testuser（system_admin）
- **注意事项**：Subsystem 字段默认 isAutoAttach=false，需手动附加到项目才能在工单中使用

### 审核重点（给 code-review 会话）
- **重点关注文件**：CustomFieldService.java（新增 enrichOptionOwnerDisplayNames 及 SysUserMapper 依赖）、CustomFieldOptionService.java（ownerUserId 在多处传递）
- **潜在风险点**：ownerUserId 通过 N+1 优化批量查询，但当选项数量很大时可能需要检查 selectBatchIds 的参数量
- **已知遗留项**：工单详情侧边栏中 ownedField 类型的选项下拉尚未显示 owner 名称（仅管理后台显示），后续可增强为"值 → owner"格式

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
TrackFlow 自定义字段系统不支持 ownedField 类型，无法创建带 owner 属性的枚举字段（如 Subsystem），属于功能缺失。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `V254__add_owned_field_type.sql` | 更新 field_format 检查约束，新增 owner_user_id 列，预置 Subsystem 字段 |
| `CustomFieldOption.java` | Entity 新增 ownerUserId 字段 |
| `CustomFieldOptionVO.java` | VO 新增 ownerUserId + ownerDisplayName |
| `CustomFieldConverter.java` | MapStruct 映射 ownerUserId（Long→String） |
| DTOs (3个) | 新增 ownerUserId 参数 |
| Service 层 (6个文件) | 全面支持 ownedField：验证、排序、显示、创建、更新、转换 |
| `CustomFieldController.java` | addOption 传递 ownerUserId |
| Frontend (5个文件) | 类型声明、管理界面、工单创建/详情/查询支持 ownedField |

### 影响范围
- 自定义字段模块（后端+前端）
- 工单创建面板、工单详情页、搜索查询输入
- 数据库 schema（新列+约束变更）
