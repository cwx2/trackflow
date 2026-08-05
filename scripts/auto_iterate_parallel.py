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
  - 浏览器隔离：Playwright MCP 以 --isolated 模式运行，每个连接独立 browser context

AI 后端：
　支持两种 AI 提供者，通过环境变量 TRACKFLOW_AI 或 _config.py 中的 AI_PROVIDER 切换。

　　Claude Code（默认）：
　　　　claude --print --dangerously-skip-permissions --output-format json
　　　　- 模型：通过 _config.py 中 CLAUDE_MODEL / CLAUDE_MODEL_FIX 设置
　　　　  可选 opus / sonnet / haiku / fable，空字符串 = 使用默认
　　　　- MCP：从 .claude/mcp.json 加载（5 个 Server、51 个工具）
　　　　- 权限：.claude/settings.json 全放行
　　　　- 预算：CLAUDE_MAX_BUDGET_USD / CLAUDE_MAX_BUDGET_USD_FIX
　　　　- 上下文：CLAUDE.md 自动加载项目规范（整合自 .kiro/steering/）

　　Kiro CLI（兼容模式）：
　　　　set TRACKFLOW_AI=kiro 切回

模块结构：
  _config.py     — 常量、路径、锁、SKILLS、AI_PROVIDER、日志
  _claude.py     — Claude Code CLI 封装（run_claude / run_claude_resume）
  _kiro.py       — Kiro CLI 封装（run_kiro / run_kiro_resume）
  _parsers.py    — 解析各阶段输出、读取需求状态、提取架构问题
  _workers.py    — producer/reviewer/consumer 三类线程循环（自动切换 AI 后端）
  _utils.py      — 需求文件操作、工作流加载、清理函数
  _playwright.py — Playwright 残留进程清理

用法：
  # 默认启动（Kiro CLI，1 生产者 + 1 消费者）
  python scripts/auto_iterate_parallel.py

  # 使用 Claude Code（通过 --claude 切换）
  python scripts/auto_iterate_parallel.py --claude

  # 4 worker（1p + 3c）
  python scripts/auto_iterate_parallel.py --workers 4

  # 手动指定比例
  python scripts/auto_iterate_parallel.py --producers 1 --consumers 3

  # 只消费存量需求（跳过生产+审核）
  python scripts/auto_iterate_parallel.py --consumers 3 --skip-produce

  # 并行模式
  python scripts/auto_iterate_parallel.py --consumers 3 --skip-produce --mode pipeline  # 并行（推荐）
  python scripts/auto_iterate_parallel.py --consumers 3 --skip-produce --mode safe      # 串行（调试）

  # Claude + 高吞吐（6 worker，2p + 4c）
  python scripts/auto_iterate_parallel.py --claude --workers 6 --mode pipeline
"""

import argparse
import _config

from _config import (
    KIRO_MODEL, KIRO_MODEL_FIX, CLAUDE_MODEL, CLAUDE_MODEL_FIX, REVIEW_DIR, IMPLEMENT_DIR, log,
)
from _utils import count_develop, cleanup_screenshots, cleanup_working, AutomationInstanceLock
from _playwright import kill_stale_playwright_processes, stop_all_playwright, cleanup_worker_envs
from _workers import main_loop


def main() -> None:
    parser = argparse.ArgumentParser(description="TrackFlow 并行迭代脚本（永不停止）")
    parser.add_argument("--workers", type=int, default=2,
                        help="总 worker 数量（默认 2）。自动按 1:3 比例分配生产者和消费者")
    parser.add_argument("--producers", type=int, default=None,
                        help="手动指定生产者数量（覆盖自动分配）")
    parser.add_argument("--consumers", type=int, default=None,
                        help="手动指定消费者数量（覆盖自动分配）")
    parser.add_argument("--skip-produce", action="store_true",
                        help="跳过生产阶段，只消费 develop/ 中的现有需求")
    parser.add_argument("--claude", action="store_true",
                        help="使用 Claude Code CLI 替代默认的 Kiro CLI")
    parser.add_argument("--mode", choices=["safe", "pipeline", "full"], default=None,
                        help=(
                            "并行模式（覆盖 _config.py 中的 PARALLEL_MODE）：\n"
                            "  safe     — 整个流水线串行，绝对无冲突（调试用）\n"
                            "  pipeline — fix/test/review 并行，push 时排队（默认推荐）\n"
                            "  full     — 全并行含 push，push 冲突自动重试（积压多时用）"
                        ))
    args = parser.parse_args()

    # --claude 切换 AI 提供者（必须在任何 import _workers 之前设置）
    if args.claude:
        _config.AI_PROVIDER = "claude"

    # 命令行 --mode 覆盖配置文件
    if args.mode is not None:
        _config.PARALLEL_MODE = args.mode

    # 计算生产者/消费者数量
    if args.producers is not None or args.consumers is not None:
        num_producers = args.producers if args.producers is not None else 1
        num_consumers = args.consumers if args.consumers is not None else max(1, args.workers - num_producers)
    else:
        # 自动分配：2→(1,1)  3→(1,2)  4→(1,3)  6→(2,4)  8→(2,6)
        num_producers = max(1, args.workers // 4) if args.workers >= 4 else 1
        num_consumers = max(1, args.workers - num_producers)

    if args.skip_produce:
        num_producers = 0
        num_consumers = args.consumers if args.consumers is not None else args.workers

    provider = _config.AI_PROVIDER
    if provider == "claude":
        model_info = f"fix={CLAUDE_MODEL or '默认'} | test/review/produce={CLAUDE_MODEL or '默认'}"
    else:
        model_info = f"fix={KIRO_MODEL_FIX or '默认'} | test/review/produce={KIRO_MODEL or '默认'}"

    log.info("=" * 60)
    log.info(f"TrackFlow 并行迭代 | 生产者={num_producers} 消费者={num_consumers} 模式={_config.PARALLEL_MODE}")
    log.info(f"AI 提供者: {provider} | 模型: {model_info}")
    log.info(f"状态: review={len(list(REVIEW_DIR.glob('*.md')))} "
             f"develop={count_develop()} implement={len(list(IMPLEMENT_DIR.glob('*.md')))}")
    log.info("永不停止，Ctrl+C 手动终止")
    log.info("=" * 60)

    try:
        with AutomationInstanceLock():
            kill_stale_playwright_processes()  # 杀掉残留的旧 Playwright 进程，确保新配置生效
            cleanup_screenshots()              # 只保留最新 200 张截图
            cleanup_working()                  # 将 working/ 残留文件放回 develop/
            main_loop(num_producers, num_consumers, args.skip_produce)
    except RuntimeError as e:
        log.error(f"[启动失败] {e}")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        log.info("\n[中断] 用户手动停止")
        cleanup_working()
        stop_all_playwright()
        cleanup_worker_envs()
