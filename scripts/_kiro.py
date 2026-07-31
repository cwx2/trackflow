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
from _playwright import start_playwright_for_worker, setup_worker_kiro_dir

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


def _build_env(worker_id: str | None) -> dict:
    """构建完整的环境变量字典，为 worker 注入独立 KIRO_HOME"""
    env = os.environ.copy()
    env.update(_BASE_ENV)
    if worker_id:
        port = start_playwright_for_worker(worker_id)
        worker_kiro_dir = setup_worker_kiro_dir(worker_id, port)
        env["KIRO_HOME"] = str(worker_kiro_dir / ".kiro")
        log.debug(f"[{worker_id}] KIRO_HOME={env['KIRO_HOME']} playwright_port={port}")
    return env


def run_kiro(prompt: str, label: str,
             model: str | None = None,
             worker_id: str | None = None) -> tuple[bool, str]:
    """
    启动一个新的 kiro-cli 会话执行 prompt。

    label:     日志前缀（如 consumer-1、consumer-1-test1）
    model:     指定模型，None 时使用 KIRO_MODEL 默认值
    worker_id: 传入时为该 worker 启动独立 Playwright 进程并设置 KIRO_HOME

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
            env=_build_env(worker_id),
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
            env=_build_env(worker_id),
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


def get_latest_session_id(req_stem: str | None = None) -> str | None:
    """
    获取最新的 kiro-cli session ID。
    req_stem: 需求文件 stem（如 'requirement-123'），用于精确匹配本需求的会话。
    多 worker 并发时通过 req_stem 避免拿到别的 worker 的 session。
    """
    try:
        result = subprocess.run(
            [KIRO_CLI, "chat", "--list-sessions", "--format", "json"],
            capture_output=True, text=True,
            cwd=str(WORKSPACE), encoding="utf-8", errors="replace", timeout=15
        )
        raw = result.stdout + result.stderr
        start = raw.find("[")
        if start == -1:
            return None
        sessions = json.loads(raw[start:])
        if not sessions:
            return None
        sessions.sort(key=lambda s: s.get("updatedAt", ""), reverse=True)
        if req_stem:
            for s in sessions:
                if req_stem in s.get("title", ""):
                    return s.get("sessionId")
        return sessions[0].get("sessionId")
    except Exception as e:
        log.warning(f"[session] 获取 session_id 失败: {e}")
        return None
