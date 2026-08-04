# REQ-186：优先级应迁移为可配置的枚举类型自定义字段

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-186 |
| 标题 | 优先级应迁移为可配置的枚举类型自定义字段 |
| 类型 | 功能缺失 / 架构差异 |
| 严重程度 | P1 |
| 发现方式 | 用户反馈 + 文档对标 |
| 发现日期 | 2026-08-04 |
| 关联模块 | issue/priority、customfield、project/settings |
| 对标文档 | https://www.jetbrains.com/help/youtrack/server/default-custom-fields.html |
| 状态 | 待修复 |

---

## 1. YouTrack 标准行为

### 1.1 优先级是枚举类型自定义字段

YouTrack 中，Priority 是所有新建项目默认附加的**枚举（enum）类型自定义字段**（Default Custom Fields 文档）：

> "Priority: Used to prioritize which issues need to be resolved first. Issues that are assigned the Show-stopper priority should be resolved before Critical issues, and so on. The following set of values is pre-configured: Show-stopper / Critical / Major / Normal / Minor. Each value is assigned a color that corresponds with the severity of the condition."

优先级的完整特性如下：

#### 1.1.1 全局层面（系统管理员）

- 优先级字段在全局自定义字段列表（Administration → Custom Fields）中可见
- 管理员可以：
  - 在全局层面修改字段名称、别名
  - 增删字段的值（如新增"Show-stopper"等级）
  - 为每个值设置颜色、描述、是否归档
  - 设置排序方式（手动排序 / 按名称排序）
  - 设置是否 auto-attach 到新建项目
  - 设置是否 private（私有字段，仅有 Read Issue Private Fields 权限的用户可见）

#### 1.1.2 项目层面（项目管理员）

- 项目设置 → 自定义字段 页面中，优先级字段可见
- 项目管理员可以：
  - 添加或移除项目中的优先级字段
  - 为本项目的优先级字段创建**独立值集合（independent copy）**，与其他项目互不影响
  - 在本项目中增删优先级值（独立副本后）
  - 修改本项目优先级值的颜色和描述（独立副本后）
  - 设置本项目中优先级值的排序方式
  - 设置是否必填（can be empty / cannot be empty）
  - 设置默认值
  - 设置条件显示（Conditional field，如某 Type 下才显示优先级）
  - 设置私有性（Make private / Make public）
  - 设置组级可见性（Advanced Settings：Visible to / Updatable by 指定用户组）
  - 修改字段顺序（拖拽调整在侧边栏/列表中的显示位置）

#### 1.1.3 值的属性（Value-specific Settings）

每个优先级值拥有以下可配置属性：

| 属性 | 说明 |
|------|------|
| Name | 值的名称 |
| Description | 悬停时显示的描述 tooltip |
| Color | 颜色方案（影响 badge 展示颜色） |
| Archived | 是否归档（归档后不可在 UI 中直接选择，但可通过 API/命令操作） |

#### 1.1.4 排序方式

- 手动排序（默认）：按照优先级等级从高到低手动调整顺序，影响下拉菜单顺序和范围查询
- 按名称排序（升序/降序，区分/不区分大小写）

### 1.2 在工单列表中的展示

优先级以**彩色方块 + 文字标签**的形式展示在每行工单，颜色来自字段值的 Color 属性，文字为值的名称。

---

## 2. TrackFlow 当前实现

### 2.1 实现方式

优先级当前是 `issue` 表上的普通 `VARCHAR` 字段（`issue.priority`），与自定义字段系统完全分离：

```sql
-- issue 表中
priority VARCHAR(50)  -- 硬编码存 "Critical"/"High"/"Normal"/"Low"
```

前端 `fieldLabels.ts` 里优先级选项是写死的：

```ts
export const priorityLabelMap: Record<string, string> = {
  Critical: '紧急', High: '高', Normal: '普通', Low: '低',
  critical: '紧急', high: '高', normal: '普通', low: '低',
}
```

### 2.2 已有能力

TrackFlow 已有完整的自定义字段模块（`customfield` 包），支持 enum、user、state、string、integer、date 等类型，支持项目级别的值集合配置、条件显示、私有性等。**但优先级没有接入这套体系。**

### 2.3 缺失对照

| YouTrack 能力 | TrackFlow 现状 |
|--------------|----------------|
| 优先级是枚举自定义字段，可通过字段管理界面配置 | 硬编码 VARCHAR，不可配置 |
| 值列表可增删（如新增 Show-stopper 等级） | 固定 4 个值：Critical/High/Normal/Low |
| 每个值有颜色属性，可自定义颜色 | 颜色硬编码在前端 CSS |
| 每个值有描述属性，hover 显示 tooltip | 无 |
| 值可以归档（不再在 UI 中出现） | 无 |
| 可设置排序方式（手动 / 名称排序） | 无排序控制 |
| 项目可创建独立值集合，各项目优先级不同 | 全局硬编码，所有项目一样 |
| 可设置默认值 | 无，创建时用户必须手动选 |
| 可设置必填性（can be empty） | 无 |
| 可设置条件显示 | 无 |
| 可设置私有性（仅有权限用户可见/编辑） | 无 |
| 可设置组级可见性（Visible to / Updatable by） | 无 |
| 字段顺序可在项目中拖拽调整 | 无 |
| 列表行展示彩色方块 + 文字标签 | 仅显示 8px 圆点 |

---

## 4. 期望结果

### 4.1 核心目标

将优先级从硬编码字段迁移为**枚举类型自定义字段**，接入现有 `customfield` 体系，实现与 YouTrack 一致的完整配置能力。

### 4.2 具体要求

#### 4.2.1 数据迁移

- 在 `custom_field_definition` 表中创建全局优先级字段（type=enum，名称="优先级 / Priority"）
- 预置 5 个值：Show-stopper（最高）、Critical（紧急）、High（高）、Normal（普通）、Low（低），每个值有预设颜色
  - 保持与当前数据兼容：现有 Critical/High/Normal/Low 对应新值
- 将 `issue.priority VARCHAR` 迁移：现有数据映射到自定义字段值，`issue.priority` 可保留作为冗余缓存或废弃
- 新建 Flyway 迁移脚本完成数据迁移

#### 4.2.2 值管理

项目管理员在项目设置 → 自定义字段 中对优先级字段可以：

- **增加新值**（输入名称、选颜色）
- **删除/归档值**（已被工单引用的值只能归档不能删除）
- **修改值的名称、颜色、描述**
- **拖拽调整值的排序**（影响下拉菜单顺序）
- **设置排序方式**：手动 / 按名称升序 / 按名称降序
- **创建独立值集合**（让本项目的优先级值与其他项目独立）

#### 4.2.3 字段属性配置

- 设置**默认值**（创建工单时自动填充）
- 设置**是否必填**（can be empty / cannot be empty）
- 设置**是否私有**（Make private / Make public）
- 设置**组级可见性**（Visible to / Updatable by 指定用户组）

#### 4.2.4 列表展示

工单列表每行优先级改为**彩色方块 + 文字标签**（参考 REQ-185），颜色来自字段值的 color 属性。

---

## 5. 验收标准

### 基础能力
- [ ] 优先级字段在项目设置 → 自定义字段中可见
- [ ] 可为本项目创建独立值集合
- [ ] 可增加新的优先级值（名称 + 颜色）
- [ ] 可归档优先级值（已引用则只归档不删除）
- [ ] 可修改值的名称、颜色、描述
- [ ] 可拖拽调整值的排序顺序
- [ ] 已有工单数据正确迁移，现有 Critical/High/Normal/Low 对应到新值

### 字段属性
- [ ] 可设置默认值
- [ ] 可设置是否必填
- [ ] 可设置是否私有

### 展示
- [ ] 工单列表每行优先级以彩色方块 + 文字展示
- [ ] 颜色来自字段值的 color 配置
- [ ] 工单详情侧边栏优先级下拉菜单显示各值颜色
- [ ] 创建工单时优先级下拉菜单显示各值颜色和名称

### 兼容性
- [ ] 所有使用到 priority 的功能正常（筛选、排序、报表、看板分列等）
- [ ] 归档的值不在 UI 下拉中出现，但旧数据仍显示

---

## 6. 实现建议

### 分阶段实现

**阶段 1（最小可用）**：将优先级值配置化
- 在数据库增加优先级值定义表（或直接复用 `custom_field_option` 表）
- 允许项目管理员增删改优先级值和颜色
- 前端从 API 动态加载优先级选项，不再硬编码
- 工单列表改为彩色方块+文字展示

**阶段 2（完整接入）**：将 priority 完全接入自定义字段体系
- 创建全局优先级自定义字段定义
- 各项目可选择共享或独立值集合
- 支持字段所有高级配置（必填、私有、条件显示、组级可见性）

### 数据库结构参考

```sql
-- 方案1：在已有 custom_field_option 表中增加优先级选项
-- 方案2：新增 issue_priority_option 表（更简单但更割裂）
-- 推荐方案1，接入现有自定义字段体系
```

## 7. 备注

- TrackFlow 已有完整的自定义字段模块，此需求是将优先级"纳入"这套体系，而非重新开发一套
- 迁移时需注意所有引用了 `issue.priority` 的地方：筛选、排序、报表、看板、工单列表、活动记录等
- YouTrack 文档中也提到 Priority 和 Due Date 字段默认是私有字段：*"The custom fields for Priority and Due Date are marked as private by default"*，实现时可预置该配置

## 自动化状态

fix_status: DONE
fix_commit: de643ade
fix_round: 2
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 00:31

### 本次改动摘要
第 2 轮修复，处理 code review 发现的 2 个 MUST 问题和 1 个 SHOULD 问题：

- **MUST-1（IssueController.java）**：将 getPriorityOptions 中的手动 entity→VO 映射替换为 `customFieldConverter.toOptionVOList(options)`，新增 `CustomFieldConverter` 字段注入
- **MUST-2（V250 迁移 + PriorityFieldService + 前端映射）**：新增 V250 迁移脚本将数据库中 `medium` 脏数据规范为 `Normal`；PriorityFieldService 新增 `normalizePriority()` 方法；前端添加 medium 映射
- **SHOULD（.priority-badge 提取）**：将共享的 `.priority-badge` 样式提取到全局 `components.css`，从 3 个组件中移除重复定义
- **Suggestion 3（localizePriority 合并）**：使用 `priority.toLowerCase()` 后统一 switch

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/issue/controller/IssueController.java`
- `trackflow-server/src/main/java/com/trackflow/issue/service/IssueExportService.java`
- `trackflow-server/src/main/java/com/trackflow/issue/service/PriorityFieldService.java`
- `trackflow-server/src/main/resources/db/migration/V250__normalize_priority_medium_to_normal.sql`
- `trackflow-web/src/styles/components.css`
- `trackflow-web/src/utils/fieldLabels.ts`
- `trackflow-web/src/views/issue/IssueCreatePanel.vue`
- `trackflow-web/src/views/issue/IssueListView.vue`
- `trackflow-web/src/views/issue/components/BatchActionToolbar.vue`
- `trackflow-web/src/views/issue/composables/usePriorityOptions.ts`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 打开项目 DE4 → 查看 Issue 列表 → 确认优先级列显示彩色方块 + 中文标签
  2. 点击某工单优先级下拉 → 确认选项列表包含 5 个值（阻塞/紧急/高/普通/低）
  3. 修改优先级为其他值 → 确认保存成功
  4. 创建新工单 → 确认优先级下拉包含 5 个带颜色的选项
- **边界场景**：
  - 数据库中不再有 "medium" 值（已由 V250 清理）
  - `.priority-badge` 样式在所有组件中正常渲染
- **建议测试账号**：testuser
- **注意事项**：本轮变更为纯修复性质，功能逻辑未改变

### 审核重点（给 code-review 会话）
- **重点关注文件**：IssueController.java（CustomFieldConverter 注入和使用）、V250 迁移脚本
- **潜在风险点**：IssueCreatePanel 中 `.priority-badge` 仅保留 `margin-right: 6px` 覆盖
- **已知遗留项**：Phase 2 未做（完全废弃 issue.priority VARCHAR）


======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
优先级字段在 TrackFlow 中是 `issue.priority VARCHAR(50)` 硬编码实现，前端选项写死在各组件中。与 YouTrack 中优先级作为可配置的枚举类型自定义字段的设计存在重大架构差异。系统已有完整的自定义字段模块但优先级未接入。

### 修复方案（第 1 轮）
| 文件 | 改动说明 |
|------|----------|
| `V249__seed_priority_custom_field.sql` | 创建 Priority 自定义字段定义 + 5 个带颜色选项 + 自动附加所有项目 + 数据规范化 |
| `PriorityFieldService.java` | 封装优先级字段逻辑，屏蔽自定义字段系统复杂性 |
| `IssueController.java` | 新增 /priority-options 端点 |
| `IssueService.java` | 默认优先级改为动态获取 |
| `IssueExportService.java` | 支持所有优先级值的中文本地化 |
| `usePriorityOptions.ts` | 前端 composable，5 分钟缓存 + API 回退 |
| 前端组件（5 个） | 动态加载优先级选项，彩色方块替代圆点 |

### 修复方案（第 2 轮 - code review 反馈）
| 文件 | 改动说明 |
|------|----------|
| `IssueController.java` | MUST-1: 使用 CustomFieldConverter.toOptionVOList() 替代手动映射 |
| `V250__normalize_priority_medium_to_normal.sql` | MUST-2: 清理 medium 脏数据 |
| `PriorityFieldService.java` | MUST-2: 新增 normalizePriority() + case-insensitive 验证 |
| `IssueExportService.java` | 合并 localizePriority switch 分支 + 支持 medium |
| `components.css` | SHOULD: 提取 .priority-badge 全局样式 |
| `IssueListView/IssueCreatePanel/BatchActionToolbar` | SHOULD: 移除重复 .priority-badge 定义 |
| `fieldLabels.ts` / `usePriorityOptions.ts` | MUST-2: 添加 medium 映射 |

### 影响范围
- 工单列表页（优先级列展示和行内编辑）
- 工单详情页（侧边栏优先级字段）
- 工单创建面板（优先级选择）
- 批量操作工具栏（变更优先级）
- 筛选器（优先级筛选选项）
- 工单导出（优先级本地化）
