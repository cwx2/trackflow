"""
Worker 循环：producer_loop、reviewer_loop、consumer_loop、主循环调度。
"""

import random
import shutil
import subprocess
import threading
import time
from pathlib import Path

from _config import (
    WORKSPACE, DEVELOP_DIR, IMPLEMENT_DIR, REJECTED_DIR, WORKING_DIR,
    REVIEW_DIR, SKILLS, PRODUCER_CONFIGS,
    MIN_DEVELOP_QUEUE, MAX_RETRIES, MAX_TEST_RETRIES, MAX_REVIEW_RETRIES,
    REVIEW_BATCH_SIZE, KIRO_MODEL_FIX, COOLDOWN_SECONDS,
    _claim_lock, _review_lock, _retry_lock, _retry_counts,
    log,
)
from _kiro import run_kiro, run_kiro_resume, get_current_commit, get_latest_session_id
from _parsers import (
    parse_fix_result, parse_test_result, parse_review_result,
    read_req_status, extract_arch_issues,
)
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
    success, _ = run_kiro(prompt, worker_id)
    return success


# ============ 阶段二：审核 ============

def run_review_phase(batch: list[Path] | None = None) -> None:
    """
    审核一批需求文件（每批一个新 kiro-cli 会话，避免 context 过长）。
    batch 为 None 时自动取 review/ 前 REVIEW_BATCH_SIZE 个。
    """
    if batch is None:
        batch = sorted(REVIEW_DIR.glob("requirement-*.md"))[:REVIEW_BATCH_SIZE]
    if not batch:
        return

    skill_info = SKILLS["review-requirement"]
    req_list = ", ".join(f.name for f in batch)
    prompt = (
        f"[使用 skill: review-requirement] "
        f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
        f"审核需求：请审核 requirements/review/ 中的以下需求文件：{req_list}。"
        f"文件路径格式为 requirements/review/requirement-XX.md。"
    )
    log.info(f"[审核] 审核 {len(batch)} 个需求：{req_list}")
    run_kiro(prompt, "reviewer", worker_id="reviewer")
    log.info(f"[审核] 完成，develop/ 当前 {count_develop()} 个")


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


def consume_one(worker_id: str) -> str | None:
    """
    完整处理一个需求：修需求 → 测试闭环 → 代码审核闭环 → 归档推送。

    返回 req_file.name（成功）或 None（失败/放回 develop/）。
    """
    req_file = claim_requirement(worker_id)
    if req_file is None:
        return None

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
        if req_file.exists():
            shutil.move(str(req_file), str(DEVELOP_DIR / req_file.name))
        log.warning(f"[{label}] kiro-cli 启动失败，{req_file.name} 放回 develop/，等待 60s")
        time.sleep(60)
        return None

    # 需求本身不合理（FIX_BLOCKED）→ 移到 rejected/
    _, blocked, block_reason = parse_fix_result(fix_output)
    if blocked:
        log.warning(f"[{label}] ⛔ {req_file.name} 被拦截: {block_reason}")
        if req_file.exists():
            shutil.move(str(req_file), str(REJECTED_DIR / req_file.name))
        return req_file.name

    if not success:
        if req_file.exists():
            shutil.move(str(req_file), str(DEVELOP_DIR / req_file.name))
        log.warning(f"[{label}] ❌ {req_file.name} 修需求失败，放回 develop/")
        return None

    # ── 步骤 2：获取 session_id ──
    commit_after = get_current_commit()
    diff_range = f"{commit_before}..HEAD" if commit_before and commit_before != commit_after else "HEAD~1"
    log.info(f"[{label}] 代码变更范围: {diff_range}")

    time.sleep(2)
    session_id = get_latest_session_id(req_file.stem)
    if session_id:
        log.info(f"[{label}] 绑定 session: {session_id[:8]}...")
    else:
        log.warning(f"[{label}] 未获取到 session_id，后续反馈将开新会话")

    # ── 步骤 3：测试闭环 ──
    test_skill = SKILLS["e2e-test"]
    prev_test_summary = ""
    test_passed = False
    # kiro-cli 内置 Playwright 的 Chrome 进程退出 + SingletonLock 文件释放需要时间，
    # 等待不足会导致下一个 kiro-cli 会话启动时看到锁文件而循环等待
    time.sleep(15)

    for test_round in range(1, MAX_TEST_RETRIES + 1):
        log.info(f"[{label}] 测试第 {test_round} 轮...")

        if test_round == 1:
            test_prompt = (
                f"[使用 skill: e2e-test] "
                f"(skill 文件: {test_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"测试需求 {req_file.stem}，验证其验收标准。需求文件位于 {actual_path}\n\n"
                f"⚠️ 开始测试前必须先读取需求文件 {actual_path}，"
                f"提取验收标准和「Agent 交接上下文」章节中的测试重点。"
            )
        else:
            test_prompt = (
                f"[使用 skill: e2e-test] "
                f"(skill 文件: {test_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"重测需求 {req_file.stem}（第 {test_round} 轮）。需求文件位于 {actual_path}\n\n"
                f"上轮失败摘要：\n{prev_test_summary}\n\n"
                f"请仅验证上轮失败的用例，并回归已通过的用例。"
            )

        _, test_output = run_kiro(test_prompt, f"{label}-test{test_round}", worker_id=worker_id)

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
            if session_id:
                _, fb_out = run_kiro_resume(session_id, feedback_prompt,
                                            f"{label}-fix{test_round}", worker_id=worker_id)
            else:
                fallback = (
                    f"[使用 skill: fix-requirement-auto] "
                    f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
                    f"继续处理 {req_file.stem}（{actual_path}），测试失败，请修复：\n\n{prev_test_summary}"
                )
                _, fb_out = run_kiro(fallback, f"{label}-fix{test_round}",
                                     model=KIRO_MODEL_FIX, worker_id=worker_id)
            fix_done, fix_blocked2, _ = parse_fix_result(fb_out)
            if fix_blocked2:
                log.warning(f"[{label}] 修复被拦截，停止测试循环")
                break
            if not fix_done:
                log.warning(f"[{label}] 修复未输出 FIX_DONE，继续测试验证")

    if not test_passed:
        log.warning(f"[{label}] ⚠️ 测试经 {MAX_TEST_RETRIES} 轮仍未通过，继续审核")

    # ── 步骤 4：代码审核闭环 ──
    time.sleep(15)  # 等待上一个 kiro-cli 的 Chrome 进程释放 SingletonLock
    review_skill = SKILLS["code-review"]
    prev_review_summary = ""
    review_passed = False

    for review_round in range(1, MAX_REVIEW_RETRIES + 1):
        log.info(f"[{label}] 审核第 {review_round} 轮...")

        if review_round == 1:
            review_prompt = (
                f"[使用 skill: code-review] "
                f"(skill 文件: {review_skill['path']}，请严格按照该 skill 的规则执行)\n\n"
                f"审核需求 {req_file.stem} 的本次代码变更。需求文件位于 {actual_path}\n\n"
                f"⚠️ 开始审核前必须先读取需求文件 {actual_path}，"
                f"提取「Agent 交接上下文」中的变更文件清单和审核重点。\n\n"
                f"变更范围：git diff {diff_range}\n"
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
            if session_id:
                _, rv_out = run_kiro_resume(session_id, feedback_prompt,
                                            f"{label}-fixr{review_round}", worker_id=worker_id)
            else:
                fallback = (
                    f"[使用 skill: fix-requirement-auto] "
                    f"(skill 文件: {skill_info['path']}，请严格按照该 skill 的规则执行)\n\n"
                    f"继续处理 {req_file.stem}（{actual_path}），审核发现 MUST 问题：\n\n{prev_review_summary}"
                )
                _, rv_out = run_kiro(fallback, f"{label}-fixr{review_round}",
                                     model=KIRO_MODEL_FIX, worker_id=worker_id)
            fix_done2, _, _ = parse_fix_result(rv_out)
            if not fix_done2:
                log.warning(f"[{label}] 审核反馈修复未输出 FIX_DONE，继续二次审核验证")

    # ── 步骤 5：归档与推送 ──
    overall_success = test_passed and review_passed

    if overall_success:
        try:
            files_result = subprocess.run(
                ["git", "diff", diff_range, "--name-only"],
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
        except Exception as e:
            log.warning(f"[{label}] ⚠️ git push 异常: {e}")

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
        return None

    return req_file.name


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
                        run_review_phase(batch)
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

            result = consume_one(worker_id)

            if result is None:
                # 处理失败，累计重试次数
                for f in list_available(DEVELOP_DIR):
                    with _retry_lock:
                        if f.name == first.name:
                            _retry_counts[f.name] = _retry_counts.get(f.name, 0) + 1
                time.sleep(COOLDOWN_SECONDS)
            else:
                with _retry_lock:
                    _retry_counts.pop(result, None)
                log.info(f"[{worker_id}] ✅ 累计完成 {sum(1 for _ in IMPLEMENT_DIR.glob('*.md'))} 个")

        except Exception as e:
            log.error(f"[{worker_id}] 消费者异常: {e}", exc_info=True)
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
