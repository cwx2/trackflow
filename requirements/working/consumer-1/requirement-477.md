# REQ-477：筛选模式「类型」属性值选择器弹出项目列表而非类型选项

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-477 |
| 标题 | 筛选模式「类型」属性值选择器弹出项目列表而非类型选项 |
| 类型 | 缺陷 |
| 严重程度 | P1 |
| 发现方式 | 测试人员操作验证 |
| 发现日期 | 2026-08-10 |
| 关联模块 | 工单列表筛选 |
| 对标文档 | YouTrack Filter Bar |
| 状态 | 开发完成 |

## 1. YouTrack 标准行为

YouTrack 筛选模式中：
- 选择「类型」属性后，值选择器应弹出工单类型列表（如：缺陷、任务、需求、改进等）
- 每个属性的值选择器应展示与该属性相关的选项数据

## 2. TrackFlow 当前实现

在 TrackFlow 筛选模式中：
- 点击「添加筛选」后输入框出现，可选择属性（状态、类型、优先级等）
- 选择「类型」属性后，筛选 chip 正确显示为 `类型 是 选择...`
- **问题**：点击「选择...」后弹出的下拉选项是**项目列表**（TF1、FE1、DE4），而非工单类型选项（缺陷、任务、需求等）

**参考截图**：
- `![TrackFlow 筛选Bug截图](file:///D:/project/YT/test/tf-tester-filter-bug-1.png)` — 筛选模式中类型属性值选择器错误显示项目列表

## 3. 差异分析

| 维度 | YouTrack | TrackFlow | 差异 |
|------|----------|-----------|------|
| 类型值选择器 | 显示工单类型选项 | 显示项目列表 | 值选择器数据源错误 |
| 属性-值映射 | 每个属性对应正确的值域 | 类型属性的值域错误映射为项目 | 属性值加载逻辑有Bug |

## 4. 期望结果

- 选择「类型」属性后，值选择器应弹出工单类型列表（缺陷/任务/需求等）
- 每个筛选属性的值选择器应正确加载该属性对应的选项数据
- 选择值后筛选条件应正确应用，返回匹配的工单列表

## 5. 验收标准

- [ ] 筛选模式中选择「类型」属性后，值选择器正确显示工单类型选项（缺陷、任务、需求等）
- [ ] 选择具体类型值后，工单列表正确按类型筛选
- [ ] 其他属性（状态、优先级、负责人等）的值选择器也各自显示正确的选项数据
- [ ] 不同属性之间的值选择器数据源不会互相混淆

## 6. 备注

此问题与 REQ-435（筛选模式选择「类型=缺陷」后返回 0 结果）可能存在关联，都涉及筛选模式中类型属性的处理逻辑。建议一并排查筛选模式中属性值加载的前端逻辑。

---

## 审核记录

**审核日期**：2026-08-10
**审核结论**：✅ 通过

### YouTrack 对标验证

| 维度 | 结果 |
|------|------|
| 文档确认 | ✅ YouTrack Simple Search 文档（smart-filters.html）明确描述选择属性后应自动展示该属性对应的可能值列表 |
| 行为一致 | ✅ 需求描述的期望行为（类型属性应弹出工单类型选项）与 YouTrack 文档标准一致 |

### TrackFlow 验证

| 维度 | 结果 |
|------|------|
| 问题存在 | ✅ 截图 `![TrackFlow筛选Bug截图](file:///D:/project/YT/test/tf-tester-filter-bug-1.png)` 确认当前类型属性值选择器错误显示项目列表 |
| 代码确认 | ✅ `FilterBar.vue` 中 `loadValueOptions` 函数有明确的 `issueType` case（第701行），但实际表现与代码预期不符，存在执行路径问题或数据源映射错误 |
| 关联修复 | ⚠️ REQ-435（筛选模式选择「类型=缺陷」后返回 0 结果）已在 implement/ 中，本 Bug 可能与其修复相关（回归或不完整修复） |

**通过理由**：YouTrack 文档明确确认筛选值选择器应展示属性对应的值列表。TrackFlow 截图证实类型属性的值选择器错误显示为项目列表，属于核心筛选功能的数据源映射 Bug，P1 级别。
**前置依赖**：无（独立 Bug，可直接修复）

## 自动化状态

fix_status: DONE
fix_commit: a6fb0808
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-10 11:25

### 本次改动摘要
- 改动1：`trackflow-web/src/views/issue/components/FilterBar.vue` — 修复了筛选模式中「类型」(issueType) 属性值选择器显示项目列表数据（而非工单类型选项）的 Bug。根因是 `loadValueOptions` 函数中 `case 'issueType'` 分支存在两个问题：(1) 在"全部项目"模式下没有像 `assignee`/`sprint` 那样查找已选项目筛选条件来获取项目 ID，导致在某些场景下无法正确加载项目级工单类型选项；(2) 缺少异步竞态防护，当 `loadIssueTypeOptions` 的异步调用返回时未验证当前编辑的仍是 issueType 字段，可能被其他操作的结果覆盖。

### 本次变更文件清单
- `trackflow-web/src/views/issue/components/FilterBar.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 进入工单列表 → 点击筛选模式图标 → 点击「添加筛选」→ 选择「类型」属性 → 验证弹出的值选择器显示工单类型选项（缺陷、任务、需求、史诗、故事）而非项目列表
  2. 在「全部项目」模式下（不选择特定项目）→ 添加「类型」筛选 → 验证值选择器正确显示工单类型
  3. 先添加「项目」筛选（选择 DE4）→ 再添加「类型」筛选 → 验证值选择器显示该项目的工单类型选项
  4. 在特定项目上下文中（侧栏选择 DE4）→ 添加「类型」筛选 → 验证值选择器正确显示工单类型
- **边界场景**：
  - 快速连续点击不同筛选属性的值选择器，检查是否有竞态问题
  - 「状态」「优先级」「负责人」等其他属性的值选择器不受影响
- **建议测试账号**：testuser（超级管理员，可访问所有项目）
- **注意事项**：本次改动仅涉及前端 FilterBar 组件，不需要重启后端

### 审核重点（给 code-review 会话）
- **重点关注文件**：`trackflow-web/src/views/issue/components/FilterBar.vue`（loadValueOptions 函数的 issueType case）
- **潜在风险点**：
  - 异步竞态防护检查 `editingChip.value && activeFilters.value[editingChip.value.index]?.fieldKey === 'issueType'` 是否足够严谨
  - 在全部项目模式下查找项目筛选条件 `activeFilters.value.find(f => f.fieldKey === 'project')` 的逻辑是否与 assignee/sprint 一致
- **已知遗留项**：本次未处理其他筛选属性的类似竞态防护（sprint、assignee 等异步加载的属性也可能有类似问题，但未在本 REQ 范围内）

======================

## 修复记录

**修复日期**：2026-08-10
**修复人**：AI Agent（auto 模式）

### 根因分析
`FilterBar.vue` 中 `loadValueOptions` 函数的 `case 'issueType'` 分支存在以下问题：
1. **项目上下文获取不完整**：仅使用 `props.projectId` 判断是否调用 API，但在"全部项目"模式下未检查已选的项目筛选条件，与 `assignee`/`sprint` 的处理逻辑不一致
2. **缺少异步竞态防护**：`loadIssueTypeOptions` 是异步调用，当用户快速切换不同筛选属性时，先前的异步结果可能覆盖后续同步设置的值选择器数据，导致显示错误的选项列表

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `trackflow-web/src/views/issue/components/FilterBar.vue` | 在 issueType case 中：(1) 添加对已选项目筛选条件的查找逻辑 `activeFilters.value.find(f => f.fieldKey === 'project')`；(2) 在异步结果返回后添加竞态防护检查，确认当前编辑字段仍为 issueType 后才赋值 |

### 影响范围
- 筛选模式中「类型」属性的值选择器行为
- 不影响其他属性的值选择器
- 不影响后端逻辑
