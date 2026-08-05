# REQ-215：转换命名（transition_name）未在工单状态下拉中展示

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-215 |
| 标题 | 转换命名（transition_name）未在工单状态下拉中展示 |
| 类型 | 功能缺失 |
| 严重程度 | P2 |
| 发现方式 | YouTrack 功能对标 |
| 发现日期 | 2026-08-05 |
| 关联模块 | Workflow / IssueDetail / DetailSidebar |
| 对标文档 | YouTrack: workflow-constructor-state-machines.html |
| 状态 | 待修复 |

---

## 1. 问题描述

`workflow_transition` 表已有 `transition_name VARCHAR(100)` 字段（REQ-203-1 阶段添加），允许管理员为每个转换配置展示名称（如"开始处理"、"标记已完成"等）。

但是：
1. **前端 `DetailSidebar.vue` 的状态下拉只显示目标状态名称**，没有读取 `transition_name`
2. **`getAvailableTransitions()` 的返回 VO 中 `transitionName` 字段存在，但前端未使用**
3. 工单详情页的状态变更下拉只显示"进行中"、"已完成"等状态名，而非"开始处理"、"标记已完成"等更具操作引导性的转换名

---

## 2. YouTrack 标准行为

YouTrack 状态下拉会优先显示转换名（如果已配置），未配置时 fallback 到目标状态名：
- 有转换名："开始处理" → 点击后状态变为"进行中"
- 无转换名："进行中" → 点击后状态变为"进行中"

转换名让操作更具意图性，用户看到的是"做什么"而不仅仅是"变成什么状态"。

---

## 3. 技术方案

### 前端（DetailSidebar.vue 或状态下拉组件）

当 `availableTransitions` 列表的某项有 `transitionName` 字段时，优先显示 `transitionName`，否则显示 `statusName`：

```vue
<!-- 当前 -->
<a-option :value="t.statusId">{{ t.statusName }}</a-option>

<!-- 修改后 -->
<a-option :value="t.statusId">{{ t.transitionName || t.statusName }}</a-option>
```

如果 `transitionName` 存在，显示格式可以是：
- 仅显示转换名：`开始处理`
- 同时显示转换名和目标状态：`开始处理 (→ 进行中)`

### 后端（WorkflowTransitionVO）

确认 `getAvailableTransitions()` 返回的 VO 中包含 `transitionName` 字段（已在 REQ-203-1 中添加，确认正确映射）。

### 前端管理界面（WorkflowEditor.vue）

在转换格子的配置面板中，增加"转换名称"输入框：
- 可选填，留空则 fallback 到目标状态名
- 管理员可以为每个转换起一个操作性的名称

---

## 4. 验收标准

- [ ] 管理员可以在状态矩阵中为转换配置名称
- [ ] 工单详情页状态下拉中，配置了名称的转换显示转换名而非目标状态名
- [ ] 未配置名称时显示目标状态名（向后兼容）
- [ ] 转换名称变更后，工单页面下拉即时生效

## 5. 测试案例

### TC-WF-TNAME-001：配置转换名称后状态下拉展示
**步骤**：
1. 以 lina 进入工作流编辑器，在"待处理 → 进行中"转换上设置名称"开始处理"
2. 以 wangqiang 打开处于"待处理"状态的工单
3. 点击状态字段下拉

**预期**：下拉中显示"开始处理"而非"进行中"；点击后状态变为"进行中"

### TC-WF-TNAME-002：未配置名称时 fallback
**步骤**：不配置"待处理 → 已完成"的转换名称
1. 查看工单状态下拉

**预期**：显示"已完成"（目标状态名）

## 自动化状态

fix_status: DONE
fix_commit: 69c5b73a
fix_round: 3
test_status: PASS
test_round: 2
review_status: PENDING
review_round: 1

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 16:42

### 本次改动摘要
- **Round 3 修复（code review MUST-1 + SHOULD T2/T3）**：
- 改动1：新增 `UpdateTransitionNameDTO.java` — 替代 `Map<String, String>` 作为 `PATCH /workflows/transitions/{id}/name` 的 RequestBody，包含 `@Size(max=100)` 校验
- 改动2：`WorkflowController.java` — `updateTransitionName` 方法改为接收 `@Valid @RequestBody UpdateTransitionNameDTO dto`
- 改动3：`BatchActionToolbar.vue` — 单选模式下不再覆盖 `name` 字段为 `transitionName`，而是将 `transitionName` 存为独立可选字段；模板渲染使用 `status.transitionName || localizeStatusName(status.name)` fallback
- 改动4：`types.ts` — `BatchAvailableStatusVO` 接口新增可选 `transitionName` 字段
- 改动5：`TransitionActionPanel.vue` — 保存按钮添加 `:disabled="nameSaving"` 防止快速双击重复请求

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/workflow/controller/WorkflowController.java`
- `trackflow-server/src/main/java/com/trackflow/workflow/dto/UpdateTransitionNameDTO.java`
- `trackflow-web/src/api/types.ts`
- `trackflow-web/src/views/admin/TransitionActionPanel.vue`
- `trackflow-web/src/views/issue/components/BatchActionToolbar.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 管理 → 工作流编辑器 → 选择项目 DE4、角色 project_admin → 点击已勾选的转换格子（如"待处理→进行中"）→ 在"转换名称"框内输入"开始处理"→ 点保存 → 确认成功提示
  2. 切换到工单列表 → 找到一个"待处理"状态的工单 → 点击状态 badge 打开下拉 → 验证显示"开始处理"而非"进行中"
  3. 打开工单详情页 → 侧边栏状态字段下拉中也应显示"开始处理"
  4. 选择"开始处理"执行转换 → 工单状态变为"进行中"
- **边界场景**：
  - 未配置 transitionName 的转换仍显示目标状态名（如"已完成"）
  - 清除转换名称（清空输入框+保存）后回退到状态名显示
- **建议测试账号**：testuser（有管理员权限配置工作流）
- **注意事项**：API `PATCH /api/v1/workflows/transitions/{id}/name` 现在接收 `{"transitionName": "..."}` JSON body（与之前格式相同，但后端改用 DTO 接收）；前端 `workflowApi.updateTransitionName` 发送格式未变

### 审核重点（给 code-review 会话）
- **重点关注文件**：UpdateTransitionNameDTO.java（新增 DTO）、WorkflowController.java（Map→DTO 替换）、BatchActionToolbar.vue（name 字段不再被覆盖）
- **潜在风险点**：无，此轮修改范围很小且定向
- **已知遗留项**：T1（TransitionResult 内部类提取到独立文件）为 SUGGESTION 级，当前规模可接受，不阻塞

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
`getAvailableTransitions` API 返回的是 `List<IssueStatus>`（仅包含状态名），完全丢失了 `workflow_transition.transition_name` 字段。虽然后端实体和 VO 中都有 `transitionName` 字段（REQ-203-1 已添加），但数据流断裂在 WorkflowService → Controller 环节：service 只返回状态 ID 列表，没有携带转换名。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `WorkflowTransitionMapper.xml` | SQL 查询增加 transition_name 列 |
| `WorkflowService.java` | 新增 resolveAllowedStatusMap 返回 Map<statusId, transitionName>；新增 getAvailableTransitionsWithNames 合并查询；新增 updateTransitionName |
| `IssueController.java` | 使用合并方法，单次查询获取状态+转换名 |
| `WorkflowController.java` | 新增 PATCH 端点配置转换名称 |
| `IssueStatusVO.java` | 新增 transitionName 字段 |
| 前端多个文件 | IssueStatusVO 类型扩展、所有状态下拉优先展示 transitionName、管理界面新增编辑 UI |

### 影响范围
- 工单详情页状态下拉
- 工单列表页行内状态下拉
- 看板预览抽屉状态下拉
- 批量操作工具栏状态选项
- 管理界面工作流编辑器（新增转换名称配置功能）
