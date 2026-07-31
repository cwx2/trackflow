"""
Playwright 进程管理：仅在脚本启动时清理残留进程。

架构说明：
  所有 kiro-cli 实例（consumer/producer/reviewer）共享项目级 .kiro/settings/mcp.json
  中配置的同一个 Playwright MCP 进程，该进程以 --isolated 模式运行，每个连接
  独立 browser context，互不干扰。无需为每个 worker 启动独立 MCP 进程。
"""

import subprocess
import time
from _config import log


def kill_stale_playwright_processes() -> None:
    """
    脚本启动时调用：扫描并杀掉所有由上一次运行遗留的 Playwright MCP 进程。
    正常情况下 Kiro IDE 管理的 MCP 进程不受影响（端口不在扫描范围内）。
    """
    try:
        import psutil
    except ImportError:
        log.warning("[playwright] psutil 未安装，跳过残留进程清理（pip install psutil 可启用）")
        return

    # 只清理脚本自己之前可能启动的残留进程（历史遗留：worker 独立端口 9101-9130）
    stale_ports = set(range(9101, 9131))
    killed = 0
    for conn in psutil.net_connections(kind="tcp"):
        if conn.laddr.port in stale_ports and conn.status == "LISTEN":
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
    """兼容旧调用，现在是空操作（MCP 由 Kiro IDE 管理）"""
    pass


def cleanup_worker_envs() -> None:
    """兼容旧调用，现在是空操作"""
    pass
