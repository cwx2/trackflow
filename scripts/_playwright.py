"""
Playwright 多实例管理：worker 端口分配、独立浏览器进程启动/停止、KIRO_HOME 隔离。
"""

import json
import os
import shutil
import socket
import subprocess
import threading
import time
from pathlib import Path

from _config import (
    WORKSPACE, PLAYWRIGHT_PORT_BASE, log,
)

# 全局注册表：worker_id → Popen
_playwright_processes: dict[str, subprocess.Popen] = {}
_playwright_lock = threading.Lock()


def get_worker_port(worker_id: str) -> int:
    """
    固定端口映射：
      producer-N → 9101+N
      consumer-N → 9111+N
      reviewer / 其他 → 9121+
    """
    import re
    match = re.search(r"(\d+)$", worker_id)
    index = int(match.group(1)) if match else 1
    if worker_id.startswith("producer"):
        return PLAYWRIGHT_PORT_BASE + index
    elif worker_id.startswith("consumer"):
        return PLAYWRIGHT_PORT_BASE + 10 + index
    else:
        return PLAYWRIGHT_PORT_BASE + 20 + index


def start_playwright_for_worker(worker_id: str) -> int:
    """
    启动（或复用）指定 worker 的 Playwright MCP SSE 进程。
    返回分配的端口号。

    每个 worker 拥有独立的 Chrome user-data-dir，彻底避免多实例争抢同一浏览器。
    """
    port = get_worker_port(worker_id)
    with _playwright_lock:
        existing = _playwright_processes.get(worker_id)
        if existing and existing.poll() is None:
            return port  # 进程仍在运行，直接复用

        if existing is not None:
            log.warning(f"[playwright] {worker_id} 的 MCP 进程已退出（code={existing.poll()}），重启...")
            time.sleep(2)

        # 每个 worker 独立的 Chrome 用户数据目录
        user_data_dir = WORKSPACE / "scripts" / "worker-envs" / worker_id / "chrome-data"
        # --isolated 模式：每次 kiro-cli 连接使用独立的 browser context，
        # 连接断开后自动清理，不依赖 user-data-dir，彻底避免与 Kiro IDE 的
        # Chrome 实例争抢资源（main-console data-dir 冲突问题的根本解决方案）。
        cmd = [
            "npx.cmd", "@playwright/mcp@latest",
            "--port", str(port),
            "--isolated",
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

        # 等待端口就绪（最多 15 秒）
        deadline = time.time() + 15
        while time.time() < deadline:
            try:
                with socket.create_connection(("localhost", port), timeout=1):
                    log.info(f"[playwright] {worker_id} 端口 {port} 就绪")
                    return port
            except OSError:
                time.sleep(0.5)
        log.warning(f"[playwright] {worker_id} 端口 {port} 等待超时，继续...")
        return port


def kill_stale_playwright_processes() -> None:
    """
    脚本启动时调用：扫描并杀掉所有占用 worker 端口的残留进程。

    脚本重启后 _playwright_processes 字典为空，但旧进程可能仍占着端口，
    导致新进程无法绑定（kiro-cli 会连上没有 --user-data-dir 的旧进程）。
    """
    try:
        import psutil
    except ImportError:
        log.warning("[playwright] psutil 未安装，跳过残留进程清理（pip install psutil 可启用）")
        return

    worker_ports = set(range(PLAYWRIGHT_PORT_BASE + 1, PLAYWRIGHT_PORT_BASE + 31))
    killed = 0
    for conn in psutil.net_connections(kind="tcp"):
        if conn.laddr.port in worker_ports and conn.status == "LISTEN":
            try:
                proc = psutil.Process(conn.pid)
                cmdline = " ".join(proc.cmdline())
                if "@playwright/mcp" in cmdline or "playwright\\mcp" in cmdline:
                    log.info(f"[playwright] 杀掉残留进程 PID={conn.pid} 端口={conn.laddr.port}")
                    proc.kill()
                    killed += 1
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                pass
    if killed:
        log.info(f"[playwright] 清理完成，共杀掉 {killed} 个残留进程")
        time.sleep(1)


def stop_all_playwright() -> None:
    """停止所有 Playwright MCP 进程（脚本退出时调用）"""
    with _playwright_lock:
        for worker_id, proc in list(_playwright_processes.items()):
            if proc.poll() is None:
                _kill_proc_tree(proc.pid)
                try:
                    proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    pass
        _playwright_processes.clear()
    log.info("[playwright] 所有独立 MCP 进程已停止")


def make_worker_mcp_config(worker_id: str, port: int) -> dict:
    """生成 worker 专属 mcp.json：playwright 改为 SSE url，其余沿用主配置"""
    main_config_path = WORKSPACE / ".kiro" / "settings" / "mcp.json"
    with open(main_config_path, "r", encoding="utf-8") as f:
        config = json.load(f)
    playwright_autoApprove = config["mcpServers"].get("playwright", {}).get("autoApprove", [])
    config["mcpServers"]["playwright"] = {
        "url": f"http://localhost:{port}/sse",
        "disabled": False,
        "autoApprove": playwright_autoApprove,
    }
    return config


def setup_worker_kiro_dir(worker_id: str, port: int) -> Path:
    """
    创建 worker 专属 .kiro 目录，写入指向独立 Playwright 端口的 mcp.json。
    通过 KIRO_HOME 让 kiro-cli 使用此目录代替默认的 ~/.kiro。

    目录结构：
      scripts/worker-envs/{worker_id}/.kiro/settings/mcp.json  ← playwright 专属端口
      scripts/worker-envs/{worker_id}/.kiro/steering/          ← 软链接到主配置
      scripts/worker-envs/{worker_id}/.kiro/skills/            ← 软链接到主配置

    幂等：mcp.json 每次覆盖写入，软链接已存在则跳过。
    """
    worker_env_dir = WORKSPACE / "scripts" / "worker-envs" / worker_id
    kiro_settings_dir = worker_env_dir / ".kiro" / "settings"
    kiro_settings_dir.mkdir(parents=True, exist_ok=True)

    # 写入专属 mcp.json
    mcp_config = make_worker_mcp_config(worker_id, port)
    with open(kiro_settings_dir / "mcp.json", "w", encoding="utf-8") as f:
        json.dump(mcp_config, f, indent=2, ensure_ascii=False)

    # steering / skills 软链接到主配置（kiro-cli 需要读取 steering 规则）
    src_kiro = WORKSPACE / ".kiro"
    dst_kiro = worker_env_dir / ".kiro"
    for subdir in ["steering", "skills"]:
        src = src_kiro / subdir
        dst = dst_kiro / subdir
        if src.exists() and not dst.exists():
            try:
                os.symlink(src, dst)
            except (OSError, NotImplementedError):
                pass  # Windows 无管理员权限时忽略，kiro-cli 会向上查找

    log.debug(f"[worker-env] {worker_id} 配置目录: {worker_env_dir}")
    return worker_env_dir


def _kill_proc_tree(pid: int) -> None:
    """递归杀掉进程及其所有子进程（处理 npx → node → Chrome 的进程树）"""
    try:
        import psutil
        parent = psutil.Process(pid)
        children = parent.children(recursive=True)
        for child in children:
            try:
                child.kill()
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                pass
        parent.kill()
    except Exception:
        pass  # psutil 未安装或进程已不存在


def _wait_port_free(port: int, timeout: float = 10.0) -> bool:
    """等待端口被释放，返回 True 表示端口已空闲"""
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with socket.create_connection(("localhost", port), timeout=0.5):
                time.sleep(0.5)  # 端口仍被占用，继续等
        except OSError:
            return True  # 连不上 = 端口已释放
    return False


def restart_playwright_for_worker(worker_id: str) -> None:
    """
    重置指定 worker 的 Playwright MCP 进程（不影响其他 worker）。

    每次 kiro-cli 会话结束后调用，确保下一轮会话拿到干净的 browser context。
    - 递归杀掉 npx 及其子进程（Chrome），彻底释放端口
    - 等待端口真正释放后才返回，避免下一次 start 时 EADDRINUSE
    """
    port = get_worker_port(worker_id)
    with _playwright_lock:
        proc = _playwright_processes.pop(worker_id, None)
        if proc and proc.poll() is None:
            _kill_proc_tree(proc.pid)
            try:
                proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                pass

    # 锁外等待端口释放（最多 10s），避免下次 start 时 EADDRINUSE
    freed = _wait_port_free(port, timeout=10.0)
    if freed:
        log.debug(f"[playwright] {worker_id} MCP 进程已重置，端口 {port} 已释放")
    else:
        log.warning(f"[playwright] {worker_id} 端口 {port} 10s 内未释放，可能影响下次启动")


def cleanup_worker_envs() -> None:
    """清理所有 worker 环境目录（脚本退出时调用）"""
    env_base = WORKSPACE / "scripts" / "worker-envs"
    if env_base.exists():
        shutil.rmtree(env_base, ignore_errors=True)
        log.info("[worker-env] 已清理所有 worker 环境目录")
