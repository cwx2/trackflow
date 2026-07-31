"""
TrackFlow 并行迭代脚本（永不停止）

三类线程完全解耦并行运行，通过文件目录队列通信：
  生产者 → review/ → 审核者 → develop/ → 消费者 → implement/

线程角色：
  生产者（N）：找需求 → 写到 review/，develop 充足时休眠
  审核者（1）：监听 review/ → 分批审核（每批 5 个，每批新会话）→ 写到 develop/
  消费者（M）：从 develop/ 领取需求 → 修需求+测试+代码审核 → 归档到 implement/

防冲突机制：
  - 消费阶段：worker 通过"领取"机制（原子 rename 到 working/）防止重复处理
  - 审核阶段：全局 _review_lock，审核者线程唯一，天然无冲突
  - 重试计数：全局 _retry_counts + _retry_lock，超过 MAX_RETRIES 次移到 rejected/
  - 浏览器隔离：每个 worker 通过 KIRO_HOME 指向独立 .kiro 目录，独立 Playwright 端口

worker 分配规则（--workers N 自动分配，审核者不占配额）：
  workers=2 → 1 生产者 + 1 消费者 + 1 审核者
  workers=3 → 1 生产者 + 2 消费者 + 1 审核者
  workers=4 → 1 生产者 + 3 消费者 + 1 审核者
  workers=6 → 2 生产者 + 4 消费者 + 1 审核者
  workers=8 → 2 生产者 + 6 消费者 + 1 审核者

用法：
  python scripts/auto_iterate_parallel.py                              # 2 worker（1+1），永不停止
  python scripts/auto_iterate_parallel.py --workers 4                  # 自动分配：1 生产者 + 3 消费者
  python scripts/auto_iterate_parallel.py --producers 1 --consumers 3  # 手动指定
  python scripts/auto_iterate_parallel.py --skip-produce               # 只跑消费者，不生产不审核
"""

import subprocess
import time
import logging
import argparse
import re
import os
import random
import shutil
import threading
import json
import socket
from pathlib import Path

# ============ 配置 ============

WORKSPACE = Path(__file__).parent.parent
REQUIREMENTS_BASE = WORKSPACE / "requirements"
REVIEW_DIR = REQUIREMENTS_BASE / "review"
DEVELOP_DIR = REQUIREMENTS_BASE / "develop"
IMPLEMENT_DIR = REQUIREMENTS_BASE / "implement"
REJECTED_DIR = REQUIREMENTS_BASE / "rejected"
WORKING_DIR = REQUIREMENTS_BASE / "working"
KIRO_CLI = "kiro-cli"

# 每个 worker 专属的 Playwright MCP SSE 端口起始值
# worker-1 → 9101, worker-2 → 9102, ...
PLAYWRIGHT_PORT_BASE = 9100

SCREENSHOT_DIR = WORKSPACE / "test"   # Playwright 截图输出目录

for d in [REVIEW_DIR, DEVELOP_DIR, IMPLEMENT_DIR, REJECTED_DIR, WORKING_DIR]:
    d.mkdir(parents=True, exist_ok=True)

# 阈值
MIN_DEVELOP_QUEUE = 6       # develop 低于此数时触发生产
TIMEOUT_SECONDS = 2400      # 单次 kiro-cli 超时（40分钟）
MAX_RETRIES = 5           # 真正的处理失败（非启动失败）才计入，超过5次才放弃
COOLDOWN_SECONDS = 5
MAX_TEST_RETRIES = 3        # 测试最多重试轮数
MAX_REVIEW_RETRIES = 2      # 审核最多重试轮数

# 模型配置（None = 使用 kiro-cli 默认模型）
# 可选值：claude-sonnet-4.6 / claude-opus-4.5 / claude-sonnet-4.5 / auto
KIRO_MODEL = "claude-sonnet-4.6"            # 默认模型（测试/审核/生产用）
KIRO_MODEL_FIX = "claude-opus-4.5"          # 修需求用更强模型（分析+写代码）

# 领取锁
_claim_lock = threading.Lock()

# Skill 声明
SKILLS = {
    "write-requirement": {"path": ".kiro/skills/write-requirement/SKILL.md"},
    "tech-requirement": {"path": ".kiro/skills/tech-requirement/SKILL.md"},
    "review-requirement": {"path": ".kiro/skills/review-requirement/SKILL.md"},
    "fix-requirement": {"path": ".kiro/skills/fix-requirement/SKILL.md"},
    "fix-requirement-auto": {"path": ".kiro/skills/fix-requirement-auto/SKILL.md"},
    "e2e-test": {"path": ".kiro/skills/e2e-test/SKILL.md"},
    "code-review": {"path": ".kiro/skills/code-review/SKILL.md"},
}

# ============ 角色工作流加载 ============
# 工作流文件位于 scripts/role-workflows/，每个文件描述一个角色的详细日常操作步骤。
# 运行时动态读取文件内容，嵌入到生产者 prompt 中，让 AI 代理像真实员工一样操作系统。

ROLE_WORKFLOW_DIR = Path(__file__).parent / "role-workflows"


def load_workflow(filename: str) -> str:
    """读取角色工作流文件内容，失败时返回空字符串"""
    path = ROLE_WORKFLOW_DIR / filename
    try:
        return path.read_text(encoding="utf-8")
    except Exception as e:
        log.warning(f"[workflow] 读取 {filename} 失败: {e}")
        return ""


def make_workflow_prompt(workflow_file: str, workflow_section: str = "", skill: str = "write-requirement") -> dict:
    """
    构造一个引用角色工作流的生产者配置。
    workflow_file: 工作流文件名（如 developer.md）
    workflow_section: 留空表示使用整个文件内容
    """
    return {
        "skill": skill,
        "workflow_file": workflow_file,
        "workflow_section": workflow_section,
    }


# 生产者配置池 —— 按角色工作流驱动
# 每个配置对应 scripts/role-workflows/ 下一个角色文件。
# 运行时动态读取工作流文件内容作为 prompt，让 AI 代理像真实员工一样操作系统。
PRODUCER_CONFIGS = [
    # 每个角色配置多条，同一文件重复出现 = 权重更高（每次随机选账号+情况，不会重复）
    make_workflow_prompt("developer.md"),
    make_workflow_prompt("developer.md"),
    make_workflow_prompt("developer.md"),
    make_workflow_prompt("tech_lead.md"),
    make_workflow_prompt("tech_lead.md"),
    make_workflow_prompt("product_manager.md"),
    make_workflow_prompt("product_manager.md"),
    make_workflow_prompt("tester.md"),
    make_workflow_prompt("tester.md"),
    make_workflow_prompt("admin.md"),
    make_workflow_prompt("observer.md"),
    make_workflow_prompt("observer.md"),
]

# ============ 日志 ============

from logging.handlers import TimedRotatingFileHandler

_LOG_DIR = WORKSPACE / "scripts" / "log"
_LOG_DIR.mkdir(parents=True, exist_ok=True)

_log_formatter = logging.Formatter("%(asctime)s [%(levelname)s] [%(threadName)s] %(message)s")

_console_handler = logging.StreamHandler()
_console_handler.setLevel(logging.INFO)   # 控制台只显示 INFO 及以上
_console_handler.setFormatter(_log_formatter)

# 按天轮转，文件名格式：auto_iterate_parallel.2026-07-31.log
_file_handler = TimedRotatingFileHandler(
    _LOG_DIR / "auto_iterate_parallel.log",
    when="midnight",        # 每天 0 点滚动
    interval=1,
    backupCount=30,         # 保留最近 30 天
    encoding="utf-8",
    utc=False,
)
_file_handler.suffix = "%Y-%m-%d"          # 轮转后文件名后缀：.2026-07-31
_file_handler.setLevel(logging.DEBUG)      # 文件记录所有会话输出行（DEBUG）
_file_handler.setFormatter(_log_formatter)

logging.basicConfig(level=logging.DEBUG, handlers=[_console_handler, _file_handler])
log = logging.getLogger(__name__)

# 确保 root logger 级别和 handler 生效（basicConfig 在已有 handler 时不覆盖）
_root_logger = logging.getLogger()
_root_logger.setLevel(logging.DEBUG)
if not any(isinstance(h, TimedRotatingFileHandler) for h in _root_logger.handlers):
    _root_logger.addHandler(_file_handler)
if not any(isinstance(h, logging.StreamHandler) and not isinstance(h, TimedRotatingFileHandler)
           for h in _root_logger.handlers):
    _root_logger.addHandler(_console_handler)

# ANSI 转义码过滤（日志文件写入前清除颜色控制字符）
import re as _re
_ANSI_RE = _re.compile(r'\x1b\[[0-9;?]*[A-Za-z]|\x1b\][^\x07]*\x07|\x1b[()][A-B0-2]')

def strip_ansi(text: str) -> str:
    """移除字符串中所有 ANSI 转义码"""
    return _ANSI_RE.sub('', text)

# ============ Playwright 多实例管理 ============

# 全局注册表：worker_id → subprocess.Popen（Playwright MCP 进程）
_playwright_processes: dict[str, subprocess.Popen] = {}
_playwright_lock = threading.Lock()


def get_worker_port(worker_id: str) -> int:
    """根据 worker_id 分配固定端口，如 producer-1→9101, consumer-2→9102"""
    # 从 worker_id 中提取序号
    match = re.search(r"(\d+)$", worker_id)
    index = int(match.group(1)) if match else 1
    # producer 和 consumer 错开端口段，避免复用时冲突
    if worker_id.startswith("producer"):
        return PLAYWRIGHT_PORT_BASE + index
    elif worker_id.startswith("consumer"):
        return PLAYWRIGHT_PORT_BASE + 10 + index
    else:
        return PLAYWRIGHT_PORT_BASE + 20 + index


def start_playwright_for_worker(worker_id: str) -> int:
    """
    为指定 worker 启动一个独立的 Playwright MCP SSE 进程。
    返回分配的端口号。若已存在且仍在运行则直接复用，不重复启动。
    端口号由 worker_id 固定映射，整个脚本生命周期内不会新增端口。
    """
    port = get_worker_port(worker_id)
    with _playwright_lock:
        existing = _playwright_processes.get(worker_id)
        if existing and existing.poll() is None:
            # 进程仍在运行，直接复用
            return port

        # 进程不存在或已崩溃，需要（重新）启动
        if existing is not None:
            log.warning(f"[playwright] {worker_id} 的 MCP 进程已退出（code={existing.poll()}），重启...")
            # 等待端口释放，避免 Address already in use
            time.sleep(2)

        # 启动新进程
        # Windows 上 npx 是 .ps1 脚本，subprocess 无法直接调用，需用 npx.cmd
        # --user-data-dir：每个 worker 使用独立的 Chrome 用户数据目录，
        #   彻底隔离浏览器实例，防止多 worker 争抢同一 Chrome 进程（"Browser is already in use"）
        # --isolated 在内存中保持 profile，与 --user-data-dir 配合确保完全隔离
        user_data_dir = WORKSPACE / "scripts" / "worker-envs" / worker_id / "chrome-data"
        cmd = [
            "npx.cmd", "@playwright/mcp@latest",
            "--port", str(port),
            "--isolated",
            "--user-data-dir", str(user_data_dir),
            "--viewport-size=1920x1080",
            f"--output-dir={WORKSPACE / 'test'}",
        ]
        log.info(f"[playwright] 为 {worker_id} 启动独立 MCP，端口 {port}...")
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            cwd=str(WORKSPACE),
        )
        _playwright_processes[worker_id] = proc
        # 等待 SSE 服务就绪（最多 15 秒）
        deadline = time.time() + 15
        while time.time() < deadline:
            try:
                with socket.create_connection(("localhost", port), timeout=1):
                    log.info(f"[playwright] {worker_id} 端口 {port} 就绪")
                    return port
            except OSError:
                time.sleep(0.5)
        log.warning(f"[playwright] {worker_id} 端口 {port} 等待超时，继续尝试...")
        return port


def stop_playwright_for_worker(worker_id: str):
    """停止指定 worker 的 Playwright MCP 进程"""
    with _playwright_lock:
        proc = _playwright_processes.pop(worker_id, None)
        if proc and proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()
            log.info(f"[playwright] {worker_id} 的 MCP 进程已停止")


def kill_stale_playwright_processes() -> None:
    """
    脚本启动时调用：扫描并杀掉所有占用 worker 端口的残留 Playwright MCP 进程。

    重启脚本后 _playwright_processes 字典是空的，但上一次的进程可能仍在运行，
    导致新进程无法绑定端口，kiro-cli 连上的是没有 --user-data-dir 的旧进程。
    通过 psutil 按端口找到残留进程并强制终止，确保新配置生效。
    """
    try:
        import psutil
    except ImportError:
        # psutil 不可用时退化为按进程名匹配（可能误杀前端 dev server 等，谨慎）
        log.warning("[playwright] psutil 未安装，跳过残留进程清理（pip install psutil 可启用）")
        return

    # worker 端口范围：9101-9130（生产者/消费者/审核者）
    worker_ports = set(range(PLAYWRIGHT_PORT_BASE + 1, PLAYWRIGHT_PORT_BASE + 31))
    killed = 0
    for conn in psutil.net_connections(kind="tcp"):
        if conn.laddr.port in worker_ports and conn.status == "LISTEN":
            try:
                proc = psutil.Process(conn.pid)
                # 只杀 playwright MCP 进程，避免误杀其他服务
                cmdline = " ".join(proc.cmdline())
                if "@playwright/mcp" in cmdline or "playwright\\mcp" in cmdline:
                    log.info(f"[playwright] 杀掉残留进程 PID={conn.pid} 端口={conn.laddr.port}")
                    proc.kill()
                    killed += 1
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                pass
    if killed:
        log.info(f"[playwright] 清理完成，共杀掉 {killed} 个残留进程")
        time.sleep(1)   # 等待端口释放


def stop_all_playwright():
    """停止所有 Playwright MCP 进程（脚本退出时调用）"""
    with _playwright_lock:
        for worker_id, proc in list(_playwright_processes.items()):
            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    proc.kill()
        _playwright_processes.clear()
    log.info("[playwright] 所有独立 MCP 进程已停止")


def make_worker_mcp_config(worker_id: str, port: int) -> dict:
    """
    生成 worker 专属的 mcp.json 内容。
    playwright 改为 SSE url 模式连接独立进程，其他 MCP 沿用主配置。
    """
    # 读取主配置
    main_config_path = WORKSPACE / ".kiro" / "settings" / "mcp.json"
    with open(main_config_path, "r", encoding="utf-8") as f:
        config = json.load(f)

    # 替换 playwright 为 SSE url 模式
    playwright_autoApprove = config["mcpServers"].get("playwright", {}).get("autoApprove", [])
    config["mcpServers"]["playwright"] = {
        "url": f"http://localhost:{port}/sse",
        "disabled": False,
        "autoApprove": playwright_autoApprove,
    }
    return config


def setup_worker_kiro_dir(worker_id: str, port: int) -> Path:
    """
    为 worker 创建专属的 .kiro 目录，写入指向独立 Playwright 端口的 mcp.json。
    通过 KIRO_HOME 环境变量让 kiro-cli 使用此目录代替默认的 ~/.kiro。

    目录结构：
      scripts/worker-envs/{worker_id}/.kiro/settings/mcp.json  ← playwright 指向专属端口
      scripts/worker-envs/{worker_id}/.kiro/steering/          ← 软链接到主配置
      scripts/worker-envs/{worker_id}/.kiro/skills/            ← 软链接到主配置

    多次调用幂等：mcp.json 每次覆盖写入（端口不变则内容相同），软链接已存在则跳过。
    """
    worker_env_dir = WORKSPACE / "scripts" / "worker-envs" / worker_id
    kiro_settings_dir = worker_env_dir / ".kiro" / "settings"
    kiro_settings_dir.mkdir(parents=True, exist_ok=True)

    # 写入专属 mcp.json
    mcp_config = make_worker_mcp_config(worker_id, port)
    mcp_path = kiro_settings_dir / "mcp.json"
    with open(mcp_path, "w", encoding="utf-8") as f:
        json.dump(mcp_config, f, indent=2, ensure_ascii=False)

    # 同时复制其他 .kiro/settings/ 文件（steering、skills 等通过 WORKSPACE 软链引用）
    # kiro-cli 还需要 steering 文件，steering 在 .kiro/steering/ 而非 settings/
    # 创建软链接或直接复制 steering 目录
    src_kiro = WORKSPACE / ".kiro"
    dst_kiro = worker_env_dir / ".kiro"

    for subdir in ["steering", "skills"]:
        src = src_kiro / subdir
        dst = dst_kiro / subdir
        if src.exists() and not dst.exists():
            try:
                os.symlink(src, dst)
            except (OSError, NotImplementedError):
                # Windows 可能需要管理员权限创建软链接，fallback 到不复制
                # kiro-cli 会从父目录向上查找，WORKSPACE/.kiro 仍然可见
                pass

    log.debug(f"[worker-env] {worker_id} 配置目录: {worker_env_dir}")
    return worker_env_dir


def cleanup_worker_envs():
    """清理所有 worker 环境目录"""
    env_base = WORKSPACE / "scripts" / "worker-envs"
    if env_base.exists():
        shutil.rmtree(env_base, ignore_errors=True)
        log.info("[worker-env] 已清理所有 worker 环境目录")

# ============ 工具函数 ============


def _sort_key(filename: str) -> tuple:
    match = re.match(r"requirement-(\d+)-(\d+)\.md", filename)
    if match:
        return (int(match.group(1)), int(match.group(2)))
    match = re.match(r"requirement-(\d+)\.md", filename)
    if match:
        return (int(match.group(1)), 0)
    return (0, 0)


def extract_number(filename: str) -> int:
    match = re.search(r"requirement-(\d+)", filename)
    return int(match.group(1)) if match else 0


def is_sub_requirement(filepath: Path) -> bool:
    return bool(re.match(r"requirement-\d+-\d+\.md", filepath.name))


def has_sub_requirements(number: int) -> bool:
    for dir_path in [DEVELOP_DIR, WORKING_DIR, IMPLEMENT_DIR, REJECTED_DIR]:
        if list(dir_path.glob(f"requirement-{number}-*.md")):
            return True
    return False


def list_available(directory: Path) -> list[Path]:
    """列出可消费的需求文件（排除已拆解父需求）"""
    files = list(directory.glob("requirement-*.md"))
    files.sort(key=lambda f: _sort_key(f.name))
    result = []
    for f in files:
        if not is_sub_requirement(f):
            number = extract_number(f.name)
            if has_sub_requirements(number):
                continue
        result.append(f)
    return result


def count_develop() -> int:
    return len(list_available(DEVELOP_DIR))


def extract_title(filepath: Path) -> str:
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip().startswith("#"):
                    return line.strip().lstrip("#").strip()
    except Exception:
        pass
    return filepath.name


# ============ Kiro CLI ============


def run_kiro(prompt: str, label: str, model: str | None = None, worker_id: str | None = None) -> tuple[bool, str]:
    """
    启动一个新的 kiro-cli 会话执行 prompt。

    label: 日志前缀（如 consumer-1、consumer-1-test1）
    model: 指定模型，None 时使用 KIRO_MODEL 默认值
    worker_id: 消费者 worker ID，传入时为该 worker 启动独立 Playwright 进程并设置 KIRO_HOME，
               生产者/审核者不传，共享默认浏览器

    返回 (success, output)，output 为完整 stdout 文本。
    特殊返回值：output="STARTUP_FAIL" 表示 kiro-cli 在 30 秒内异常退出（认证/并发问题）
    """
    cmd = [KIRO_CLI, "chat", "--no-interactive", "--trust-all-tools"]
    effective_model = model or KIRO_MODEL
    if effective_model:
        cmd += ["--model", effective_model]
    cmd.append(prompt)
    start = time.time()
    output_lines = []

    # kiro-cli 始终以项目根目录为 cwd（需要访问源码和 steering 文件）
    cwd = str(WORKSPACE)

    # 环境变量：抑制子进程中命令的交互式行为
    env = os.environ.copy()
    env["GIT_TERMINAL_PROMPT"] = "0"     # git 不弹认证提示
    env["GIT_EDITOR"] = "true"           # git commit 无编辑器（兜底）
    env["EDITOR"] = "true"               # 通用编辑器指向 true（立即退出）
    env["VISUAL"] = "true"
    env["CI"] = "true"                   # 很多工具检测 CI 环境跳过交互
    env["NPM_CONFIG_YES"] = "true"       # npm 自动 yes
    env["DEBIAN_FRONTEND"] = "noninteractive"  # apt 等不提问
    env["NO_COLOR"] = "1"                # 禁用颜色输出（ANSI 转义码）
    env["FORCE_COLOR"] = "0"             # 强制关闭颜色（部分工具识别此变量）
    env["KIRO_LOG_NO_COLOR"] = "1"       # kiro-cli 专属禁色变量

    # 为 worker 设置独立的 KIRO_HOME（独立 Playwright 端口，避免多 worker 共享浏览器）
    if worker_id:
        port = start_playwright_for_worker(worker_id)
        worker_kiro_dir = setup_worker_kiro_dir(worker_id, port)
        env["KIRO_HOME"] = str(worker_kiro_dir / ".kiro")
        log.debug(f"[{worker_id}] KIRO_HOME={env['KIRO_HOME']} playwright_port={port}")

    try:
        process = subprocess.Popen(
            cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, cwd=cwd, encoding="utf-8", errors="replace",
            env=env,
        )
        for line in process.stdout:
            line_stripped = line.rstrip("\n")
            print(f"  [{label}] {line_stripped}")
            log.debug(f"[{label}] {strip_ansi(line_stripped)}")
            output_lines.append(line_stripped)
        process.wait(timeout=TIMEOUT_SECONDS)
        elapsed = time.time() - start
        success = process.returncode == 0
        level = "INFO" if success else "WARNING"
        log.log(logging.getLevelName(level), f"[{label}] {'完成' if success else '失败'} ({elapsed:.0f}s)")
        # 如果进程在 30 秒内就退出，说明 kiro-cli 启动失败（认证/网络/并发问题）
        # 返回特殊标记 "STARTUP_FAIL" 让调用方区分处理
        if not success and elapsed < 30:
            log.warning(f"[{label}] kiro-cli 启动失败（{elapsed:.0f}s），可能是认证过期或并发限制")
            return False, "STARTUP_FAIL"
        return success, "\n".join(output_lines)
    except subprocess.TimeoutExpired:
        process.kill()
        log.error(f"[{label}] 超时")
        return False, "TIMEOUT"
    except Exception as e:
        log.error(f"[{label}] 异常: {e}")
        return False, str(e)


def get_current_commit() -> str:
    """获取当前 HEAD commit hash（7位短 hash）"""
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--short=7", "HEAD"],
            capture_output=True, text=True, cwd=str(WORKSPACE),
            encoding="utf-8", timeout=10
        )
        return result.stdout.strip() if result.returncode == 0 else ""
    except Exception:
        return ""


def get_latest_session_id(req_stem: str | None = None) -> str | None:
    """
    获取最新的 session ID。
    req_stem: 需求文件的 stem（如 'requirement-123'），用于精确匹配本需求的会话。
    多 worker 并发时通过 req_stem 避免拿到别的 worker 的 session。
    """
    try:
        result = subprocess.run(
            [KIRO_CLI, "chat", "--list-sessions", "--format", "json"],
            capture_output=True, text=True, cwd=str(WORKSPACE),
            encoding="utf-8", errors="replace", timeout=15
        )
        # 合并 stdout 和 stderr（kiro-cli 可能输出到 stderr）
        raw = result.stdout + result.stderr
        # 找到 JSON 数组起始位置
        start = raw.find("[")
        if start == -1:
            return None
        sessions = json.loads(raw[start:])
        if not sessions:
            return None
        # 按时间倒序
        sessions.sort(key=lambda s: s.get("updatedAt", ""), reverse=True)
        # 如果传入了需求标识，优先匹配标题包含该需求名的 session
        if req_stem:
            for s in sessions:
                title = s.get("title", "")
                if req_stem in title:
                    return s.get("sessionId")
        # 找不到精确匹配，退回最新一条
        return sessions[0].get("sessionId")
    except Exception as e:
        log.warning(f"[session] 获取 session_id 失败: {e}")
        return None


def run_kiro_resume(session_id: str, prompt: str, label: str, model: str | None = None, worker_id: str | None = None) -> tuple[bool, str]:
    """恢复指定会话并发送消息"""
    cmd = [KIRO_CLI, "chat", "--no-interactive", "--trust-all-tools",
           "--resume-id", session_id]
    effective_model = model or KIRO_MODEL_FIX  # resume 默认用 FIX 模型（回修需求会话）
    if effective_model:
        cmd += ["--model", effective_model]
    cmd.append(prompt)

    start = time.time()
    output_lines = []
    env = os.environ.copy()
    env.update({"GIT_TERMINAL_PROMPT": "0", "GIT_EDITOR": "true",
                 "EDITOR": "true", "VISUAL": "true", "CI": "true",
                 "NPM_CONFIG_YES": "true", "DEBIAN_FRONTEND": "noninteractive",
                 "NO_COLOR": "1", "FORCE_COLOR": "0", "KIRO_LOG_NO_COLOR": "1"})

    # resume 时沿用同一 worker 的独立 KIRO_HOME（保证使用同一 Playwright 进程）
    if worker_id:
        port = start_playwright_for_worker(worker_id)
        worker_kiro_dir = setup_worker_kiro_dir(worker_id, port)
        env["KIRO_HOME"] = str(worker_kiro_dir / ".kiro")
        log.debug(f"[{worker_id}] resume KIRO_HOME={env['KIRO_HOME']}")
    try:
        process = subprocess.Popen(
            cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, cwd=str(WORKSPACE), encoding="utf-8", errors="replace", env=env,
        )
        for line in process.stdout:
            line_stripped = line.rstrip("\n")
            print(f"  [{label}] {line_stripped}")
            log.debug(f"[{label}] {strip_ansi(line_stripped)}")
            output_lines.append(line_stripped)
        process.wait(timeout=TIMEOUT_SECONDS)
        elapsed = time.time() - start
        success = process.returncode == 0
        log.info(f"[{label}] resume {'完成' if success else '失败'} ({elapsed:.0f}s)")
        return success, "\n".join(output_lines)
    except subprocess.TimeoutExpired:
        process.kill()
        log.error(f"[{label}] resume 超时")
        return False, "TIMEOUT"
    except Exception as e:
        log.error(f"[{label}] resume 异常: {e}")
        return False, str(e)


def parse_fix_result(output: str) -> tuple[bool, bool, str]:
    """
    解析 fix-requirement-auto 的输出。
    返回 (done, blocked, detail)
    - done=True: 输出了 FIX_DONE
    - blocked=True: 输出了 FIX_BLOCKED
    """
    clean = strip_ansi(output)
    lines = clean.strip().split("\n")
    for line in reversed(lines):
        line = line.strip()
        if line == "FIX_DONE":
            return True, False, ""
        if line.startswith("FIX_BLOCKED:"):
            reason = line[len("FIX_BLOCKED:"):].strip()
            return False, True, reason
    # 没有明确标记——进程返回 0 但没有标记，当作完成
    return False, False, "\n".join(lines[-10:])


def read_req_status(req_file: Path) -> dict[str, str]:
    """
    读取需求文件中 ## 自动化状态 区块的字段。
    返回字典，key 为字段名，value 为字段值（字符串）。
    如果区块不存在，返回空字典。

    格式示例：
      ## 自动化状态

      fix_status: DONE
      fix_commit: abc1234
      fix_round: 1
      test_status: PASS
      test_round: 2
      review_status: PENDING
      review_round: 0
    """
    status: dict[str, str] = {}
    try:
        content = req_file.read_text(encoding="utf-8")
    except Exception:
        return status

    # 找到 ## 自动化状态 区块
    in_block = False
    for line in content.split("\n"):
        stripped = line.strip()
        if stripped == "## 自动化状态":
            in_block = True
            continue
        if in_block:
            # 遇到下一个 ## 标题则停止
            if stripped.startswith("## ") and stripped != "## 自动化状态":
                break
            # 解析 key: value 行
            if ":" in stripped and not stripped.startswith("#"):
                key, _, val = stripped.partition(":")
                status[key.strip()] = val.strip()
    return status


def parse_test_result(output: str) -> tuple[bool, str]:
    """
    解析 e2e-test 的输出（兜底方案，优先使用 read_req_status）。
    返回 (all_passed, failure_summary)

    优先识别结构化标记：
    - TEST_RESULT: PASS/FAIL（最后一行）
    - TEST_FAILURES_BEGIN...TEST_FAILURES_END（失败摘要块）

    降级时仅凭「测试报告结构 + 无 ❌ 失败标记」判断，避免 console 错误描述中的 ❌ 误判。
    """
    clean = strip_ansi(output)
    lines = clean.strip().split("\n")

    # 1. 检查 TEST_RESULT 标记（最后 15 行内，覆盖 kiro-cli 尾部追加的 Time/空行）
    result_line = None
    for line in reversed(lines[-15:]):
        stripped = line.strip()
        if stripped in ("TEST_RESULT: PASS", "TEST_RESULT: FAIL"):
            result_line = stripped
            break

    # 2. 提取 TEST_FAILURES_BEGIN...END 块
    failures_summary = ""
    in_block = False
    failure_lines = []
    for line in lines:
        if line.strip() == "TEST_FAILURES_BEGIN":
            in_block = True
            continue
        if line.strip() == "TEST_FAILURES_END":
            in_block = False
            continue
        if in_block:
            failure_lines.append(line.strip())
    if failure_lines:
        failures_summary = "\n".join(failure_lines)

    # 3. 根据标记返回（最可靠路径）
    if result_line == "TEST_RESULT: PASS":
        return True, ""
    if result_line == "TEST_RESULT: FAIL":
        summary = failures_summary if failures_summary else "\n".join(lines[-30:])
        return False, summary

    # 4. 降级：必须同时满足「有测试报告结构」才用关键词判断
    #    不再单独依赖 ❌ 关键词，避免 console 错误描述误触发
    in_test_report = "测试报告" in clean or "TrackFlow 测试报告" in clean or "## 测试结果" in clean
    if not in_test_report:
        # 没有测试报告结构，无法可靠判断，默认失败让脚本重试
        return False, "\n".join(lines[-20:])

    # 有测试报告结构时，看是否有明确的失败标记行（❌ FAIL 或 ❌ 失败，且在测试结果表格中）
    # 只有 TEST_FAILURES_BEGIN 块或报告表格里出现 ❌ 才算真失败
    has_structured_fail = bool(failures_summary)  # 有 TEST_FAILURES 块
    has_table_fail = any(
        "❌" in line and ("|" in line or line.strip().startswith("-"))
        for line in lines
    )

    if has_structured_fail or has_table_fail:
        summary = failures_summary if failures_summary else "\n".join(lines[-30:])
        return False, summary

    # 有报告结构但没有明确失败标记 → PASS
    return True, ""


def parse_review_result(output: str) -> tuple[bool, str]:
    """
    解析 code-review 的输出（兜底方案，优先使用 read_req_status）。
    返回 (can_merge, summary)
    优先识别机器标记 REVIEW_RESULT: PASS/FAIL，降级时用内容关键词。
    """
    clean = strip_ansi(output)
    lines = clean.strip().split("\n")
    # 优先检查最后几行的机器标记（最后 15 行，覆盖 kiro-cli 尾部追加的 Time/空行）
    for line in reversed(lines[-15:]):
        line = line.strip()
        if line == "REVIEW_RESULT: PASS":
            return True, "\n".join(lines[-10:])
        if line == "REVIEW_RESULT: FAIL":
            return False, "\n".join(lines[-40:])

    # 降级：关键词检测（ANSI 已清除，emoji 可靠匹配）
    can_merge = "🟢" in clean or "可以合并" in clean
    has_must = "MUST" in clean and "❌" in clean
    summary = "\n".join(lines[-40:])
    if has_must:
        return False, summary
    if can_merge:
        return True, summary
    if "🟡" in clean or "修改后合并" in clean:
        return True, summary
    return False, summary


def extract_arch_issues(req_file: Path) -> tuple[list[str], str]:
    """
    从需求文件中检测 code-review 写入的架构问题标记。

    code-review SKILL 在发现系统性架构问题时会在需求文件末尾写入：

        ## 🏗️ 架构问题（ARCH_ISSUES_DETECTED）

        ARCH_KEYWORDS: Sprint管理, 状态流转

        ### 详情
        1. xxx
        2. xxx

    返回 (keywords_list, detail_text)。
    未检测到标记时返回 ([], "")。
    """
    if not req_file.exists():
        return [], ""
    content = req_file.read_text(encoding="utf-8", errors="ignore")
    if "ARCH_ISSUES_DETECTED" not in content:
        return [], ""

    keywords: list[str] = []
    detail_lines: list[str] = []
    in_detail = False

    for line in content.split("\n"):
        if line.startswith("ARCH_KEYWORDS:"):
            raw = line.split(":", 1)[1].strip()
            keywords = [k.strip() for k in raw.split(",") if k.strip()]
        elif line.strip().startswith("### 详情"):
            in_detail = True
        elif in_detail:
            # 遇到下一个 ## 标题时停止
            if line.startswith("## "):
                break
            detail_lines.append(line)

    detail = "\n".join(detail_lines).strip()
    return keywords, detail


# ============ 阶段一：并行生产 ============


def produce_one(worker_id: str) -> bool:
    """单个生产者：随机选角色工作流配置，找一个需求"""
    config = random.choice(PRODUCER_CONFIGS)
    skill_name = config["skill"]
    skill_info = SKILLS[skill_name]
    workflow_file = config["workflow_file"]
    workflow_section = config.get("workflow_section", "")

    # 动态读取角色工作流文件
    workflow_content = load_workflow(workflow_file)
    if not workflow_content:
        log.warning(f"[{worker_id}] 工作流文件 {workflow_file} 读取失败，跳过")
        return False

    # 如果指定了 section 则提取，否则使用整个文件
    if workflow_section:
        section_content = _extract_workflow_section(workflow_content, workflow_section)
        if not section_content:
            log.warning(f"[{worker_id}] 未找到 section「{workflow_section}」，使用完整工作流文件")
            section_content = workflow_content
    else:
        section_content = workflow_content

    prompt = (
        f"[使用 skill: {skill_name}] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"找一下需求。请按照以下角色工作流的步骤操作系统，"
        f"把操作过程中遇到的任何不合理、不好用、报错、UI 有问题的地方记录为需求。\n\n"
        f"---\n\n{section_content}"
    )

    # 生产阶段串行运行，共享浏览器，直接调用
    log.info(f"[{worker_id}] 生产: {workflow_file}")
    success, _ = run_kiro(prompt, worker_id)
    return success


def _extract_workflow_section(content: str, section_title: str) -> str:
    """
    从工作流文件中提取指定 section 的内容。
    section_title 支持两种格式：
      - "工作流 A"（前缀匹配）
      - "工作流 A：早上开工——查看我的任务"（完整标题）

    提取从该标题行开始，到下一个同级（## 开头）标题之前的所有内容。
    """
    lines = content.split("\n")
    start_idx = None
    end_idx = len(lines)

    # 提取匹配关键词：取冒号前的部分，或整个标题
    match_key = section_title.split("：")[0].split(":")[0].strip()

    for i, line in enumerate(lines):
        stripped = line.strip()
        # 必须是 ## 开头的标题行
        if not stripped.startswith("##"):
            continue
        # 去掉 ## 后的标题内容
        title_text = stripped.lstrip("#").strip()
        # 精确包含匹配关键词（如"工作流 A"）
        if match_key in title_text:
            start_idx = i
            break

    if start_idx is None:
        return ""

    # 找到下一个同级（## 开头但不是 ### ）标题
    for i in range(start_idx + 1, len(lines)):
        if lines[i].startswith("## ") and not lines[i].startswith("### "):
            end_idx = i
            break

    return "\n".join(lines[start_idx:end_idx]).strip()



# ============ 阶段二：审核 ============

REVIEW_BATCH_SIZE = 5  # 每次审核最多处理的需求数量


def run_review_phase(batch: list[Path] | None = None):
    """
    审核一批需求文件（每批最多 REVIEW_BATCH_SIZE 个，一个新会话处理）。
    batch: 指定审核的文件列表；为 None 时自动从 review/ 取前 REVIEW_BATCH_SIZE 个。
    """
    if batch is None:
        batch = sorted(REVIEW_DIR.glob("requirement-*.md"))[:REVIEW_BATCH_SIZE]
    if not batch:
        return

    skill_info = SKILLS["review-requirement"]
    req_list = ", ".join([f.name for f in batch])

    prompt = (
        f"[使用 skill: review-requirement] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"审核需求：请审核 requirements/review/ 中的以下需求文件：{req_list}。"
        f"文件路径格式为 requirements/review/requirement-XX.md。"
    )

    log.info(f"[审核] 审核 {len(batch)} 个需求：{req_list}")
    run_kiro(prompt, "reviewer", worker_id="reviewer")
    log.info(f"[审核] 完成，develop/ 当前 {count_develop()} 个")


# ============ 阶段三：并行消费 ============


def claim_requirement(worker_id: str) -> Path | None:
    """原子领取一个需求"""
    worker_dir = WORKING_DIR / worker_id
    worker_dir.mkdir(parents=True, exist_ok=True)

    with _claim_lock:
        available = list_available(DEVELOP_DIR)
        if not available:
            return None
        source = available[0]
        dest = worker_dir / source.name
        try:
            source.rename(dest)
            return dest
        except (OSError, FileNotFoundError):
            return None


def consume_one(worker_id: str) -> str | None:
    """
    单个 worker 领取并处理一个需求，完整流程：
      1. 修需求（新会话）
      2. 拿到 session_id
      3. 测试（独立会话）→ 失败则 resume 原会话反馈 → 循环最多 MAX_TEST_RETRIES 次
      4. 审核（独立会话）→ 有 MUST 则 resume 原会话反馈 → 循环最多 MAX_REVIEW_RETRIES 次
      5. 成功 → 移到 implement
    """
    req_file = claim_requirement(worker_id)
    if req_file is None:
        return None

    skill_info = SKILLS["fix-requirement-auto"]
    actual_path = f"requirements/working/{worker_id}/{req_file.name}"
    title = extract_title(req_file)
    label = worker_id

    log.info(f"[{label}] 消费: {req_file.name} ({title})")

    # ── 步骤 1：修需求（新会话）──
    # 记录修需求前的 commit hash，用于后续 diff 精确范围
    commit_before = get_current_commit()

    fix_prompt = (
        f"[使用 skill: fix-requirement-auto] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"修需求 {req_file.stem}，需求文件位于 {actual_path}"
    )
    success, fix_output = run_kiro(fix_prompt, label, model=KIRO_MODEL_FIX, worker_id=worker_id)

    # kiro-cli 启动失败（<30s 退出）→ 不算需求失败，放回 develop/ 并等待环境恢复
    if fix_output == "STARTUP_FAIL":
        if req_file.exists():
            shutil.move(str(req_file), str(DEVELOP_DIR / req_file.name))
        log.warning(f"[{label}] ⚠️ kiro-cli 启动失败，{req_file.name} 放回 develop/，等待 60s 后重试")
        time.sleep(60)
        return None  # 返回 None 但不累积重试计数（因为文件放回了 develop/ 而非触发计数）

    # 检查 FIX_BLOCKED（需求不合理）
    _, blocked, block_reason = parse_fix_result(fix_output)
    if blocked:
        log.warning(f"[{label}] ⛔ {req_file.name} 被拦截: {block_reason}")
        if req_file.exists():
            shutil.move(str(req_file), str(REJECTED_DIR / req_file.name))
        return req_file.name  # 归档但标记为拦截，不重试

    if not success:
        if req_file.exists():
            shutil.move(str(req_file), str(DEVELOP_DIR / req_file.name))
        log.warning(f"[{label}] ❌ {req_file.name} 修需求失败，放回 develop/")
        return None

    # 记录修需求后的 commit hash（用于 diff 范围）
    commit_after = get_current_commit()
    diff_range = f"{commit_before}..HEAD" if commit_before and commit_before != commit_after else "HEAD~1"
    log.info(f"[{label}] 代码变更范围: {diff_range}")

    # ── 步骤 2：拿 session_id（修需求结束后立刻查）──
    time.sleep(2)  # 给 kiro-cli 一点时间写入 session
    session_id = get_latest_session_id(req_file.stem)
    if session_id:
        log.info(f"[{label}] 绑定 session: {session_id[:8]}...")
    else:
        log.warning(f"[{label}] 未获取到 session_id，后续反馈将开新会话")

    # ── 步骤 3：测试闭环 ──
    test_skill = SKILLS["e2e-test"]
    prev_test_summary = ""
    test_passed = False

    # 等待 playwright MCP server 就绪（修需求会话可能占用了 MCP，稍等再发起测试）
    time.sleep(5)

    for test_round in range(1, MAX_TEST_RETRIES + 1):
        log.info(f"[{label}] 测试第 {test_round} 轮...")

        if test_round == 1:
            test_prompt = (
                f"[使用 skill: e2e-test] "
                f"(skill 文件: {test_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"测试需求 {req_file.stem}，验证其验收标准。需求文件位于 {actual_path}\n\n"
                f"⚠️ 开始测试前必须先读取需求文件 {actual_path}，"
                f"提取验收标准和「Agent 交接上下文」章节中的测试重点。"
            )
        else:
            test_prompt = (
                f"[使用 skill: e2e-test] "
                f"(skill 文件: {test_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"重测需求 {req_file.stem}（第 {test_round} 轮）。需求文件位于 {actual_path}\n\n"
                f"⚠️ 开始测试前必须先读取需求文件 {actual_path}，确认最新的「Agent 交接上下文」。\n\n"
                f"上轮失败摘要：\n{prev_test_summary}\n\n"
                f"请仅验证上轮失败的用例，并回归已通过的用例。"
            )

        _, test_output = run_kiro(test_prompt, f"{label}-test{test_round}", worker_id=worker_id)

        # ── 优先从需求文件状态区块读取结果（更可靠）──
        req_status = read_req_status(req_file)
        file_test_status = req_status.get("test_status", "")
        if file_test_status == "PASS":
            log.info(f"[{label}] ✅ 测试通过（第 {test_round} 轮，来源：文件状态）")
            test_passed = True
            break
        elif file_test_status == "FAIL":
            log.warning(f"[{label}] ❌ 测试失败（第 {test_round} 轮，来源：文件状态）")
            test_passed = False
            # 从 stdout 提取失败摘要（用于给 fix 会话的反馈）
            _, prev_test_summary = parse_test_result(test_output)
        else:
            # 文件状态未更新（agent 未写入或格式不对）→ 降级到 stdout 解析
            log.warning(f"[{label}] ⚠️ 需求文件中未找到 test_status，降级到 stdout 解析")
            test_passed, prev_test_summary = parse_test_result(test_output)
            if test_passed:
                log.info(f"[{label}] ✅ 测试通过（第 {test_round} 轮，来源：stdout 解析）")
                break
            log.warning(f"[{label}] ❌ 测试失败（第 {test_round} 轮，来源：stdout 解析）")

        if test_round < MAX_TEST_RETRIES:
            # resume 原会话，把测试失败结果反馈给它
            feedback_prompt = (
                f"端到端测试失败（第 {test_round} 轮），以下用例未通过，请根据失败信息修复代码：\n\n"
                f"{prev_test_summary}\n\n"
                f"修复完成后请输出 FIX_DONE。"
            )
            if session_id:
                _, fb_out = run_kiro_resume(session_id, feedback_prompt, f"{label}-fix{test_round}", worker_id=worker_id)
            else:
                # 无 session_id 降级为新会话
                fallback_prompt = (
                    f"[使用 skill: fix-requirement-auto] "
                    f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
                    f"继续处理需求 {req_file.stem}（{actual_path}），测试失败，请修复：\n\n"
                    f"```\n{prev_test_summary}\n```"
                )
                _, fb_out = run_kiro(fallback_prompt, f"{label}-fix{test_round}", model=KIRO_MODEL_FIX, worker_id=worker_id)
            # 判断修复是否完成（FIX_DONE），未完成则提前退出测试循环
            fix_done, fix_blocked2, _ = parse_fix_result(fb_out)
            if fix_blocked2:
                log.warning(f"[{label}] 修复被拦截，停止测试循环")
                break
            if not fix_done:
                log.warning(f"[{label}] 修复未输出 FIX_DONE，可能未完成，继续测试验证")

    if not test_passed:
        log.warning(f"[{label}] ⚠️ 测试经 {MAX_TEST_RETRIES} 轮仍未通过，继续审核（记录问题）")

    # ── 步骤 4：代码审核闭环 ──
    review_skill = SKILLS["code-review"]
    prev_review_summary = ""
    review_passed = False

    for review_round in range(1, MAX_REVIEW_RETRIES + 1):
        log.info(f"[{label}] 审核第 {review_round} 轮...")

        if review_round == 1:
            review_prompt = (
                f"[使用 skill: code-review] "
                f"(skill 文件: {review_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"审核需求 {req_file.stem} 的本次代码变更。需求文件位于 {actual_path}\n\n"
                f"⚠️ 开始审核前必须先读取需求文件 {actual_path}，"
                f"提取「Agent 交接上下文」中的变更文件清单和审核重点。\n\n"
                f"变更范围：git diff {diff_range}\n"
                f"请审核这个范围内的所有改动（可能包含多个 commit）。"
            )
        else:
            review_prompt = (
                f"[使用 skill: code-review] "
                f"(skill 文件: {review_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"二次审核需求 {req_file.stem}（第 {review_round} 轮）。需求文件位于 {actual_path}\n\n"
                f"⚠️ 开始审核前必须先读取需求文件 {actual_path}，确认最新的「Agent 交接上下文」。\n\n"
                f"上轮 MUST 问题：\n{prev_review_summary}\n\n"
                f"请验证 MUST 问题是否已修复，无需重新做完整审核。"
            )

        _, review_output = run_kiro(review_prompt, f"{label}-review{review_round}", worker_id=worker_id)

        # ── 优先从需求文件状态区块读取结果（更可靠）──
        req_status = read_req_status(req_file)
        file_review_status = req_status.get("review_status", "")
        if file_review_status == "PASS":
            log.info(f"[{label}] ✅ 审核通过（第 {review_round} 轮，来源：文件状态）")
            review_passed = True
            break
        elif file_review_status == "FAIL":
            log.warning(f"[{label}] ❌ 审核有 MUST 问题（第 {review_round} 轮，来源：文件状态），反馈给修需求会话...")
            review_passed = False
            _, prev_review_summary = parse_review_result(review_output)
        else:
            # 文件状态未更新 → 降级到 stdout 解析
            log.warning(f"[{label}] ⚠️ 需求文件中未找到 review_status，降级到 stdout 解析")
            review_passed, prev_review_summary = parse_review_result(review_output)
            if review_passed:
                log.info(f"[{label}] ✅ 审核通过（第 {review_round} 轮，来源：stdout 解析）")
                break
            log.warning(f"[{label}] ❌ 审核有 MUST 问题（第 {review_round} 轮，来源：stdout 解析），反馈给修需求会话...")

        if review_round < MAX_REVIEW_RETRIES:
            feedback_prompt = (
                f"代码审核发现 MUST 级问题（第 {review_round} 轮），请修复以下问题后重新 commit：\n\n"
                f"```\n{prev_review_summary}\n```\n\n"
                f"修复完成后请输出 FIX_DONE。"
            )
            if session_id:
                _, rv_out = run_kiro_resume(session_id, feedback_prompt, f"{label}-fixr{review_round}", worker_id=worker_id)
            else:
                fallback_prompt = (
                    f"[使用 skill: fix-requirement-auto] "
                    f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
                    f"继续处理需求 {req_file.stem}（{actual_path}），审核发现 MUST 问题：\n\n"
                    f"```\n{prev_review_summary}\n```"
                )
                _, rv_out = run_kiro(fallback_prompt, f"{label}-fixr{review_round}", model=KIRO_MODEL_FIX, worker_id=worker_id)
            fix_done2, _, _ = parse_fix_result(rv_out)
            if not fix_done2:
                log.warning(f"[{label}] 审核反馈修复未输出 FIX_DONE，继续二次审核验证")

    # ── 步骤 5：归档与推送 ──
    # 只有测试和审核全部通过，才执行 git push 和归档
    # 任何一个没通过 → 放回 develop/ 等待下一轮重试
    overall_success = test_passed and review_passed

    if overall_success:
        # push 前打印本次变更文件清单（多进程并发时方便追踪）
        try:
            files_result = subprocess.run(
                ["git", "diff", diff_range, "--name-only"],
                capture_output=True, text=True,
                cwd=str(WORKSPACE), timeout=15
            )
            changed_files = files_result.stdout.strip()
            log.info(f"[{label}] 本次推送文件清单（{diff_range}）:\n{changed_files}")
        except Exception as e:
            log.warning(f"[{label}] 获取文件清单失败: {e}")

        # git push（所有验证通过后才推送）
        try:
            push_result = subprocess.run(
                ["git", "push"],
                capture_output=True, text=True,
                cwd=str(WORKSPACE), timeout=60
            )
            if push_result.returncode == 0:
                log.info(f"[{label}] ✅ git push 成功")
            else:
                log.warning(f"[{label}] ⚠️ git push 失败: {push_result.stderr.strip()}")
        except Exception as e:
            log.warning(f"[{label}] ⚠️ git push 异常: {e}")

        # 归档需求文件到 implement/
        if req_file.exists():
            shutil.move(str(req_file), str(IMPLEMENT_DIR / req_file.name))
        log.info(f"[{label}] ✅ {req_file.name} 全流程完成，已归档")

        # ── 步骤 6：检测并触发架构审计（可选）──
        # code-review 若发现系统性架构问题，会在需求文件中写入 ARCH_ISSUES_DETECTED 标记
        # 检测到后开启新的 tech-requirement 会话深入分析，自动写入技术需求到 review/
        arch_req_file = IMPLEMENT_DIR / req_file.name  # 文件已移到 implement/
        arch_keywords, arch_detail = extract_arch_issues(arch_req_file)
        if arch_keywords:
            log.info(f"[{label}] 🏗️ 发现架构问题，触发 tech-requirement 审计：{arch_keywords}")
            tech_skill = SKILLS.get("tech-requirement", {})
            tech_prompt = (
                f"[使用 skill: tech-requirement] "
                f"(skill 文件: {tech_skill.get('path', '.kiro/skills/tech-requirement/SKILL.md')}，"
                f"请严格按照该 skill 的规则执行)\n\n"
                f"在审核需求 {req_file.stem} 的代码变更时，code-review 发现了以下系统性架构问题，"
                f"请以此为切入点进行技术审计，找出根因并写成技术需求文档：\n\n"
                f"**涉及模块**：{', '.join(arch_keywords)}\n\n"
                f"**问题详情**：\n{arch_detail}\n\n"
                f"请按照 tech-requirement SKILL 的完整审计流程执行，"
                f"从 YouTrack 文档建立业务基线，追踪 TrackFlow 全链路数据流，"
                f"将发现的问题写入 review/ 目录。"
            )
            run_kiro(tech_prompt, f"{label}-arch", worker_id=worker_id)
            log.info(f"[{label}] 🏗️ 架构审计会话结束")
    else:
        # 验证未通过 → 放回 develop/ 重试
        status = []
        if not test_passed:
            status.append(f"测试未全通({MAX_TEST_RETRIES}轮)")
        if not review_passed:
            status.append(f"审核未全通({MAX_REVIEW_RETRIES}轮)")
        log.warning(f"[{label}] ❌ {req_file.name} 未通过验证（{', '.join(status)}），放回 develop/ 等待重试")
        if req_file.exists():
            shutil.move(str(req_file), str(DEVELOP_DIR / req_file.name))
        return None  # 不计入完成数，等待重试

    return req_file.name


# ============ 清理 ============


def cleanup_screenshots(keep_count: int = 200) -> None:
    """清理 test/ 目录，只保留最新的 keep_count 张截图。

    每次脚本启动时调用一次，防止长期运行后目录无限膨胀。
    """
    if not SCREENSHOT_DIR.exists():
        return
    files = sorted(SCREENSHOT_DIR.iterdir(), key=lambda f: f.stat().st_mtime, reverse=True)
    to_delete = [f for f in files if f.is_file()][keep_count:]
    for f in to_delete:
        f.unlink(missing_ok=True)
    if to_delete:
        log.info(f"[截图清理] 删除 {len(to_delete)} 个旧截图，保留最新 {keep_count} 张")


def cleanup_working():
    """清理 working/ 残留文件"""
    for worker_dir in WORKING_DIR.iterdir():
        if worker_dir.is_dir():
            for f in worker_dir.glob("requirement-*.md"):
                shutil.move(str(f), str(DEVELOP_DIR / f.name))
                log.info(f"[清理] {f.name} → develop/")
            try:
                worker_dir.rmdir()
            except OSError:
                pass


def archive_decomposed_parents():
    """归档已拆解的父需求"""
    for f in list(DEVELOP_DIR.glob("requirement-*.md")):
        if not is_sub_requirement(f):
            number = extract_number(f.name)
            if has_sub_requirements(number):
                shutil.move(str(f), str(IMPLEMENT_DIR / f.name))
                log.info(f"[归档] 父需求 {f.name} → implement/")


# ============ 主循环 ============

# 审核全局锁：防止多个生产者同时审核 review/ 造成重复处理
_review_lock = threading.Lock()

# 消费重试计数：跨轮次记录每个需求文件的失败次数
_retry_counts: dict[str, int] = {}
_retry_lock = threading.Lock()


def producer_loop(worker_id: str):
    """
    生产者线程：持续找需求，写到 review/，由专职审核线程处理。
    只在 develop 队列不足时才生产，避免过度堆积。
    """
    log.info(f"[{worker_id}] 生产者启动")
    while True:
        try:
            archive_decomposed_parents()
            if count_develop() < MIN_DEVELOP_QUEUE:
                log.info(f"[{worker_id}] develop={count_develop()} < {MIN_DEVELOP_QUEUE}，开始生产...")
                produce_one(worker_id)
            else:
                log.debug(f"[{worker_id}] develop 充足（{count_develop()}），跳过生产")
        except Exception as e:
            log.error(f"[{worker_id}] 生产者异常: {e}", exc_info=True)

        time.sleep(COOLDOWN_SECONDS)


def reviewer_loop():
    """
    专职审核线程（唯一）：持续监听 review/，有文件立即按批审核。
    每批最多 REVIEW_BATCH_SIZE 个需求，一个新会话处理，避免 context 过长。
    review/ 为空时每 30 秒轮询一次，每 5 分钟打一条 INFO。
    """
    log.info("[reviewer] 审核线程启动")
    idle_rounds = 0
    while True:
        try:
            review_files = sorted(REVIEW_DIR.glob("requirement-*.md"))
            if review_files:
                idle_rounds = 0
                # 分批处理，每批一个新会话
                batches = [
                    review_files[i:i + REVIEW_BATCH_SIZE]
                    for i in range(0, len(review_files), REVIEW_BATCH_SIZE)
                ]
                log.info(f"[reviewer] {len(review_files)} 个待审需求，分 {len(batches)} 批处理")
                with _review_lock:
                    for idx, batch in enumerate(batches, 1):
                        log.info(f"[reviewer] 第 {idx}/{len(batches)} 批（{len(batch)} 个）...")
                        run_review_phase(batch)
            else:
                idle_rounds += 1
                if idle_rounds % 10 == 1:
                    log.info("[reviewer] review/ 为空，等待生产者...")
                time.sleep(30)
        except Exception as e:
            log.error(f"[reviewer] 审核线程异常: {e}", exc_info=True)
            time.sleep(COOLDOWN_SECONDS)


def consumer_loop(worker_id: str):
    """
    消费者线程：持续从 develop/ 领取需求并完整处理（修+测+审）。
    失败超过 MAX_RETRIES 次的需求移到 rejected/。
    develop/ 为空时每 30 秒轮询一次，每 5 分钟打一条 INFO 日志。
    """
    log.info(f"[{worker_id}] 消费者启动")
    idle_rounds = 0
    while True:
        try:
            available = list_available(DEVELOP_DIR)
            if not available:
                idle_rounds += 1
                if idle_rounds % 10 == 1:  # 首次 + 每 5 分钟打一条
                    log.info(f"[{worker_id}] develop/ 为空，等待生产者补货...")
                time.sleep(30)
                continue

            idle_rounds = 0  # 有需求了，重置空闲计数

            # 检查队首是否超过重试上限
            first = available[0]
            with _retry_lock:
                count = _retry_counts.get(first.name, 0)
            if count >= MAX_RETRIES:
                log.warning(f"[{worker_id}] {first.name} 已失败 {count} 次，移到 rejected/")
                shutil.move(str(first), str(REJECTED_DIR / first.name))
                with _retry_lock:
                    _retry_counts.pop(first.name, None)
                continue

            # 领取并处理
            result = consume_one(worker_id)

            if result is None:
                # 处理失败（放回 develop/）或队列为空，累计重试次数
                # 只对确实存在于 develop/ 的文件计数
                for f in list_available(DEVELOP_DIR):
                    with _retry_lock:
                        if f.name == first.name:
                            _retry_counts[f.name] = _retry_counts.get(f.name, 0) + 1
                time.sleep(COOLDOWN_SECONDS)
            else:
                # 成功，清理计数
                with _retry_lock:
                    _retry_counts.pop(result, None)
                log.info(f"[{worker_id}] ✅ 累计完成 {sum(1 for _ in IMPLEMENT_DIR.glob('*.md'))} 个")

        except Exception as e:
            log.error(f"[{worker_id}] 消费者异常: {e}", exc_info=True)
            time.sleep(COOLDOWN_SECONDS)


def main_loop(num_producers: int, num_consumers: int, skip_produce: bool):
    """
    主循环：启动三类线程，永不停止。

    生产者（N）：找需求 → 写到 review/
    审核者（1）：监听 review/ → 分批审核 → 写到 develop/
    消费者（M）：从 develop/ 领取需求 → 修+测+审 → 归档

    三类线程通过文件目录队列通信，完全解耦。
    """
    threads: list[threading.Thread] = []

    # 启动专职审核线程（固定 1 个，不占 workers 配额）
    if not skip_produce:
        t = threading.Thread(target=reviewer_loop, name="reviewer", daemon=True)
        t.start()
        threads.append(t)
        log.info("[主] 审核线程已启动")
        time.sleep(2)

    # 启动生产者线程
    if not skip_produce:
        for i in range(num_producers):
            worker_id = f"producer-{i+1}"
            t = threading.Thread(
                target=producer_loop,
                args=(worker_id,),
                name=worker_id,
                daemon=True,
            )
            t.start()
            threads.append(t)
            log.info(f"[主] 生产者 {worker_id} 已启动")
            time.sleep(5)  # 错开启动
    else:
        log.info("[主] --skip-produce 模式，不启动生产者和审核线程")

    # 启动消费者线程（错开 30 秒，避免同时触发 kiro-cli 并发限制）
    STAGGER_SECONDS = 30
    for i in range(num_consumers):
        worker_id = f"consumer-{i+1}"
        t = threading.Thread(
            target=consumer_loop,
            args=(worker_id,),
            name=worker_id,
            daemon=True,
        )
        t.start()
        threads.append(t)
        log.info(f"[主] 消费者 {worker_id} 已启动")
        if i < num_consumers - 1:
            time.sleep(STAGGER_SECONDS)

    log.info(
        f"[主] 全部线程已启动：1 审核者 + {num_producers} 生产者 + {num_consumers} 消费者"
        if not skip_produce else
        f"[主] 全部线程已启动：{num_consumers} 消费者（仅消费模式）"
    )

    # 主线程定期打印状态，永不退出
    while True:
        time.sleep(60)
        log.info(
            f"[状态] review={len(list(REVIEW_DIR.glob('*.md')))} "
            f"develop={count_develop()} "
            f"implement={len(list(IMPLEMENT_DIR.glob('*.md')))} "
            f"rejected={len(list(REJECTED_DIR.glob('*.md')))}"
        )


# ============ 入口 ============


def main():
    parser = argparse.ArgumentParser(description="TrackFlow 并行迭代脚本（永不停止）")
    parser.add_argument("--workers", type=int, default=2,
                        help="总 worker 数量（默认 2）。自动按 1:3 比例分配生产者和消费者")
    parser.add_argument("--producers", type=int, default=None,
                        help="手动指定生产者数量（覆盖自动分配）")
    parser.add_argument("--consumers", type=int, default=None,
                        help="手动指定消费者数量（覆盖自动分配）")
    parser.add_argument("--skip-produce", action="store_true",
                        help="跳过生产阶段，只消费 develop/ 中的现有需求")
    args = parser.parse_args()

    # 计算生产者/消费者数量
    if args.producers is not None or args.consumers is not None:
        # 手动指定
        num_producers = args.producers if args.producers is not None else 1
        num_consumers = args.consumers if args.consumers is not None else max(1, args.workers - num_producers)
    else:
        # 自动分配：workers 越多，消费者比例越高
        # 2→(1,1)  3→(1,2)  4→(1,3)  5→(1,4)  6→(2,4)  8→(2,6)
        num_producers = max(1, args.workers // 4) if args.workers >= 4 else 1
        num_consumers = max(1, args.workers - num_producers)

    if args.skip_produce:
        num_producers = 0
        num_consumers = args.workers

    log.info("=" * 60)
    log.info(f"TrackFlow 并行迭代 | 生产者={num_producers} 消费者={num_consumers}")
    log.info(f"模型: fix={KIRO_MODEL_FIX or '默认'} | test/review/produce={KIRO_MODEL or '默认'}")
    log.info(f"状态: review={len(list(REVIEW_DIR.glob('*.md')))} "
             f"develop={count_develop()} implement={len(list(IMPLEMENT_DIR.glob('*.md')))}")
    log.info("永不停止，Ctrl+C 手动终止")
    log.info("=" * 60)

    kill_stale_playwright_processes()   # 杀掉上次残留的 Playwright MCP 进程，避免复用无 --user-data-dir 的旧进程
    cleanup_screenshots()   # 清理超过 200 张的截图，防止 test/ 无限膨胀
    cleanup_working()
    main_loop(num_producers, num_consumers, args.skip_produce)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        log.info("\n[中断] 用户手动停止")
        cleanup_working()
        stop_all_playwright()
        cleanup_worker_envs()
