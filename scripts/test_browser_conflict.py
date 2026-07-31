"""
浏览器冲突 Bug 复现与验证脚本（轻量版）

不依赖 kiro-cli，直接调用 Playwright MCP 的 HTTP API。
MCP SSE 接口是 JSON-RPC over SSE，我们用 requests 简单模拟。

用法：
  python scripts/test_browser_conflict.py --reproduce   # 复现：legacy 模式不重启
  python scripts/test_browser_conflict.py --fixed       # 验证：legacy + 重启 MCP
  python scripts/test_browser_conflict.py --isolated    # 验证：--isolated 模式
  python scripts/test_browser_conflict.py               # 三种场景全跑
"""

import argparse
import json
import shutil
import socket
import subprocess
import sys
import time
import threading
import uuid
from pathlib import Path

try:
    import requests
except ImportError:
    print("需要安装 requests: pip install requests")
    raise

WORKSPACE = Path(__file__).parent.parent
WORKER_ID = "test-worker-1"
PORT_A = 9151  # 第一个 MCP 进程
PORT_B = 9152  # 第二个 MCP 进程（--isolated 对比测试用）


# ── MCP 进程管理 ──────────────────────────────────────────────────────────────

def start_mcp(port: int, mode: str, worker_id: str = WORKER_ID) -> subprocess.Popen:
    """
    启动 Playwright MCP 进程。
    mode: 'legacy'（--user-data-dir）或 'isolated'（--isolated）
    """
    chrome_data = WORKSPACE / "scripts" / "worker-envs" / worker_id / "chrome-data"

    if mode == "legacy":
        cmd = [
            "npx.cmd", "@playwright/mcp@latest",
            "--port", str(port),
            "--user-data-dir", str(chrome_data),
            "--viewport-size=1280x720",
        ]
        print(f"[MCP:{port}] legacy 模式，chrome-data={chrome_data.name}")
    else:
        cmd = [
            "npx.cmd", "@playwright/mcp@latest",
            "--port", str(port),
            "--isolated",
            "--viewport-size=1280x720",
        ]
        print(f"[MCP:{port}] isolated 模式")

    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,  # 捕获 stderr 以检测冲突错误
        cwd=str(WORKSPACE),
    )

    deadline = time.time() + 20
    while time.time() < deadline:
        try:
            with socket.create_connection(("localhost", port), timeout=1):
                print(f"[MCP:{port}] 就绪 PID={proc.pid}")
                return proc
        except OSError:
            time.sleep(0.5)

    print(f"[MCP:{port}] ⚠️ 超时未就绪")
    return proc


def stop_mcp(proc: subprocess.Popen, port: int) -> None:
    if proc and proc.poll() is None:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
    print(f"[MCP:{port}] 已停止")


def read_stderr_async(proc: subprocess.Popen, results: list) -> None:
    """异步读取 MCP 进程的 stderr，收集错误信息"""
    try:
        for line in proc.stderr:
            line = line.decode("utf-8", errors="replace").strip()
            if line:
                results.append(line)
    except Exception:
        pass


# ── 直接调用 MCP 工具 ─────────────────────────────────────────────────────────

def call_mcp_tool(port: int, tool_name: str, args: dict,
                  session_token: str | None = None) -> dict:
    """
    通过 MCP HTTP API 调用 Playwright 工具。
    MCP SSE 模式需要 Accept: application/json, text/event-stream
    """
    url = f"http://localhost:{port}/message"
    payload = {
        "jsonrpc": "2.0",
        "id": str(uuid.uuid4()),
        "method": "tools/call",
        "params": {
            "name": tool_name,
            "arguments": args
        }
    }
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
    }
    if session_token:
        headers["mcp-session-id"] = session_token

    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=30)
        # SSE 响应：逐行解析 data: {...} 格式
        for line in resp.text.splitlines():
            line = line.strip()
            if line.startswith("data:"):
                data_str = line[5:].strip()
                if data_str:
                    try:
                        data = json.loads(data_str)
                        if "error" in data:
                            return {"error": data["error"]}
                        if "result" in data:
                            return data["result"]
                    except json.JSONDecodeError:
                        pass
        return {"raw": resp.text[:200]}
    except Exception as e:
        return {"error": str(e)}


def init_mcp_session(port: int) -> str | None:
    """初始化 MCP 会话，返回 session token"""
    url = f"http://localhost:{port}/message"
    payload = {
        "jsonrpc": "2.0",
        "id": "init-1",
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "test-client", "version": "1.0"}
        }
    }
    try:
        resp = requests.post(url, json=payload,
                             headers={
                                 "Content-Type": "application/json",
                                 "Accept": "application/json, text/event-stream",
                             }, timeout=10)
        token = resp.headers.get("mcp-session-id")
        return token
    except Exception as e:
        print(f"  init 失败: {e}")
        return None


def simulate_browser_session(port: int, session_name: str,
                              url: str = "http://localhost:3000") -> tuple[bool, str]:
    """
    模拟一次浏览器会话：初始化 → 导航 → 截图
    返回 (success, error_message)
    """
    print(f"  [{session_name}] 初始化 MCP 会话...")

    # 先初始化
    token = init_mcp_session(port)

    # 导航
    print(f"  [{session_name}] 导航到 {url}...")
    result = call_mcp_tool(port, "browser_navigate", {"url": url}, token)

    error_msg = ""
    if "error" in result:
        error_msg = str(result["error"])
        print(f"  [{session_name}] ❌ 导航失败: {error_msg}")
        return False, error_msg

    # 截图
    result2 = call_mcp_tool(port, "browser_take_screenshot",
                             {"type": "png", "scale": "css",
                              "filename": f"conflict-test-{session_name}.png"}, token)
    if "error" in result2:
        error_msg = str(result2["error"])
        print(f"  [{session_name}] ❌ 截图失败: {error_msg}")
        return False, error_msg

    print(f"  [{session_name}] ✅ 完成")
    return True, ""


# ── 测试场景 ──────────────────────────────────────────────────────────────────

def test_scenario(mcp_mode: str, restart_between: bool) -> dict:
    """
    运行完整测试场景，返回结果字典。
    """
    print(f"\n{'='*60}")
    print(f"场景: mode={mcp_mode}, restart={restart_between}")
    print(f"{'='*60}")

    # 清理旧 chrome-data
    chrome_data = WORKSPACE / "scripts" / "worker-envs" / WORKER_ID / "chrome-data"
    if chrome_data.exists():
        shutil.rmtree(chrome_data, ignore_errors=True)

    stderr_lines: list[str] = []
    proc = start_mcp(PORT_A, mcp_mode)

    # 启动 stderr 监听线程
    t = threading.Thread(target=read_stderr_async, args=(proc, stderr_lines), daemon=True)
    t.start()
    time.sleep(1)

    results = {}
    try:
        # 会话 1（模拟 fix 阶段）
        print("\n[阶段1] 模拟 fix 会话...")
        ok1, err1 = simulate_browser_session(PORT_A, "session1-fix")
        results["session1"] = {"ok": ok1, "error": err1}

        if restart_between:
            print("\n[重置] 停止并重启 MCP 进程...")
            stop_mcp(proc, PORT_A)
            time.sleep(2)
            proc = start_mcp(PORT_A, mcp_mode)
            t2 = threading.Thread(target=read_stderr_async, args=(proc, stderr_lines), daemon=True)
            t2.start()
            time.sleep(1)
        else:
            print("\n[等待] 模拟上一个 kiro-cli 退出后的短暂等待（3s）...")
            time.sleep(3)

        # 会话 2（模拟 test 阶段）
        print("\n[阶段2] 模拟 test 会话...")
        ok2, err2 = simulate_browser_session(PORT_A, "session2-test")
        results["session2"] = {"ok": ok2, "error": err2}

    finally:
        stop_mcp(proc, PORT_A)
        time.sleep(1)

    # 分析冲突
    conflict_keywords = ["already in use", "SingletonLock", "Profile is already",
                         "main-console", "data-dir conflict"]
    conflict_in_stderr = any(
        any(k in line for k in conflict_keywords)
        for line in stderr_lines
    )
    conflict_in_errors = any(
        any(k in (r.get("error", "")) for k in conflict_keywords)
        for r in results.values()
    )

    conflict_detected = conflict_in_stderr or conflict_in_errors
    results["conflict_detected"] = conflict_detected
    results["stderr_sample"] = stderr_lines[:10]

    # 打印结论
    print(f"\n{'─'*40}")
    print(f"结论:")
    print(f"  会话1: {'✅ 正常' if results['session1']['ok'] else '❌ 失败'}")
    print(f"  会话2: {'✅ 正常' if results['session2']['ok'] else '❌ 失败'}")
    if conflict_detected:
        print(f"  ⚠️  检测到浏览器冲突（Bug 复现成功）")
    elif results["session2"]["ok"]:
        print(f"  ✅  无冲突，修复验证通过")
    else:
        print(f"  ❓  会话2失败，但未检测到标准冲突关键词")
        if results["session2"]["error"]:
            print(f"      错误: {results['session2']['error'][:200]}")
    if stderr_lines:
        print(f"\n  MCP stderr（前5行）:")
        for line in stderr_lines[:5]:
            print(f"    {line}")
    print(f"{'─'*40}")

    return results


# ── 主入口 ────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--reproduce", action="store_true",
                       help="复现 Bug（legacy + 不重启，预期出现冲突）")
    group.add_argument("--fixed", action="store_true",
                       help="验证 restart 修复（legacy + 重启，预期无冲突）")
    group.add_argument("--isolated", action="store_true",
                       help="验证 --isolated 修复（不重启，预期无冲突）")
    args = parser.parse_args()

    if args.reproduce:
        r = test_scenario(mcp_mode="legacy", restart_between=False)
        sys.exit(0 if not r["conflict_detected"] else 1)
    elif args.fixed:
        r = test_scenario(mcp_mode="legacy", restart_between=True)
        sys.exit(0 if r["session2"]["ok"] else 1)
    elif args.isolated:
        r = test_scenario(mcp_mode="isolated", restart_between=False)
        sys.exit(0 if r["session2"]["ok"] else 1)
    else:
        print("运行全部三个场景\n")
        r1 = test_scenario("legacy",   restart_between=False)  # 应复现 bug
        r2 = test_scenario("legacy",   restart_between=True)   # 应修复
        r3 = test_scenario("isolated", restart_between=False)  # 应修复

        print(f"\n{'='*60}")
        print("总结:")
        print(f"  1. legacy + 不重启:  {'⚠️  Bug 已复现' if r1['conflict_detected'] else '未复现（可能环境差异）'}")
        print(f"  2. legacy + 重启:    {'✅ 修复有效' if r2['session2']['ok'] else '❌ 仍有问题'}")
        print(f"  3. isolated + 不重启: {'✅ 修复有效' if r3['session2']['ok'] else '❌ 仍有问题'}")
        print(f"{'='*60}")
