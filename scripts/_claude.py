"""
Claude Code CLI 调用封装。

提供与 _kiro.py 完全一致的接口，可直接替换使用：

    run_claude(prompt, label, ...)         → (success, output)
    run_claude_resume(session_id, ...)     → (success, output)
    get_current_commit()                   → str
    get_latest_session_id(req_stem, ...)   → str | None

切换方式：
    方式一：设置环境变量 TRACKFLOW_AI=claude，_workers.py 自动导入此模块
    方式二：在 _config.py 中 import 此模块替代 _kiro

与 kiro-cli 的关键差异：
    - Claude Code 无 --list-sessions 命令，改用内存字典跟踪 session
    - 输出为 JSON 格式，通过 --output-format json 获取结构化结果
    - 通过 --max-budget-usd 控制单次调用成本上限
"""

import json
import logging
import os
import re
import subprocess
import threading
import time
import uuid
from pathlib import Path

from _config import (
    WORKSPACE, KIRO_MODEL, KIRO_MODEL_FIX,
    TIMEOUT_SECONDS, IDLE_TIMEOUT_SECONDS, log, strip_ansi,
)

# ============ Claude Code 专用常量 ============

# Claude Code CLI 可执行文件。优先使用环境变量，然后自动检测。
# Windows 上需要 .cmd 扩展名，subprocess.Popen 不会自动添加。
CLAUDE_CLI = os.environ.get("CLAUDE_CLI", "")

if not CLAUDE_CLI:
    # 自动检测 claude 可执行文件路径
    # Windows 上优先使用 .cmd，因为 sh 脚本无法被 subprocess.Popen 直接执行
    import shutil as _shutil
    if os.name == "nt":
        _detected = _shutil.which("claude.cmd") or _shutil.which("claude")
    else:
        _detected = _shutil.which("claude") or _shutil.which("claude.cmd")
    if _detected:
        CLAUDE_CLI = _detected
    else:
        CLAUDE_CLI = "claude"  # fallback

log.debug(f"Claude CLI: {CLAUDE_CLI}")
MCP_CONFIG_PATH = ".claude/mcp.json"

# 单次调用最大预算（美元）。修复阶段可用更高预算。
DEFAULT_MAX_BUDGET_USD = 0.75
FIX_MAX_BUDGET_USD = 1.0

# 构建 Claude Code 所需的环境变量（抑制交互式行为）
_BASE_ENV = {
    "GIT_TERMINAL_PROMPT": "0",
    "GIT_EDITOR": "true",
    "EDITOR": "true",
    "VISUAL": "true",
    "CI": "true",
    "NPM_CONFIG_YES": "true",
    "DEBIAN_FRONTEND": "noninteractive",
    "NO_COLOR": "1",
    "FORCE_COLOR": "0",
}

# 运行时约束提示（追加到每条 prompt 前面）
# Claude Code 自动遵守 .claude/settings.json 权限配置，这里只保留关键行为约束
_RUNTIME_GUARD = ""  # 项目规范通过 CLAUDE.md 加载，不在此处重复

# ============ 瞬态错误检测 ============

_TRANSIENT_OUTPUT_MARKERS = (
    "dispatch failure",
    "failed to send the request",
    "error sending request for url",
    "claude is having trouble responding right now",
    "rate limit reached",
    "request quota exceeded",
    "quota exceeded",
    "overloaded",
    "service unavailable",
    "internal server error",
    "model is currently overloaded",
)

_TRANSIENT_STOP_REASONS = (
    "max_budget_reached",
    "max_tokens",
    "tool_use_blocked",
)

# ============ 敏感信息脱敏 ============

_SENSITIVE_OUTPUT_PATTERNS = (
    (re.compile(r'(?i)("password"\s*:\s*")[^"]*(")'), r"\1***\2"),
    (re.compile(r"(?i)('password'\s*:\s*')[^']*(')"), r"\1***\2"),
    (re.compile(r"(?i)(password=)[^&\s\"']+"), r"\1***"),
    (re.compile(r'(?i)("access_token"\s*:\s*")[^"]*(")'), r"\1***\2"),
    (re.compile(r"(?i)(Authorization\s*:\s*Bearer\s+)[A-Za-z0-9._~+/=-]+"), r"\1***"),
    (re.compile(r"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b"), "***JWT***"),
    (re.compile(r"(?i)((?:#password|password)[^'\"]*\.fill\(['\"])[^'\"]*(['\"]\))"), r"\1***\2"),
)

_FORBIDDEN_OUTPUT_PATTERNS = (
    re.compile(
        r"(?i)I will run the following command:.*"
        r"(TRACKFLOW_TEST_PASSWORD|\.env\b|keycloak.*credentials|credentials.*keycloak)"
    ),
    re.compile(r"(?i)cmdlet\s+Write-Output"),
    re.compile(r"请为以下参数提供值"),
)


def _redact_sensitive_output(text: str) -> str:
    """移除日志中的敏感凭据。"""
    for pattern, replacement in _SENSITIVE_OUTPUT_PATTERNS:
        text = pattern.sub(replacement, text)
    return text


def _is_forbidden_output(text: str) -> bool:
    """识别会泄露凭据或触发交互式 shell 的行为。"""
    clean = strip_ansi(text)
    return any(pattern.search(clean) for pattern in _FORBIDDEN_OUTPUT_PATTERNS)


# ============ 全局熔断 ============

_circuit_lock = threading.Lock()
_circuit_until = 0.0
_circuit_failures = 0


def _wait_for_circuit(label: str) -> None:
    """限流/网络故障期间全局退避。"""
    with _circuit_lock:
        delay = max(0.0, _circuit_until - time.time())
    if delay > 0:
        log.info(f"[{label}] Claude 全局退避中，{delay:.0f}s 后重试")
        time.sleep(delay)


def _open_circuit(label: str) -> None:
    """打开熔断，增加退避延迟。"""
    global _circuit_until, _circuit_failures
    with _circuit_lock:
        _circuit_failures = min(_circuit_failures + 1, 5)
        delay = min(30 * (2 ** (_circuit_failures - 1)), 600)
        _circuit_until = max(_circuit_until, time.time() + delay)
    log.warning(f"[{label}] Claude 服务暂时不可用，全局退避 {delay}s")


def _close_circuit() -> None:
    """关闭熔断（成功调用后）。"""
    global _circuit_until, _circuit_failures
    with _circuit_lock:
        _circuit_until = 0.0
        _circuit_failures = 0


# ============ Session 跟踪 ============

# 内存字典：req_stem → session_id
# 因为每个 consumer 线程是串行处理一个需求的，同一时刻一个 worker 只处理一个需求
_session_map: dict[str, str] = {}
_session_lock = threading.Lock()


def _track_session(req_stem: str, session_id: str) -> None:
    """关联 req_stem 与 session_id，供后续 resume 使用。"""
    with _session_lock:
        _session_map[req_stem] = session_id
    log.debug(f"[session] 跟踪 {req_stem} → {session_id[:8]}...")


def _lookup_session(req_stem: str) -> str | None:
    """按 req_stem 查找最近的 session_id。"""
    with _session_lock:
        sid = _session_map.get(req_stem)
    return sid


# ============ 环境构建 ============

def _build_env(worker_id: str | None = None) -> dict:
    """构建完整的环境变量字典。"""
    env = os.environ.copy()
    env.update(_BASE_ENV)
    if worker_id:
        env["TRACKFLOW_AUTOMATION_WORKER"] = worker_id
    return env


# ============ 核心：运行 Claude Code ============

def _build_base_cmd(budget_usd: float | None = None) -> list[str]:
    """构建基础命令行参数。"""
    budget = budget_usd or DEFAULT_MAX_BUDGET_USD
    return [
        CLAUDE_CLI,
        "--print",
        "--dangerously-skip-permissions",
        "--permission-mode", "bypassPermissions",
        "--output-format", "json",
        "--max-budget-usd", str(budget),
        f"--mcp-config={MCP_CONFIG_PATH}",   # use = form to prevent consuming prompt as additional config path
    ]


def _parse_claude_output(stdout: str) -> dict | None:
    """从 Claude Code 的 stdout 中解析 JSON 结果。

    stdout 可能包含多行（启动日志、hook 输出等），JSON 结果在最后。
    """
    # 从最后一行开始找完整的 JSON 对象
    lines = stdout.strip().split("\n")
    for line in reversed(lines):
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            parsed = json.loads(line)
            # 确认是 Claude 的结果对象（包含 result 或 is_error 字段）
            if "result" in parsed or "is_error" in parsed:
                return parsed
        except json.JSONDecodeError:
            continue
    return None


def _is_transient_cli_output(output: str) -> bool:
    """识别 Claude Code 瞬时故障（不应消耗需求重试次数）。"""
    normalized = output.lower()
    return any(marker in normalized for marker in _TRANSIENT_OUTPUT_MARKERS)


def _is_transient_stop_reason(stop_reason: str) -> bool:
    """识别瞬时性 stop_reason。"""
    return stop_reason in _TRANSIENT_STOP_REASONS


def _run_cli(cmd: list[str], label: str, req_stem: str | None = None,
             worker_id: str | None = None) -> tuple[bool, str]:
    """运行 Claude Code CLI，管理超时、熔断、输出解析。

    返回 (success, output_text)。
    output_text 可能是:
      - 正常 AI 输出文本
      - "STARTUP_FAIL" — Claude Code 在 30s 内异常退出
      - "TIMEOUT" — 超过总超时
      - "IDLE_TIMEOUT" — 长时间无输出
      - "PROCESS_ERROR" — 进程异常
      - "POLICY_BLOCKED" — 检测到敏感/交互式行为
      - "BUDGET_EXCEEDED" — 超过预算上限
    """
    _wait_for_circuit(label)
    start = time.time()
    output_lines: list[str] = []
    last_output_at = [start]
    policy_blocked = [False]

    try:
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, cwd=str(WORKSPACE),
            encoding="utf-8", errors="replace",
            env=_build_env(worker_id),
        )
    except Exception as e:
        log.error(f"[{label}] 启动异常: {e}")
        _open_circuit(label)
        return False, "STARTUP_FAIL"

    def read_output() -> None:
        try:
            if process.stdout is None:
                return
            for line in process.stdout:
                line_stripped = line.rstrip("\r\n")
                safe_line = _redact_sensitive_output(line_stripped)
                # JSON 行提取关键状态信息，非 JSON 行完整输出
                if not line_stripped.startswith("{"):
                    print(f"  [{label}] {safe_line}")
                    log.info(f"[{label}] {strip_ansi(safe_line)}")
                else:
                    # 只显示关键 JSON 状态信息（不打印完整 JSON 减少噪音）
                    try:
                        parsed = json.loads(line_stripped)
                        if "result" in parsed or "is_error" in parsed:
                            # 这是最终的 result 行，不在这里打印，等 _run_cli 统一输出
                            pass
                    except json.JSONDecodeError:
                        pass
                output_lines.append(safe_line)
                last_output_at[0] = time.time()
                if _is_forbidden_output(line_stripped):
                    policy_blocked[0] = True
                    log.error(f"[{label}] 检测到敏感/交互式行为，立即终止 Claude Code")
                    try:
                        process.kill()
                    except Exception:
                        pass
                    break
        except Exception as e:
            log.warning(f"[{label}] 读取输出异常: {e}")

    reader = threading.Thread(target=read_output, name=f"{label}-stdout", daemon=True)
    reader.start()

    try:
        deadline = start + TIMEOUT_SECONDS
        while reader.is_alive() and time.time() < deadline:
            reader.join(5)
            if not reader.is_alive():
                break
            if time.time() - last_output_at[0] > IDLE_TIMEOUT_SECONDS:
                log.error(
                    f"[{label}] 无输出超时（{IDLE_TIMEOUT_SECONDS}s），"
                    "疑似工具卡死，终止 Claude Code"
                )
                process.kill()
                reader.join(10)
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    log.error(f"[{label}] Claude Code 进程未能及时退出")
                return False, "IDLE_TIMEOUT"

        if reader.is_alive():
            log.error(f"[{label}] 超时（{TIMEOUT_SECONDS}s），终止 Claude Code")
            process.kill()
            reader.join(10)
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                log.error(f"[{label}] Claude Code 进程未能及时退出")
            _open_circuit(label)
            return False, "TIMEOUT"

        process.wait(timeout=10)
    except Exception as e:
        log.error(f"[{label}] 进程等待异常: {e}")
        try:
            process.kill()
            process.wait(timeout=5)
        except Exception:
            pass
        _open_circuit(label)
        return False, "PROCESS_ERROR"

    elapsed = time.time() - start
    stdout = "\n".join(output_lines)

    if policy_blocked[0]:
        log.warning(f"[{label}] 已按策略阻断敏感/交互式命令")
        return False, "POLICY_BLOCKED"

    # 解析 JSON 输出
    parsed = _parse_claude_output(stdout)
    if parsed is None:
        log.warning(f"[{label}] 无法解析 Claude Code 输出为 JSON")
        # 检查是否是瞬时错误
        if _is_transient_cli_output(stdout):
            log.warning(f"[{label}] 检测到瞬时故障标记")
            _open_circuit(label)
            return False, "STARTUP_FAIL"
        return False, stdout[-500:]  # 返回最后部分供排查

    is_error = parsed.get("is_error", False)
    result_text = parsed.get("result", "")
    session_id = parsed.get("session_id", "")
    stop_reason = parsed.get("stop_reason", "")
    cost = parsed.get("total_cost_usd", 0)
    turns = parsed.get("num_turns", 0)

    log.info(
        f"[{label}] Claude {'✅' if not is_error else '❌'} "
        f"({elapsed:.0f}s, {turns}轮, ${cost:.4f}, session={session_id[:8] if session_id else '?'}...)"
    )

    # 简短信息直接打出来方便监控
    first_line = result_text.split("\n")[0].strip() if result_text else ""
    if first_line and len(first_line) < 120:
        log.info(f"[{label}] → {first_line}")

    if is_error:
        error_detail = parsed.get("api_error_status", "") or result_text[:200]
        log.warning(f"[{label}] Claude 返回错误: {error_detail}")

        # 检查预算超限
        if stop_reason == "max_budget_reached" or "budget" in result_text.lower():
            log.warning(f"[{label}] 预算超限")
            _open_circuit(label)
            return False, "BUDGET_EXCEEDED"

        # 瞬时错误 → 放回队列，不消耗重试
        if _is_transient_cli_output(result_text):
            _open_circuit(label)
            return False, "STARTUP_FAIL"

        # 启动失败（进程快速退出）
        if elapsed < 30:
            log.warning(f"[{label}] Claude Code 启动失败（{elapsed:.0f}s）")
            _open_circuit(label)
            return False, "STARTUP_FAIL"

        return False, result_text

    # 成功 → 关闭熔断
    _close_circuit()

    # 跟踪 session（用于后续 resume）
    if session_id and req_stem:
        _track_session(req_stem, session_id)

    return True, result_text


# ============ 公开 API（与 _kiro.py 接口一致）============

def run_claude(prompt: str, label: str,
               model: str | None = None,
               worker_id: str | None = None,
               req_stem: str | None = None,
               is_fix: bool = False) -> tuple[bool, str]:
    """
    启动一个新的 Claude Code 会话执行 prompt。

    Args:
        prompt:     提示内容
        label:      日志前缀（如 consumer-1、consumer-1-test1）
        model:      指定模型（"opus"/"sonnet"/"haiku"/"fable"），None 使用默认
        worker_id:  工作线程标识
        req_stem:   需求文件名主干（如 "requirement-242"），用于 session 跟踪
        is_fix:     是否为修复阶段（使用更高预算）

    Returns:
        (success, output)
        output="STARTUP_FAIL" 表示 Claude Code 在 30s 内异常退出。
    """
    budget = FIX_MAX_BUDGET_USD if is_fix else DEFAULT_MAX_BUDGET_USD
    cmd = _build_base_cmd(budget)

    effective_model = model or KIRO_MODEL
    if effective_model and effective_model != "auto":
        # Claude Code 接受 opus/sonnet/haiku/fable 作为 --model 参数
        cmd += ["--model", effective_model]

    cmd.append(prompt)
    return _run_cli(cmd, label, req_stem=req_stem, worker_id=worker_id)


def run_claude_resume(session_id: str, prompt: str, label: str,
                      model: str | None = None,
                      worker_id: str | None = None,
                      req_stem: str | None = None) -> tuple[bool, str]:
    """
    恢复指定会话并发送新消息。

    Args:
        session_id: 要恢复的会话 ID
        prompt:     提示内容
        label:      日志前缀
        model:      指定模型，None 使用 KIRO_MODEL_FIX
        worker_id:  工作线程标识
        req_stem:   需求文件名主干

    Returns:
        (success, output)
    """
    budget = DEFAULT_MAX_BUDGET_USD
    cmd = _build_base_cmd(budget)

    effective_model = model or KIRO_MODEL_FIX
    if effective_model and effective_model != "auto":
        cmd += ["--model", effective_model]

    cmd += ["--resume", session_id]
    cmd.append(prompt)
    return _run_cli(cmd, label, req_stem=req_stem, worker_id=worker_id)


def get_current_commit() -> str:
    """获取当前 HEAD commit 的 7 位短 hash。"""
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--short=7", "HEAD"],
            capture_output=True, text=True,
            cwd=str(WORKSPACE), encoding="utf-8", timeout=10
        )
        return result.stdout.strip() if result.returncode == 0 else ""
    except Exception:
        return ""


def get_latest_session_id(req_stem: str | None = None,
                          worker_id: str | None = None) -> str | None:
    """
    按 req_stem 查找最近的 Claude Code session ID。

    策略：
    1. 先查内存中的 _session_map（由 run_claude 自动记录）
    2. 未匹配则返回 None（调用方会降级为开新会话）

    注意：Claude Code 没有 --list-sessions 命令，所以依赖内存跟踪。
    如果进程重启，session 信息会丢失，此时只能开新会话。
    """
    if req_stem:
        sid = _lookup_session(req_stem)
        if sid:
            log.debug(f"[session] 匹配 {req_stem} → {sid[:8]}...")
            return sid
    log.debug(f"[session] 未找到 {req_stem} 的 session，将开新会话")
    return None


# ============ 兼容性别名（方便从 _kiro 无缝切换）============

# 这些别名使 _claude.py 可以完全替代 _kiro.py 的 import
run_kiro = run_claude
run_kiro_resume = run_claude_resume
# get_current_commit 和 get_latest_session_id 直接使用上面的实现
