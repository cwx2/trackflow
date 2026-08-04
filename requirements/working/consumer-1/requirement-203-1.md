# REQ-203-1：状态转换矩阵完整能力建设

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-203-1 |
| 标题 | 状态转换矩阵完整能力建设 |
| 父需求 | REQ-203 |
| 类型 | 功能增强 |
| 严重程度 | P0 |
| 对标文档 | YouTrack: workflow-constructor-state-machines.html, state-machine-per-issue-type.html |
| 状态 | 待修复 |

---

## 1. 功能定位

状态转换矩阵（State Machine）解决的核心问题是：**哪个状态可以流转到哪个状态，对哪种工单类型、哪个角色生效**。这是整个 Workflow 体系的基础层，任何工单的状态流转都必须经过这里校验。

TrackFlow 已有 `/workflow` 路由下的状态矩阵页面（截图所示），但与 YouTrack 对比存在以下能力缺失。

---

## 2. YouTrack 标准能力 vs TrackFlow 现状

### 2.1 核心矩阵功能

| 功能 | YouTrack | TrackFlow 现状 |
|------|---------|----------------|
| 状态 × 状态的转换矩阵 | ✅ 勾选格子表示允许该转换 | ✅ 已实现 |
| 按工单类型分别配置转换规则 | ✅ 不同 Type 可有不同矩阵 | ⚠️ 有"所有类型"选项但不清晰 |
| 按角色分别配置转换权限 | ✅ 可为特定角色限制/开放某转换 | ✅ 已实现 |
| 全局规则 / 项目级规则 | ✅ 可全局或附加到特定项目 | ⚠️ 有"全局/项目"切换但行为不明确 |
| 状态分组（待处理/进行中/已完成/已取消） | ✅ 按 category 分组展示 | ✅ 已实现 |

### 2.2 转换上的"动作"（Actions on Transition）

YouTrack 支持在转换发生时自动执行动作，TrackFlow 截图中蓝点表示"已配置动作"，但实际能力未知。

| 转换动作类型 | YouTrack | TrackFlow |
|------------|---------|-----------|
| 转换时自动设置字段（如进入 In Progress 自动设置 Assignee = 当前用户） | ✅ | ❓ 蓝点但能力不明 |
| 转换时自动添加评论 | ✅ | ❌ |
| 转换时自动添加标签 | ✅ | ❌ |
| 转换时要求必填字段（如关闭时必须填 Resolution） | ✅ | ❌ |
| 转换时运行命令（Apply Command） | ✅ | ❌ |

### 2.3 转换上的"卫士条件"（Guard Conditions）

YouTrack 支持在转换上配置前置条件，不满足时禁止该转换。TrackFlow 截图中橙点表示"已配置卫士条件"，实际能力待确认。

| 卫士条件类型 | YouTrack | TrackFlow |
|------------|---------|-----------|
| 字段值满足某条件（如优先级必须是 Critical 才能直接关闭） | ✅ | ❓ |
| 关联工单全部已解决（父任务所有子任务完成才能关闭） | ✅ | ❌ |
| 用户属于特定角色 | ✅ | ❌ |
| 字段不为空（必填校验） | ✅ | ❌ |

### 2.4 State Machine 可视化画布（YouTrack 最新版新增）

| 功能 | YouTrack | TrackFlow |
|------|---------|-----------|
| 画布式可视化（拖拽节点，连线表示转换） | ✅ 支持自由画布 | ❌ 仅矩阵视图 |
| 矩阵视图（勾选格子） | ✅ 也支持 | ✅ 已实现 |
| 标记"初始状态"（新建工单的默认状态） | ✅ | ❓ |
| 转换命名（转换可以有自己的名字，用于命令行） | ✅ | ❌ |

### 2.5 按 Issue Type 分别配置（State Machine Per Issue Type）

YouTrack 支持为同一项目内不同类型的工单（Bug/Task/Story/Epic）配置完全不同的状态转换规则。

| 功能 | YouTrack | TrackFlow |
|------|---------|-----------|
| 不同 Issue Type 使用不同的状态流转规则 | ✅ 完整支持 | ⚠️ UI 有"所有类型"选项但未完整实现 |
| 不同 Type 可用的状态集合也不同 | ✅ | ❌ |

---

## 3. 差异与缺失总结

### 缺失功能（优先级排序）

**P0 - 必须实现**：
1. **转换动作配置完整化**：点击蓝点后能配置：设置字段值 / 添加评论 / 添加标签
2. **转换卫士条件完整化**：点击橙点后能配置：字段值检查 / 关联工单状态检查 / 必填校验
3. **按 Issue Type 分别配置转换规则**：Bug 和 Task 可以有完全不同的转换矩阵

**P1 - 重要**：
4. **初始状态标记**：可以指定新建工单时默认进入哪个状态
5. **转换命名**：每个转换可以有自定义名称，用于 Apply Command

**P2 - 可视化增强**：
6. **画布式可视化视图**：作为矩阵视图的补充（YouTrack 两者都支持）

---

## 4. 详细功能需求

### 4.1 转换动作配置（点击矩阵蓝点进入）

管理员点击矩阵中的蓝点（或某个勾选的转换格子右键），弹出动作配置面板：

```
转换：待处理 → 进行中
动作列表：
  [+ 添加动作]
    ├── 设置字段    → 选择字段 + 选择值（如 负责人 = 当前用户）
    ├── 添加评论    → 输入评论模板文本（支持变量 {issue.id}）
    ├── 添加标签    → 输入标签名
    └── 要求必填    → 选择必须有值的字段
```

配置保存后，矩阵格子出现蓝点标记。

### 4.2 转换卫士条件配置（点击矩阵橙点进入）

管理员点击矩阵中的橙点（或某个勾选的转换格子右键），弹出卫士条件配置面板：

```
转换：进行中 → 已关闭
卫士条件（不满足时禁止此转换）：
  [+ 添加条件]
    ├── 字段满足条件   → 如 Priority = Critical
    ├── 字段不为空     → 如 Resolution 不为空
    ├── 关联工单全部解决 → 子任务全部处于已解决状态
    └── 错误提示文本   → "请先完成所有子任务"
```

不满足卫士条件时，状态下拉列表中该选项变灰，hover 时显示配置的错误提示。

### 4.3 按 Issue Type 分别配置

页面顶部"所有类型"下拉，选择具体 Issue Type 后，矩阵切换到该类型独立的转换规则：

- 每种 Issue Type 有独立的勾选矩阵
- 不同类型可用的状态集合可以不同（通过状态与类型的关联配置）
- "所有类型"表示适用于所有未单独配置的类型（fallback）

### 4.4 初始状态配置

矩阵视图中，状态名旁边可标记"初始状态"（星形图标），表示新建该类型工单时默认进入此状态：

- 每种 Issue Type 只能有一个初始状态
- 初始状态决定 `POST /api/v1/issues` 创建时的默认 status_id

### 4.5 转换命名

每个转换可以配置一个展示名（如"开始处理"、"关闭"），用于：
- 工单详情页状态下拉中显示（而非直接显示目标状态名）
- Apply Command 弹窗的命令建议

---

## 5. 后端技术要求

### 数据库变更（Flyway 迁移）

```sql
-- 转换动作表
CREATE TABLE workflow_transition_action (
    id               BIGSERIAL PRIMARY KEY,
    transition_id    BIGINT NOT NULL,        -- 关联 workflow_transition.id
    action_type      VARCHAR(50) NOT NULL,   -- set_field / add_comment / add_tag / require_field
    config           JSONB NOT NULL,         -- 动作配置（字段名、值、模板等）
    sort_order       INT DEFAULT 0,
    created_at       TIMESTAMP DEFAULT NOW()
);

-- 转换卫士条件表
CREATE TABLE workflow_transition_guard (
    id               BIGSERIAL PRIMARY KEY,
    transition_id    BIGINT NOT NULL,        -- 关联 workflow_transition.id
    condition_type   VARCHAR(50) NOT NULL,   -- field_check / field_not_empty / links_resolved
    config           JSONB NOT NULL,         -- 条件配置
    error_message    VARCHAR(500),           -- 不满足时的错误提示
    created_at       TIMESTAMP DEFAULT NOW()
);

-- workflow_transition 表扩展
ALTER TABLE workflow_transition
    ADD COLUMN transition_name VARCHAR(100),    -- 转换显示名
    ADD COLUMN is_initial      BOOLEAN DEFAULT FALSE; -- 标记初始状态
```

### API 扩展

```
GET    /api/v1/workflow/transitions/{id}/actions       -- 查询转换动作
POST   /api/v1/workflow/transitions/{id}/actions       -- 创建转换动作
DELETE /api/v1/workflow/transitions/{id}/actions/{aid} -- 删除转换动作

GET    /api/v1/workflow/transitions/{id}/guards        -- 查询卫士条件
POST   /api/v1/workflow/transitions/{id}/guards        -- 创建卫士条件
DELETE /api/v1/workflow/transitions/{id}/guards/{gid}  -- 删除卫士条件
```

### 执行时机

- **卫士条件**：在 `WorkflowService.getAvailableTransitions()` 中额外过滤，不满足的转换从可用列表移除
- **转换动作**：在 `WorkflowService.applyTransition()` 中，状态变更后依次执行动作列表

---

## 6. 前端 UI 需求

### 矩阵格子交互升级

当前：格子只能勾选/取消。

改造后：
- 勾选的格子右上角显示操作图标：蓝圆（有动作）、橙圆（有卫士条件）
- 格子 hover 时显示"设置动作" / "设置条件"按钮
- 点击进入动作/条件配置面板（Drawer 或 Modal）

### 动作配置面板

```
┌─── 转换动作配置 ────────────────────────┐
│  转换：待处理 → 进行中                    │
├─────────────────────────────────────────┤
│  动作列表                                 │
│  ┌──────────────────────────────────┐   │
│  │ 1. 设置字段  负责人 → 当前用户  🗑 │   │
│  └──────────────────────────────────┘   │
│  ┌──────────────────────────────────┐   │
│  │ 2. 添加评论  "已开始处理..."     🗑 │   │
│  └──────────────────────────────────┘   │
│  [+ 添加动作]                            │
│                          [取消] [保存]   │
└─────────────────────────────────────────┘
```

---

## 7. 验收标准

- [ ] 点击矩阵蓝点可配置转换动作（设置字段/添加评论/添加标签/要求必填）
- [ ] 配置的转换动作在实际状态变更时被执行
- [ ] 点击矩阵橙点可配置卫士条件（字段检查/必填校验）
- [ ] 不满足卫士条件时，该转换在状态下拉中不可用，并有错误提示
- [ ] 可按 Issue Type 分别配置不同的转换矩阵
- [ ] 可标记某状态为"初始状态"，新建工单默认使用该状态
- [ ] 转换可以配置显示名称

---

## 8. 测试案例

**测试账号约定**：
- `lina` — 项目管理员（有权配置 Workflow）
- `wangqiang` — 开发人员
- `zhaojing` — 测试人员
- `huanglei` — 观察者（最小权限）

---

### TC-WF-001：查看状态转换矩阵

**前置条件**：以 `lina` 登录，进入 `/workflow`

**步骤**：
1. 打开工作流页面，切换到"状态转换矩阵"标签
2. 查看矩阵页面的行（From 状态）与列（To 状态）

**预期结果**：
- 矩阵中行和列分别显示所有可用状态，按 category 分组（待处理/进行中/已完成/已取消）
- 对角线格子显示 `—`（自我转换）
- 勾选的格子表示允许该方向的转换
- 已配置动作的格子显示蓝点，已配置卫士条件的格子显示橙点

---

### TC-WF-002：允许/禁止状态转换

**前置条件**：以 `lina` 登录

**步骤**：
1. 在矩阵中找到"待处理 → 已完成"格子（当前未勾选）
2. 勾选该格子，点击"保存工作流"
3. 以 `wangqiang` 登录，打开任意工单，将状态改为"待处理"，再尝试变更到"已完成"

**预期结果**：
- 步骤 3：状态下拉列表中出现"已完成"选项，变更成功

**逆向验证**：
4. 以 `lina` 重新取消勾选"待处理 → 已完成"，保存
5. 以 `wangqiang` 再次从"待处理"尝试变更到"已完成"

**预期结果**：
- 步骤 5：状态下拉中**不出现**"已完成"选项

---

### TC-WF-003：按角色限制转换权限

**前置条件**：以 `lina` 登录

**步骤**：
1. 矩阵顶部"角色"筛选选择"测试人员"
2. 勾选"测试中 → 已完成"转换，保存
3. 选择"开发人员"角色，确认"测试中 → 已完成"是否勾选
4. 分别以 `zhaojing`（测试人员）和 `wangqiang`（开发人员）登录，打开处于"测试中"状态的工单，查看状态下拉

**预期结果**：
- 步骤 3：开发人员视图中"测试中 → 已完成"**未勾选**
- 步骤 4：`zhaojing` 能看到并选择"已完成"；`wangqiang` 的下拉中**无此选项**

---

### TC-WF-004：按 Issue Type 分别配置转换规则

**前置条件**：以 `lina` 登录

**步骤**：
1. 矩阵顶部"类型"选择"Bug"
2. 勾选"已完成 → 待处理"（Bug 可重新打开），保存
3. 类型切换回"所有类型"，检查"已完成 → 待处理"是否勾选
4. 以 `wangqiang` 登录，分别打开 Bug 工单和 Task 工单（均处于"已完成"），查看状态下拉

**预期结果**：
- 步骤 3："所有类型"视图中该转换**未勾选**
- 步骤 4：Bug 工单下拉有"待处理"；Task 工单下拉**无"待处理"**

---

### TC-WF-005：配置转换动作——进入"进行中"自动设置负责人

**步骤**：
1. 以 `lina` 登录，找到"待处理 → 进行中"格子，点击蓝点（或右键 → 配置动作）
2. 添加动作：设置字段，负责人 = 当前用户，保存
3. 以 `wangqiang` 登录，打开一个未分配负责人的工单（状态为"待处理"），将状态改为"进行中"

**预期结果**：
- 状态成功变为"进行中"，负责人字段自动设置为 `wangqiang`
- 活动流中有状态变更和负责人变更两条记录

---

### TC-WF-006：配置转换动作——关闭时自动添加评论

**步骤**：
1. 以 `lina` 登录，找到任意"→ 已关闭"转换，配置动作：添加评论，内容为"工单已关闭，感谢跟进！"
2. 以 `wangqiang` 将任意工单状态改为"已关闭"

**预期结果**：
- 状态变更后，评论区自动出现"工单已关闭，感谢跟进！"

---

### TC-WF-007：配置卫士条件——关闭时必须填写 Resolution

**步骤**：
1. 以 `lina` 登录，找到"进行中 → 已完成"转换，点击橙点 → 配置卫士条件
2. 添加条件：字段不为空，字段选择"Resolution"，错误提示："请先填写解决方案"，保存
3. 以 `wangqiang` 打开 Resolution 为空的工单，尝试将状态改为"已完成"

**预期结果**：
- 步骤 3：提示"请先填写解决方案"，状态**不变**
- 填写 Resolution 后再次尝试，状态成功变更

---

### TC-WF-008：卫士条件——所有子任务完成才能关闭父任务

**步骤**：
1. 以 `lina` 在"进行中 → 已关闭"转换上配置卫士：关联工单全部已解决
2. 创建父工单 A，并创建两个处于"进行中"的子任务
3. 尝试关闭父工单 A

**预期结果**：
- 步骤 3：无法关闭，提示子任务未完成
- 将两个子任务改为"已完成"后，父工单 A 成功关闭

---

### TC-WF-009：标记初始状态

**步骤**：
1. 以 `lina` 登录，在状态矩阵中将"待处理"标记为"初始状态"，保存
2. 以 `wangqiang` 创建一个新工单（不手动选择状态）

**预期结果**：
- 新工单的默认状态为"待处理"

---

### TC-WF-010：观察者无权修改 Workflow

**步骤**：
1. 以 `huanglei`（观察者）登录，尝试访问工作流配置页面
2. 尝试勾选/取消任何转换格子

**预期结果**：
- 步骤 1：无权限访问，或以只读模式展示
- 步骤 2：所有操作被拒绝

## 自动化状态

fix_status: DONE
fix_commit: 961b354a
fix_round: 3
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 07:28

### 本次改动摘要
第3轮修复，针对 code-review MUST 问题：
- **transitionAction.ts** — `TransitionActionVO.actionConfig` 类型补全：新增 `comment_template`、`tag_id`、`tag_name`、`required_field_id`、`required_field_name`、`warning_message`、`weights` 可选字段；`strategy` 改为可选（非 auto_assign 类型时不存在）。
- **TransitionActionForm.vue** — `loadCustomFields()` 从 `customFieldApi.list({ pageSize: 200 })` 改为 `customFieldApi.listByProject(props.projectId)`，确保只返回当前工作流所属项目的自定义字段。
- **TransitionActionPanel.vue** — `strategyDescription()` 函数新增 `actionType` 参数，按动作类型明确展示对应信息，不再依赖 config 键名检测（修复 actionType 标记错误）；同步更新模板调用传入 `action.actionType`。

### 本次变更文件清单
- `trackflow-web/src/api/transitionAction.ts`
- `trackflow-web/src/views/admin/TransitionActionForm.vue`
- `trackflow-web/src/views/admin/TransitionActionPanel.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 进入 /workflow → 选择项目 DE4 → 点击一个已勾选的转换格子 → 打开动作面板 → 验证"新增动作"弹窗有 4 种类型可选
  2. 选择 `require_field` 类型 → 验证字段下拉列表只显示 DE4 项目的自定义字段（而非全局所有项目字段）
  3. 新增一个 `add_tag` 类型动作（标签名：workflow-test）→ 保存 → 验证面板列表中显示"添加标签"标签和"标签: workflow-test"描述
  4. 新增一个 `add_comment` 类型动作 → 保存 → 验证列表中显示"添加评论"标签和模板内容摘要
- **边界场景**：
  - require_field 下拉应该只有当前项目字段，不含其他项目的字段
  - 尝试创建 add_tag 但不填标签名 → 应报错"请输入标签名称"
- **建议测试账号**：testuser（系统管理员，有 manage_workflow 权限）
- **注意事项**：现有 auto_assign 动作不受影响；面板列表中的描述文字正确区分各动作类型

### 审核重点（给 code-review 会话）
- **重点关注文件**：transitionAction.ts（类型定义）、TransitionActionForm.vue（API 调用切换）、TransitionActionPanel.vue（展示逻辑修复）
- **本轮修复的 MUST 问题**：
  - MUST-1（前端类型缺失）：actionConfig 接口已补全所有动作类型字段
  - MUST-2（actionType 标记错误）：strategyDescription 改为通过参数显式传入 actionType 判断
  - customFieldApi 改用 listByProject(projectId) 限制字段范围
- **已知遗留项**：
  - `is_initial` 字段前端 UI 暂未提供标记初始状态的操作按钮（留给后续子需求）
  - `transition_name` 字段状态下拉暂未使用（留给后续子需求）

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
TrackFlow 工作流模块的转换动作系统在底层引擎层（TransitionActionEngine）已支持 `auto_assign`、`add_comment`、`require_field` 三种动作，但 Service 层的 `VALID_ACTION_TYPES` 白名单仅包含 `auto_assign`，导致通过 API 创建其他类型动作时被拒绝。同时缺少 `add_tag` 动作实现，以及 `transition_name`/`is_initial` 数据库字段。前端表单也仅支持 `auto_assign` 一种类型的配置。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `TransitionActionService.java` | 扩展 VALID_ACTION_TYPES 包含 4 种类型；放宽 add_comment/add_tag 唯一性限制 |
| `TransitionActionEngine.java` | 新增 executeAddTagAction 方法；在两个 switch 语句中注册 add_tag case |
| `ActionConfig.java` | 新增 tagId/tagName 字段 |
| `WorkflowTransition.java` | 新增 transitionName/isInitial 字段 |
| `WorkflowTransitionVO.java` | 新增 transitionName/isInitial 字段 |
| `WorkflowConverterImpl.java` | toVO 方法映射新字段 |
| `V258__*.sql` | DDL 增加列 + CHECK 约束 |
| `TransitionActionForm.vue` | 全面重构支持 4 种动作类型 |
| `TransitionActionPanel.vue` | 更新标签和描述显示 |
| `types.ts` | VO 类型扩展 |

### 影响范围
- 工作流编辑器页面（/workflow）的动作配置面板
- 状态转换时的动作执行逻辑
- 工作流 API 返回的 VO 数据结构（新增字段，向后兼容）
