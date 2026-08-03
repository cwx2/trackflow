---
inclusion: auto
---

# 开发环境注意事项

## 技术问题先查官方文档（强制）

遇到框架/库的报错、废弃警告、配置问题时，**必须先查阅官方文档或 GitHub Issues**，不要凭经验猜测解决方案。

**流程**：
1. 先用 `web_search` 搜索官方文档或 GitHub Issues
2. 用 `web_fetch` 获取官方迁移指南或解决方案
3. 按照官方推荐的方式修复
4. 如果官方没有明确指南，再尝试社区方案

**常见需要查官方文档的场景**：
- TypeScript 配置废弃警告（tsconfig.json 选项变更）
- Vue/Vite 版本升级问题
- 第三方库（Vue Flow、Arco Design 等）的兼容性问题
- Node.js/npm 版本相关问题

**反例**：不要看到报错就凭经验改配置，结果改出更多问题（如 baseUrl → node → bundler 来回改）。

---

## PowerShell 中文输出不可靠

PowerShell 控制台无法正确显示 UTF-8 中文字符（会显示为 `é èµµ` 等乱码）。

**规则**：当需要验证 API 返回值或命令输出中是否包含正确中文时，**禁止**直接依赖 PowerShell 终端输出判断。必须采用以下方式之一：

1. 将 HTTP 响应原始字节写入文件，再用 `readFile` 工具读取：
   ```powershell
   $raw = Invoke-WebRequest -UseBasicParsing -Uri $url -Headers $headers
   [System.IO.File]::WriteAllBytes("temp_result.json", $raw.RawContentStream.ToArray())
   ```
2. 使用 Playwright `browser_evaluate` 在浏览器环境中验证（浏览器正确支持 UTF-8）
3. 使用 `mcp_postgres_query` 直接查询数据库（返回值正确编码）

**绝对不要**因为 PowerShell 输出乱码就判定"数据有问题"。

## Keycloak 字段映射

| Keycloak 字段 | JWT Claim | 含义 | 示例 |
|---------------|-----------|------|------|
| `firstName` | `given_name` | 名（given name） | 静 |
| `lastName` | `family_name` | 姓（family name） | 赵 |
| （自动拼接） | `name` | `firstName + " " + lastName` | 静 赵 |
| `username` | `preferred_username` | 登录用户名 | zhaojing |
| `email` | `email` | 邮箱 | zhaojing@company.com |

**CJK 姓名处理规则**：
- **不要**直接使用 JWT `name` claim 展示中文姓名（格式为"名 姓"带空格）
- 正确做法：从 `family_name`（姓）+ `given_name`（名）拼接，无空格
- 前端：`buildDisplayName()` in `src/utils/jwt.ts`
- 后端：`UserSyncService.buildDisplayName()` 静态方法

**realm JSON 中的用户配置**（`trackflow-server/keycloak/trackflow-realm.json`）：
- `firstName` 填"名"（如"静"）
- `lastName` 填"姓"（如"赵"）
- 这是 Keycloak 的标准定义，不要反过来填

## Java 编译流程

项目没有 Maven wrapper，自动化统一通过 `trackflow-test` MCP 的 `build_backend` 使用 JDK 21 和现有依赖 classpath 完整编译后端，不依赖 IntelliJ IDEA。

**关键约束**：
- `build_backend` 先编译到临时目录，成功并校验入口 class 后才原子更新 `target/classes`
- 相同源代码和依赖指纹会命中构建缓存，禁止重复执行同一构建
- `restart_backend` 会自动调用构建并在构建失败时停止，不会启动旧 class

**构建与校验**：

⛔ **禁止使用 PowerShell 命令检查 class 文件**（`Test-Path`、`Get-Item`、`Get-ChildItem` 等检查 `target/classes` 的操作全部禁止）。这类命令极易因路径问题阻塞等待输入，在无人值守模式下造成长时间卡死。

✅ **正确做法——使用 MCP 工具**：
```
# 完整构建本轮 Java 变更
mcp_trackflow_test_build_backend(java_files=["com/trackflow/report/entity/ReportType.java"])

# 构建后重启后端
mcp_trackflow_test_restart_backend()

# 只在需要诊断时检查 class 时间戳
mcp_trackflow_test_check_compilation_batch(java_files=["com/trackflow/issue/service/IssueService.java"])
```

构建失败应报告为 `TEST_ENVIRONMENT_FAILURE`，不输出 `FIX_DONE`，也不通过重复检查等待 IDEA 编译。

## Shell 命令防阻塞规则（强制）

> ⛔ **铁律：任何 PowerShell 命令都不允许产生交互式等待。违反此规则会导致无人值守模式下进程永久卡死。**

### 绝对禁止的命令模式（零容忍）

以下模式**无论什么理由都不得使用**：

1. **`Test-Path` 的参数来自变量或表达式** — 变量为空时 PS 提示 `Path[0]:` 永久阻塞
2. **多步逻辑写成单行 PowerShell** — 中间步骤失败导致后续变量为空，触发上述阻塞
3. **检查 `target/classes` 下的 `.class` 文件** — 已在"Java 编译限制"中明确禁止
4. **任何使用 `Read-Host`、`Pause`、`$host.UI.Prompt*` 的命令**
5. **`git commit` 不带 `-m`** — 会打开编辑器永久阻塞
6. **`git rebase -i`** — 交互式 rebase 需要编辑器
7. **`git merge` 有冲突时不带 `--abort`** — 等待手动解决
8. **`npm init` 不带 `-y`** — 逐项询问
9. **`npx create-*` 不带 `--yes` 或 `--default`** — 交互式向导
10. **`ssh`、`telnet`、`ftp`** — 交互式连接
11. **`vim`、`nano`、`code --wait`** — 编辑器
12. **`python`/`node` 不带脚本参数** — 进入 REPL
13. **`docker run` 不带 `-d` 且不带 `--rm`** — 可能前台阻塞
14. **`psql`/`redis-cli` 不带 `-c` 参数** — 进入交互模式
15. **`Set-Content`/`Add-Content` 用于修改源代码文件** — 变量为空时提示 `Value[0]:` 永久阻塞
16. **`Get-Content` + 变量赋值 + `Set-Content` 的多步文件修改模式** — 任何一步变量为空都会导致后续 cmdlet 阻塞等待输入
17. **`taskkill /F /IM chrome.exe`** — 会杀掉所有 worker 的独立 Chrome 实例，破坏并行隔离机制

## 浏览器进程管理规则（并行模式）

在 `auto_iterate_parallel.py` 并行模式下，每个 worker 有独立的 Playwright 实例和 Chrome user-data-dir。

**绝对禁止**：
- ❌ `taskkill /F /IM chrome.exe` — 这会杀掉**所有** Chrome 进程，包括其他 worker 的
- ❌ 自己判断"浏览器被占用"然后等待 — 各 worker 独立，不存在共享冲突
- ❌ 看到其他 worker 在操作浏览器就等待 — 互不干扰

**正确做法**：
- ✅ 如果 Playwright 工具返回 `Completed`，说明成功，继续下一步
- ✅ 如果不确定浏览器是否可用，调用 `check_browser_available` 工具检查
- ✅ 操作失败时最多重试 3 次，每次间隔 2-3 秒，然后报错退出

### 文件修改必须用 Kiro 原生工具（强制）

> ⛔ **禁止用 PowerShell 命令（`Set-Content`、`Add-Content`、`Out-File`、重定向 `>`）修改源代码文件。**
> 这些命令在变量为空、路径错误、编码问题时都可能阻塞或损坏文件。

| 需求 | ❌ 不要这样做 | ✅ 正确做法 |
|------|-------------|-----------|
| 替换文件中的文本 | `(Get-Content).Replace(...) \| Set-Content` | 用 `str_replace` 工具 |
| 向文件追加内容 | `Add-Content $path $text` | 用 `fs_append` 工具 |
| 创建/覆盖写文件 | `Set-Content $path $text` / `$text > $path` | 用 `fs_write` 工具 |
| 批量替换多处 | PowerShell 循环 + `-replace` | 多次调用 `str_replace` 工具 |
| 读取文件内容 | `Get-Content $path` | 用 `read_file` 工具 |

**唯一允许的 PowerShell 文件操作**：
- `Remove-Item -Force` 删除文件（但优先用 `delete_file` 工具）
- `Copy-Item` 复制文件（无交互风险）
- `Get-ChildItem` 列目录（但优先用 `list_directory` 工具）

### 常见命令的安全写法

| 场景 | ❌ 会阻塞 | ✅ 安全写法 |
|------|----------|-----------|
| git 提交 | `git commit` | `git commit -m "msg"` |
| git 标签 | `git tag -a v1.0` | `git tag -a v1.0 -m "msg"` |
| npm 创建 | `npm init` | `npm init -y` |
| 安装询问 | `npm install` 某些包提示 | 加 `--yes` 或环境变量 `CI=true` |
| PostgreSQL | `psql` | `psql -c "SELECT 1"` 或用 MCP 工具 |
| Redis | `redis-cli` | `redis-cli PING` 或用 MCP 工具 |
| Docker | `docker run ubuntu` | `docker run -d ubuntu` 或 `docker run --rm ubuntu echo ok` |
| Python | `python` | `python -c "print('ok')"` 或 `python script.py` |

### 替代方案速查表

| 需求 | ❌ 不要这样做 | ✅ 正确做法 |
|------|-------------|-----------|
| 检查文件是否存在 | `Test-Path $var` | 用 `list_directory` 工具 |
| 检查 class 是否已编译 | PowerShell 比较时间戳 | 等 3 秒后直接操作，失败再重试 |
| 获取文件修改时间 | `(Get-Item $path).LastWriteTime` | 不需要。信任 IDEA auto-build |
| 删除文件/目录 | `Remove-Item` 无 `-Force` | 加 `-Force -Confirm:$false` |
| 确认执行策略 | `Set-ExecutionPolicy` 无 `-Force` | 加 `-Force` 或别调用 |

### 安全命令编写规则

1. **每条命令独立简单**：一条 `execute_pwsh` 只做一件事。不要用 `;` 或 `|` 串接可能失败的多步操作。
2. **所有路径用字面字符串**：路径直接写死在命令里，不要通过变量间接引用。
3. **加超时**：任何不确定执行时长的命令必须设 `timeout`（建议 10000ms）。
4. **加 ErrorAction**：可能失败的命令统一加 `-ErrorAction SilentlyContinue`。
5. **优先用 Kiro 原生工具**：`list_directory`、`read_file`、`grep_search` 等工具不会阻塞，能完成的事就不要用 PowerShell。
