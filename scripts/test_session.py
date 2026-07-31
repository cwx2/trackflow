"""
测试 get_latest_session_id 的实际行为。
用法：python scripts/test_session.py
"""
import json
import os
import subprocess
import sys
from pathlib import Path

WORKSPACE = Path(__file__).parent.parent
KIRO_CLI = "kiro-cli"


def test_list_sessions_raw(worker_id: str | None = None):
    """直接调 --list-sessions，打印原始输出，诊断格式问题"""
    env = os.environ.copy()
    if worker_id:
        worker_kiro_home = WORKSPACE / "scripts" / "worker-envs" / worker_id / ".kiro"
        env["KIRO_HOME"] = str(worker_kiro_home)
        print(f"KIRO_HOME = {worker_kiro_home}")
    else:
        print("KIRO_HOME = 默认 (未指定 worker_id)")

    print("\n--- kiro-cli chat --list-sessions --format json ---")
    result = subprocess.run(
        [KIRO_CLI, "chat", "--list-sessions", "--format", "json"],
        capture_output=True, text=True,
        cwd=str(WORKSPACE), env=env,
        encoding="utf-8", errors="replace", timeout=15
    )
    print(f"returncode: {result.returncode}")
    print(f"stdout ({len(result.stdout)} chars):\n{result.stdout[:2000]}")
    print(f"stderr ({len(result.stderr)} chars):\n{result.stderr[:500]}")

    # 尝试解析
    raw = result.stdout + result.stderr
    start = raw.find("[")
    if start == -1:
        print("\n❌ 找不到 JSON 数组开头 '['，无法解析")
        return

    try:
        sessions = json.loads(raw[start:])
        print(f"\n✅ 解析成功，共 {len(sessions)} 个 session")
        if sessions:
            sessions.sort(key=lambda s: s.get("updatedAt", ""), reverse=True)
            print("\n最新 5 个 session：")
            for i, s in enumerate(sessions[:5]):
                print(f"  [{i}] id={s.get('sessionId','')[:12]}... "
                      f"title={s.get('title','')!r} "
                      f"updatedAt={s.get('updatedAt','')}")
            print(f"\n所有 session 的 key: {list(sessions[0].keys())}")
    except json.JSONDecodeError as e:
        print(f"\n❌ JSON 解析失败: {e}")
        print(f"尝试解析的内容: {raw[start:start+200]!r}")


def test_list_sessions_no_format(worker_id: str | None = None):
    """不加 --format json，看默认输出格式"""
    env = os.environ.copy()
    if worker_id:
        worker_kiro_home = WORKSPACE / "scripts" / "worker-envs" / worker_id / ".kiro"
        env["KIRO_HOME"] = str(worker_kiro_home)

    print("\n--- kiro-cli chat --list-sessions (无 --format) ---")
    result = subprocess.run(
        [KIRO_CLI, "chat", "--list-sessions"],
        capture_output=True, text=True,
        cwd=str(WORKSPACE), env=env,
        encoding="utf-8", errors="replace", timeout=15
    )
    print(f"returncode: {result.returncode}")
    print(f"stdout:\n{result.stdout[:2000]}")
    print(f"stderr:\n{result.stderr[:500]}")


def test_list_sessions_help():
    """打印 kiro-cli chat --help，看有没有 --list-sessions 的正确用法"""
    print("\n--- kiro-cli chat --help ---")
    result = subprocess.run(
        [KIRO_CLI, "chat", "--help"],
        capture_output=True, text=True,
        cwd=str(WORKSPACE),
        encoding="utf-8", errors="replace", timeout=10
    )
    print(result.stdout[:3000])
    print(result.stderr[:500])


if __name__ == "__main__":
    # 先检查有没有已有的 worker-envs
    worker_envs = WORKSPACE / "scripts" / "worker-envs"
    existing_workers = [d.name for d in worker_envs.iterdir() if d.is_dir()] if worker_envs.exists() else []
    print(f"现有 worker 环境目录: {existing_workers}")

    # 用默认 KIRO_HOME 测试
    test_list_sessions_raw(worker_id=None)

    # 如果有 worker 环境，也测试 worker 的
    if existing_workers:
        print(f"\n\n{'='*60}")
        print(f"测试 worker: {existing_workers[0]}")
        test_list_sessions_raw(worker_id=existing_workers[0])
    else:
        print("\n\n没有现有 worker 环境目录，跳过 worker 测试")
        print("（需要在 auto_iterate_parallel.py 运行时才会创建）")

    print(f"\n\n{'='*60}")
    test_list_sessions_no_format(worker_id=None)

    print(f"\n\n{'='*60}")
    test_list_sessions_help()
