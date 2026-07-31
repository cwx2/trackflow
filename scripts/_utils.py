"""
工具函数：需求文件操作、工作流加载、目录清理、父需求归档。
"""

import re
import shutil
from pathlib import Path

from _config import (
    WORKSPACE, DEVELOP_DIR, IMPLEMENT_DIR, WORKING_DIR,
    SCREENSHOT_DIR, ROLE_WORKFLOW_DIR, log,
)


# ============ 需求文件操作 ============

def _sort_key(filename: str) -> tuple:
    """需求文件排序键：主编号升序，子编号升序"""
    m = re.match(r"requirement-(\d+)-(\d+)\.md", filename)
    if m:
        return (int(m.group(1)), int(m.group(2)))
    m = re.match(r"requirement-(\d+)\.md", filename)
    if m:
        return (int(m.group(1)), 0)
    return (0, 0)


def extract_number(filename: str) -> int:
    """从文件名提取需求主编号"""
    m = re.search(r"requirement-(\d+)", filename)
    return int(m.group(1)) if m else 0


def is_sub_requirement(filepath: Path) -> bool:
    """判断是否为子需求（如 requirement-160-1.md）"""
    return bool(re.match(r"requirement-\d+-\d+\.md", filepath.name))


def has_sub_requirements(number: int) -> bool:
    """判断某编号是否已有子需求文件（任意目录）"""
    for dir_path in [DEVELOP_DIR, WORKING_DIR, IMPLEMENT_DIR]:
        if list(dir_path.glob(f"requirement-{number}-*.md")):
            return True
    return False


def list_available(directory: Path) -> list[Path]:
    """列出可消费的需求文件（已拆解的父需求不返回）"""
    files = list(directory.glob("requirement-*.md"))
    files.sort(key=lambda f: _sort_key(f.name))
    result = []
    for f in files:
        if not is_sub_requirement(f):
            if has_sub_requirements(extract_number(f.name)):
                continue
        result.append(f)
    return result


def count_develop() -> int:
    """返回 develop/ 中可消费的需求数量"""
    return len(list_available(DEVELOP_DIR))


def extract_title(filepath: Path) -> str:
    """从需求文件提取标题（第一个 # 行）"""
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip().startswith("#"):
                    return line.strip().lstrip("#").strip()
    except Exception:
        pass
    return filepath.name


# ============ 工作流加载 ============

def load_workflow(filename: str) -> str:
    """读取角色工作流文件，失败时返回空字符串"""
    path = ROLE_WORKFLOW_DIR / filename
    try:
        return path.read_text(encoding="utf-8")
    except Exception as e:
        log.warning(f"[workflow] 读取 {filename} 失败: {e}")
        return ""


def _extract_workflow_section(content: str, section_title: str) -> str:
    """
    从工作流文件中提取指定 section 的内容。
    匹配 ## 开头的标题行，提取到下一个同级标题之前的内容。
    """
    lines = content.split("\n")
    match_key = section_title.split("：")[0].split(":")[0].strip()
    start_idx = None
    end_idx = len(lines)

    for i, line in enumerate(lines):
        stripped = line.strip()
        if not stripped.startswith("##"):
            continue
        title_text = stripped.lstrip("#").strip()
        if match_key in title_text:
            start_idx = i
            break

    if start_idx is None:
        return ""

    for i in range(start_idx + 1, len(lines)):
        if lines[i].startswith("## ") and not lines[i].startswith("### "):
            end_idx = i
            break

    return "\n".join(lines[start_idx:end_idx]).strip()


# ============ 清理 ============

def cleanup_screenshots(keep_count: int = 200) -> None:
    """只保留最新 keep_count 张截图，防止 test/ 无限膨胀"""
    if not SCREENSHOT_DIR.exists():
        return
    files = sorted(SCREENSHOT_DIR.iterdir(), key=lambda f: f.stat().st_mtime, reverse=True)
    to_delete = [f for f in files if f.is_file()][keep_count:]
    for f in to_delete:
        f.unlink(missing_ok=True)
    if to_delete:
        log.info(f"[截图清理] 删除 {len(to_delete)} 个旧截图，保留最新 {keep_count} 张")


def cleanup_working() -> None:
    """将 working/ 中的残留文件放回 develop/"""
    if not WORKING_DIR.exists():
        return
    for worker_dir in WORKING_DIR.iterdir():
        if worker_dir.is_dir():
            for f in worker_dir.glob("requirement-*.md"):
                shutil.move(str(f), str(DEVELOP_DIR / f.name))
                log.info(f"[清理] {f.name} → develop/")
            try:
                worker_dir.rmdir()
            except OSError:
                pass


def archive_decomposed_parents() -> None:
    """归档 develop/ 中所有已拆解（有子需求）的父需求文件"""
    for f in list(DEVELOP_DIR.glob("requirement-*.md")):
        if not is_sub_requirement(f):
            number = extract_number(f.name)
            if has_sub_requirements(number):
                shutil.move(str(f), str(IMPLEMENT_DIR / f.name))
                log.info(f"[归档] 父需求 {f.name} → implement/")
