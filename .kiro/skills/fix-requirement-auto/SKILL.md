---
name: fix-requirement-auto
description: 脚本自动化模式下的需求处理技能。由 auto_iterate_parallel.py 脚本驱动，测试和审核由外部脚本在本会话结束后独立执行并将结果回传。专注于"分析→修复→提交"三件事。
---

# TrackFlow 需求处理技能（脚本自动化版）

## ⚠️ 铁律：你只做三件事

> **分析问题 → 修复代码 → git commit → 输出 FIX_DONE**

任何超出这三件事的行为都是违规：

| 禁止行为 | 原因 |
|----------|------|
| ❌ 自己跑 e2e 测试 | 测试由外部 e2e-test 会话执行，你的结果不可信 |
| ❌ 自己做 code review | 审核由外部 code-review 会话执行 |
| ❌ git push | push 由脚本在测试+审核全通过后统一执行 |
| ❌ 归档需求文件 | 归档由脚本统一执行，禁止调用 move_requirement |
| ❌ 新建 git 分支 | 直接提交到 main |
| ❌ git add . / git add -A | 多进程并发，只 stage 本需求文件 |

**违反以上任何一条，都会破坏整个自动化流程。**

---

## 运行模式说明

你由 `auto_iterate_parallel.py` 脚本驱动，完整流程如下：

```
① 脚本启动本会话（你）
      ↓
② 你完成：读需求 → 分析 → 写代码 → git commit → 输出 FIX_DONE
      ↓  本会话暂停（等待外部结果）
③ 脚本独立启动 e2e-test 会话执行测试
      ↓
  ├─ 测试通过 → 进入审核
  └─ 测试失败 → 脚本通过 --resume-id 恢复本会话（你）
                  ↓
              你收到测试失败摘要，修复代码，git commit，输出 FIX_DONE
                  ↓  循环，最多 3 轮
④ 脚本独立启动 code-review 会话执行审核
      ↓
  ├─ 审核通过 → 脚本 git push + 归档
  └─ 审核失败（有 MUST 问题）→ 脚本通过 --resume-id 恢复本会话（你）
                               ↓
                           你收到 MUST 问题清单，修复代码，git commit，输出 FIX_DONE
                               ↓  循环，最多 2 轮
```

**你在整个流程中只负责"修代码"这一环，测试和审核的执行完全不归你管。**

---

## 任务类型判定

| 类型 | 触发词 | commit 前缀 |
|------|--------|-------------|
| Bug 修复 | "修需求"、"处理 REQ-X" | `fix` |
| 新功能 | 需求类型=Feature | `feat` |
| 架构改进 | "处理架构建议"、"优化" | `refactor` |

---

## 工作流程（六步，必须按顺序执行）

### 第一步：读取需求

需求文件路径由脚本在 prompt 中指定，格式为 `requirements/working/{worker-id}/requirement-{N}.md`。

1. 读取需求文件完整内容
2. 理解：问题描述、复现步骤、期望行为、验收标准
3. **如果是子需求**（文件名含 `-1`、`-2`）：
   - 先读父需求文件，了解拆解背景
   - 参考已完成的前序子需求（`implement/` 目录）保持命名/风格一致

### 第二步：需求合理性评估

读取需求后立即评估，以下情况直接输出 `FIX_BLOCKED: {原因}` 并结束：

- 需求描述自相矛盾、验收标准无法验证
- 依赖尚未实现的前置模块
- 要求做与项目管理完全无关的功能

Bug 修复类通常默认合理，直接进入第三步。

### 第三步：技术分析

**不急于写代码**，先追踪数据流：

**3.1 研读参考资料**

- **YouTrack 文档（本地优先）**：
  
  **本地离线文档**已下载到 `youtrack-docs/` 目录，包含 600+ 页 Markdown 和 3000+ 张截图。
  
  **查阅方式**：用 `read_file` 读取 `youtrack-docs/pages/{topic}.html.md`
  
  **常用文档速查**：
  | 主题 | 本地文档文件 |
  |------|-------------|
  | Issue 列表/详情 | `issues-list.html.md`, `issue-full-page-view.html.md` |
  | Sprint/看板 | `manage-sprints.html.md`, `agile-board.html.md` |
  | 搜索/报表 | `attribute-based-search.html.md`, `working-with-reports.html.md` |
  | 自定义字段/工作流 | `custom-fields.html.md`, `workflow-tutorial.html.md` |
  
  **本地文档不足时**才上网：`web_search("site:jetbrains.com/help/youtrack/server {关键词}")`

- 查阅 `openproject/` 目录对应模块的代码实现
- YouTrack 决定"做成什么样"，OpenProject 参考"怎么写代码"

**3.2 追踪数据流**
```
前端组件 → API → Controller → Service → Mapper → DB
```
找到断裂点，确认修改范围。

**3.3 复现验证（可选）**
如需浏览器验证，新建独立 tab 操作，操作完关闭 tab：
```
browser_tabs(action: "new", url: "http://localhost:3000")
```

> ⚠️ **浏览器可用性判断规则（严格遵守）**：
> - Playwright 工具返回 `Completed in X.Xs` = **成功**，立即继续下一步
> - 5 秒左右完成是**正常耗时**，不是"被占用"的信号
> - 每个 worker 有独立 Playwright 端口和 Chrome 实例，**互不干扰**
> - 看到其他 worker 在操作浏览器 ≠ 你的浏览器被占用
> - **禁止**在操作返回 "Completed" 后说"浏览器锁定"或"被占用"
> - 不确定时调用 `check_browser_available` 工具检查，**相信工具返回结果**

### 第四步：修复代码

**编码规范**（参照 java-coding-standards.md 和 frontend-coding-standards.md）：
- Controller 只编排，业务逻辑在 Service
- VO 的 ID 字段为 String，用 MapStruct Converter 转换
- 新增 API 必须有 `@PreAuthorize`
- 前端 API 从 `@/api` 统一导入

**数据库迁移脚本版本号查询**（有表结构变更时）：
```powershell
Get-ChildItem trackflow-server/src/main/resources/db/migration/*.sql | Sort-Object { [int]($_.Name -replace 'V(\d+)__.*','$1') } | Select-Object -Last 1
```
版本号 = 最新 + 1。

**修复后自检**：
- `getDiagnostics` 确认无编译/类型错误
- VO/DTO/Entity 字段映射一致
- 前端类型定义与后端 VO 匹配

**后端重启判断**：

| 需要重启 | 不需要重启 |
|----------|-----------|
| 新增 Spring Bean 类 | 已有类方法体修改 |
| 新增 Flyway 迁移 | 新增/修改 DTO/VO/Entity |
| 修改 SecurityConfig | 修改 application-dev.yml |

需要重启时，按以下顺序操作：

**第一步：通过 MCP 完整构建后端**
```
# 完整构建并校验本轮修改的 Java 文件
mcp_trackflow_test_build_backend(java_files=["修改的文件路径"])
```
- 返回 ✅ 构建完成 → 进入第二步
- 返回构建缓存命中 → 进入第二步，不重复编译
- 返回 ❌ 构建失败 → 标记 `TEST_ENVIRONMENT_FAILURE`，停止本轮，不提交无可运行产物的修复，不重复重试同一构建错误

**第二步：调用重启工具**
```
mcp_trackflow_test_restart_backend()
```
工具会再次命中构建缓存，然后使用已校验的 `target/classes` 启动后端，日志写入 `trackflow-server/logs/backend.log`。

> ⛔ **严禁以下行为**：
> - 绕过 `build_backend` 直接启动后端
> - 构建失败后继续执行 Playwright 或提交“已完成”状态
> - 重复执行同一份源代码的完整构建（工具会自动使用缓存）
>
> `build_backend` 是自动化构建入口，不依赖 IDEA；构建失败属于环境/构建阻塞，必须停止本轮。

### 第五步：变更级自检（commit 前必做，不重复跑完整 e2e）

> 外部脚本会独立执行完整 `e2e-test`。本阶段只验证本次变更能安全提交，禁止再次完整走一遍验收路径。

必须完成：

1. 对修改文件执行 `getDiagnostics` 或等价的静态检查，确认没有编译/类型错误。
2. 对本次变更涉及的关键方法做最小化验证：
   - 数据修复：用只读查询确认目标数据和幂等条件；
   - API/后端逻辑：只验证本次新增或修改的接口/路径；
   - 前端交互：只验证变更组件能加载、关键请求参数正确。
3. 检查 `git diff` 和精确 staged 文件清单，确认没有无关改动。
4. 如果最小化验证发现真实失败，修复后再提交；如果只是外部服务不可用，记录为环境阻塞，不要反复重试完整流程。

禁止在此阶段执行：

- 完整浏览器验收、全量回归、重复截图；这些由外部 `e2e-test` 会话负责。
- 为了“再确认一次”重复已完成的测试路径。
- 在没有证据时声称验证通过。

### 第六步：git commit（精确 stage，禁止 add .）

**多进程并发环境，stage 文件必须精确。**

commit 步骤：

1. `git status` 查看当前改动
2. 逐个 `git add {文件路径}` 只 stage 本需求相关文件
   - ✅ 本需求修改/新增的源文件、迁移脚本、前端组件
   - ❌ 禁止 `git add .` 或 `git add -A`
3. `git diff --cached --name-only` 确认 staged 列表只含本需求文件
4. 执行 commit，message 格式：
   ```
   {类型}(模块名): 简短描述

   - 修复/实现了什么
   - 关键改动点

   Closes REQ-{编号}
   ```
5. 打印本次 commit 文件清单：`git diff HEAD~1 --name-only`

commit 完成后，必须按以下顺序完成三件事，然后**立即输出 `FIX_DONE`**：

**① 更新需求文件中的「自动化状态」区块**（机器解析用，脚本靠它驱动流转）。

在需求文件末尾找到 `## 自动化状态` 区块并更新（如果不存在则追加）：
```
## 自动化状态

fix_status: DONE
fix_commit: {本次 commit 的 7 位 hash}
fix_round: {当前是第几轮修复，首次为 1}
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0
```
- `fix_round` 首次为 1，每次收到测试/审核反馈再次 commit 后递增（如第 2 次反馈修复后为 2）
- `test_status` / `review_status` 此时保持 `PENDING`，由 e2e-test / code-review 会话负责更新
- 使用文件写工具（`strReplace`）更新，不要用 echo/PowerShell 写入

**② 更新需求文件中的「Agent 交接上下文」章节**（见下方模板），写入本次改动的完整上下文。这是下游 e2e-test 和 code-review 会话的唯一信息来源，**必须写，不可省略**。

**③ 追加修复记录**（见下方修复记录模板）。

> ⛔ commit 后不要做任何其他事情（不测试、不审核、不 push、不归档）。输出 FIX_DONE 后本轮结束。

---

## 收到外部反馈时（resume 模式）

当脚本通过 `--resume-id` 恢复本会话时，你会收到以下两种反馈之一：

### 情况 A：测试失败摘要

```
端到端测试失败（第 N 轮），以下用例未通过，请根据失败信息修复代码：

[失败摘要内容]

修复完成后请输出 FIX_DONE。
```

处理方式：
1. 仔细阅读每条失败信息，定位根因
2. **只修复失败的部分**，不要重构无关代码
3. `getDiagnostics` 确认无错误
4. `git commit`（追加 commit，不要 amend）
5. 更新需求文件 `## 自动化状态` 区块：`fix_status: DONE`，`fix_commit` 更新为新 hash，`fix_round` 递增，`test_status: PENDING`（重置，等待下轮测试）
6. 更新「Agent 交接上下文」（反映本轮修复内容）
7. 输出 `FIX_DONE`

### 情况 B：审核 MUST 问题

```
代码审核发现 MUST 级问题（第 N 轮），请修复以下问题后重新 commit：

[MUST 问题清单]

修复完成后请输出 FIX_DONE。
```

处理方式：
1. 逐条处理 MUST 问题（SHOULD 级可选处理）
2. **不要重新读需求文件、不要重新分析技术方案**，直接针对问题修复
3. `getDiagnostics` 确认无错误
4. `git commit`（追加 commit）
5. 更新需求文件 `## 自动化状态` 区块：`fix_status: DONE`，`fix_commit` 更新为新 hash，`fix_round` 递增，`review_status: PENDING`（重置，等待下轮审核）
6. 更新「Agent 交接上下文」（反映本轮修复内容）
7. 输出 `FIX_DONE`

> ⚠️ resume 时**不要**重走第一步到第三步的完整流程，直接针对反馈内容修复。

---

## Agent 交接上下文模板

每次 commit 完成后，在需求文件末尾**覆盖更新**（不是追加，每轮替换最新内容）此章节：

```markdown

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：YYYY-MM-DD HH:MM

### 本次改动摘要
（用人话描述，不是 diff，说清楚改了什么逻辑、为什么这样改）
- 改动1：[文件] — [做了什么，为什么]
- 改动2：[文件] — [做了什么，为什么]

### 本次变更文件清单
（从 git diff HEAD~1 --name-only 复制）
- `path/to/file1`
- `path/to/file2`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. [操作路径描述，例：以 testuser 登录 → 打开项目 DE4 → 创建 Issue → 验证字段保存]
  2. [另一条路径]
- **边界场景**：
  - [场景描述，例：空标题提交应报错]
- **建议测试账号**：[角色/用户名，例：开发人员 wangqiang]
- **注意事项**：[本次改动可能影响的其他功能点]

### 审核重点（给 code-review 会话）
- **重点关注文件**：[文件列表，例：IssueService.java, issue.ts]
- **潜在风险点**：[描述，例：新增了权限判断分支，需确认所有角色场景]
- **已知遗留项**：[本次未处理的 SHOULD 级问题，例：暂未加分页，下次迭代处理]
```

**写作要求**：
- 测试路径要具体到操作步骤，不能只写"测试 Issue 创建"
- 每次 resume 修复后也要更新此章节（反映最新改动）
- 如果是多轮修复，在摘要里说明"第N轮修复，修了什么"

---

## 修复记录模板

commit 完成后在需求文件末尾追加：

```markdown

======================

## 修复记录

**修复日期**：YYYY-MM-DD
**修复人**：AI Agent（auto 模式）

### 根因分析
[技术根因]

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `path/file` | 改了什么 |

### 影响范围
- [影响的模块/页面]
```

---

## 测试账号

所有密码由本地环境变量 `TRACKFLOW_TEST_PASSWORD` 提供，禁止写入需求、日志或提交。

| 角色 | 用户名 |
|------|--------|
| 超级管理员 | testuser |
| 技术负责人 | zhangwei / zhoujie |
| 开发人员 | wangqiang / liuyang |
| 产品经理 | sunlei / yangmin |
| 测试人员 | zhaojing / chenfei |
| 观察者 | huanglei / wumin |

---

## 关键约束汇总

| 约束 | 说明 |
|------|------|
| ❌ 禁止简化实现 | 企业级标准，一步到位 |
| ❌ 禁止只修表面 | 追溯根因，同类问题一并修 |
| ❌ 禁止给 /api/v1/ 添加 permitAll | 所有 API 必须认证 |
| ❌ 禁止新建 git 分支 | 直接提交到 main |
| ❌ 禁止 git add . / git add -A | 精确 stage，只含本需求文件 |
| ❌ 禁止自己跑测试 | 测试由外部 e2e-test 会话执行 |
| ❌ 禁止自己做审核 | 审核由外部 code-review 会话执行 |
| ❌ 禁止 git push | push 由脚本在测试+审核全通过后统一执行 |
| ❌ 禁止归档需求文件 | 归档由脚本统一执行 |
| ✅ 修复完必须 git commit | 不能只改文件不提交 |
| ✅ 新增 API 必须加 @PreAuthorize | 无一例外 |
| ✅ 需要后端重启时调用 restart_backend | 直接调工具，工具负责等待编译，禁止手动编译或改 pom.xml |
| ✅ 每轮修复结束后必须输出 FIX_DONE | 脚本依赖此标记判断本轮完成 |

---

## 输出结束标记（机器解析用）

**每次完成一轮修复并 commit 之后**，必须在输出最后一行单独输出以下标记之一：

- 正常完成修复并已 commit：
  `FIX_DONE`

- 需求不合理/依赖缺失，无法执行：
  `FIX_BLOCKED: {原因}`

**格式要求**：
- 单独一行，前后无空格、无标点、无 markdown 格式
- 放在整个输出的**最后一行**
- 首次完成要输出，每次收到反馈修复后**也要输出**
- 缺少此标记会导致脚本无法判断本轮是否完成，整个流程将卡住
