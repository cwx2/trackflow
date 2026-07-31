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

模块结构：
  _config.py     — 常量、路径、锁、SKILLS、日志
  _playwright.py — Playwright 进程管理、KIRO_HOME 隔离
  _kiro.py       — run_kiro / run_kiro_resume / session 管理
  _parsers.py    — 解析各阶段输出、读取需求状态、提取架构问题
  _utils.py      — 需求文件操作、工作流加载、清理函数
  _workers.py    — producer/reviewer/consumer 三类线程循环

用法：
  python scripts/auto_iterate_parallel.py                               # 2 worker（1+1）
  python scripts/auto_iterate_parallel.py --workers 4                   # 1 生产者 + 3 消费者
  python scripts/auto_iterate_parallel.py --producers 1 --consumers 3  # 手动指定
  python scripts/auto_iterate_parallel.py --skip-produce                # 只跑消费者
"""

import argparse

from _config import (
    KIRO_MODEL, KIRO_MODEL_FIX, REVIEW_DIR, IMPLEMENT_DIR, log,
)
from _utils import count_develop, cleanup_screenshots, cleanup_working
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
    args = parser.parse_args()

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
        num_consumers = args.workers

    log.info("=" * 60)
    log.info(f"TrackFlow 并行迭代 | 生产者={num_producers} 消费者={num_consumers}")
    log.info(f"模型: fix={KIRO_MODEL_FIX or '默认'} | test/review/produce={KIRO_MODEL or '默认'}")
    log.info(f"状态: review={len(list(REVIEW_DIR.glob('*.md')))} "
             f"develop={count_develop()} implement={len(list(IMPLEMENT_DIR.glob('*.md')))}")
    log.info("永不停止，Ctrl+C 手动终止")
    log.info("=" * 60)

    kill_stale_playwright_processes()  # 杀掉残留的旧 Playwright 进程，确保新配置生效
    cleanup_screenshots()              # 只保留最新 200 张截图
    cleanup_working()                  # 将 working/ 残留文件放回 develop/
    main_loop(num_producers, num_consumers, args.skip_produce)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        log.info("\n[中断] 用户手动停止")
        cleanup_working()
        stop_all_playwright()
        cleanup_worker_envs()
