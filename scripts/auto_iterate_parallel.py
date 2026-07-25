"""
TrackFlow 并行迭代脚本（永不停止）

多个 worker 并行找需求 + 并行消费需求，审核单线程保证去重。
脚本永远运行，只能通过 Ctrl+C 手动停止。

流程循环（无限）：
  1. develop/ 队列不足 → 多代理并行找需求（生产）
  2. review/ 有文件 → 单代理审核（串行，保证去重）
  3. develop/ 有文件 → 多代理并行消费（修需求）
  4. 失败的需求重试 3 次后跳过（移到 rejected/），继续下一个
  5. 全部消费完 → 回到 1 继续生产

防冲突：
  - 消费阶段：worker 通过"领取"机制（原子 rename 到 working/）防止重复处理
  - 生产阶段：各 worker 随机选不同配置，产出的需求文件编号由 MCP 工具保证唯一
  - 审核阶段：单线程，天然无冲突

用法：
  python scripts/auto_iterate_parallel.py                # 2 worker 并行，永不停止
  python scripts/auto_iterate_parallel.py --workers 3    # 3 worker 并行
  python scripts/auto_iterate_parallel.py --skip-produce # 只消费不生产
  python scripts/auto_iterate_parallel.py --workers 2 --skip-produce  # 只消费不生产
"""

import subprocess
import time
import logging
import argparse
import re
import os
import random
import shutil
import threading
import json
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, wait
from datetime import datetime

# ============ 配置 ============

WORKSPACE = Path(__file__).parent.parent
REQUIREMENTS_BASE = WORKSPACE / "requirements"
REVIEW_DIR = REQUIREMENTS_BASE / "review"
DEVELOP_DIR = REQUIREMENTS_BASE / "develop"
IMPLEMENT_DIR = REQUIREMENTS_BASE / "implement"
REJECTED_DIR = REQUIREMENTS_BASE / "rejected"
WORKING_DIR = REQUIREMENTS_BASE / "working"
KIRO_CLI = "kiro-cli"

for d in [REVIEW_DIR, DEVELOP_DIR, IMPLEMENT_DIR, REJECTED_DIR, WORKING_DIR]:
    d.mkdir(parents=True, exist_ok=True)

# 阈值
MIN_DEVELOP_QUEUE = 6       # develop 低于此数时触发生产
TIMEOUT_SECONDS = 2400      # 单次 kiro-cli 超时（40分钟）
MAX_RETRIES = 3
COOLDOWN_SECONDS = 5
MAX_TEST_RETRIES = 3        # 测试最多重试轮数
MAX_REVIEW_RETRIES = 2      # 审核最多重试轮数

# 模型配置（None = 使用 kiro-cli 默认模型）
# 可选值：claude-sonnet-4.6 / claude-opus-4.5 / claude-sonnet-4.5 / auto
KIRO_MODEL = "claude-sonnet-4.6"

# 领取锁
_claim_lock = threading.Lock()

# Skill 声明
SKILLS = {
    "write-requirement": {"path": ".kiro/skills/write-requirement/SKILL.md"},
    "tech-requirement": {"path": ".kiro/skills/tech-requirement/SKILL.md"},
    "review-requirement": {"path": ".kiro/skills/review-requirement/SKILL.md"},
    "fix-requirement": {"path": ".kiro/skills/fix-requirement/SKILL.md"},
    "e2e-test": {"path": ".kiro/skills/e2e-test/SKILL.md"},
    "code-review": {"path": ".kiro/skills/code-review/SKILL.md"},
}

# 生产者配置池
PRODUCER_CONFIGS = [
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
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个开发人员的使用者，用 wangqiang 账号登录系统，研究工单列表和详情功能，找到需求"},
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个开发人员的使用者，用 wangqiang 账号登录系统，研究看板和Sprint功能，找到需求"},
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个测试人员的使用者，用 zhaojing 账号登录系统，研究工单状态变更和测试流程，找到需求"},
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个产品经理的使用者，用 sunlei 账号登录系统，研究工单创建和Sprint规划功能，找到需求"},
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个观察者的使用者，用 huanglei 账号登录系统，研究只读访问和评论功能，找到需求"},
    {"skill": "write-requirement", "prompt": "找一下需求，你现在是一个技术负责人的使用者，用 zhangwei 账号登录系统，研究Sprint管理和工单分配功能，找到需求"},
]

# ============ 日志 ============

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(threadName)s] %(message)s",
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler(WORKSPACE / "scripts" / "auto_iterate_parallel.log", encoding="utf-8"),
    ]
)
log = logging.getLogger(__name__)

# ============ 工具函数 ============


def _sort_key(filename: str) -> tuple:
    match = re.match(r"requirement-(\d+)-(\d+)\.md", filename)
    if match:
        return (int(match.group(1)), int(match.group(2)))
    match = re.match(r"requirement-(\d+)\.md", filename)
    if match:
        return (int(match.group(1)), 0)
    return (0, 0)


def extract_number(filename: str) -> int:
    match = re.search(r"requirement-(\d+)", filename)
    return int(match.group(1)) if match else 0


def is_sub_requirement(filepath: Path) -> bool:
    return bool(re.match(r"requirement-\d+-\d+\.md", filepath.name))


def has_sub_requirements(number: int) -> bool:
    for dir_path in [DEVELOP_DIR, WORKING_DIR, IMPLEMENT_DIR, REJECTED_DIR]:
        if list(dir_path.glob(f"requirement-{number}-*.md")):
            return True
    return False


def list_available(directory: Path) -> list[Path]:
    """列出可消费的需求文件（排除已拆解父需求）"""
    files = list(directory.glob("requirement-*.md"))
    files.sort(key=lambda f: _sort_key(f.name))
    result = []
    for f in files:
        if not is_sub_requirement(f):
            number = extract_number(f.name)
            if has_sub_requirements(number):
                continue
        result.append(f)
    return result


def count_develop() -> int:
    return len(list_available(DEVELOP_DIR))


def extract_title(filepath: Path) -> str:
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip().startswith("#"):
                    return line.strip().lstrip("#").strip()
    except Exception:
        pass
    return filepath.name


# ============ Kiro CLI ============


def run_kiro(prompt: str, label: str) -> tuple[bool, str]:
    cmd = [KIRO_CLI, "chat", "--no-interactive", "--trust-all-tools"]
    if KIRO_MODEL:
        cmd += ["--model", KIRO_MODEL]
    cmd.append(prompt)
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
    env["DEBIAN_FRONTEND"] = "noninteractive"  # apt 等不提问

    try:
        process = subprocess.Popen(
            cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, cwd=str(WORKSPACE), encoding="utf-8", errors="replace",
            env=env,
        )
        for line in process.stdout:
            line_stripped = line.rstrip("\n")
            print(f"  [{label}] {line_stripped}")
            output_lines.append(line_stripped)
        process.wait(timeout=TIMEOUT_SECONDS)
        elapsed = time.time() - start
        success = process.returncode == 0
        level = "INFO" if success else "WARNING"
        log.log(logging.getLevelName(level), f"[{label}] {'完成' if success else '失败'} ({elapsed:.0f}s)")
        return success, "\n".join(output_lines)
    except subprocess.TimeoutExpired:
        process.kill()
        log.error(f"[{label}] 超时")
        return False, "TIMEOUT"
    except Exception as e:
        log.error(f"[{label}] 异常: {e}")
        return False, str(e)


def get_latest_session_id(req_stem: str | None = None) -> str | None:
    """
    获取最新的 session ID。
    req_stem: 需求文件的 stem（如 'requirement-123'），用于精确匹配本需求的会话。
    多 worker 并发时通过 req_stem 避免拿到别的 worker 的 session。
    """
    try:
        result = subprocess.run(
            [KIRO_CLI, "chat", "--list-sessions", "--format", "json"],
            capture_output=True, text=True, cwd=str(WORKSPACE),
            encoding="utf-8", errors="replace", timeout=15
        )
        # 合并 stdout 和 stderr（kiro-cli 可能输出到 stderr）
        raw = result.stdout + result.stderr
        # 找到 JSON 数组起始位置
        start = raw.find("[")
        if start == -1:
            return None
        sessions = json.loads(raw[start:])
        if not sessions:
            return None
        # 按时间倒序
        sessions.sort(key=lambda s: s.get("updatedAt", ""), reverse=True)
        # 如果传入了需求标识，优先匹配标题包含该需求名的 session
        if req_stem:
            for s in sessions:
                title = s.get("title", "")
                if req_stem in title:
                    return s.get("sessionId")
        # 找不到精确匹配，退回最新一条
        return sessions[0].get("sessionId")
    except Exception as e:
        log.warning(f"[session] 获取 session_id 失败: {e}")
        return None


def run_kiro_resume(session_id: str, prompt: str, label: str) -> tuple[bool, str]:
    """恢复指定会话并发送消息"""
    cmd = [KIRO_CLI, "chat", "--no-interactive", "--trust-all-tools",
           "--resume-id", session_id]
    if KIRO_MODEL:
        cmd += ["--model", KIRO_MODEL]
    cmd.append(prompt)

    start = time.time()
    output_lines = []
    env = os.environ.copy()
    env.update({"GIT_TERMINAL_PROMPT": "0", "GIT_EDITOR": "true",
                 "EDITOR": "true", "VISUAL": "true", "CI": "true",
                 "NPM_CONFIG_YES": "true", "DEBIAN_FRONTEND": "noninteractive"})
    try:
        process = subprocess.Popen(
            cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, cwd=str(WORKSPACE), encoding="utf-8", errors="replace", env=env,
        )
        for line in process.stdout:
            line_stripped = line.rstrip("\n")
            print(f"  [{label}] {line_stripped}")
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


def parse_test_result(output: str) -> tuple[bool, str]:
    """
    解析测试输出，判断是否全部通过。
    返回 (all_passed, summary)

    通过标准：
    - 出现测试报告的 ✅ PASS 标记，且不存在 ❌ FAIL
    - 用更具体的测试报告关键词避免误判修需求过程中的 ❌/✅
    """
    # 用测试报告特有的模式判断，避免被 fix-requirement 过程中的符号干扰
    # e2e-test SKILL 输出的报告格式固定含 "PASS" / "FAIL" / "测试报告"
    in_test_report = "测试报告" in output or "TrackFlow 测试报告" in output or "## 测试结果" in output
    has_fail = ("❌" in output and "FAIL" in output) or ("❌" in output and "失败" in output and in_test_report)
    has_pass = ("✅" in output and "PASS" in output) or (in_test_report and "✅" in output and not has_fail)

    lines = output.strip().split("\n")
    if has_fail:
        return False, "\n".join(lines[-30:])
    if has_pass:
        return True, "\n".join(lines[-10:])
    # 没有明确的测试报告标记，认为失败（测试可能根本没跑起来）
    return False, "\n".join(lines[-20:])


def parse_review_result(output: str) -> tuple[bool, str]:
    """
    解析 code review 输出，判断是否可以合并。
    返回 (can_merge, summary)
    通过标准：出现 🟢 或 "可以合并"，且无 MUST 问题
    """
    can_merge = "🟢" in output or "可以合并" in output
    has_must = "MUST" in output and "❌" in output
    lines = output.strip().split("\n")
    summary = "\n".join(lines[-40:])
    if has_must:
        return False, summary
    if can_merge:
        return True, summary
    # 模糊情况：有 🟡 修改后合并也算过（SHOULD 级不阻塞）
    if "🟡" in output or "修改后合并" in output:
        return True, summary
    return False, summary


# ============ 阶段一：并行生产 ============


def produce_one(worker_id: str) -> bool:
    """单个生产者：随机选配置，找一个需求"""
    config = random.choice(PRODUCER_CONFIGS)
    skill_name = config["skill"]
    skill_info = SKILLS[skill_name]

    prompt = (
        f"[使用 skill: {skill_name}] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"{config['prompt']}"
    )

    log.info(f"[{worker_id}] 生产: {config['prompt'][:50]}...")
    success, _ = run_kiro(prompt, worker_id)
    return success


def run_produce_phase(num_workers: int):
    """并行生产阶段：多个 worker 同时找需求"""
    log.info(f"[生产] 启动 {num_workers} 个 worker 并行找需求...")

    with ThreadPoolExecutor(max_workers=num_workers, thread_name_prefix="producer") as executor:
        futures = []
        for i in range(num_workers):
            futures.append(executor.submit(produce_one, f"producer-{i+1}"))
        wait(futures)

    new_review = len(list(REVIEW_DIR.glob("requirement-*.md")))
    log.info(f"[生产] 完成，review/ 当前 {new_review} 个")


# ============ 阶段二：串行审核 ============


def run_review_phase():
    """单线程审核：保证去重一致性"""
    review_files = sorted(REVIEW_DIR.glob("requirement-*.md"))
    if not review_files:
        log.info("[审核] review/ 为空，跳过")
        return

    skill_info = SKILLS["review-requirement"]
    req_list = ", ".join([f.name for f in review_files])

    prompt = (
        f"[使用 skill: review-requirement] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"审核需求：请审核 requirements/review/ 中的以下需求文件：{req_list}。"
        f"文件路径格式为 requirements/review/requirement-XX.md。"
    )

    log.info(f"[审核] 审核 {len(review_files)} 个需求（串行）...")
    run_kiro(prompt, "reviewer")
    log.info(f"[审核] 完成，develop/ 当前 {count_develop()} 个")


# ============ 阶段三：并行消费 ============


def claim_requirement(worker_id: str) -> Path | None:
    """原子领取一个需求"""
    worker_dir = WORKING_DIR / worker_id
    worker_dir.mkdir(parents=True, exist_ok=True)

    with _claim_lock:
        available = list_available(DEVELOP_DIR)
        if not available:
            return None
        source = available[0]
        dest = worker_dir / source.name
        try:
            source.rename(dest)
            return dest
        except (OSError, FileNotFoundError):
            return None


def consume_one(worker_id: str) -> str | None:
    """
    单个 worker 领取并处理一个需求，完整流程：
      1. 修需求（新会话）
      2. 拿到 session_id
      3. 测试（独立会话）→ 失败则 resume 原会话反馈 → 循环最多 MAX_TEST_RETRIES 次
      4. 审核（独立会话）→ 有 MUST 则 resume 原会话反馈 → 循环最多 MAX_REVIEW_RETRIES 次
      5. 成功 → 移到 implement
    """
    req_file = claim_requirement(worker_id)
    if req_file is None:
        return None

    skill_info = SKILLS["fix-requirement"]
    actual_path = f"requirements/working/{worker_id}/{req_file.name}"
    title = extract_title(req_file)
    label = worker_id

    log.info(f"[{label}] 消费: {req_file.name} ({title})")

    # ── 步骤 1：修需求（新会话）──
    fix_prompt = (
        f"[使用 skill: fix-requirement] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"修需求 {req_file.stem}，需求文件位于 {actual_path}"
    )
    success, fix_output = run_kiro(fix_prompt, label)
    if not success:
        if req_file.exists():
            shutil.move(str(req_file), str(DEVELOP_DIR / req_file.name))
        log.warning(f"[{label}] ❌ {req_file.name} 修需求失败，放回 develop/")
        return None

    # ── 步骤 2：拿 session_id（修需求结束后立刻查）──
    time.sleep(2)  # 给 kiro-cli 一点时间写入 session
    session_id = get_latest_session_id(req_file.stem)
    if session_id:
        log.info(f"[{label}] 绑定 session: {session_id[:8]}...")
    else:
        log.warning(f"[{label}] 未获取到 session_id，后续反馈将开新会话")

    # ── 步骤 3：测试闭环 ──
    test_skill = SKILLS["e2e-test"]
    prev_test_summary = ""
    test_passed = False

    for test_round in range(1, MAX_TEST_RETRIES + 1):
        log.info(f"[{label}] 测试第 {test_round} 轮...")

        if test_round == 1:
            test_prompt = (
                f"[使用 skill: e2e-test] "
                f"(skill 文件: {test_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"测试需求 {req_file.stem}，验证其验收标准。需求文件位于 {actual_path}"
            )
        else:
            test_prompt = (
                f"[使用 skill: e2e-test] "
                f"(skill 文件: {test_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"重测需求 {req_file.stem}（第 {test_round} 轮）。\n\n"
                f"上轮失败摘要：\n{prev_test_summary}\n\n"
                f"请仅验证上轮失败的用例，并回归已通过的用例。"
            )

        _, test_output = run_kiro(test_prompt, f"{label}-test{test_round}")
        test_passed, test_summary = parse_test_result(test_output)

        if test_passed:
            log.info(f"[{label}] ✅ 测试通过（第 {test_round} 轮）")
            break

        log.warning(f"[{label}] ❌ 测试失败（第 {test_round} 轮），反馈给修需求会话...")
        prev_test_summary = test_summary

        if test_round < MAX_TEST_RETRIES:
            # resume 原会话，把测试失败结果反馈给它
            feedback_prompt = (
                f"端到端测试失败（第 {test_round} 轮），请根据以下失败信息修复代码：\n\n"
                f"```\n{test_summary}\n```\n\n"
                f"修复后，测试子代理会重新验证。请完成修复并确认已 git commit。"
            )
            if session_id:
                run_kiro_resume(session_id, feedback_prompt, f"{label}-fix{test_round}")
            else:
                # 无 session_id 降级为新会话
                fallback_prompt = (
                    f"[使用 skill: fix-requirement] "
                    f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
                    f"继续处理需求 {req_file.stem}（{actual_path}），测试失败，请修复：\n\n"
                    f"```\n{test_summary}\n```"
                )
                run_kiro(fallback_prompt, f"{label}-fix{test_round}")

    if not test_passed:
        log.warning(f"[{label}] ⚠️ 测试经 {MAX_TEST_RETRIES} 轮仍未通过，继续审核（记录问题）")

    # ── 步骤 4：代码审核闭环 ──
    review_skill = SKILLS["code-review"]
    prev_review_summary = ""
    review_passed = False

    for review_round in range(1, MAX_REVIEW_RETRIES + 1):
        log.info(f"[{label}] 审核第 {review_round} 轮...")

        if review_round == 1:
            review_prompt = (
                f"[使用 skill: code-review] "
                f"(skill 文件: {review_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"审核需求 {req_file.stem} 的本次代码变更（git diff HEAD~1 或 staged 文件）。"
            )
        else:
            review_prompt = (
                f"[使用 skill: code-review] "
                f"(skill 文件: {review_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"二次审核需求 {req_file.stem}（第 {review_round} 轮）。\n\n"
                f"上轮 MUST 问题：\n{prev_review_summary}\n\n"
                f"请验证 MUST 问题是否已修复，无需重新做完整审核。"
            )

        _, review_output = run_kiro(review_prompt, f"{label}-review{review_round}")
        review_passed, review_summary = parse_review_result(review_output)

        if review_passed:
            log.info(f"[{label}] ✅ 审核通过（第 {review_round} 轮）")
            break

        log.warning(f"[{label}] ❌ 审核有 MUST 问题（第 {review_round} 轮），反馈给修需求会话...")
        prev_review_summary = review_summary

        if review_round < MAX_REVIEW_RETRIES:
            feedback_prompt = (
                f"代码审核发现 MUST 级问题（第 {review_round} 轮），请修复以下问题后重新 commit：\n\n"
                f"```\n{review_summary}\n```\n\n"
                f"修复完成后审核子代理会重新验证。"
            )
            if session_id:
                run_kiro_resume(session_id, feedback_prompt, f"{label}-fixr{review_round}")
            else:
                fallback_prompt = (
                    f"[使用 skill: fix-requirement] "
                    f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
                    f"继续处理需求 {req_file.stem}（{actual_path}），审核发现 MUST 问题：\n\n"
                    f"```\n{review_summary}\n```"
                )
                run_kiro(fallback_prompt, f"{label}-fixr{review_round}")

    # ── 步骤 5：归档 ──
    overall_success = test_passed and review_passed
    if req_file.exists():
        shutil.move(str(req_file), str(IMPLEMENT_DIR / req_file.name))

    if overall_success:
        log.info(f"[{label}] ✅ {req_file.name} 全流程完成")
    else:
        status = []
        if not test_passed:
            status.append(f"测试未全通({MAX_TEST_RETRIES}轮)")
        if not review_passed:
            status.append(f"审核未全通({MAX_REVIEW_RETRIES}轮)")
        log.warning(f"[{label}] ⚠️ {req_file.name} 已归档但存在问题: {', '.join(status)}")

    return req_file.name


def run_consume_phase(num_workers: int) -> int:
    """
    并行消费阶段：多个 worker 同时处理需求，直到 develop/ 空。
    失败的需求（kiro-cli 进程本身崩溃）会被放回 develop/，重试 MAX_RETRIES 次后移到 rejected/。
    """
    total_done = 0
    retry_counts: dict[str, int] = {}  # filename -> 连续失败次数
    prev_in_develop: set[str] = set()  # 上一批次在 develop/ 的文件名快照

    while True:
        available = list_available(DEVELOP_DIR)
        if not available:
            break

        current_names = {f.name for f in available}

        # 对比快照：上轮就在 develop/ 且本轮还在 → 没被消费，说明本轮有 worker 失败放回
        for name in current_names:
            if name in prev_in_develop:
                retry_counts[name] = retry_counts.get(name, 0) + 1
            else:
                # 新出现的文件（刚生产或从 working 放回）从 0 开始
                retry_counts.setdefault(name, 0)

        # 清理不在 develop/ 的过期计数
        for name in list(retry_counts.keys()):
            if name not in current_names:
                del retry_counts[name]

        # 检查队首文件是否已多次失败，是则跳过
        first = available[0]
        if retry_counts.get(first.name, 0) >= MAX_RETRIES:
            log.warning(f"[消费] {first.name} 已失败 {retry_counts[first.name]} 次，移到 rejected/")
            shutil.move(str(first), str(REJECTED_DIR / first.name))
            del retry_counts[first.name]
            prev_in_develop.discard(first.name)
            continue

        # 记录本批次前的快照（消费后不在 develop/ 就是成功消费了）
        prev_in_develop = current_names

        # 启动 min(workers, available) 个并发任务
        batch_size = min(num_workers, len(available))

        with ThreadPoolExecutor(max_workers=batch_size, thread_name_prefix="consumer") as executor:
            futures = []
            for i in range(batch_size):
                futures.append(executor.submit(consume_one, f"consumer-{i+1}"))
            wait(futures)

            for f in futures:
                try:
                    result = f.result()
                    if result:
                        total_done += 1
                        retry_counts.pop(result, None)
                except Exception:
                    pass

        time.sleep(COOLDOWN_SECONDS)

    return total_done


# ============ 清理 ============


def cleanup_working():
    """清理 working/ 残留文件"""
    for worker_dir in WORKING_DIR.iterdir():
        if worker_dir.is_dir():
            for f in worker_dir.glob("requirement-*.md"):
                shutil.move(str(f), str(DEVELOP_DIR / f.name))
                log.info(f"[清理] {f.name} → develop/")
            try:
                worker_dir.rmdir()
            except OSError:
                pass


def archive_decomposed_parents():
    """归档已拆解的父需求"""
    for f in list(DEVELOP_DIR.glob("requirement-*.md")):
        if not is_sub_requirement(f):
            number = extract_number(f.name)
            if has_sub_requirements(number):
                shutil.move(str(f), str(IMPLEMENT_DIR / f.name))
                log.info(f"[归档] 父需求 {f.name} → implement/")


# ============ 主循环 ============


def main_loop(num_workers: int, skip_produce: bool):
    """
    主循环：永远不停。
      develop 不够 → 并行生产 → 串行审核 → 并行消费 → 循环
      失败的需求重试 3 次后跳过（rejected），继续下一个
    """
    round_num = 0
    total_consumed = 0

    while True:
        round_num += 1
        log.info(f"\n{'='*60}")
        log.info(f"第 {round_num} 轮 | develop={count_develop()} review={len(list(REVIEW_DIR.glob('*.md')))}")
        log.info(f"{'='*60}")

        # 归档已拆解父需求
        archive_decomposed_parents()

        # 生产补货（develop 不够时）
        if not skip_produce and count_develop() < MIN_DEVELOP_QUEUE:
            # 先审核 review/ 中已有的
            run_review_phase()

            # 还不够？并行生产 + 审核，循环直到够
            while count_develop() < MIN_DEVELOP_QUEUE:
                run_produce_phase(num_workers)
                run_review_phase()
                time.sleep(COOLDOWN_SECONDS)

        # 消费阶段
        if count_develop() > 0:
            consumed = run_consume_phase(num_workers)
            total_consumed += consumed
            log.info(f"[消费] 本轮完成 {consumed} 个，累计 {total_consumed} 个")
        else:
            log.info("[消费] develop/ 为空，等待后继续...")

        time.sleep(COOLDOWN_SECONDS)


# ============ 入口 ============


def main():
    parser = argparse.ArgumentParser(description="TrackFlow 并行迭代脚本（永不停止）")
    parser.add_argument("--workers", type=int, default=2,
                        help="并行 worker 数量（默认 2）")
    parser.add_argument("--skip-produce", action="store_true",
                        help="跳过生产阶段，只消费 develop/ 中的现有需求（consume 完也会继续等待）")
    args = parser.parse_args()

    log.info("=" * 60)
    log.info(f"TrackFlow 并行迭代 | workers={args.workers} | 模式={'仅消费' if args.skip_produce else '完整循环'}")
    log.info(f"模型: {KIRO_MODEL or 'kiro-cli 默认'}")
    log.info(f"状态: review={len(list(REVIEW_DIR.glob('*.md')))} "
             f"develop={count_develop()} implement={len(list(IMPLEMENT_DIR.glob('*.md')))}")
    log.info("永不停止，Ctrl+C 手动终止")
    log.info("=" * 60)

    cleanup_working()
    main_loop(args.workers, args.skip_produce)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        log.info("\n[中断] 用户手动停止")
        cleanup_working()
