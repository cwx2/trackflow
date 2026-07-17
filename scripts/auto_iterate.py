"""
TrackFlow 自动迭代调度脚本（生产者-消费者模型）

目录结构即状态：
  requirements/review/    ← 生产者产出，待审核
  requirements/develop/   ← 审核通过，待开发
  requirements/implement/ ← 开发完成，已归档
  requirements/rejected/  ← 审核拦截

流程：
  生产者（找需求 → 输出到 review/）
  审核者（从 review/ 取 → 通过移到 develop/，拦截移到 rejected/）
  消费者（从 develop/ 取 → 开发完移到 implement/）

Skill 依赖声明（脚本依赖以下 skill，prompt 中显式引用）：
  - write-requirement  → 生产者：以用户视角操作系统找问题
  - tech-requirement   → 生产者：以架构师视角审计代码
  - review-requirement → 审核者：验证需求真实性，通过/拆解/拒绝
  - fix-requirement    → 消费者：读需求→改代码→测试→提交

重要原则：
  - 脚本的 prompt 必须与 SKILL.md 中的规则保持一致，不得矛盾
  - prompt 中显式声明使用哪个 skill，确保每次运行行为一致
  - 修改 SKILL.md 后脚本自动适用新规则（kiro-cli 每次实时读取 SKILL 文件）

用法：
  python scripts/auto_iterate.py [--max-rounds 20] [--dry-run]
"""

import subprocess
import time
import logging
import argparse
import re
import os
import random
import shutil
from pathlib import Path
from datetime import datetime

# ============ 配置 ============

WORKSPACE = Path(__file__).parent.parent
REVIEW_DIR = WORKSPACE / "requirements" / "review"
DEVELOP_DIR = WORKSPACE / "requirements" / "develop"
IMPLEMENT_DIR = WORKSPACE / "requirements" / "implement"
REJECTED_DIR = WORKSPACE / "requirements" / "rejected"
KIRO_CLI = "kiro-cli"

# 确保目录存在
for d in [REVIEW_DIR, DEVELOP_DIR, IMPLEMENT_DIR, REJECTED_DIR]:
    d.mkdir(parents=True, exist_ok=True)

# 阈值
MIN_DEVELOP_QUEUE = 6       # develop 队列低于此数时触发生产
MAX_RETRIES = 3             # 同一需求最多尝试次数
TIMEOUT_SECONDS = 2400      # 单次 kiro-cli 执行超时（40分钟，夜间任务给宽裕些）
MAX_ROUNDS_PER_DAY = 50     # 每日最大消费轮次（夜间跑设大一些）
COOLDOWN_SECONDS = 10       # 两轮之间冷却时间
MAX_CONSECUTIVE_FAILURES = 5  # 连续失败次数达到此值时跳过当前需求，但不停止脚本

# Skill/Agent 显式声明（脚本使用的所有 skill 和 agent 必须在此注册）
# prompt 中必须包含触发关键词以确保 skill 被激活
SKILLS = {
    "write-requirement": {
        "path": ".kiro/skills/write-requirement/SKILL.md",
        "trigger_keywords": ["找一下需求", "找到需求", "写需求", "找问题"],
        "description": "以真实用户视角操作系统，发现产品问题，撰写需求文档",
    },
    "tech-requirement": {
        "path": ".kiro/skills/tech-requirement/SKILL.md",
        "trigger_keywords": ["技术需求", "技术审计", "审计模块", "数据流分析"],
        "description": "从技术架构角度审计代码，发现逻辑缺失/架构缺陷",
    },
    "review-requirement": {
        "path": ".kiro/skills/review-requirement/SKILL.md",
        "trigger_keywords": ["审核需求", "筛选需求", "review requirement"],
        "description": "审核需求的真实性和价值，通过/拆解/拒绝",
    },
    "fix-requirement": {
        "path": ".kiro/skills/fix-requirement/SKILL.md",
        "trigger_keywords": ["修需求", "处理需求", "fix requirement", "修复需求"],
        "description": "读取需求文件，定位代码，修复问题，测试验证，提交代码",
    },
}

# 生产者配置：skill + 角色 + 模块轮询
# 产品需求（write-requirement）覆盖所有角色 × 关键模块
# 技术需求（tech-requirement）覆盖所有核心模块
PRODUCER_CONFIGS = [
    # --- 管理员视角 ---
    {"skill": "write-requirement", "prompt": "找一下需求，请用管理员研究一下工单管理的完整流程，找到需求"},
    {"skill": "tech-requirement", "prompt": "技术需求：审计工单（Issue）模块的完整数据流"},
    {"skill": "write-requirement", "prompt": "找一下需求，请用管理员研究一下项目管理功能，找到需求"},
    {"skill": "tech-requirement", "prompt": "技术需求：审计项目管理模块的完整数据流"},
    {"skill": "write-requirement", "prompt": "找一下需求，请用管理员研究一下Sprint管理功能，找到需求"},
    {"skill": "tech-requirement", "prompt": "技术需求：审计 Sprint 模块的完整数据流"},
    {"skill": "write-requirement", "prompt": "找一下需求，请用管理员研究一下看板功能，找到需求"},
    {"skill": "tech-requirement", "prompt": "技术需求：审计看板模块的完整数据流"},
    {"skill": "write-requirement", "prompt": "找一下需求，请用管理员研究一下工作流配置功能，找到需求"},
    {"skill": "tech-requirement", "prompt": "技术需求：审计工作流引擎模块的完整数据流"},
    {"skill": "write-requirement", "prompt": "找一下需求，请用管理员研究一下用户与角色管理功能，找到需求"},
    {"skill": "tech-requirement", "prompt": "技术需求：审计权限与认证模块的完整数据流"},
    {"skill": "write-requirement", "prompt": "找一下需求，请用管理员研究一下报表与仪表盘功能，找到需求"},
    {"skill": "tech-requirement", "prompt": "技术需求：审计报表模块的完整数据流"},
    {"skill": "write-requirement", "prompt": "找一下需求，请用管理员研究一下通知系统功能，找到需求"},
    {"skill": "tech-requirement", "prompt": "技术需求：审计通知模块的完整数据流"},
    {"skill": "write-requirement", "prompt": "找一下需求，请用管理员研究一下自定义字段功能，找到需求"},
    {"skill": "tech-requirement", "prompt": "技术需求：审计自定义字段模块的完整数据流"},
    {"skill": "write-requirement", "prompt": "找一下需求，请用管理员研究一下时间追踪与工时功能，找到需求"},
    {"skill": "tech-requirement", "prompt": "技术需求：审计时间追踪模块的完整数据流"},
    # --- 开发人员视角 ---
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个开发人员的使用者，用 wangqiang 账号登录系统，研究工单列表和详情功能，找到需求"},
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个开发人员的使用者，用 wangqiang 账号登录系统，研究看板和Sprint功能，找到需求"},
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个开发人员的使用者，用 wangqiang 账号登录系统，研究时间记录和工作台功能，找到需求"},
    # --- 测试人员视角 ---
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个测试人员的使用者，用 zhaojing 账号登录系统，研究工单状态变更和测试流程，找到需求"},
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个测试人员的使用者，用 zhaojing 账号登录系统，研究筛选、搜索和Saved Query功能，找到需求"},
    # --- 产品经理视角 ---
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个产品经理的使用者，用 sunlei 账号登录系统，研究工单创建和Sprint规划功能，找到需求"},
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个产品经理的使用者，用 sunlei 账号登录系统，研究报表和项目概览功能，找到需求"},
    # --- 观察者视角 ---
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个观察者的使用者，用 huanglei 账号登录系统，研究只读访问和评论功能，找到需求"},
    # --- 技术负责人视角 ---
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个技术负责人的使用者，用 zhangwei 账号登录系统，研究Sprint管理和工单分配功能，找到需求"},
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个技术负责人的使用者，用 zhangwei 账号登录系统，研究代码审查和工作流权限功能，找到需求"},
]

# ============ 日志 ============

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler(WORKSPACE / "scripts" / "auto_iterate.log", encoding="utf-8"),
    ]
)
log = logging.getLogger(__name__)

# ============ 队列操作 ============


def count_files(directory: Path) -> int:
    """统计目录中的需求文件数"""
    return len(list(directory.glob("requirement-*.md")))


def list_files(directory: Path) -> list[Path]:
    """列出目录中的需求文件，按编号排序（子需求排在父需求后面，按子序号升序）"""
    files = list(directory.glob("requirement-*.md"))
    files.sort(key=lambda f: _sort_key(f.name))
    return files


def _sort_key(filename: str) -> tuple[int, int]:
    """
    生成排序 key：(父编号, 子序号)
    - requirement-72.md → (72, 0)
    - requirement-160.md → (160, 0)
    - requirement-160-1.md → (160, 1)
    - requirement-160-2.md → (160, 2)
    父需求(子序号=0)排在同编号的子需求前面。
    """
    # 先尝试匹配子需求：requirement-{parent}-{sub}.md
    match = re.match(r"requirement-(\d+)-(\d+)\.md", filename)
    if match:
        return (int(match.group(1)), int(match.group(2)))
    # 普通需求：requirement-{number}.md
    match = re.match(r"requirement-(\d+)\.md", filename)
    if match:
        return (int(match.group(1)), 0)
    return (0, 0)


def extract_number(filename: str) -> int:
    match = re.search(r"requirement-(\d+)", filename)
    return int(match.group(1)) if match else 0


def is_sub_requirement(filepath: Path) -> bool:
    """判断是否为子需求文件（requirement-{parent}-{sub}.md）"""
    return bool(re.match(r"requirement-\d+-\d+\.md", filepath.name))


def is_parent_of_subs(filepath: Path, directory: Path) -> bool:
    """判断该文件是否是已拆解的父需求（同目录中存在对应的子需求文件）"""
    number = extract_number(filepath.name)
    # 检查是否有子需求文件存在于任何目录
    for dir_path in [directory, REVIEW_DIR, DEVELOP_DIR, IMPLEMENT_DIR, REJECTED_DIR]:
        if list(dir_path.glob(f"requirement-{number}-*.md")):
            return True
    return False


def extract_title(filepath: Path) -> str:
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("#"):
                    return line.lstrip("#").strip()
        return filepath.name
    except Exception:
        return filepath.name


# ============ Kiro CLI 执行器 ============


def run_kiro(prompt: str, timeout: int = TIMEOUT_SECONDS) -> tuple[bool, str]:
    """
    执行 kiro-cli chat 命令。
    实时打印 kiro-cli 输出到终端，同时收集完整输出。
    返回 (success, output)
    """
    cmd = [
        KIRO_CLI, "chat",
        "--no-interactive",
        "--trust-all-tools",
        prompt,
    ]

    log.info(f"执行: kiro-cli chat \"{prompt[:80]}...\"")
    log.info("-" * 40 + " kiro-cli 输出开始 " + "-" * 40)
    start = time.time()
    output_lines = []

    # 环境变量：抑制子进程中命令的交互式行为
    env = os.environ.copy()
    env["GIT_TERMINAL_PROMPT"] = "0"     # git 不弹认证提示
    env["GIT_EDITOR"] = "true"           # git commit 无编辑器（兜底）
    env["EDITOR"] = "true"               # 通用编辑器指向 true（立即退出）
    env["VISUAL"] = "true"
    env["CI"] = "true"                   # 很多工具检测 CI 环境跳过交互
    env["NPM_CONFIG_YES"] = "true"       # npm 自动 yes

    try:
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            cwd=str(WORKSPACE),
            encoding="utf-8",
            errors="replace",
            env=env,
        )

        # 实时读取并打印输出
        for line in process.stdout:
            line_stripped = line.rstrip("\n")
            print(f"  │ {line_stripped}")
            output_lines.append(line_stripped)

        process.wait(timeout=timeout)
        elapsed = time.time() - start
        success = process.returncode == 0
        output = "\n".join(output_lines)

        log.info("-" * 40 + " kiro-cli 输出结束 " + "-" * 40)
        if success:
            log.info(f"完成 ({elapsed:.0f}s, exit=0)")
        else:
            log.warning(f"失败 (exit={process.returncode}, {elapsed:.0f}s)")

        return success, output

    except subprocess.TimeoutExpired:
        process.kill()
        elapsed = time.time() - start
        log.error(f"超时 ({elapsed:.0f}s > {timeout}s)，已杀进程")
        return False, "TIMEOUT"

    except Exception as e:
        log.error(f"执行异常: {e}")
        return False, str(e)


# ============ 生产者 ============


def run_producer(skill_index: int) -> bool:
    """运行生产者：随机选择配置找需求（不同 skill + 不同角色 + 不同模块）"""
    config = random.choice(PRODUCER_CONFIGS)
    skill_name = config["skill"]
    skill_info = SKILLS[skill_name]
    log.info(f"[生产者] 随机选择: {skill_name} — {config['prompt'][:50]}...")

    # 显式在 prompt 开头声明使用哪个 skill，确保激活
    explicit_prompt = (
        f"[使用 skill: {skill_name}] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"{config['prompt']}"
    )

    success, output = run_kiro(explicit_prompt)
    return success


# ============ 审核者 ============


def run_reviewer() -> int:
    """运行审核者：从 review/ 审核，通过的移到 develop/，拦截的移到 rejected/"""
    review_files = list_files(REVIEW_DIR)
    if not review_files:
        log.info("[审核] review/ 目录为空，无需审核")
        return count_files(DEVELOP_DIR)

    skill_name = "review-requirement"
    skill_info = SKILLS[skill_name]
    req_list = ", ".join([f"requirement-{extract_number(f.name)}.md" for f in review_files])
    prompt = (
        f"[使用 skill: {skill_name}] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"审核需求：请审核 requirements/review/ 中的以下需求文件：{req_list}。"
        f"文件路径格式为 requirements/review/requirement-XX.md。"
    )

    log.info(f"[审核] 审核 {len(review_files)} 个需求...")
    success, output = run_kiro(prompt)

    # 返回当前 develop 队列深度
    develop_count = count_files(DEVELOP_DIR)
    log.info(f"[审核] 完成，develop 队列：{develop_count} 个")
    return develop_count


# ============ 消费者 ============


def run_consumer(req_file: Path, attempt: int = 1) -> bool:
    """运行消费者：处理一个需求"""
    number = extract_number(req_file.name)
    title = extract_title(req_file)
    skill_name = "fix-requirement"
    skill_info = SKILLS[skill_name]
    log.info(f"[消费者] 处理 requirement-{number}：{title} (尝试 {attempt}/{MAX_RETRIES})")

    prompt = (
        f"[使用 skill: {skill_name}] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"修需求 requirement-{number}，需求文件位于 requirements/develop/requirement-{number}.md"
    )
    success, output = run_kiro(prompt)

    if success:
        # 检查文件是否已被 fix-requirement 移动到 implement/
        if not req_file.exists():
            log.info(f"[消费者] requirement-{number} 处理完成 ✅ (文件已归档)")
        else:
            # 如果 skill 没有自动移动，手动移动
            dest = IMPLEMENT_DIR / req_file.name
            shutil.move(str(req_file), str(dest))
            log.info(f"[消费者] requirement-{number} 处理完成 ✅ (手动归档)")
    else:
        log.warning(f"[消费者] requirement-{number} 处理失败 (尝试 {attempt})")

    return success


# ============ 主调度循环 ============


def main_loop(max_rounds: int, dry_run: bool = False):
    """主循环：生产者-消费者交替"""
    consume_count = 0       # 消费轮次计数（仅计消费者）
    failure_streak = 0
    retry_map: dict[int, int] = {}  # req_number -> attempt count

    log.info("=" * 60)
    log.info("TrackFlow 自动迭代启动")
    log.info(f"develop 阈值: {MIN_DEVELOP_QUEUE} | 最大消费轮次: {max_rounds} | 超时: {TIMEOUT_SECONDS}s")
    log.info(f"当前状态: review={count_files(REVIEW_DIR)} develop={count_files(DEVELOP_DIR)} implement={count_files(IMPLEMENT_DIR)}")
    log.info("=" * 60)

    while max_rounds == 0 or consume_count < max_rounds:
        develop_count = count_files(DEVELOP_DIR)
        review_count = count_files(REVIEW_DIR)

        log.info(f"\n--- 状态检查 | review={review_count} develop={develop_count} consumed={consume_count}/{max_rounds} ---")

        # ===== 判断是否需要生产 =====
        if develop_count == 0:
            log.info(f"[调度] develop 为空，进入生产+审核阶段（目标: ≥{MIN_DEVELOP_QUEUE} 个）")

            if dry_run:
                log.info("[DRY RUN] 跳过生产阶段")
                break

            # 先审核 review 里已有的
            if review_count > 0:
                run_reviewer()
                develop_count = count_files(DEVELOP_DIR)
                if develop_count >= MIN_DEVELOP_QUEUE:
                    continue  # 审核后已达标，直接消费

            # 还不够，找新需求直到达标
            while count_files(DEVELOP_DIR) < MIN_DEVELOP_QUEUE:
                run_producer(0)  # 参数保留兼容签名，内部随机选择
                time.sleep(COOLDOWN_SECONDS)

                # 找完后审核
                if count_files(REVIEW_DIR) > 0:
                    run_reviewer()

                time.sleep(COOLDOWN_SECONDS)

            continue

        # ===== 消费阶段 =====
        develop_files = list_files(DEVELOP_DIR)
        if not develop_files:
            log.info("[调度] develop 目录为空（状态异常），重新检查")
            continue

        req_file = develop_files[0]

        # 跳过已拆解的父需求（有子需求存在则不直接处理父需求）
        if not is_sub_requirement(req_file) and is_parent_of_subs(req_file, DEVELOP_DIR):
            log.info(f"[调度] {req_file.name} 是已拆解的父需求，移到 implement/ 归档")
            dest = IMPLEMENT_DIR / req_file.name
            shutil.move(str(req_file), str(dest))
            continue

        number = extract_number(req_file.name)
        attempt = retry_map.get(number, 0) + 1

        if attempt > MAX_RETRIES:
            log.warning(f"[调度] requirement-{number} 超过最大重试次数，移到 rejected/")
            dest = REJECTED_DIR / f"{req_file.name}"
            shutil.move(str(req_file), str(dest))
            failure_streak += 1
            continue

        if dry_run:
            log.info(f"[DRY RUN] 跳过消费 requirement-{number}")
            consume_count += 1
            continue

        retry_map[number] = attempt
        success = run_consumer(req_file, attempt)
        consume_count += 1

        if success:
            failure_streak = 0
        else:
            failure_streak += 1

        # 连续失败处理：不停止脚本，而是跳过当前需求继续下一个
        if failure_streak >= MAX_CONSECUTIVE_FAILURES:
            log.warning(f"[调度] 连续 {failure_streak} 次失败，暂停 60 秒后继续（不停止）")
            failure_streak = 0  # 重置，给后续需求机会
            time.sleep(60)

        time.sleep(COOLDOWN_SECONDS)

    log.info(f"[调度] 主循环结束 (消费了 {consume_count} 个需求)")


# ============ 入口 ============


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="TrackFlow 自动迭代调度脚本")
    parser.add_argument("--max-rounds", type=int, default=0,
                        help=f"最大消费轮次 (默认: 0 = 无限制，跑到没需求为止)")
    parser.add_argument("--dry-run", action="store_true",
                        help="试运行模式，不实际执行 kiro-cli")
    args = parser.parse_args()

    try:
        main_loop(max_rounds=args.max_rounds, dry_run=args.dry_run)
    except KeyboardInterrupt:
        log.info("\n[中断] 用户手动停止")
    except Exception as e:
        log.error(f"[异常] 未处理的错误: {e}", exc_info=True)
        log.info("[恢复] 等待 30 秒后重新启动主循环...")
        time.sleep(30)
        try:
            main_loop(max_rounds=args.max_rounds, dry_run=args.dry_run)
        except KeyboardInterrupt:
            log.info("\n[中断] 用户手动停止")
        except Exception as e2:
            log.error(f"[异常] 二次重启仍失败，退出: {e2}", exc_info=True)
