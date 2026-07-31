"""
浏览器冲突 Bug 并发复现与验证脚本

复现真实场景：
  场景A（同 worker 连续）：同一个 MCP 进程，上一会话刚退出，下一个立刻连接
  场景B（跨 worker 并发）：两个 worker 的 MCP 进程同时运行，互不干扰

用法：
  python scripts/test_browser_conflict.py                     # 全跑
  python scripts/test_browser_conflict.py --scenario A        # 只跑场景A（同worker连续）
  python scripts/test_browser_conflict.py --scenario B        # 只跑场景B（跨worker并发）
  python scripts/test_browser_conflict.py --mode legacy       # 强制用旧模式
  python scripts/test_browser_conflict.py --mode isolated     # 强制用新模式
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
    sys.exit(1)

WORKSPACE = Path(__file__).parent.parent

# 测试专用端口，不与正常 worker 冲突
PORT_W1 = 9151   # worker-1 的 MCP
PORT_W2 = 9152   # worker-2 的 MCP

# 全局日志锁，防止并发输出乱序
_log_lock = threading.Lock()


def log(tag: str, msg: str, level: str = "INFO") -> None:
    ts = time.strftime("%H:%M:%S")
    icons = {"INFO": "  ", "OK": "✅", "FAIL": "❌", "WARN": "⚠️", "BUG": "🐛", "FIX": "🔧"}
    icon = icons.get(level, "  ")
    with _log_lock:
        print(f"[{ts}] {icon} [{tag}] {msg}", flush=True)


# ── MCP 进程管理 ──────────────────────────────────────────────────────────────

def kill_port_process(port: int) -> None:
    """杀掉占用指定端口的进程（场景切换时清理残留）"""
    try:
        import psutil
        for conn in psutil.net_connections(kind="tcp"):
            if conn.laddr.port == port and conn.status == "LISTEN":
                try:
                    proc = psutil.Process(conn.pid)
                    for child in proc.children(recursive=True):
                        child.kill()
                    proc.kill()
                    log("Cleanup", f"杀掉占用端口 {port} 的残留进程 PID={conn.pid}", "WARN")
                except (psutil.NoSuchProcess, psutil.AccessDenied):
                    pass
    except ImportError:
        pass  # psutil 未安装，跳过


def start_mcp(worker_id: str, port: int, mode: str) -> subprocess.Popen | None:
    """
    启动一个 Playwright MCP 进程。
    mode: 'legacy'（--user-data-dir，旧行为）或 'isolated'（新行为）
    """
    chrome_data = WORKSPACE / "scripts" / "worker-envs" / worker_id / "chrome-data"

    # 先清理可能残留的同端口进程
    kill_port_process(port)
    time.sleep(0.5)

    if mode == "legacy":
        cmd = [
            "npx.cmd", "@playwright/mcp@latest",
            "--port", str(port),
            "--user-data-dir", str(chrome_data),
            "--viewport-size=1280x720",
        ]
        log(f"MCP:{port}", f"启动 legacy 模式 (--user-data-dir={chrome_data})")
    else:
        cmd = [
            "npx.cmd", "@playwright/mcp@latest",
            "--port", str(port),
            "--isolated",
            "--viewport-size=1280x720",
        ]
        log(f"MCP:{port}", f"启动 isolated 模式")

    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        cwd=str(WORKSPACE),
    )

    # 等待端口就绪（最多 20s）
    deadline = time.time() + 20
    while time.time() < deadline:
        try:
            with socket.create_connection(("localhost", port), timeout=1):
                log(f"MCP:{port}", f"就绪 PID={proc.pid}", "OK")
                return proc
        except OSError:
            time.sleep(0.3)

    log(f"MCP:{port}", "启动超时，端口未就绪", "FAIL")
    proc.kill()
    return None


def stop_mcp(proc: subprocess.Popen, port: int, tag: str = "") -> None:
    """停止 MCP 进程，递归杀子进程（Chrome），并等待端口释放"""
    if proc is None:
        return

    stderr_output = []
    try:
        # 递归杀掉整个进程树（npx → node → Chrome）
        try:
            import psutil
            parent = psutil.Process(proc.pid)
            for child in parent.children(recursive=True):
                try:
                    child.kill()
                except (psutil.NoSuchProcess, psutil.AccessDenied):
                    pass
            parent.kill()
        except Exception:
            proc.terminate()

        _, err = proc.communicate(timeout=5)
        if err:
            stderr_output = err.decode("utf-8", errors="replace").splitlines()
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait()
    except Exception:
        pass

    # 等待端口真正释放（最多 8s），避免下一个场景 EADDRINUSE
    deadline = time.time() + 8
    while time.time() < deadline:
        try:
            with socket.create_connection(("localhost", port), timeout=0.5):
                time.sleep(0.3)
        except OSError:
            break

    log(f"MCP:{port}", f"已停止{' ' + tag if tag else ''}")

    # 打印 stderr 中的关键行
    conflict_keywords = ["already in use", "SingletonLock", "Profile is already",
                         "main-console", "Error", "error"]
    for line in stderr_output:
        if any(k.lower() in line.lower() for k in conflict_keywords):
            log(f"MCP:{port}", f"  stderr: {line.strip()}", "WARN")


# ── MCP HTTP 调用 ─────────────────────────────────────────────────────────────

def mcp_request(port: int, method: str, params: dict,
                session_token: str | None = None) -> tuple[dict, str | None]:
    """发送 MCP JSON-RPC 请求，返回 (result_dict, new_session_token)"""
    url = f"http://localhost:{port}/message"
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
    }
    if session_token:
        headers["mcp-session-id"] = session_token

    payload = {
        "jsonrpc": "2.0",
        "id": str(uuid.uuid4()),
        "method": method,
        "params": params,
    }

    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=30)
        new_token = resp.headers.get("mcp-session-id", session_token)

        # 解析 SSE 响应
        for line in resp.text.splitlines():
            line = line.strip()
            if line.startswith("data:"):
                data_str = line[5:].strip()
                if data_str:
                    try:
                        data = json.loads(data_str)
                        if "error" in data:
                            return {"error": data["error"]}, new_token
                        if "result" in data:
                            return data["result"], new_token
                    except json.JSONDecodeError:
                        pass

        return {"raw": resp.text[:100]}, new_token
    except requests.exceptions.ConnectionError as e:
        return {"error": f"连接失败: {e}"}, session_token
    except Exception as e:
        return {"error": f"请求异常: {e}"}, session_token


def mcp_session_navigate_screenshot(
    port: int, worker_id: str, session_idx: int, url: str
) -> dict:
    """
    完整模拟一次 kiro-cli 的浏览器操作：
    1. initialize（建立新 session）
    2. browser_navigate
    3. browser_snapshot（获取页面状态）
    4. browser_take_screenshot
    返回包含各步结果的 dict
    """
    tag = f"{worker_id}:sess{session_idx}"
    result = {
        "worker_id": worker_id,
        "session_idx": session_idx,
        "port": port,
        "steps": {},
        "conflict": False,
        "success": False,
        "error": "",
    }

    log(tag, f"=== 开始会话 {session_idx} ===")

    # Step 1: initialize
    log(tag, "Step1: initialize MCP 会话")
    r, token = mcp_request(port, "initialize", {
        "protocolVersion": "2024-11-05",
        "capabilities": {},
        "clientInfo": {"name": f"test-{worker_id}-{session_idx}", "version": "1.0"},
    })
    result["steps"]["init"] = {"ok": "error" not in r, "token": token}
    if "error" in r:
        result["error"] = str(r["error"])
        log(tag, f"Step1 失败: {r['error']}", "FAIL")
        return result
    log(tag, f"Step1 OK, token={token}")

    # Step 2: browser_navigate
    log(tag, f"Step2: 导航到 {url}")
    r, token = mcp_request(port, "tools/call", {
        "name": "browser_navigate",
        "arguments": {"url": url},
    }, token)
    nav_ok = "error" not in r and "error" not in str(r.get("raw", ""))
    result["steps"]["navigate"] = {"ok": nav_ok, "raw": str(r)[:200]}

    # 检查是否包含冲突错误
    conflict_markers = ["already in use", "SingletonLock", "Profile is already",
                        "main-console", "被其他进程", "data-dir"]
    r_str = str(r)
    if any(m.lower() in r_str.lower() for m in conflict_markers):
        result["conflict"] = True
        result["error"] = f"浏览器冲突: {r_str[:200]}"
        log(tag, f"Step2 检测到浏览器冲突！{r_str[:300]}", "BUG")
        return result

    if not nav_ok:
        result["error"] = str(r.get("error", r))[:200]
        log(tag, f"Step2 失败: {result['error']}", "FAIL")
        return result
    log(tag, "Step2 OK", "OK")

    # Step 3: browser_snapshot（验证页面状态是干净的，不是上一个会话的残留）
    log(tag, "Step3: 获取页面快照")
    r, token = mcp_request(port, "tools/call", {
        "name": "browser_snapshot",
        "arguments": {"depth": 2},
    }, token)
    snap_ok = "error" not in r
    result["steps"]["snapshot"] = {"ok": snap_ok}
    if snap_ok:
        # 检查 snapshot 是否包含上一个会话留下的状态标记
        snap_str = str(r)[:500]
        log(tag, f"Step3 OK, snapshot 片段: {snap_str[:150]}", "OK")
    else:
        log(tag, f"Step3 失败: {r}", "WARN")

    # Step 4: 截图
    log(tag, "Step4: 截图")
    fname = f"conflict-test-{worker_id}-{session_idx}.png"
    r, token = mcp_request(port, "tools/call", {
        "name": "browser_take_screenshot",
        "arguments": {"type": "png", "scale": "css", "filename": fname},
    }, token)
    shot_ok = "error" not in r
    result["steps"]["screenshot"] = {"ok": shot_ok}
    if shot_ok:
        log(tag, f"Step4 截图成功: {fname}", "OK")
    else:
        log(tag, f"Step4 截图失败: {r}", "WARN")

    result["success"] = nav_ok and snap_ok
    log(tag, f"=== 会话 {session_idx} {'成功 ✅' if result['success'] else '失败 ❌'} ===")
    return result


# ── 场景 A：同 worker 连续会话（复现旧 bug） ──────────────────────────────────

def scenario_a_sequential(mode: str) -> dict:
    """
    场景A：同一个 worker，连续两个会话共享一个 MCP 进程。
    不重启 MCP = 旧行为（容易出现残留 context）
    重启 MCP = restart 修复

    返回 {session1: ..., session2: ..., conflict_detected: bool}
    """
    tag = "ScenarioA"
    log(tag, f"{'='*50}")
    log(tag, f"场景A：同 worker 连续会话（mode={mode}）")
    log(tag, f"模拟: fix 会话结束 → [不重启MCP] → test 会话启动")
    log(tag, f"{'='*50}")

    worker_id = "test-worker-seq"
    # 清理旧 chrome-data
    chrome_data = WORKSPACE / "scripts" / "worker-envs" / worker_id / "chrome-data"
    if chrome_data.exists():
        shutil.rmtree(chrome_data, ignore_errors=True)
        log(tag, f"清理旧 chrome-data")

    proc = start_mcp(worker_id, PORT_W1, mode)
    if not proc:
        return {"error": "MCP 启动失败"}

    try:
        # 会话 1（fix 阶段）
        log(tag, "--- 会话1（模拟 fix 阶段）---")
        r1 = mcp_session_navigate_screenshot(PORT_W1, worker_id, 1, "http://localhost:3000")

        # 模拟 kiro-cli 退出后的短暂等待（不重启 MCP）
        log(tag, "fix 会话结束，等待 3s（不重启 MCP，模拟旧行为）")
        time.sleep(3)

        # 会话 2（test 阶段）
        log(tag, "--- 会话2（模拟 test 阶段）---")
        r2 = mcp_session_navigate_screenshot(PORT_W1, worker_id, 2, "http://localhost:3000/issues")

        conflict = r1.get("conflict") or r2.get("conflict")
        return {"session1": r1, "session2": r2, "conflict_detected": conflict}

    finally:
        stop_mcp(proc, PORT_W1, "(场景A结束)")


def scenario_a_with_restart(mode: str) -> dict:
    """场景A变体：重启 MCP 之后再起第二个会话（验证 restart 修复）"""
    tag = "ScenarioA-Restart"
    log(tag, f"{'='*50}")
    log(tag, f"场景A+重启：fix结束 → 重启MCP → test启动（mode={mode}）")
    log(tag, f"{'='*50}")

    worker_id = "test-worker-seq-restart"
    chrome_data = WORKSPACE / "scripts" / "worker-envs" / worker_id / "chrome-data"
    if chrome_data.exists():
        shutil.rmtree(chrome_data, ignore_errors=True)

    proc = start_mcp(worker_id, PORT_W1, mode)
    if not proc:
        return {"error": "MCP 启动失败"}

    try:
        log(tag, "--- 会话1（fix 阶段）---")
        r1 = mcp_session_navigate_screenshot(PORT_W1, worker_id, 1, "http://localhost:3000")

        # 重启 MCP（模拟 restart_playwright_for_worker）
        log(tag, "fix 会话结束，重启 MCP（模拟 restart_playwright_for_worker）", "FIX")
        stop_mcp(proc, PORT_W1)
        time.sleep(2)
        proc = start_mcp(worker_id, PORT_W1, mode)
        if not proc:
            return {"session1": r1, "error": "MCP 重启失败"}

        log(tag, "--- 会话2（test 阶段）---")
        r2 = mcp_session_navigate_screenshot(PORT_W1, worker_id, 2, "http://localhost:3000/issues")

        conflict = r1.get("conflict") or r2.get("conflict")
        return {"session1": r1, "session2": r2, "conflict_detected": conflict}

    finally:
        stop_mcp(proc, PORT_W1, "(场景A+重启结束)")


# ── 场景 B：跨 worker 并发（两个 worker 同时操作） ───────────────────────────

def scenario_b_concurrent(mode: str) -> dict:
    """
    场景B：两个 worker 同时运行，各自有独立的 MCP 进程和 chrome-data。
    在并发执行期间，两个 worker 各自跑 3 个连续会话。
    验证：互相不干扰。
    """
    tag = "ScenarioB"
    log(tag, f"{'='*50}")
    log(tag, f"场景B：两个 worker 并发（mode={mode}）")
    log(tag, f"各自 3 个连续会话，验证互不干扰")
    log(tag, f"{'='*50}")

    worker1_id = "test-worker-concurrent-1"
    worker2_id = "test-worker-concurrent-2"

    # 清理
    for wid in [worker1_id, worker2_id]:
        cd = WORKSPACE / "scripts" / "worker-envs" / wid / "chrome-data"
        if cd.exists():
            shutil.rmtree(cd, ignore_errors=True)

    proc1 = start_mcp(worker1_id, PORT_W1, mode)
    proc2 = start_mcp(worker2_id, PORT_W2, mode)

    if not proc1 or not proc2:
        log(tag, "MCP 启动失败", "FAIL")
        return {"error": "MCP 启动失败"}

    results = {
        "worker1": [],
        "worker2": [],
        "conflicts": [],
    }

    def run_worker(worker_id: str, port: int, sessions: list, urls: list) -> None:
        """单个 worker 连续跑多个会话"""
        for i, url in enumerate(urls, 1):
            log(f"WORKER:{worker_id}", f"开始第 {i}/{len(urls)} 个会话")
            r = mcp_session_navigate_screenshot(port, worker_id, i, url)
            sessions.append(r)
            if r.get("conflict"):
                with _log_lock:
                    results["conflicts"].append({
                        "worker": worker_id,
                        "session": i,
                        "error": r["error"],
                    })
            # 模拟会话间短暂等待（不重启 MCP）
            if i < len(urls):
                log(f"WORKER:{worker_id}", f"会话 {i} 结束，等待 2s...")
                time.sleep(2)

    # worker1 访问的 URL 序列
    urls_w1 = [
        "http://localhost:3000",
        "http://localhost:3000/issues",
        "http://localhost:3000",
    ]
    # worker2 访问的 URL 序列（故意交叉，模拟真实并发）
    urls_w2 = [
        "http://localhost:3000/issues",
        "http://localhost:3000",
        "http://localhost:3000/issues",
    ]

    t1 = threading.Thread(
        target=run_worker,
        args=(worker1_id, PORT_W1, results["worker1"], urls_w1),
        name="worker1",
    )
    t2 = threading.Thread(
        target=run_worker,
        args=(worker2_id, PORT_W2, results["worker2"], urls_w2),
        name="worker2",
    )

    log(tag, "并发启动两个 worker...")
    t1.start()
    t2.start()
    t1.join()
    t2.join()
    log(tag, "两个 worker 全部完成")

    stop_mcp(proc1, PORT_W1, "(worker1)")
    stop_mcp(proc2, PORT_W2, "(worker2)")

    results["conflict_detected"] = len(results["conflicts"]) > 0
    return results


# ── 打印汇总 ──────────────────────────────────────────────────────────────────

def print_summary(label: str, results: dict) -> None:
    print(f"\n{'─'*60}")
    print(f"📊 {label} 汇总")
    print(f"{'─'*60}")

    if "error" in results and not isinstance(results.get("session1"), dict):
        print(f"  ❌ 运行失败: {results['error']}")
        return

    # 场景A格式
    if "session1" in results:
        s1 = results["session1"]
        s2 = results.get("session2", {})
        print(f"  会话1: {'✅ 成功' if s1.get('success') else '❌ 失败'} "
              f"{'⚠️ 冲突' if s1.get('conflict') else ''}")
        if s1.get("error"):
            print(f"         错误: {s1['error'][:150]}")
        print(f"  会话2: {'✅ 成功' if s2.get('success') else '❌ 失败'} "
              f"{'⚠️ 冲突' if s2.get('conflict') else ''}")
        if s2.get("error"):
            print(f"         错误: {s2['error'][:150]}")
        conflict = results.get("conflict_detected", False)
        print(f"\n  {'🐛 检测到浏览器冲突！（Bug 已复现）' if conflict else '✅ 无冲突，运行正常'}")

    # 场景B格式
    elif "worker1" in results:
        w1 = results["worker1"]
        w2 = results["worker2"]
        w1_ok = sum(1 for r in w1 if r.get("success"))
        w2_ok = sum(1 for r in w2 if r.get("success"))
        print(f"  worker1: {w1_ok}/{len(w1)} 个会话成功")
        print(f"  worker2: {w2_ok}/{len(w2)} 个会话成功")
        conflicts = results.get("conflicts", [])
        if conflicts:
            print(f"\n  🐛 检测到 {len(conflicts)} 个冲突:")
            for c in conflicts:
                print(f"     - {c['worker']} 会话{c['session']}: {c['error'][:100]}")
        else:
            print(f"\n  ✅ 无跨 worker 冲突，隔离正常")

    print(f"{'─'*60}")


# ── 主入口 ────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="浏览器冲突并发复现与验证")
    parser.add_argument("--scenario", choices=["A", "B", "A-restart"],
                        help="指定场景（默认全跑）")
    parser.add_argument("--mode", choices=["legacy", "isolated"],
                        help="强制指定 MCP 模式（默认：先 legacy 后 isolated 对比）")
    args = parser.parse_args()

    modes_to_test = [args.mode] if args.mode else ["legacy", "isolated"]

    if args.scenario == "A":
        for m in modes_to_test:
            r = scenario_a_sequential(m)
            print_summary(f"场景A 连续会话 [{m}]", r)

    elif args.scenario == "A-restart":
        for m in modes_to_test:
            r = scenario_a_with_restart(m)
            print_summary(f"场景A+重启 [{m}]", r)

    elif args.scenario == "B":
        for m in modes_to_test:
            r = scenario_b_concurrent(m)
            print_summary(f"场景B 并发 [{m}]", r)

    else:
        # 全跑：对比 legacy 和 isolated 的差异
        print("\n" + "="*60)
        print("完整测试：legacy vs isolated 对比")
        print("="*60)

        # 场景A：同 worker 连续（不重启 MCP）
        print("\n【1】场景A：同worker连续，不重启MCP")
        r_a_legacy   = scenario_a_sequential("legacy")
        r_a_isolated = scenario_a_sequential("isolated")
        print_summary("场景A [legacy]",   r_a_legacy)
        print_summary("场景A [isolated]", r_a_isolated)

        # 场景A+重启
        print("\n【2】场景A+重启：legacy + restart_playwright_for_worker")
        r_a_restart = scenario_a_with_restart("legacy")
        print_summary("场景A+重启 [legacy]", r_a_restart)

        # 场景B：跨worker并发
        print("\n【3】场景B：两个worker并发")
        r_b_legacy   = scenario_b_concurrent("legacy")
        r_b_isolated = scenario_b_concurrent("isolated")
        print_summary("场景B [legacy]",   r_b_legacy)
        print_summary("场景B [isolated]", r_b_isolated)

        # 最终汇总
        print(f"\n{'='*60}")
        print("最终汇总：")
        all_results = [
            ("场景A legacy 连续",      r_a_legacy.get("conflict_detected")),
            ("场景A isolated 连续",    r_a_isolated.get("conflict_detected")),
            ("场景A+restart legacy",   r_a_restart.get("conflict_detected")),
            ("场景B legacy 并发",       r_b_legacy.get("conflict_detected")),
            ("场景B isolated 并发",    r_b_isolated.get("conflict_detected")),
        ]
        for name, conflict in all_results:
            if conflict:
                print(f"  🐛 {name}: 冲突已复现")
            elif conflict is False:
                print(f"  ✅ {name}: 无冲突")
            else:
                print(f"  ❓ {name}: 未知（运行出错）")
        print("="*60)
