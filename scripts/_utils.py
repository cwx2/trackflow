"""
工具函数：需求文件操作、工作流加载、目录清理、父需求归档。
"""

import re
import shutil
import os
import subprocess
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
    """保守清理截图；默认不删除测试证据，避免需求引用的截图被误删。"""
    if os.environ.get("TRACKFLOW_CLEANUP_SCREENSHOTS") != "1":
        log.debug("[截图清理] 默认保留全部测试证据；设置 TRACKFLOW_CLEANUP_SCREENSHOTS=1 才启用清理")
        return
    if not SCREENSHOT_DIR.exists():
        return
    files = sorted(SCREENSHOT_DIR.iterdir(), key=lambda f: f.stat().st_mtime, reverse=True)
    to_delete = [f for f in files if f.is_file()][keep_count:]
    for f in to_delete:
        f.unlink(missing_ok=True)
    if to_delete:
        log.info(f"[截图清理] 删除 {len(to_delete)} 个旧截图，保留最新 {keep_count} 张")


def cleanup_working() -> None:
    """将 working/ 中的残留文件放回 develop/，并用 git mv 提交路径变化。"""
    if not WORKING_DIR.exists():
        return
    moved: list[tuple[str, str]] = []  # (old_rel, new_rel)
    for worker_dir in WORKING_DIR.iterdir():
        if worker_dir.is_dir():
            for f in worker_dir.glob("requirement-*.md"):
                destination = DEVELOP_DIR / f.name
                if destination.exists():
                    log.error(f"[清理] 跳过 {f.name}：develop/ 已存在同名文件，避免覆盖")
                    continue
                old_rel = str(f.relative_to(WORKSPACE)).replace("\\", "/")
                new_rel = str(destination.relative_to(WORKSPACE)).replace("\\", "/")
                shutil.move(str(f), str(destination))
                moved.append((old_rel, new_rel))
                log.info(f"[清理] {f.name} → develop/")
            try:
                worker_dir.rmdir()
            except OSError:
                pass

    if moved:
        try:
            all_paths = [p for pair in moved for p in pair]
            subprocess.run(
                ["git", "add", "--"] + all_paths,
                capture_output=True, cwd=str(WORKSPACE), timeout=15,
            )
            names = ", ".join(old.split("/")[-1] for old, _ in moved)
            subprocess.run(
                ["git", "commit", "-m", f"chore: recover {len(moved)} working req(s) to develop/ [{names}]"],
                capture_output=True, cwd=str(WORKSPACE), timeout=30,
            )
            log.info(f"[清理] ✅ 已提交 {len(moved)} 个需求文件的路径归还记录")
        except Exception as e:
            log.warning(f"[清理] git 提交失败（文件已移动）: {e}")


class AutomationInstanceLock:
    """防止两个自动化主进程同时操作同一个 Git 工作区。"""

    def __init__(self) -> None:
        self.path = WORKSPACE / "scripts" / ".auto_iterate_parallel.lock"
        self._owns_lock = False

    def __enter__(self) -> "AutomationInstanceLock":
        self.path.parent.mkdir(parents=True, exist_ok=True)

        # 兼容旧版本实例：旧版本没有 lock 文件，先通过进程命令行阻止重复启动。
        try:
            import psutil
            for process in psutil.process_iter(["pid", "name", "cmdline", "status"]):
                # 跳过 zombie/dead 进程，避免误判残留进程为活跃实例
                status = (process.info.get("status") or "").lower()
                if status in {"zombie", "dead"}:
                    continue
                if process.info["pid"] == os.getpid():
                    continue
                process_name = (process.info.get("name") or "").lower()
                if process_name not in {"python", "python.exe", "pythonw.exe"}:
                    continue
                command_args = process.info.get("cmdline") or []
                is_automation_script = any(
                    Path(str(argument).strip('"')).name.lower() == "auto_iterate_parallel.py"
                    for argument in command_args
                )
                if is_automation_script:
                    # 二次确认进程确实存活，避免竞态或 psutil 缓存导致误判
                    if not psutil.pid_exists(process.info["pid"]):
                        continue
                    raise RuntimeError(
                        f"自动化脚本已经运行（PID={process.info['pid']}），请先停止已有实例"
                    )
        except ImportError:
            pass
        except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
            pass

        try:
            with self.path.open("x", encoding="utf-8") as lock_file:
                lock_file.write(str(os.getpid()))
            self._owns_lock = True
            return self
        except FileExistsError:
            try:
                owner_pid = int(self.path.read_text(encoding="utf-8").strip())
            except FileNotFoundError:
                self.path.unlink(missing_ok=True)
                with self.path.open("x", encoding="utf-8") as lock_file:
                    lock_file.write(str(os.getpid()))
                self._owns_lock = True
                return self
            except ValueError as exc:
                raise RuntimeError(
                    f"自动化锁文件内容无效：{self.path}，请确认没有其它实例后手动处理"
                ) from exc

            try:
                import psutil
                owner_alive = psutil.pid_exists(owner_pid)
            except ImportError:
                try:
                    os.kill(owner_pid, 0)
                    owner_alive = True
                except ProcessLookupError:
                    owner_alive = False
                except OSError as exc:
                    # Windows 上 os.kill(pid, 0) 可能返回 WinError 87，
                    # 该错误只表示该 PID 不存在/不支持此探测方式。
                    if getattr(exc, "winerror", None) == 87:
                        owner_alive = False
                    else:
                        raise RuntimeError(
                            f"无法确认自动化实例 PID={owner_pid} 是否仍在运行"
                        ) from exc

            if not owner_alive:
                self.path.unlink(missing_ok=True)
                with self.path.open("x", encoding="utf-8") as lock_file:
                    lock_file.write(str(os.getpid()))
                self._owns_lock = True
                return self
            raise RuntimeError(
                f"自动化脚本已经运行（PID={owner_pid}），请先停止已有实例"
            )

    def __exit__(self, _exc_type, _exc_value, _traceback) -> None:
        if self._owns_lock:
            self.path.unlink(missing_ok=True)
            self._owns_lock = False


def archive_decomposed_parents() -> None:
    """归档 develop/ 中所有已拆解（有子需求）的父需求文件"""
    for f in list(DEVELOP_DIR.glob("requirement-*.md")):
        if not is_sub_requirement(f):
            number = extract_number(f.name)
            if has_sub_requirements(number):
                shutil.move(str(f), str(IMPLEMENT_DIR / f.name))
                log.info(f"[归档] 父需求 {f.name} → implement/")
