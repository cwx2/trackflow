"""
需求文件管理 MCP Server

工具：
  - create_requirement: 创建需求文件（自动分配编号）
  - list_requirements: 列出指定目录的需求文件
  - move_requirement: 在目录间移动需求文件
  - get_next_number: 获取下一个可用编号（仅查询不创建）
  - create_sub_requirement: 创建子需求文件（拆解复杂需求，如 requirement-160-1.md）
  - list_sub_requirements: 列出指定父需求的所有子需求
"""

import os
import re
import shutil
from pathlib import Path
from mcp.server.fastmcp import FastMCP

WORKSPACE = Path(os.environ.get("WORKSPACE", r"D:\project\YT"))
REQUIREMENTS_BASE = WORKSPACE / "requirements"

# 子目录定义
DIRS = {
    "review": REQUIREMENTS_BASE / "review",
    "develop": REQUIREMENTS_BASE / "develop",
    "implement": REQUIREMENTS_BASE / "implement",
    "rejected": REQUIREMENTS_BASE / "rejected",
}

# 确保目录存在
for d in DIRS.values():
    d.mkdir(parents=True, exist_ok=True)

mcp = FastMCP("requirement-manager", instructions="""需求文件管理工具。
- create_requirement: 传入需求内容（markdown），自动分配编号并写入指定目录。内容中用 {N} 占位，会被替换为实际编号。
- list_requirements: 列出指定目录下的需求文件（标题+编号）。
- move_requirement: 在 review/develop/implement/rejected 之间移动需求文件。
- get_next_number: 查询下一个可用的需求编号。
- create_sub_requirement: 创建子需求文件（如 requirement-160-1.md），用于拆解复杂需求为多期交付。
- list_sub_requirements: 列出指定父需求的所有子需求。
""")


def _find_max_number() -> int:
    """扫描所有目录，找到最大需求编号"""
    max_num = 0

    # 扫描子目录
    for dir_path in DIRS.values():
        for f in dir_path.glob("requirement-*.md"):
            num = _extract_number(f.name)
            if num > max_num:
                max_num = num

    # 扫描根目录（兼容旧文件）
    for f in REQUIREMENTS_BASE.glob("requirement-*.md"):
        num = _extract_number(f.name)
        if num > max_num:
            max_num = num

    return max_num


def _extract_number(filename: str) -> int:
    match = re.search(r"requirement-(\d+)", filename)
    return int(match.group(1)) if match else 0


def _extract_title(filepath: Path) -> str:
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("#"):
                    return line.lstrip("#").strip()
        return filepath.name
    except Exception:
        return filepath.name


@mcp.tool()
async def create_requirement(content: str, target_dir: str = "review") -> str:
    """
    创建需求文件，自动分配编号。

    参数:
      content: 需求文件的完整 markdown 内容。编号占位用 {N}，会被替换为实际编号。
      target_dir: 目标目录，可选 review/develop/implement/rejected（默认 review）

    返回:
      创建的文件路径和分配的编号
    """
    if target_dir not in DIRS:
        return f"错误：无效目录 '{target_dir}'，可选：{list(DIRS.keys())}"

    if not content.strip():
        return "错误：内容为空"

    # 分配编号
    max_num = _find_max_number()
    new_num = max_num + 1

    # 替换占位符
    content = content.replace("{N}", str(new_num))
    content = re.sub(r"REQ-\{N\}", f"REQ-{new_num}", content)

    # 写入文件
    filename = f"requirement-{new_num}.md"
    filepath = DIRS[target_dir] / filename
    filepath.write_text(content, encoding="utf-8")

    return f"已创建: {filepath}\n编号: REQ-{new_num}\n目录: {target_dir}"


@mcp.tool()
async def list_requirements(target_dir: str = "all") -> str:
    """
    列出需求文件。

    参数:
      target_dir: 目标目录，可选 review/develop/implement/rejected/all（默认 all）

    返回:
      需求文件列表（编号 + 标题）
    """
    if target_dir == "all":
        dirs_to_scan = DIRS.items()
    elif target_dir in DIRS:
        dirs_to_scan = [(target_dir, DIRS[target_dir])]
    else:
        return f"错误：无效目录 '{target_dir}'，可选：{list(DIRS.keys()) + ['all']}"

    lines = []
    for dir_name, dir_path in dirs_to_scan:
        files = sorted(dir_path.glob("requirement-*.md"), key=lambda f: _extract_number(f.name))
        if files:
            lines.append(f"\n## {dir_name}/ ({len(files)} 个)")
            for f in files:
                num = _extract_number(f.name)
                title = _extract_title(f)
                lines.append(f"  REQ-{num}: {title}")

    if not lines:
        return "所有目录均为空"

    # 统计
    total = sum(len(list(d.glob("requirement-*.md"))) for d in DIRS.values())
    lines.insert(0, f"总计: {total} 个需求文件")
    return "\n".join(lines)


@mcp.tool()
async def move_requirement(number: int, from_dir: str, to_dir: str, sub_index: int = None) -> str:
    """
    在目录间移动需求文件。

    参数:
      number: 需求编号（如 59）
      from_dir: 源目录（review/develop/implement/rejected）
      to_dir: 目标目录（review/develop/implement/rejected）
      sub_index: 子需求序号（可选，如 1 表示 requirement-59-1.md）

    返回:
      移动结果
    """
    if from_dir not in DIRS:
        return f"错误：无效源目录 '{from_dir}'"
    if to_dir not in DIRS:
        return f"错误：无效目标目录 '{to_dir}'"

    # 构造精确文件名
    if sub_index is not None:
        filename = f"requirement-{number}-{sub_index}.md"
    else:
        filename = f"requirement-{number}.md"

    source_dir = DIRS[from_dir]
    source_file = source_dir / filename

    if not source_file.exists():
        # 回退：模糊匹配
        candidates = list(source_dir.glob(f"requirement-{number}*.md"))
        if not candidates:
            return f"错误：在 {from_dir}/ 中未找到 {filename}"
        source_file = candidates[0]
        filename = source_file.name

    dest_file = DIRS[to_dir] / filename

    # 移动
    shutil.move(str(source_file), str(dest_file))
    return f"已移动: {from_dir}/{filename} → {to_dir}/{filename}"


@mcp.tool()
async def get_next_number() -> str:
    """
    获取下一个可用的需求编号（仅查询不创建文件）。

    返回:
      下一个可用编号
    """
    max_num = _find_max_number()
    return f"当前最大编号: REQ-{max_num}\n下一个可用: REQ-{max_num + 1}"


@mcp.tool()
async def create_sub_requirement(parent_number: int, sub_index: int, content: str, target_dir: str = "develop") -> str:
    """
    创建子需求文件（用于拆解复杂需求）。

    文件命名规则：requirement-{parent}-{sub_index}.md
    例如：parent_number=160, sub_index=1 → requirement-160-1.md

    参数:
      parent_number: 父需求编号（如 160）
      sub_index: 子需求序号（1, 2, 3...）
      content: 子需求的完整 markdown 内容。内容中可用 {PARENT} 代表父编号，{SUB} 代表子序号。
      target_dir: 目标目录，可选 review/develop/implement/rejected（默认 develop）

    返回:
      创建的文件路径和编号
    """
    if target_dir not in DIRS:
        return f"错误：无效目录 '{target_dir}'，可选：{list(DIRS.keys())}"

    if not content.strip():
        return "错误：内容为空"

    if sub_index < 1:
        return "错误：sub_index 必须 >= 1"

    # 检查父需求是否存在（在任何目录中）
    parent_exists = False
    for dir_path in DIRS.values():
        if list(dir_path.glob(f"requirement-{parent_number}.md")):
            parent_exists = True
            break
    if not parent_exists:
        # 也检查根目录
        if list(REQUIREMENTS_BASE.glob(f"requirement-{parent_number}.md")):
            parent_exists = True

    if not parent_exists:
        return f"警告：父需求 requirement-{parent_number}.md 未找到（仍然创建子需求）"

    # 检查子需求是否已存在
    filename = f"requirement-{parent_number}-{sub_index}.md"
    for dir_path in DIRS.values():
        if (dir_path / filename).exists():
            return f"错误：{filename} 已存在于 {dir_path.name}/ 目录"

    # 替换占位符
    content = content.replace("{PARENT}", str(parent_number))
    content = content.replace("{SUB}", str(sub_index))
    content = content.replace("{N}", f"{parent_number}-{sub_index}")

    # 写入文件
    filepath = DIRS[target_dir] / filename
    filepath.write_text(content, encoding="utf-8")

    return f"已创建: {filepath}\n编号: REQ-{parent_number}-{sub_index}\n父需求: REQ-{parent_number}\n目录: {target_dir}"


@mcp.tool()
async def list_sub_requirements(parent_number: int) -> str:
    """
    列出指定父需求的所有子需求。

    参数:
      parent_number: 父需求编号（如 160）

    返回:
      子需求列表（编号 + 标题 + 所在目录）
    """
    lines = []
    pattern = f"requirement-{parent_number}-*.md"

    for dir_name, dir_path in DIRS.items():
        files = sorted(dir_path.glob(pattern))
        for f in files:
            title = _extract_title(f)
            # 提取子序号
            match = re.search(rf"requirement-{parent_number}-(\d+)\.md", f.name)
            sub_idx = match.group(1) if match else "?"
            lines.append(f"  REQ-{parent_number}-{sub_idx}: {title} [{dir_name}/]")

    if not lines:
        return f"未找到 REQ-{parent_number} 的子需求"

    lines.insert(0, f"REQ-{parent_number} 的子需求 ({len(lines)} 个):")
    return "\n".join(lines)


def main():
    mcp.run()


if __name__ == "__main__":
    main()
