"""
列出 requirements/ 目录下所有需求文件的标题（第一行 # 开头的内容）。
用于在找需求前快速了解已有需求，避免重复。

用法：python scripts/list_requirements.py
"""

import os
import re
from pathlib import Path


def get_requirement_titles():
    req_dir = Path(__file__).parent.parent / "requirements"
    if not req_dir.exists():
        print("requirements/ 目录不存在")
        return

    files = sorted(req_dir.glob("requirement-*.md"), key=lambda f: extract_number(f.name))
    
    if not files:
        print("未找到任何需求文件")
        return

    done_count = 0
    open_count = 0

    for f in files:
        title = extract_title(f)
        is_done = "-done" in f.name
        status = "✅" if is_done else "⬚ "
        print(f"  {status} {title}")
        if is_done:
            done_count += 1
        else:
            open_count += 1

    print(f"\n总计：{len(files)} 个需求 | ✅ 已完成 {done_count} | ⬚  待处理 {open_count}")


def extract_title(filepath: Path) -> str:
    """读取文件第一行 # 开头的标题"""
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("#"):
                    return line.lstrip("#").strip()
                if line:  # 非空非标题行，取第一行内容
                    return line
        return f"({filepath.name} - 空文件)"
    except Exception as e:
        return f"({filepath.name} - 读取失败: {e})"


def extract_number(filename: str) -> int:
    """从文件名提取编号用于排序"""
    match = re.search(r"requirement-(\d+)", filename)
    return int(match.group(1)) if match else 0


if __name__ == "__main__":
    get_requirement_titles()
