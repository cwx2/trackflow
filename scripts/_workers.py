"""
Worker 循环：producer_loop、reviewer_loop、consumer_loop、主循环调度。
"""

import random
import shutil
import subprocess
import threading
import time
from dataclasses import dataclass
from pathlib import Path

from _config import (
    WORKSPACE, DEVELOP_DIR, IMPLEMENT_DIR, REJECTED_DIR, WORKING_DIR,
    REVIEW_DIR, SKILLS, PRODUCER_CONFIGS,
    MIN_DEVELOP_QUEUE, MAX_RETRIES, MAX_TEST_RETRIES, MAX_REVIEW_RETRIES,
    REVIEW_BATCH_SIZE, KIRO_MODEL_FIX, COOLDOWN_SECONDS,
    _claim_lock, _review_lock, _consumer_lock, _retry_lock, _retry_counts,
    log,
)
from _kiro import run_kiro, run_kiro_resume, get_current_commit, get_latest_session_id
from _parsers import (
    parse_fix_result, parse_test_result, parse_review_result,
    read_req_status, reset_req_status, is_test_environment_failure, extract_arch_issues,
)


@dataclass(frozen=True)
class ConsumeOutcome:
    """单个需求的处理结果；transient=True 表示环境故障，不应消耗重试次数。"""

    requirement_name: str | None
    transient: bool = False


def _is_transient_cli_output(output: str) -> bool:
    return output in {"STARTUP_FAIL", "TIMEOUT", "PROCESS_ERROR"}


def _move_back_to_develop(req_file: Path) -> None:
    if not req_file.exists():
        return
    destination = DEVELOP_DIR / req_file.name
    if destination.exists():
        log.error(f"[队列] 无法回滚 {req_file.name}：develop/ 已存在同名文件，保留 working/ 原文件")
        return
    shutil.move(str(req_file), str(destination))


def _git_has_unrelated_changes() -> bool:
    """检测共享工作区中是否存在非队列文件改动，避免污染其他需求。"""
    try:
        result = subprocess.run(
            ["git", "status", "--porcelain=v1", "--untracked-files=all"],
            capture_output=True, text=True, cwd=str(WORKSPACE),
            encoding="utf-8", errors="replace", timeout=15,
        )
    except Exception as e:
        log.warning(f"[git] 无法检查工作区状态: {e}")
        return True

    if result.returncode != 0:
        log.warning(f"[git] 工作区状态检查失败: {result.stderr.strip()}")
        return True

    ignored_prefixes = (
        "requirements/", "test/", "scripts/log/", "scripts/.auto_iterate_parallel.lock"
    )
    automation_files = {
        "scripts/_config.py",
        "scripts/_kiro.py",
        "scripts/_parsers.py",
        "scripts/_playwright.py",
        "scripts/_utils.py",
        "scripts/_workers.py",
        "scripts/auto_iterate_parallel.py",
    }
    changed = []
    for line in result.stdout.splitlines():
        if len(line) < 4:
            continue
        path_text = line[3:].strip().replace('\\', '/')
        paths = [part.strip() for part in path_text.split(" -> ")]
        if any(
            path not in automation_files and not path.startswith(ignored_prefixes)
            for path in paths
        ):
            changed.extend(paths)

    if changed:
        log.warning(f"[git] 共享工作区存在非队列改动，暂停消费者：{', '.join(changed[:10])}")
        return True
    return False


def _build_diff_range(commit_before: str, commit_after: str) -> tuple[str, list[str]]:
    """返回安全的变更范围，不再用 HEAD~1 猜测其它需求的提交。"""
    if commit_before and commit_after and commit_before != commit_after:
        diff_range = f"{commit_before}..{commit_after}"
        command = ["git", "diff", diff_range, "--name-only"]
    else:
        diff_range = "WORKTREE"
        command = ["git", "diff", "--name-only"]

    try:
        result = subprocess.run(
            command, capture_output=True, text=True, cwd=str(WORKSPACE),
            encoding="utf-8", errors="replace", timeout=15,
        )
        files = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    except Exception as e:
        log.warning(f"[git] 获取变更范围失败: {e}")
        files = []
    return diff_range, files
from _utils import (
    list_available, count_develop, extract_title,
    load_workflow, _extract_workflow_section, archive_decomposed_parents,
)


# ============ 阶段一：生产 ============

def produce_one(worker_id: str) -> bool:
    """单个生产者：随机选角色工作流配置，让 AI 操作系统找需求并写入 review/"""
    config = random.choice(PRODUCER_CONFIGS)
    skill_name = config["skill"]
    skill_info = SKILLS[skill_name]
    workflow_file = config["workflow_file"]
    workflow_section = config.get("workflow_section", "")

    workflow_content = load_workflow(workflow_file)
    if not workflow_content:
        log.warning(f"[{worker_id}] 工作流文件 {workflow_file} 读取失败，跳过")
        return False

    if workflow_section:
        section_content = _extract_workflow_section(workflow_content, workflow_section)
        if not section_content:
            log.warning(f"[{worker_id}] 未找到 section「{workflow_section}」，使用完整文件")
            section_content = workflow_content
    else:
        section_content = workflow_content

    prompt = (
        f"[使用 skill: {skill_name}] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"找一下需求。请按照以下角色工作流的步骤操作系统，"
        f"把操作过程中遇到的任何不合理、不好用、报错、UI 有问题的地方记录为需求。\n\n"
        f"---\n\n{section_content}"
    )
    log.info(f"[{worker_id}] 生产: {workflow_file}")
    # 生产者同样可能被 Kiro 改动工作区；与消费者共用互斥锁，避免并行写 Git。
    with _consumer_lock:
        success, _ = run_kiro(prompt, worker_id)
    return success


# ============ 阶段二：审核 ============

def run_review_phase(batch: list[Path] | None = None) -> bool:
    """
    审核一批需求文件（每批一个新 kiro-cli 会话，避免 context 过长）。
    batch 为 None 时自动取 review/ 前 REVIEW_BATCH_SIZE 个。
    """
    if batch is None:
        batch = sorted(REVIEW_DIR.glob("requirement-*.md"))[:REVIEW_BATCH_SIZE]
    if not batch:
        return True

    skill_info = SKILLS["review-requirement"]
    req_list = ", ".join(f.name for f in batch)
    prompt = (
        f"[使用 skill: review-requirement] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"审核需求：请审核 requirements/review/ 中的以下需求文件：{req_list}。"
        f"文件路径格式为 requirements/review/requirement-XX.md。"
    )
    log.info(f"[审核] 审核 {len(batch)} 个需求：{req_list}")
    # 审核会话可能会改写需求文件，必须与其它 Kiro 会话串行访问共享工作区。
    with _consumer_lock:
        success, output = run_kiro(prompt, "reviewer", worker_id="reviewer")
    if _is_transient_cli_output(output):
        log.warning("[审核] Kiro 启动/超时故障，需求保留在 review/，稍后重试")
        return False

    remaining = [path.name for path in batch if path.exists()]
    if not success or remaining:
        log.warning(
            f"[审核] 会话未完成队列迁移，仍在 review/：{', '.join(remaining) or '未知'}"
        )
        return False

    log.info(f"[审核] 完成，develop/ 当前 {count_develop()} 个")
    return True


# ============ 阶段三：消费 ============

def claim_requirement(worker_id: str) -> Path | None:
    """原子领取一个需求文件（rename 到 working/{worker_id}/）"""
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


def consume_one(worker_id: str) -> ConsumeOutcome:
    """
    完整处理一个需求：修需求 → 测试闭环 → 代码审核闭环 → 归档推送。

    返回需求名（成功/拦截）或 None（失败/放回 develop/）。
    """
    req_file = claim_requirement(worker_id)
    if req_file is None:
        return ConsumeOutcome(None)

    skill_info = SKILLS["fix-requirement-auto"]
    actual_path = f"requirements/working/{worker_id}/{req_file.name}"
    label = worker_id
    log.info(f"[{label}] 消费: {req_file.name} ({extract_title(req_file)})")

    # ── 步骤 1：修需求（新会话）──
    commit_before = get_current_commit()
    fix_prompt = (
        f"[使用 skill: fix-requirement-auto] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"修需求 {req_file.stem}，需求文件位于 {actual_path}"
    )
    success, fix_output = run_kiro(fix_prompt, label, model=KIRO_MODEL_FIX, worker_id=worker_id)

    # 启动失败（<30s 退出）→ 放回 develop/，等待环境恢复，不累积重试次数
    if fix_output == "STARTUP_FAIL":
        _move_back_to_develop(req_file)
        log.warning(f"[{label}] kiro-cli 启动失败，{req_file.name} 放回 develop/，等待 60s")
        time.sleep(60)
        return ConsumeOutcome(None, transient=True)

    # 需求本身不合理（FIX_BLOCKED）→ 移到 rejected/
    fix_done, blocked, block_reason = parse_fix_result(fix_output)
    if blocked:
        log.warning(f"[{label}] ⛔ {req_file.name} 被拦截: {block_reason}")
        if req_file.exists():
            shutil.move(str(req_file), str(REJECTED_DIR / req_file.name))
        return ConsumeOutcome(req_file.name)

    if not success or not fix_done:
        _move_back_to_develop(req_file)
        log.warning(
            f"[{label}] ❌ {req_file.name} 修需求未完成（缺少 FIX_DONE），放回 develop/"
        )
        return ConsumeOutcome(None, transient=_is_transient_cli_output(fix_output))

    # ── 步骤 2：获取 session_id（懒加载，只在需要 resume 时才查） ──
    commit_after = get_current_commit()
    diff_range, changed_files = _build_diff_range(commit_before, commit_after)
    log.info(f"[{label}] 代码变更范围: {diff_range}")
    if not changed_files:
        _move_back_to_develop(req_file)
        log.warning(f"[{label}] 修需求没有产生可审核的 Git 变更，放回 develop/")
        return ConsumeOutcome(None)
    if diff_range == "WORKTREE":
        _move_back_to_develop(req_file)
        log.warning(f"[{label}] 代码变更未提交，拒绝继续测试/推送，放回 develop/")
        return ConsumeOutcome(None, transient=True)

    session_id: str | None = None  # 延迟到第一次需要 resume 时再查，避免每次调用耗时 30s+ 的 --list-sessions

    def get_session_id_lazy() -> str | None:
        """第一次调用时查询并缓存 session_id"""
        nonlocal session_id
        if session_id is None:
            time.sleep(2)
            session_id = get_latest_session_id(req_file.stem, worker_id=worker_id)
            if session_id:
                log.info(f"[{label}] 绑定 session: {session_id[:8]}...")
            else:
                log.warning(f"[{label}] 未获取到 session_id，后续反馈将开新会话")
        return session_id

    # ── 步骤 3：测试闭环 ──
    test_skill = SKILLS["e2e-test"]
    prev_test_summary = ""
    test_passed = False
    time.sleep(5)  # 等待上一个 kiro-cli 进程完全退出

    for test_round in range(1, MAX_TEST_RETRIES + 1):
        log.info(f"[{label}] 测试第 {test_round} 轮...")
        reset_req_status(req_file, "test_status")

        if test_round == 1:
            test_prompt = (
                f"[使用 skill: e2e-test] "
                f"(skill 文件: {test_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"测试需求 {req_file.stem}，验证其验收标准。需求文件位于 {actual_path}\n\n"
                f"⚠️ 开始测试前必须先读取需求文件 {actual_path}，"
                f"提取验收标准和「Agent 交接上下文」章节中的测试重点。\n"
                f"如果前端、后端、Keycloak 或测试工具不可用，必须输出 TEST_ENVIRONMENT_FAILURE，"
                f"不要把环境故障判定为需求缺陷。"
            )
        else:
            test_prompt = (
                f"[使用 skill: e2e-test] "
                f"(skill 文件: {test_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"重测需求 {req_file.stem}（第 {test_round} 轮）。需求文件位于 {actual_path}\n\n"
                f"上轮失败摘要：\n{prev_test_summary}\n\n"
                f"请仅验证上轮失败的用例，并回归已通过的用例；如果环境不可用，输出 "
                f"TEST_ENVIRONMENT_FAILURE。"
            )

        _, test_output = run_kiro(test_prompt, f"{label}-test{test_round}", worker_id=worker_id)
        if _is_transient_cli_output(test_output):
            _move_back_to_develop(req_file)
            log.warning(f"[{label}] 测试环境故障（{test_output}），不消耗需求重试次数")
            return ConsumeOutcome(None, transient=True)
        if is_test_environment_failure(test_output):
            _move_back_to_develop(req_file)
            log.warning(f"[{label}] 测试前置环境不可用，不消耗需求重试次数")
            return ConsumeOutcome(None, transient=True)

        req_status = read_req_status(req_file)
        file_test_status = req_status.get("test_status", "")
        if file_test_status == "PASS":
            log.info(f"[{label}] ✅ 测试通过（第 {test_round} 轮，文件状态）")
            test_passed = True
            break
        elif file_test_status == "FAIL":
            log.warning(f"[{label}] ❌ 测试失败（第 {test_round} 轮，文件状态）")
            _, prev_test_summary = parse_test_result(test_output)
        else:
            log.warning(f"[{label}] ⚠️ 未找到 test_status，降级到 stdout 解析")
            test_passed, prev_test_summary = parse_test_result(test_output)
            if test_passed:
                log.info(f"[{label}] ✅ 测试通过（第 {test_round} 轮，stdout）")
                break
            log.warning(f"[{label}] ❌ 测试失败（第 {test_round} 轮，stdout）")

        if test_round < MAX_TEST_RETRIES:
            feedback_prompt = (
                f"端到端测试失败（第 {test_round} 轮），请根据失败信息修复代码：\n\n"
                f"{prev_test_summary}\n\n修复完成后请输出 FIX_DONE。"
            )
            if get_session_id_lazy():
                _, fb_out = run_kiro_resume(get_session_id_lazy(), feedback_prompt,
                                            f"{label}-fix{test_round}", worker_id=worker_id)
            else:
                fallback = (
                    f"[使用 skill: fix-requirement-auto] "
                    f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
                    f"继续处理 {req_file.stem}（{actual_path}），测试失败，请修复：\n\n{prev_test_summary}"
                )
                _, fb_out = run_kiro(fallback, f"{label}-fix{test_round}",
                                     model=KIRO_MODEL_FIX, worker_id=worker_id)
            if _is_transient_cli_output(fb_out):
                _move_back_to_develop(req_file)
                log.warning(f"[{label}] 测试修复环境故障（{fb_out}），不消耗需求重试次数")
                return ConsumeOutcome(None, transient=True)
            fix_done, fix_blocked2, _ = parse_fix_result(fb_out)
            if fix_blocked2:
                log.warning(f"[{label}] 修复被拦截，停止测试循环")
                break
            if not fix_done:
                log.warning(f"[{label}] 修复未输出 FIX_DONE，继续测试验证")

    if not test_passed:
        log.warning(f"[{label}] ⚠️ 测试经 {MAX_TEST_RETRIES} 轮仍未通过，继续审核")

    # ── 步骤 4：代码审核闭环 ──
    time.sleep(5)  # 等待上一个 kiro-cli 进程完全退出
    review_skill = SKILLS["code-review"]
    prev_review_summary = ""
    review_passed = False

    for review_round in range(1, MAX_REVIEW_RETRIES + 1):
        log.info(f"[{label}] 审核第 {review_round} 轮...")
        reset_req_status(req_file, "review_status")

        if review_round == 1:
            review_prompt = (
                f"[使用 skill: code-review] "
                f"(skill 文件: {review_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"审核需求 {req_file.stem} 的本次代码变更。需求文件位于 {actual_path}\n\n"
                f"⚠️ 开始审核前必须先读取需求文件 {actual_path}，"
                f"提取「Agent 交接上下文」中的变更文件清单和审核重点。\n\n"
                f"变更范围：{('git diff ' + diff_range) if diff_range != 'WORKTREE' else 'git diff'}\n"
                f"请审核这个范围内的所有改动（可能包含多个 commit）。"
            )
        else:
            review_prompt = (
                f"[使用 skill: code-review] "
                f"(skill 文件: {review_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"二次审核需求 {req_file.stem}（第 {review_round} 轮）。需求文件位于 {actual_path}\n\n"
                f"上轮 MUST 问题：\n{prev_review_summary}\n\n"
                f"请验证 MUST 问题是否已修复，无需重新做完整审核。"
            )

        _, review_output = run_kiro(review_prompt, f"{label}-review{review_round}", worker_id=worker_id)
        if _is_transient_cli_output(review_output):
            _move_back_to_develop(req_file)
            log.warning(f"[{label}] 审核环境故障（{review_output}），不消耗需求重试次数")
            return ConsumeOutcome(None, transient=True)

        req_status = read_req_status(req_file)
        file_review_status = req_status.get("review_status", "")
        if file_review_status == "PASS":
            log.info(f"[{label}] ✅ 审核通过（第 {review_round} 轮，文件状态）")
            review_passed = True
            break
        elif file_review_status == "FAIL":
            log.warning(f"[{label}] ❌ 审核有 MUST 问题（第 {review_round} 轮，文件状态）")
            _, prev_review_summary = parse_review_result(review_output)
        else:
            log.warning(f"[{label}] ⚠️ 未找到 review_status，降级到 stdout 解析")
            review_passed, prev_review_summary = parse_review_result(review_output)
            if review_passed:
                log.info(f"[{label}] ✅ 审核通过（第 {review_round} 轮，stdout）")
                break
            log.warning(f"[{label}] ❌ 审核有 MUST 问题（第 {review_round} 轮，stdout）")

        if review_round < MAX_REVIEW_RETRIES:
            feedback_prompt = (
                f"代码审核发现 MUST 级问题（第 {review_round} 轮），请修复后重新 commit：\n\n"
                f"```\n{prev_review_summary}\n```\n\n修复完成后请输出 FIX_DONE。"
            )
            if get_session_id_lazy():
                _, rv_out = run_kiro_resume(get_session_id_lazy(), feedback_prompt,
                                            f"{label}-fixr{review_round}", worker_id=worker_id)
            else:
                fallback = (
                    f"[使用 skill: fix-requirement-auto] "
                    f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
                    f"继续处理 {req_file.stem}（{actual_path}），审核发现 MUST 问题：\n\n{prev_review_summary}"
                )
                _, rv_out = run_kiro(fallback, f"{label}-fixr{review_round}",
                                     model=KIRO_MODEL_FIX, worker_id=worker_id)
            if _is_transient_cli_output(rv_out):
                _move_back_to_develop(req_file)
                log.warning(f"[{label}] 审核修复环境故障（{rv_out}），不消耗需求重试次数")
                return ConsumeOutcome(None, transient=True)
            fix_done2, _, _ = parse_fix_result(rv_out)
            if not fix_done2:
                log.warning(f"[{label}] 审核反馈修复未输出 FIX_DONE，继续二次审核验证")

    # ── 步骤 5：归档与推送 ──
    overall_success = test_passed and review_passed

    if overall_success:
        try:
            diff_command = ["git", "diff", diff_range, "--name-only"]
            files_result = subprocess.run(
                diff_command,
                capture_output=True, text=True, cwd=str(WORKSPACE), timeout=15
            )
            log.info(f"[{label}] 本次推送文件清单（{diff_range}）:\n{files_result.stdout.strip()}")
        except Exception as e:
            log.warning(f"[{label}] 获取文件清单失败: {e}")

        try:
            push_result = subprocess.run(
                ["git", "push"], capture_output=True, text=True,
                cwd=str(WORKSPACE), timeout=60
            )
            if push_result.returncode == 0:
                log.info(f"[{label}] ✅ git push 成功")
            else:
                log.warning(f"[{label}] ⚠️ git push 失败: {push_result.stderr.strip()}")
                _move_back_to_develop(req_file)
                return ConsumeOutcome(None, transient=True)
        except Exception as e:
            log.warning(f"[{label}] ⚠️ git push 异常: {e}")
            _move_back_to_develop(req_file)
            return ConsumeOutcome(None, transient=True)

        if req_file.exists():
            shutil.move(str(req_file), str(IMPLEMENT_DIR / req_file.name))
        log.info(f"[{label}] ✅ {req_file.name} 全流程完成，已归档")

        # ── 步骤 6：检测并触发架构审计（可选）──
        arch_req_file = IMPLEMENT_DIR / req_file.name
        arch_keywords, arch_detail = extract_arch_issues(arch_req_file)
        if arch_keywords:
            log.info(f"[{label}] 🏗️ 发现架构问题，触发 tech-requirement 审计：{arch_keywords}")
            tech_skill = SKILLS.get("tech-requirement", {})
            tech_prompt = (
                f"[使用 skill: tech-requirement] "
                f"(skill 文件: {tech_skill.get('path', '.kiro/skills/tech-requirement/SKILL.md')}，"
                f"请严格按照该 skill 的规则执行)\n\n"
                f"在审核需求 {req_file.stem} 的代码变更时，code-review 发现了以下系统性架构问题，"
                f"请以此为切入点进行技术审计，找出根因并写成技术需求文档：\n\n"
                f"**涉及模块**：{', '.join(arch_keywords)}\n\n"
                f"**问题详情**：\n{arch_detail}\n\n"
                f"请按照 tech-requirement SKILL 的完整审计流程执行，"
                f"将发现的问题写入 review/ 目录。"
            )
            run_kiro(tech_prompt, f"{label}-arch", worker_id=worker_id)
            log.info(f"[{label}] 🏗️ 架构审计会话结束")
    else:
        status = []
        if not test_passed:
            status.append(f"测试未全通({MAX_TEST_RETRIES}轮)")
        if not review_passed:
            status.append(f"审核未全通({MAX_REVIEW_RETRIES}轮)")
        log.warning(f"[{label}] ❌ {req_file.name} 未通过（{', '.join(status)}），放回 develop/")
        if req_file.exists():
            shutil.move(str(req_file), str(DEVELOP_DIR / req_file.name))
        return ConsumeOutcome(None)

    return ConsumeOutcome(req_file.name)


# ============ 主循环（三类线程） ============

def producer_loop(worker_id: str) -> None:
    """生产者线程：develop 不足时生产需求，充足时休眠"""
    log.info(f"[{worker_id}] 生产者启动")
    while True:
        try:
            archive_decomposed_parents()
            if count_develop() < MIN_DEVELOP_QUEUE:
                log.info(f"[{worker_id}] develop={count_develop()} < {MIN_DEVELOP_QUEUE}，开始生产...")
                produce_one(worker_id)
            else:
                log.debug(f"[{worker_id}] develop 充足（{count_develop()}），跳过")
        except Exception as e:
            log.error(f"[{worker_id}] 生产者异常: {e}", exc_info=True)
        time.sleep(COOLDOWN_SECONDS)


def reviewer_loop() -> None:
    """
    专职审核线程（唯一）：持续监听 review/，有文件立即按批审核。
    review/ 为空时每 30 秒轮询，每 5 分钟打一条 INFO。
    """
    log.info("[reviewer] 审核线程启动")
    idle_rounds = 0
    while True:
        try:
            review_files = sorted(REVIEW_DIR.glob("requirement-*.md"))
            if review_files:
                idle_rounds = 0
                batches = [
                    review_files[i:i + REVIEW_BATCH_SIZE]
                    for i in range(0, len(review_files), REVIEW_BATCH_SIZE)
                ]
                log.info(f"[reviewer] {len(review_files)} 个待审需求，分 {len(batches)} 批处理")
                with _review_lock:
                    for idx, batch in enumerate(batches, 1):
                        log.info(f"[reviewer] 第 {idx}/{len(batches)} 批（{len(batch)} 个）...")
                        if not run_review_phase(batch):
                            time.sleep(COOLDOWN_SECONDS)
            else:
                idle_rounds += 1
                if idle_rounds % 10 == 1:
                    log.info("[reviewer] review/ 为空，等待生产者...")
                time.sleep(30)
        except Exception as e:
            log.error(f"[reviewer] 审核线程异常: {e}", exc_info=True)
            time.sleep(COOLDOWN_SECONDS)


def consumer_loop(worker_id: str) -> None:
    """
    消费者线程：持续从 develop/ 领取需求并完整处理（修+测+审）。
    失败超过 MAX_RETRIES 次移到 rejected/，develop/ 为空时每 30 秒轮询。
    """
    log.info(f"[{worker_id}] 消费者启动")
    idle_rounds = 0
    while True:
        try:
            available = list_available(DEVELOP_DIR)
            if not available:
                idle_rounds += 1
                if idle_rounds % 10 == 1:
                    log.info(f"[{worker_id}] develop/ 为空，等待生产者补货...")
                time.sleep(30)
                continue

            idle_rounds = 0
            if _git_has_unrelated_changes():
                time.sleep(30)
                continue
            first = available[0]

            # 超过重试上限 → 移到 rejected/
            with _retry_lock:
                count = _retry_counts.get(first.name, 0)
            if count >= MAX_RETRIES:
                log.warning(f"[{worker_id}] {first.name} 已失败 {count} 次，移到 rejected/")
                shutil.move(str(first), str(REJECTED_DIR / first.name))
                with _retry_lock:
                    _retry_counts.pop(first.name, None)
                continue

            with _consumer_lock:
                outcome = consume_one(worker_id)

            if outcome.requirement_name is None:
                if outcome.transient:
                    log.info(f"[{worker_id}] 环境故障，{first.name} 不计入失败次数")
                    continue
                # 处理失败，累计重试次数
                for f in list_available(DEVELOP_DIR):
                    with _retry_lock:
                        if f.name == first.name:
                            _retry_counts[f.name] = _retry_counts.get(f.name, 0) + 1
                time.sleep(COOLDOWN_SECONDS)
            else:
                with _retry_lock:
                    _retry_counts.pop(outcome.requirement_name, None)
                log.info(f"[{worker_id}] ✅ 累计完成 {sum(1 for _ in IMPLEMENT_DIR.glob('*.md'))} 个")

        except Exception as e:
            log.error(f"[{worker_id}] 消费者异常: {e}", exc_info=True)
            if 'first' in locals():
                stranded = WORKING_DIR / worker_id / first.name
                if stranded.exists():
                    _move_back_to_develop(stranded)
            time.sleep(COOLDOWN_SECONDS)


def main_loop(num_producers: int, num_consumers: int, skip_produce: bool) -> None:
    """
    启动三类线程，永不停止。
    定期在主线程打印队列状态。
    """
    threads: list[threading.Thread] = []

    if not skip_produce:
        t = threading.Thread(target=reviewer_loop, name="reviewer", daemon=True)
        t.start()
        threads.append(t)
        log.info("[主] 审核线程已启动")
        time.sleep(2)

        for i in range(num_producers):
            worker_id = f"producer-{i+1}"
            t = threading.Thread(target=producer_loop, args=(worker_id,),
                                 name=worker_id, daemon=True)
            t.start()
            threads.append(t)
            log.info(f"[主] 生产者 {worker_id} 已启动")
            time.sleep(5)
    else:
        log.info("[主] --skip-produce 模式，不启动生产者和审核线程")

    STAGGER_SECONDS = 30
    for i in range(num_consumers):
        worker_id = f"consumer-{i+1}"
        t = threading.Thread(target=consumer_loop, args=(worker_id,),
                             name=worker_id, daemon=True)
        t.start()
        threads.append(t)
        log.info(f"[主] 消费者 {worker_id} 已启动")
        if i < num_consumers - 1:
            time.sleep(STAGGER_SECONDS)

    desc = (f"1 审核者 + {num_producers} 生产者 + {num_consumers} 消费者"
            if not skip_produce else f"{num_consumers} 消费者（仅消费模式）")
    log.info(f"[主] 全部线程已启动：{desc}")

    # 主线程永不退出，定期打印队列状态
    from _config import REVIEW_DIR, IMPLEMENT_DIR, REJECTED_DIR
    while True:
        time.sleep(60)
        log.info(
            f"[状态] review={len(list(REVIEW_DIR.glob('*.md')))} "
            f"develop={count_develop()} "
            f"implement={len(list(IMPLEMENT_DIR.glob('*.md')))} "
            f"rejected={len(list(REJECTED_DIR.glob('*.md')))}"
        )
