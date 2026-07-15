"""
列出 requirements/ 各子目录下所有需求文件。
支持新的目录结构：review / develop / implement / rejected

用法：python scripts/list_requirements.py [目录名]
  无参数：显示所有目录
  指定目录：只显示该目录（如 python scripts/list_requirements.py develop）
"""

import sys
import re
from pathlib import Path


REQUIREMENTS_BASE = Path(__file__).parent.parent / "requirements"
DIRS = ["review", "develop", "implement", "rejected"]


def get_requirement_titles(target_dir: str = "all"):
    if target_dir != "all" and target_dir not in DIRS:
        print(f"错误：无效目录 '{target_dir}'，可选：{DIRS + ['all']}")
        return

    dirs_to_scan = DIRS if target_dir == "all" else [target_dir]
    total = 0

    for dir_name in dirs_to_scan:
        dir_path = REQUIREMENTS_BASE / dir_name
        if not dir_path.exists():
            continue

        files = sorted(dir_path.glob("requirement-*.md"), key=lambda f: extract_number(f.name))
        if not files:
            continue

        icon = {"review": "📝", "develop": "🔧", "implement": "✅", "rejected": "❌"}.get(dir_name, "📄")
        print(f"\n{icon} {dir_name}/ ({len(files)} 个)")
        for f in files:
            title = extract_title(f)
            print(f"  REQ-{extract_number(f.name):03d}: {title}")
        total += len(files)

    # 兼容：扫描根目录旧文件
    legacy_files = sorted(REQUIREMENTS_BASE.glob("requirement-*.md"), key=lambda f: extract_number(f.name))
    if legacy_files:
        print(f"\n📁 根目录（旧文件）({len(legacy_files)} 个)")
        for f in legacy_files:
            title = extract_title(f)
            is_done = "-done" in f.name
            status = "✅" if is_done else "⬚ "
            print(f"  {status} REQ-{extract_number(f.name):03d}: {title}")
        total += len(legacy_files)

    print(f"\n总计：{total} 个需求文件")


def extract_title(filepath: Path) -> str:
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("#"):
                    return line.lstrip("#").strip()
                if line:
                    return line
        return f"({filepath.name} - 空文件)"
    except Exception as e:
        return f"({filepath.name} - 读取失败: {e})"


def extract_number(filename: str) -> int:
    match = re.search(r"requirement-(\d+)", filename)
    return int(match.group(1)) if match else 0


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "all"
    get_requirement_titles(target)
