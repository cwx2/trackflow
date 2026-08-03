"""
解析模块：解析 kiro-cli 各阶段输出结果、读取需求文件状态、提取架构问题标记。
"""

from pathlib import Path
import re

from _config import strip_ansi, log


def _normalize_marker_line(line: str) -> str:
    """兼容 Kiro 输出中的引用符、列表符号和 Markdown 代码标记。"""
    normalized = strip_ansi(line).strip()
    normalized = re.sub(r"^(?:>\s*|[-*]\s+|```(?:\w+)?\s*)+", "", normalized)
    return normalized.strip("`*_ ").strip()


def reset_req_status(req_file: Path, *keys: str) -> bool:
    """将自动化状态字段重置为 PENDING，避免复用上一次运行的结果。"""
    if not keys or not req_file.exists():
        return False

    try:
        content = req_file.read_text(encoding="utf-8")
    except Exception as e:
        log.warning(f"[status] 读取 {req_file.name} 失败: {e}")
        return False

    wanted = set(keys)
    lines = content.splitlines(keepends=True)
    in_block = False
    changed = False

    for index, line in enumerate(lines):
        stripped = line.strip()
        if stripped == "## 自动化状态":
            in_block = True
            continue
        if in_block and stripped.startswith("## "):
            break
        if not in_block or ":" not in stripped:
            continue

        key, _, _ = stripped.partition(":")
        if key.strip() not in wanted:
            continue

        newline = "\r\n" if line.endswith("\r\n") else "\n" if line.endswith("\n") else ""
        lines[index] = f"{key.strip()}: PENDING{newline}"
        changed = True

    if not changed:
        return False

    try:
        req_file.write_text("".join(lines), encoding="utf-8")
        return True
    except Exception as e:
        log.warning(f"[status] 更新 {req_file.name} 失败: {e}")
        return False


def parse_fix_result(output: str) -> tuple[bool, bool, str]:
    """
    解析 fix-requirement-auto 的输出。
    返回 (done, blocked, detail)：
      done=True    → 输出了 FIX_DONE
      blocked=True → 输出了 FIX_BLOCKED:<reason>
    """
    clean = strip_ansi(output)
    lines = clean.strip().split("\n")
    for line in reversed(lines):
        line = _normalize_marker_line(line)
        if line == "FIX_DONE":
            return True, False, ""
        if line.startswith("FIX_BLOCKED:"):
            return False, True, line[len("FIX_BLOCKED:"):].strip()
    return False, False, "\n".join(lines[-10:])


def read_req_status(req_file: Path) -> dict[str, str]:
    """
    读取需求文件的 ## 自动化状态 区块，返回字段字典。

    区块格式示例：
      ## 自动化状态

      fix_status: DONE
      fix_commit: abc1234
      test_status: PASS
      review_status: PENDING
    """
    status: dict[str, str] = {}
    try:
        content = req_file.read_text(encoding="utf-8")
    except Exception:
        return status

    in_block = False
    for line in content.split("\n"):
        stripped = line.strip()
        if stripped == "## 自动化状态":
            in_block = True
            continue
        if in_block:
            if stripped.startswith("## ") and stripped != "## 自动化状态":
                break
            if ":" in stripped and not stripped.startswith("#"):
                key, _, val = stripped.partition(":")
                status[key.strip()] = val.strip()
    return status


def parse_test_result(output: str) -> tuple[bool, str]:
    """
    解析 e2e-test 输出（兜底方案，优先使用 read_req_status）。
    返回 (all_passed, failure_summary)。

    优先识别机器标记 TEST_RESULT: PASS/FAIL 和 TEST_FAILURES_BEGIN...END 块。
    降级时依赖测试报告结构 + 表格中的 ❌ 标记，避免 console 错误描述误判。
    """
    clean = strip_ansi(output)
    lines = clean.strip().split("\n")

    # 1. 检查 TEST_RESULT 机器标记（最后 15 行）
    result_line = None
    for line in reversed(lines[-15:]):
        stripped = _normalize_marker_line(line)
        if stripped in ("TEST_RESULT: PASS", "TEST_RESULT: FAIL"):
            result_line = stripped
            break

    # 2. 提取 TEST_FAILURES 块
    failures_summary = ""
    in_block = False
    failure_lines: list[str] = []
    for line in lines:
        normalized = _normalize_marker_line(line)
        if normalized == "TEST_FAILURES_BEGIN":
            in_block = True
            continue
        if normalized == "TEST_FAILURES_END":
            in_block = False
            continue
        if in_block:
            failure_lines.append(line.strip())
    if failure_lines:
        failures_summary = "\n".join(failure_lines)

    # 3. 机器标记路径（最可靠）
    if result_line == "TEST_RESULT: PASS":
        return True, ""
    if result_line == "TEST_RESULT: FAIL":
        return False, failures_summary or "\n".join(lines[-30:])

    # 4. 降级：必须有测试报告结构才做关键词判断
    in_test_report = any(kw in clean for kw in ("测试报告", "TrackFlow 测试报告", "## 测试结果"))
    if not in_test_report:
        return False, "\n".join(lines[-20:])

    has_structured_fail = bool(failures_summary)
    has_table_fail = any(
        "❌" in line and ("|" in line or line.strip().startswith("-"))
        for line in lines
    )
    if has_structured_fail or has_table_fail:
        return False, failures_summary or "\n".join(lines[-30:])
    return True, ""


def is_test_environment_failure(output: str) -> bool:
    """识别测试无法执行的环境故障，避免把它当成需求缺陷反复重试。"""
    clean = strip_ansi(output)
    if "TEST_ENVIRONMENT_FAILURE" in clean or "TEST_ENV_BLOCKED" in clean:
        return True

    environment_markers = (
        "前端服务不可用",
        "后端服务不可用",
        "Keycloak 无响应",
        "连接被拒绝",
        "localhost:3000 无响应",
        "localhost:8090 无响应",
    )
    return "TEST_FAILURES_BEGIN" in clean and any(
        marker in clean for marker in environment_markers
    )


def parse_review_result(output: str) -> tuple[bool, str]:
    """
    解析 code-review 输出（兜底方案，优先使用 read_req_status）。
    返回 (can_merge, summary)。

    优先识别 REVIEW_RESULT: PASS/FAIL 机器标记，降级时用内容关键词。
    """
    clean = strip_ansi(output)
    lines = clean.strip().split("\n")

    for line in reversed(lines[-15:]):
        line = _normalize_marker_line(line)
        if line == "REVIEW_RESULT: PASS":
            return True, "\n".join(lines[-10:])
        if line == "REVIEW_RESULT: FAIL":
            return False, "\n".join(lines[-40:])

    # 降级关键词检测
    can_merge = "🟢" in clean or "可以合并" in clean
    has_must  = "MUST" in clean and "❌" in clean
    summary   = "\n".join(lines[-40:])

    if has_must:
        return False, summary
    if can_merge or "🟡" in clean or "修改后合并" in clean:
        return True, summary
    return False, summary


def extract_arch_issues(req_file: Path) -> tuple[list[str], str]:
    """
    从需求文件中检测 code-review 写入的架构问题标记。

    code-review SKILL 在发现系统性问题时写入：

        ## 🏗️ 架构问题（ARCH_ISSUES_DETECTED）

        ARCH_KEYWORDS: Sprint管理, 状态流转

        ### 详情
        1. xxx

    返回 (keywords_list, detail_text)，未检测到时返回 ([], "")。
    """
    if not req_file.exists():
        return [], ""
    content = req_file.read_text(encoding="utf-8", errors="ignore")
    if "ARCH_ISSUES_DETECTED" not in content:
        return [], ""

    keywords: list[str] = []
    detail_lines: list[str] = []
    in_detail = False

    for line in content.split("\n"):
        if line.startswith("ARCH_KEYWORDS:"):
            raw = line.split(":", 1)[1].strip()
            keywords = [k.strip() for k in raw.split(",") if k.strip()]
        elif line.strip().startswith("### 详情"):
            in_detail = True
        elif in_detail:
            if line.startswith("## "):
                break
            detail_lines.append(line)

    return keywords, "\n".join(detail_lines).strip()
