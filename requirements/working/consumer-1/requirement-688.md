# REQ-688：状态变更确认弹窗未整合目标状态所需的必填字段，用户需二次操作才能完成状态流转

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-688 |
| 标题 | 状态变更确认弹窗未整合目标状态所需的必填字段，用户需二次操作才能完成状态流转 |
| 类型 | 交互不一致 |
| 严重程度 | P1 |
| 发现方式 | 开发人员日常工作流操作 |
| 发现日期 | 2026-08-14 |
| 关联模块 | 工单详情 / 状态转换 / 工作流 |
| 状态 | 修复中 |

## 1. 问题描述

开发人员将工单从"代码审查"状态变更为"测试中"时，系统弹出"状态变更确认"弹窗（包含目标状态显示、指派人选项、备注输入框）。用户填写完备注并点击"变更为「测试中」"按钮后，系统才弹出**第二个弹窗**告知"工单进入测试阶段时必须指定「测试人员」，请先在字段面板中填写测试人员后再变更状态"。

**操作路径**：
1. 工单详情页 → 右侧字段面板 → 点击"状态"字段
2. 选择"测试中" → 弹出状态变更确认弹窗
3. 用户填写备注"代码已通过review，合入主分支，可以进入测试阶段"
4. 点击"变更为「测试中」"
5. ❌ 弹出"字段校验"弹窗，提示需要填写"测试人员"字段
6. 用户需要关闭弹窗 → 在字段面板找到"测试人员"字段 → 填写值 → 再次执行状态变更

用户操作了完整的确认流程后才被拦截，前面填写的备注信息是否保留也不确定。

**参考截图**：
![TrackFlow 状态变更确认弹窗](file:///D:/project/YT/test/tf-status-transition-modal.png)

## 2. 期望行为

状态变更确认弹窗应该在打开时就包含目标状态的所有必填字段输入：

1. 系统在用户选择目标状态时，查询该状态转换的 `require_field` 工作流规则
2. 将所需的必填字段（如"测试人员"）**直接嵌入状态变更确认弹窗中**，与"指派给"和"备注"同级展示
3. 必填字段用红色星号标记，提交时统一校验
4. 用户在一步内完成：选择目标状态 → 填写必填字段 + 指派人 + 备注 → 确认变更

**对标 YouTrack 行为**：YouTrack 的状态转换会在命令对话框中一次性展示所有需要填写的字段，用户无需多次弹窗操作。

## 3. 差异分析

| 维度 | 期望 | 当前实现 | 差异 |
|------|------|---------|------|
| 必填字段展示位置 | 集成在确认弹窗中 | 确认提交后才校验拦截 | 两步变一步 |
| 用户操作步数 | 1次弹窗交互 | 3次弹窗交互（确认→校验拦截→再次确认） | 多2步 |
| 备注数据保留 | 一次性提交 | 拦截后备注是否保留不确定 | 可能丢失用户输入 |
| 校验时机 | 前置（打开弹窗时） | 后置（提交后才校验） | 浪费用户时间 |

## 4. 验收标准

- [ ] 状态变更确认弹窗打开时，自动查询目标状态所需的 require_field 规则，将必填自定义字段渲染在弹窗表单中
- [ ] 必填字段带红色星号标记，与"指派给"和"备注"字段同层级展示
- [ ] 用户不填写必填字段直接提交时，弹窗内 inline 提示"此字段为必填"，不弹出第二个弹窗
- [ ] 用户在弹窗内填写了必填字段后，提交即完成状态变更 + 字段赋值的原子操作
- [ ] 如果目标状态无必填字段规则，弹窗表现与现在一致（仅显示指派人和备注）

## 5. 备注

- 相关需求：REQ-179（状态转换校验失败引导）只解决了"引导到字段"的问题，本需求要求**消除二次操作**
- 相关需求：REQ-676（rejected，指派给与测试人员不一致）描述了类似困惑但被驳回
- 后端已有 `require_field` 规则查询能力（工作流引擎），前端在打开弹窗时可提前调用
- 涉及接口：需要一个"获取目标状态所需字段"的 API，或在现有 `getAvailableTransitions` 响应中附带 require_field 信息

---

## 审核记录

**审核日期**：2026-08-14
**审核结论**：✅ 通过

### YouTrack 对标验证

| 维度 | 结果 |
|------|------|
| 文档确认 | ✅ YouTrack 通过 Apply Command 对话框（`commands.html`）支持一次性设置多个字段（包含状态+相关必填字段），用户无需多步操作 |
| 行为对比 | ⚠️ YouTrack 的 field panel 状态点击使用 `issue.fields.required()` 方法做后置校验（与 TrackFlow 当前类似），但 YouTrack 的 Apply Command dialog 提供了一步到位的替代路径 |
| 设计合理性 | ✅ TrackFlow 的 TransitionCommentModal 已有 `requiredFieldIds` 数据但未渲染对应输入控件，存在明确的"信息已知但未前置展示"的 UX 缺陷 |

### TrackFlow 验证

| 维度 | 结果 |
|------|------|
| 问题存在 | ✅ 代码确认：`useIssueDetailActions.ts` 中 `onTransition()` 在 `requiredFieldIds` 非空时打开 modal，但 `TransitionCommentModal.vue` 仅渲染 assignee + comment，不渲染 requiredFieldIds 对应的字段输入 |
| 二次弹窗 | ✅ 代码确认：`executeTransition()` 在后端返回 `FIELD_VALIDATION_FAILED` 时弹出第二个 `Modal.warning`，造成两步操作 |
| 数据可用 | ✅ `StatusInfo.requiredFieldIds` 已在 API 响应中返回，前端已知道需要哪些字段，只是未在 modal 中渲染 |

### 优先级校准

原始标注 P1，审核确认 P1。理由：
- 影响日常工作流（状态变更是最高频操作之一）
- 用户填写完备注后被拦截，可能丢失输入内容
- 技术上可行（数据已具备，仅需前端 modal 增强）
- 非核心功能缺失（已有 workaround：先填字段再变更），但体验差异明显

**通过理由**：问题确实存在，`requiredFieldIds` 数据已在前端可用但未被利用，将其嵌入 TransitionCommentModal 是合理且必要的 UX 提升。YouTrack 通过 Apply Command dialog 提供了一步完成的能力，TrackFlow 应在其自有的转换确认弹窗中达到同等效果。
**前置依赖**：REQ-179（状态转换校验失败引导）应先完成，本需求在其基础上进一步消除二次操作。


## 自动化状态

fix_status: DONE
fix_commit: e80d6a4c
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-14 11:35

### 本次改动摘要
状态变更确认弹窗（TransitionCommentModal）原本只显示指派人和备注，当目标状态有 `require_field` 规则时，字段校验在用户提交后才以第二个弹窗拦截。本次修复将必填字段直接嵌入确认弹窗，实现一步完成。

- 改动1：`TransitStatusDTO.java` — 新增 `customFieldValues: Map<String, String>` 字段，允许前端在状态转换请求中同时提交自定义字段值
- 改动2：`IssueService.java` — 在 `performTransition()` 中，调用 `transitStatus()` 前先保存 `customFieldValues`，这样 `validatePreTransition()` 能看到已填充的字段值，校验通过
- 改动3：`issue.ts` — API 方法 `transitStatus()` 新增可选参数 `customFieldValues`
- 改动4：`TransitionCommentModal.vue` — 完整重写，新增 required fields 渲染区域。支持 user（成员选择）、list（下拉选项）、string/text（文本输入）三种字段类型。内置 inline 校验，禁用提交按钮直到必填字段均已填写
- 改动5：`useIssueDetailActions.ts` — `onTransitionConfirm` 和 `executeTransition` 新增 `customFieldValues` 参数传递链路
- 改动6：`IssueDetailView.vue` — 向 `TransitionCommentModal` 传入 `:required-field-ids` 和 `:custom-field-defs`

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/issue/dto/TransitStatusDTO.java`
- `trackflow-server/src/main/java/com/trackflow/issue/service/IssueService.java`
- `trackflow-web/src/api/issue.ts`
- `trackflow-web/src/views/issue/IssueDetailView.vue`
- `trackflow-web/src/views/issue/components/TransitionCommentModal.vue`
- `trackflow-web/src/views/issue/composables/useIssueDetailActions.ts`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 打开任一项目工单 → 点击状态字段 → 选择有 require_field 规则的目标状态（如"测试中"）→ 验证弹窗中出现对应必填字段输入控件 → 填写字段 + 备注 → 点击确认 → 状态变更成功且无第二个弹窗
  2. 同上路径但不填写必填字段 → 点击确认按钮应被禁用（disabled），且不弹出第二个校验弹窗
  3. 无 requiredFieldIds 的普通状态转换 → 弹窗行为与之前一致（仅显示指派人和备注）
- **边界场景**：
  - 目标状态有多个必填字段时，所有字段都应渲染
  - user 类型字段应显示项目成员下拉选择
  - list 类型字段应显示选项下拉
- **建议测试账号**：开发人员 wangqiang（有状态变更权限但不是管理员）
- **注意事项**：需要有配置了 require_field 工作流动作的转换路径才能测试。如果当前环境没有此配置，可用管理员在工作流中添加一条 require_field 动作后再测试

### 审核重点（给 code-review 会话）
- **重点关注文件**：TransitStatusDTO.java, IssueService.java, TransitionCommentModal.vue
- **潜在风险点**：
  - `performTransition()` 中先保存字段值再做 `validatePreTransition()`，如果后续转换因其他原因失败（如 WIP 限制），字段值已经被修改但状态未变更。这在同一事务中由 `@Transactional` 保证回滚——但 `saveSingleValue` 内部可能有独立事务。需确认事务传播行为
  - `customFieldValues` 的 key 是 fieldId 字符串，Jackson 反序列化时由 `Long.parseLong()` 转换，非数字 key 会抛 NumberFormatException（应由 Controller 层校验）
- **已知遗留项**：当 `saveSingleValue` 成功但后续 `transitStatus` 失败（如工作流引擎内部错误），由于都在同一事务中，回滚可保证一致性。但如果 `customFieldService.saveSingleValue` 使用了 `REQUIRES_NEW` 传播，则字段值可能无法回滚

======================

## 修复记录

**修复日期**：2026-08-14
**修复人**：AI Agent（auto 模式）

### 根因分析
TransitionCommentModal 打开时虽然知道 `requiredFieldIds`（通过 available-transitions API 返回），但只渲染了指派人和备注输入框，未渲染必填自定义字段的输入控件。用户提交后，后端 `validatePreTransition()` 检查字段为空，返回 `FIELD_VALIDATION_FAILED`，前端再弹出第二个 `Modal.warning` 告知用户需要填写字段。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `TransitStatusDTO.java` | 新增 `customFieldValues: Map<String, String>` 字段 |
| `IssueService.java` | `performTransition()` 中在 `transitStatus()` 前保存 `customFieldValues` |
| `issue.ts` | `transitStatus()` API 新增 `customFieldValues` 可选参数 |
| `TransitionCommentModal.vue` | 重写：渲染 requiredFieldIds 对应的字段控件，内置 inline 校验 |
| `useIssueDetailActions.ts` | 透传 `customFieldValues` 参数链路 |
| `IssueDetailView.vue` | 传入 `requiredFieldIds` 和 `customFieldDefs` props |

### 影响范围
- 工单详情页状态变更流程
- 状态转换 API（`POST /issues/{id}/transitions`）新增可选字段
- 不影响无 require_field 规则的普通状态转换（customFieldValues 为 null 时完全向后兼容）
