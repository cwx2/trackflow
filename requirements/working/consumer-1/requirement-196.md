# REQ-196：自定义字段全局管理界面完善

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-196 |
| 标题 | 自定义字段全局管理界面完善 |
| 类型 | 功能缺失 |
| 严重程度 | P2 |
| 发现方式 | 文档对标 |
| 发现日期 | 2026-08-04 |
| 关联模块 | admin/CustomFieldManage |
| 对标文档 | https://www.jetbrains.com/help/youtrack/server/custom-fields.html |
| 状态 | 已修复 |

---

## 1. YouTrack 标准行为

YouTrack 的 `Administration → Custom Fields → Fields List` 页面：

[![Custom Fields page showing global field types, defaults, values, and project usage.](file:///D:/project/YT/youtrack-docs/images/8aac5d3c4c5c.png)](https://resources.jetbrains.com/help/img/youtrack/2026.2/global-custom-field-settings.png)

全局字段列表提供以下能力：

| 功能 | 说明 |
|------|------|
| 字段列表展示字段类型 | 每行显示字段名称、类型、是否 auto-attach、被多少项目使用 |
| 查看字段被哪些项目使用 | 点击字段可查看该字段在哪些项目中已附加 |
| 字段详情侧边栏 | 点击字段打开侧边栏，显示字段的完整属性和值集合 |
| 筛选/搜索字段 | 支持按名称搜索、按类型筛选 |
| 批量操作 | Enable/Disable auto-attach、Make private/public、Hide/Show in issues list、批量删除 |
| 查看选项使用情况 | 对于 list/enum 类型字段，可查看每个选项被多少工单使用 |
| 值集合合并（Merge with） | 将另一个字段的选项合并到当前字段 |

---

## 2. TrackFlow 现状

TrackFlow 已有 `admin/CustomFieldManage.vue`，已实现：

**已有：**
- 字段列表展示（名称、类型标签）
- 按名称搜索
- 批量操作（auto-attach、私有、隐藏、删除）
- 字段详情侧边栏（编辑字段属性）

**缺失（对照 YouTrack）：**

| 缺失功能 | 说明 |
|------|------|
| 字段列表不显示"被多少项目使用" | YouTrack 中每行显示项目使用数量 |
| 无法按字段类型筛选 | YouTrack 支持按 enum/user/state 等类型过滤 |
| 无法查看某字段被哪些具体项目使用 | YouTrack 点击字段显示关联项目列表 |
| 选项使用情况不可查 | YouTrack 对 enum 类型字段可看每个选项的使用数 |
| 缺少值集合 Merge 操作 | 只有 Copy，没有 Merge |

---

## 3. 期望结果

### 3.1 字段列表增加"使用项目数"列

- 在字段列表增加"使用项目"列，显示该字段被附加到几个项目
- 点击数字可展开查看具体项目列表

**实现方式：** 后端 `GET /api/v1/admin/custom-fields` 接口返回 `usageCount`（关联项目数）

### 3.2 增加类型筛选

- 在字段列表顶部工具栏增加"按类型筛选"下拉
- 选项：全部 / 列表（list）/ 用户（user）/ 文本（string）/ 整数（int）/ 日期（date）/ 日期时间（datetime）/ 数字（float）/ 布尔（bool）/ 富文本（text）

### 3.3 选项使用情况查看

- 对于 list 类型字段，在选项列表旁显示每个选项的使用工单数
- 使用数为 0 的选项可安全删除
- 使用数 > 0 的选项只能归档，不能直接删除

**实现方式：** 后端新增接口 `GET /api/v1/admin/custom-fields/{id}/options/usage`，返回每个选项的工单引用数

### 3.4 值集合 Merge 操作

在选项管理中增加 **Merge with** 按钮：
- 选择另一个同类型字段
- 将该字段的所有活跃选项合并到当前字段
- 重复的选项（名称相同）跳过，不重复添加
- 操作后提示合并结果（新增了几个选项）

---

## 4. 验收标准

- [ ] 字段列表每行显示"使用项目数"，点击可查看具体项目
- [ ] 工具栏增加按类型筛选的下拉选择器
- [ ] list 类型字段的选项旁显示使用工单数
- [ ] 使用数 > 0 的选项只能归档，不能删除（前端禁止，后端校验）
- [ ] 选项管理界面有 "Merge with" 按钮，支持合并另一个字段的值集合

---

## 5. 备注

- "使用项目数"可从 `custom_field_project` 表统计（WHERE `is_excluded=false`）
- "选项使用数"从 `custom_field_value` 表统计（WHERE `field_id=X AND option_id=Y`）
- Merge 操作需要注意：如果源字段已被归档的选项，不应合并过来


## 自动化状态

fix_status: DONE
fix_commit: bb09771
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 04:45

### 本次改动摘要
- 改动1：`CustomFieldManage.vue` — 在字段列表工具栏添加"按类型筛选"下拉选择器（a-select），绑定 `filterFieldFormat` 变量并在变更时重置分页+重新加载列表（后端已支持 fieldFormat 参数过滤）
- 改动2：`CustomFieldManage.vue` — 在编辑抽屉的枚举选项区域添加"合并其他字段值集 (Merge with)"操作区块，包含源字段选择器和合并按钮，调用后端 merge-options 接口并刷新选项列表
- 改动3：`customField.ts` — 新增 `mergeOptions(targetFieldId, sourceFieldId)` API 方法；为 create/update 签名补充 `aliases` 可选字段
- 改动4：`CustomFieldController.java` — 新增 `POST /admin/custom-fields/{id}/merge-options` 端点，权限 system:manage_custom_fields
- 改动5：`CustomFieldOptionService.java` — 新增 `mergeOptionsFromField(targetFieldId, sourceFieldId)` 方法，逻辑：获取源字段活跃非归档全局选项，与目标字段按名称（不区分大小写）去重，新选项追加到末尾
- 改动6：`MergeOptionsDTO.java` / `MergeOptionsResultVO.java` — 新增请求/响应 VO

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/customfield/controller/CustomFieldController.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/dto/MergeOptionsDTO.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldOptionService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/service/CustomFieldService.java`
- `trackflow-server/src/main/java/com/trackflow/customfield/vo/MergeOptionsResultVO.java`
- `trackflow-web/src/api/customField.ts`
- `trackflow-web/src/views/admin/CustomFieldManage.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 进入 管理 → 自定义字段管理 → 确认"按类型筛选"下拉出现在搜索框旁
  2. 选择"列表(枚举)"类型筛选 → 确认列表只显示 list 类型字段
  3. 选择一个 list 类型字段点击"编辑" → 在抽屉中找到"合并其他字段值集 (Merge with)"区块
  4. 在 Merge with 选择器中选择另一个枚举字段 → 点击"合并" → 验证成功提示并且选项列表刷新
  5. 再次合并相同源字段 → 验证提示"所有选项均已存在"（去重生效）
- **边界场景**：
  - 清除类型筛选后恢复全部字段列表
  - 合并源字段没有活跃选项时应返回 0 added
- **建议测试账号**：超级管理员 testuser
- **注意事项**：需要系统中至少有 2 个枚举类型字段才能测试合并

### 审核重点（给 code-review 会话）
- **重点关注文件**：CustomFieldOptionService.java (mergeOptionsFromField 方法)，CustomFieldController.java (新端点)
- **潜在风险点**：merge 操作是写操作，已加 @Transactional；权限注解已配置
- **已知遗留项**：需求中提到的"点击使用项目数可展开查看具体项目"和"选项使用统计在列表中显示"功能前端已有实现（使用项目列显示项目数+tooltip，编辑抽屉中选项旁有使用数），无需额外改动

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
需求中对标 YouTrack 的 5 项缺失功能经分析发现：
- "使用项目数"列 — 已有（前端列表中有"使用项目"列，含 tooltip 显示具体项目名）
- "选项使用情况"— 已有（编辑抽屉中每个选项旁显示引用数）
- "使用数>0不可删除" — 已有（disabled 删除按钮 + Modal 警告）
- "按类型筛选" — 后端已支持 fieldFormat 查询参数，但前端缺少下拉 UI
- "Merge with" — 只有 Copy（本地追加到 form.options），缺少服务端持久化合并

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `CustomFieldManage.vue` | 工具栏添加类型筛选 a-select；编辑抽屉添加 Merge with 区块 |
| `customField.ts` | 新增 mergeOptions API；补充 aliases 类型定义 |
| `CustomFieldController.java` | 新增 POST merge-options 端点 |
| `CustomFieldOptionService.java` | 新增 mergeOptionsFromField 方法（去重逻辑） |
| `CustomFieldService.java` | 委托方法 |
| `MergeOptionsDTO.java` | 请求 DTO |
| `MergeOptionsResultVO.java` | 响应 VO |

### 影响范围
- 管理后台自定义字段管理页面（admin/CustomFieldManage）
- 自定义字段 API 模块
