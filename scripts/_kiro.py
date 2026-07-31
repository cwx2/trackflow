"""
kiro-cli 调用封装：run_kiro（新会话）、run_kiro_resume（恢复会话）、session 管理。
"""

import json
import logging
import os
import subprocess
import time
from pathlib import Path

from _config import (
    WORKSPACE, KIRO_CLI, KIRO_MODEL, KIRO_MODEL_FIX,
    TIMEOUT_SECONDS, log, strip_ansi,
)

# 构建 kiro-cli 所需的环境变量（抑制交互式行为）
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
    "KIRO_LOG_NO_COLOR": "1",
}


def _build_env(worker_id: str | None = None) -> dict:
    """构建完整的环境变量字典"""
    env = os.environ.copy()
    env.update(_BASE_ENV)
    return env


def run_kiro(prompt: str, label: str,
             model: str | None = None,
             worker_id: str | None = None) -> tuple[bool, str]:
    """
    启动一个新的 kiro-cli 会话执行 prompt。

    label:     日志前缀（如 consumer-1、consumer-1-test1）
    model:     指定模型，None 时使用 KIRO_MODEL 默认值
    worker_id: 保留参数，兼容旧调用，不再影响 MCP 配置

    返回 (success, output)。
    output="STARTUP_FAIL" 表示 kiro-cli 在 30 秒内异常退出（认证/并发问题）。
    """
    cmd = [KIRO_CLI, "chat", "--no-interactive", "--trust-all-tools"]
    effective_model = model or KIRO_MODEL
    if effective_model:
        cmd += ["--model", effective_model]
    cmd.append(prompt)

    start = time.time()
    output_lines: list[str] = []

    try:
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, cwd=str(WORKSPACE),
            encoding="utf-8", errors="replace",
            env=_build_env(),
        )
        for line in process.stdout:
            line_stripped = line.rstrip("\n")
            print(f"  [{label}] {line_stripped}")
            log.debug(f"[{label}] {strip_ansi(line_stripped)}")
            output_lines.append(line_stripped)
        process.wait(timeout=TIMEOUT_SECONDS)
        elapsed = time.time() - start
        success = process.returncode == 0
        log.log(
            logging.INFO if success else logging.WARNING,
            f"[{label}] {'完成' if success else '失败'} ({elapsed:.0f}s)"
        )
        # 30 秒内退出 → 启动失败（认证/网络/并发问题），与需求本身无关
        if not success and elapsed < 30:
            log.warning(f"[{label}] kiro-cli 启动失败（{elapsed:.0f}s）")
            return False, "STARTUP_FAIL"
        return success, "\n".join(output_lines)
    except subprocess.TimeoutExpired:
        process.kill()
        log.error(f"[{label}] 超时")
        return False, "TIMEOUT"
    except Exception as e:
        log.error(f"[{label}] 异常: {e}")
        return False, str(e)


def run_kiro_resume(session_id: str, prompt: str, label: str,
                    model: str | None = None,
                    worker_id: str | None = None) -> tuple[bool, str]:
    """恢复指定会话并发送消息（resume 模式默认用 KIRO_MODEL_FIX）"""
    cmd = [KIRO_CLI, "chat", "--no-interactive", "--trust-all-tools",
           "--resume-id", session_id]
    effective_model = model or KIRO_MODEL_FIX
    if effective_model:
        cmd += ["--model", effective_model]
    cmd.append(prompt)

    start = time.time()
    output_lines: list[str] = []

    try:
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, cwd=str(WORKSPACE),
            encoding="utf-8", errors="replace",
            env=_build_env(),
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


def get_current_commit() -> str:
    """获取当前 HEAD commit 的 7 位短 hash"""
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--short=7", "HEAD"],
            capture_output=True, text=True,
            cwd=str(WORKSPACE), encoding="utf-8", timeout=10
        )
        return result.stdout.strip() if result.returncode == 0 else ""
    except Exception:
        return ""


def get_latest_session_id(req_stem: str | None = None, worker_id: str | None = None) -> str | None:
    """
    获取最新的 kiro-cli session ID。

    策略：
    1. 取最新 50 条，过滤 2 小时内的，取前 10 个
    2. 优先在 title 中匹配 req_stem（kiro 会把需求文件路径写入 title）
    3. 匹配不上则 fallback 到最新一个

    注意：kiro-cli 不识别 KIRO_HOME，所有 worker 共享同一 session 列表，
    所以用 req_stem 匹配是唯一可靠的区分方式。
    """
    try:
        result = subprocess.run(
            [KIRO_CLI, "chat", "--list-sessions", "--format", "json"],
            capture_output=True, text=True,
            cwd=str(WORKSPACE),
            encoding="utf-8", errors="replace", timeout=60
        )
        raw = result.stdout + result.stderr
        start = raw.find("[")
        if start == -1:
            return None
        sessions = json.loads(raw[start:])
        if not sessions:
            return None

        # 按更新时间倒序，过滤 2 小时内的，避免遍历几百条历史
        sessions.sort(key=lambda s: s.get("updatedAt", ""), reverse=True)
        from datetime import datetime, timezone, timedelta
        cutoff = datetime.now(timezone.utc) - timedelta(hours=2)
        recent = [
            s for s in sessions[:50]
            if s.get("updatedAt", "") >= cutoff.strftime("%Y-%m-%dT%H:%M")
        ][:10]
        if not recent:
            recent = sessions[:10]

        # 优先匹配 req_stem（title 里包含需求文件路径）
        if req_stem:
            for s in recent:
                if req_stem in s.get("title", ""):
                    log.debug(f"[session] 精确匹配 {req_stem} → {s.get('sessionId', '')[:8]}...")
                    return s.get("sessionId")

        # fallback：返回最新一个
        session_id = recent[0].get("sessionId")
        log.debug(f"[session] req_stem 未匹配，使用最新 session → {(session_id or '')[:8]}...")
        return session_id

    except Exception as e:
        log.warning(f"[session] 获取 session_id 失败: {e}")
        return None
