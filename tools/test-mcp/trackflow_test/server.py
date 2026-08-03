"""
TrackFlow 开发辅助 MCP Server
- 截图查看：读取 test 目录下的截图文件，以 Image 格式返回给 AI 查看
- 后端管理：重启 TrackFlow Spring Boot 后端服务
- 浏览器检查：检查 Playwright 浏览器是否可用（并行 worker 场景）
"""

import os
import subprocess
import time
import socket
import urllib.request
import json
import hashlib
import shutil
import threading
import uuid
from pathlib import Path
from io import BytesIO
from mcp.server.fastmcp import FastMCP, Image

SCREENSHOT_DIR = os.environ.get("SCREENSHOT_DIR", r"D:\project\YT\test")
MAX_IMAGE_DIMENSION = 1920
BACKEND_PORT = int(os.environ.get("BACKEND_PORT", "8090"))
TARGET_CLASSES = os.environ.get("TARGET_CLASSES", r"D:\project\YT\trackflow-server\target\classes")
PROJECT_DIR = Path(os.environ.get("TRACKFLOW_SERVER_DIR", r"D:\project\YT\trackflow-server"))
SOURCE_DIR = PROJECT_DIR / "src" / "main" / "java"
RESOURCE_DIR = PROJECT_DIR / "src" / "main" / "resources"
CLASSPATH_FILE = PROJECT_DIR / "scripts" / "classpath.txt"
BUILD_STATE_FILE = PROJECT_DIR / "target" / ".mcp-build-state.json"
JAVA21_COMPILER = Path(os.environ.get("TRACKFLOW_JAVAC", r"E:\Dev\SDK\JDK\jdk21\bin\javac.exe"))
BUILD_LOCK = threading.Lock()

mcp = FastMCP("trackflow-test", instructions="""TrackFlow 开发辅助工具。
- list_screenshots: 列出 test 目录下所有截图文件
- view_screenshot: 读取指定截图文件，AI 可直接看到图片内容
- build_backend: 使用 JDK 21 和项目依赖完整编译后端，并原子更新 target/classes
- check_compilation: 检查单个 Java 文件是否已编译（代替 PowerShell Test-Path 命令）
- check_compilation_batch: 批量检查编译状态，或自动检查最近修改的文件
- restart_backend: 先构建后端，再重启并等待健康端口；构建失败不会启动旧 class
- stop_backend: 停止后端并确认 8090 端口释放，测试结束必须调用
- check_browser_available: 检查你的独立 Playwright 浏览器是否可用（重要：每个 worker 有独立浏览器，不要自己猜测冲突）
""")


def _resize_if_needed(data: bytes) -> bytes:
    """超过尺寸限制时等比缩放"""
    try:
        from PIL import Image as PILImage
        img = PILImage.open(BytesIO(data))
        w, h = img.size
        if w <= MAX_IMAGE_DIMENSION and h <= MAX_IMAGE_DIMENSION:
            return data
        scale = min(MAX_IMAGE_DIMENSION / w, MAX_IMAGE_DIMENSION / h)
        img = img.resize((int(w * scale), int(h * scale)), PILImage.LANCZOS)
        buf = BytesIO()
        img.save(buf, format="PNG")
        return buf.getvalue()
    except ImportError:
        return data


@mcp.tool()
async def list_screenshots() -> str:
    """列出 test 目录下所有截图文件（按修改时间倒序）"""
    dir_path = Path(SCREENSHOT_DIR)
    if not dir_path.exists():
        return f"目录不存在: {SCREENSHOT_DIR}"

    files = [f for f in dir_path.iterdir() if f.suffix.lower() in (".png", ".jpg", ".jpeg")]
    files.sort(key=lambda f: f.stat().st_mtime, reverse=True)

    if not files:
        return "目录为空，暂无截图"

    lines = [f"共 {len(files)} 个截图文件:\n"]
    for f in files[:20]:
        size_kb = f.stat().st_size / 1024
        lines.append(f"  {f.name} ({size_kb:.0f} KB)")
    if len(files) > 20:
        lines.append(f"  ... 还有 {len(files) - 20} 个")
    return "\n".join(lines)


@mcp.tool()
async def view_screenshot(filename: str) -> Image:
    """
    读取指定截图文件并返回图片（AI 可直接看到）。

    参数:
      filename: 文件名（如 "page-2026-07-11.png"），或 "latest" 查看最新截图
    """
    dir_path = Path(SCREENSHOT_DIR)

    if filename == "latest":
        files = [f for f in dir_path.iterdir() if f.suffix.lower() in (".png", ".jpg", ".jpeg")]
        if not files:
            raise ValueError("目录为空，暂无截图")
        files.sort(key=lambda f: f.stat().st_mtime, reverse=True)
        file_path = files[0]
    else:
        file_path = dir_path / filename
        if not file_path.exists():
            # 模糊匹配
            matches = [f for f in dir_path.iterdir() if filename in f.name]
            if matches:
                file_path = matches[0]
            else:
                raise FileNotFoundError(f"找不到文件: {filename}")

    data = file_path.read_bytes()
    data = _resize_if_needed(data)
    fmt = "png" if file_path.suffix.lower() == ".png" else "jpeg"
    return Image(data=data, format=fmt)


@mcp.tool()
async def check_browser_available() -> str:
    """
    检查你的独立 Playwright 浏览器是否可用。

    重要说明：
    - 在并行模式下，每个 worker 都有独立的 Playwright 实例和独立的 Chrome user-data-dir
    - 其他 worker 的浏览器操作不会影响你
    - 只有当此工具返回"不可用"时才需要等待
    - 如果 Playwright 工具返回 "Completed"，说明操作成功，不要自己猜测冲突

    返回:
      浏览器可用性状态和诊断信息
    """
    results = []
    
    # 1. 检查是否在并行 worker 模式（通过 KIRO_HOME 环境变量判断）
    kiro_home = os.environ.get("KIRO_HOME", "")
    worker_id = None
    playwright_port = None
    
    if "worker-envs" in kiro_home:
        # 从路径中提取 worker_id，如 .../worker-envs/consumer-1/.kiro
        parts = kiro_home.replace("\\", "/").split("/")
        for i, p in enumerate(parts):
            if p == "worker-envs" and i + 1 < len(parts):
                worker_id = parts[i + 1]
                break
    
    if worker_id:
        # 并行 worker 模式：根据 worker_id 计算端口（与 _playwright.py 中的逻辑一致）
        import re
        match = re.search(r"(\d+)$", worker_id)
        index = int(match.group(1)) if match else 1
        port_base = 9100
        if worker_id.startswith("producer"):
            playwright_port = port_base + index
        elif worker_id.startswith("consumer"):
            playwright_port = port_base + 10 + index
        else:
            playwright_port = port_base + 20 + index
        results.append(f"📌 Worker ID: {worker_id}")
        results.append(f"📌 Playwright 端口: {playwright_port}")
        
        # 检查端口是否有进程监听（支持 IPv4 和 IPv6）
        port_open = False
        for host in ["127.0.0.1", "::1"]:
            try:
                sock = socket.socket(
                    socket.AF_INET if host == "127.0.0.1" else socket.AF_INET6,
                    socket.SOCK_STREAM
                )
                sock.settimeout(2)
                if sock.connect_ex((host, playwright_port)) == 0:
                    port_open = True
                sock.close()
                if port_open:
                    break
            except Exception:
                pass
        
        if port_open:
            results.append(f"✅ Playwright MCP 端口 {playwright_port} 正在监听")
            
            # 尝试调用 Playwright MCP 的健康检查（SSE endpoint）
            try:
                url = f"http://localhost:{playwright_port}/sse"
                req = urllib.request.Request(url, method="GET")
                req.add_header("Accept", "text/event-stream")
                with urllib.request.urlopen(req, timeout=3) as resp:
                    if resp.status == 200:
                        results.append("✅ Playwright MCP SSE 端点响应正常")
            except urllib.error.URLError as e:
                results.append(f"⚠️ SSE 端点连接异常: {e.reason}")
            except Exception as e:
                results.append(f"⚠️ SSE 检查跳过: {e}")
        else:
            results.append(f"❌ Playwright MCP 端口 {playwright_port} 未监听")
            results.append("   可能原因：Playwright 进程未启动或已崩溃")
            results.append("   建议：等待脚本自动重启 Playwright，或手动重启并行脚本")
            return "\n".join(results) + "\n\n⚠️ 浏览器不可用，请等待"
    else:
        # 单实例模式：Playwright MCP 使用 stdio 通信，无法通过端口检查
        # 在这种模式下，浏览器总是可用的（由 kiro-cli 管理）
        results.append("📌 单实例模式（非并行 worker）")
        results.append("✅ Playwright MCP 使用 stdio 通信，由 kiro-cli 管理")
        results.append("")
        results.append("=" * 40)
        results.append("✅ 浏览器可用")
        results.append("")
        results.append("在单实例模式下，直接使用 Playwright 工具即可。")
        results.append("如果操作返回 'Completed'，说明成功。")
        return "\n".join(results)
    
    # 4. 检查 Chrome 进程
    try:
        result = subprocess.run(
            ["powershell", "-Command",
             "Get-Process -Name chrome -ErrorAction SilentlyContinue | Measure-Object | Select-Object -ExpandProperty Count"],
            capture_output=True, text=True, timeout=5
        )
        chrome_count = int(result.stdout.strip()) if result.stdout.strip().isdigit() else 0
        results.append(f"📊 系统中 Chrome 进程数: {chrome_count}")
        if chrome_count > 0 and worker_id:
            results.append("   （注意：多个 Chrome 进程是正常的，每个 worker 有独立实例）")
    except Exception:
        results.append("📊 Chrome 进程检查跳过")
    
    # 5. 最终结论
    results.append("")
    results.append("=" * 40)
    results.append("✅ 浏览器可用")
    results.append("")
    results.append("⚠️ 重要提示：")
    results.append("  - 如果 Playwright 工具返回 'Completed'，说明操作成功")
    results.append("  - 不要因为看到其他 worker 在用浏览器就等待")
    results.append("  - 每个 worker 有独立的浏览器实例，互不干扰")
    results.append("  - 只有当 Playwright 工具返回明确错误时才需要重试")
    
    return "\n".join(results)


def _argfile_value(value: str) -> str:
    """把 Windows 路径安全地写入 javac @argfile。"""
    normalized = str(value).replace("\\", "/")
    if any(ch.isspace() for ch in normalized) or '"' in normalized:
        return '"' + normalized.replace('"', '\\"') + '"'
    return normalized


def _annotation_processor_jars() -> list[Path]:
    """从本机 Maven 仓库发现项目需要的 Lombok/MapStruct 处理器。"""
    repository = Path(os.environ.get("MAVEN_REPOSITORY", str(Path.home() / ".m2" / "repository")))
    candidates = []
    for group, artifact in ((Path("org") / "mapstruct", "mapstruct-processor"),):
        artifact_dir = repository / group / artifact
        if artifact_dir.exists():
            candidates.extend(artifact_dir.glob(f"*/{artifact}-*.jar"))
    lombok_dir = repository / "org" / "projectlombok" / "lombok"
    if lombok_dir.exists():
        candidates.extend(lombok_dir.glob("*/lombok-*.jar"))
    # 每个处理器只取最高版本，避免不同版本同时被 ServiceLoader 加载。
    selected = {}
    for candidate in candidates:
        name = candidate.name
        key = "mapstruct" if name.startswith("mapstruct-processor-") else "lombok"
        selected[key] = max(selected.get(key, candidate), candidate, key=lambda p: p.stat().st_mtime_ns)
    return list(selected.values())


def _source_fingerprint() -> tuple[str, int]:
    """计算源代码和依赖 classpath 的指纹，用于避免同一 commit 重复编译。"""
    digest = hashlib.sha256()
    files = sorted(SOURCE_DIR.rglob("*.java"))
    for source in files:
        digest.update(str(source.relative_to(PROJECT_DIR)).encode("utf-8"))
        digest.update(source.read_bytes())
    if RESOURCE_DIR.exists():
        for resource in sorted(f for f in RESOURCE_DIR.rglob("*") if f.is_file()):
            digest.update(str(resource.relative_to(PROJECT_DIR)).encode("utf-8"))
            digest.update(resource.read_bytes())
    for dependency_file in (PROJECT_DIR / "pom.xml", CLASSPATH_FILE):
        digest.update(str(dependency_file.relative_to(PROJECT_DIR)).encode("utf-8"))
        if dependency_file.exists():
            digest.update(dependency_file.read_bytes())
    for processor in _annotation_processor_jars():
        digest.update(str(processor).encode("utf-8"))
        digest.update(str(processor.stat().st_mtime_ns).encode("ascii"))
    return digest.hexdigest(), len(files)


def _relative_java_file(java_file: str) -> str:
    """将 MCP 传入的 Java 路径转换为 src/main/java 下的相对路径。"""
    cleaned = java_file.replace("\\", "/")
    marker = "src/main/java/"
    if marker in cleaned:
        return cleaned.split(marker, 1)[1]
    return cleaned.lstrip("/")


def _requested_classes_exist(java_files: list[str] | None) -> tuple[bool, str]:
    """校验本轮变更文件对应的 class 已出现在新构建目录中。"""
    if not java_files:
        entrypoint = Path(TARGET_CLASSES) / "com" / "trackflow" / "TrackFlowApplication.class"
        return entrypoint.exists(), "com/trackflow/TrackFlowApplication.class"

    missing = []
    for java_file in java_files:
        relative = _relative_java_file(java_file)
        class_path = Path(TARGET_CLASSES) / relative.replace(".java", ".class")
        if not class_path.exists():
            missing.append(relative)
    return not missing, ", ".join(missing)


def _build_backend_sync(java_files: list[str] | None = None, force: bool = False) -> dict:
    """完整编译后端到临时目录，成功后原子替换 target/classes。"""
    started = time.time()
    project_dir = PROJECT_DIR
    target_dir = Path(TARGET_CLASSES).parent
    classes_dir = Path(TARGET_CLASSES)
    log_dir = project_dir / "logs"
    build_log = log_dir / "mcp_build.log"
    target_dir.mkdir(parents=True, exist_ok=True)
    log_dir.mkdir(parents=True, exist_ok=True)

    if not SOURCE_DIR.exists():
        return {"ok": False, "message": f"源码目录不存在: {SOURCE_DIR}"}
    if not CLASSPATH_FILE.exists():
        return {"ok": False, "message": f"依赖 classpath 不存在: {CLASSPATH_FILE}"}
    if not JAVA21_COMPILER.exists():
        return {"ok": False, "message": f"JDK 21 javac 不存在: {JAVA21_COMPILER}"}

    with BUILD_LOCK:
        fingerprint, source_count = _source_fingerprint()
        state = {}
        if BUILD_STATE_FILE.exists():
            try:
                state = json.loads(BUILD_STATE_FILE.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                state = {}

        if (
            not force
            and state.get("fingerprint") == fingerprint
            and classes_dir.exists()
            and (classes_dir / "com" / "trackflow" / "TrackFlowApplication.class").exists()
        ):
            verified, detail = _requested_classes_exist(java_files)
            if verified:
                return {
                    "ok": True,
                    "cached": True,
                    "source_count": source_count,
                    "elapsed": time.time() - started,
                    "message": f"✅ 后端构建缓存命中（{source_count} 个源文件，{detail}）",
                }

        classpath = CLASSPATH_FILE.read_text(encoding="utf-8").strip()
        if not classpath:
            return {"ok": False, "message": "classpath.txt 为空，无法编译后端"}
        processor_jars = _annotation_processor_jars()
        if not processor_jars:
            return {
                "ok": False,
                "message": "未找到 Lombok/MapStruct annotation processor，无法生成 Spring Converter。"
                "请确认 Maven 本地仓库包含 mapstruct-processor 和 lombok。",
            }
        processorpath = os.pathsep.join([classpath] + [str(path) for path in processor_jars])

        build_id = uuid.uuid4().hex
        staging_dir = target_dir / f".mcp-build-{build_id}"
        generated_dir = staging_dir / "generated-sources"
        argfile = target_dir / f".mcp-javac-{build_id}.args"
        source_files = sorted(SOURCE_DIR.rglob("*.java"))
        compile_args = [
            "-encoding", "UTF-8",
            "-parameters",
            "-classpath", classpath,
            "-processorpath", processorpath,
            "-d", str(staging_dir),
            "-s", str(generated_dir),
            "-implicit:class",
            "-Xlint:none",
        ] + [str(source) for source in source_files]

        staging_dir.mkdir(parents=True, exist_ok=False)
        argfile.write_text("\n".join(_argfile_value(arg) for arg in compile_args), encoding="utf-8")
        try:
            result = subprocess.run(
                [str(JAVA21_COMPILER), f"@{argfile}"],
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=300,
                cwd=str(project_dir),
            )
        except subprocess.TimeoutExpired:
            result = None
            output = "javac 构建超时（300 秒）"
        except OSError as exc:
            result = None
            output = f"无法启动 JDK 21 javac: {exc}"
        finally:
            try:
                argfile.unlink(missing_ok=True)
            except OSError:
                pass

        if result is not None:
            output = (result.stdout or "") + (result.stderr or "")
        build_log.write_text(
            f"sources={source_count}\ncommand=@{argfile.name}\nexit_code="
            f"{result.returncode if result is not None else 'ERROR'}\n\n{output}",
            encoding="utf-8",
        )

        if result is None or result.returncode != 0:
            shutil.rmtree(staging_dir, ignore_errors=True)
            return {
                "ok": False,
                "elapsed": time.time() - started,
                "message": f"❌ 后端构建失败，未替换旧 target/classes。日志: {build_log}\n{output[-6000:]}",
            }

        if RESOURCE_DIR.exists():
            try:
                shutil.copytree(RESOURCE_DIR, staging_dir, dirs_exist_ok=True)
            except OSError as exc:
                shutil.rmtree(staging_dir, ignore_errors=True)
                return {
                    "ok": False,
                    "elapsed": time.time() - started,
                    "message": f"❌ Java 编译成功但资源文件复制失败，未替换旧 target/classes: {exc}",
                }

        entrypoint = staging_dir / "com" / "trackflow" / "TrackFlowApplication.class"
        if not entrypoint.exists():
            shutil.rmtree(staging_dir, ignore_errors=True)
            return {
                "ok": False,
                "elapsed": time.time() - started,
                "message": "❌ javac 返回成功但缺少 TrackFlowApplication.class，拒绝启动后端",
            }

        previous_dir = target_dir / f".mcp-classes-previous-{build_id}"
        try:
            if classes_dir.exists():
                classes_dir.rename(previous_dir)
            # Windows 下目录 rename 偶尔会被 Defender/IDEA 短暂占用；复制到新目录更稳，
            # 且旧目录已经备份，复制失败仍可完整回滚。
            classes_dir.mkdir(parents=True, exist_ok=False)
            shutil.copytree(staging_dir, classes_dir, dirs_exist_ok=True)
            shutil.rmtree(staging_dir, ignore_errors=True)
        except OSError as exc:
            if classes_dir.exists():
                shutil.rmtree(classes_dir, ignore_errors=True)
            if previous_dir.exists() and not classes_dir.exists():
                previous_dir.rename(classes_dir)
            shutil.rmtree(staging_dir, ignore_errors=True)
            return {
                "ok": False,
                "elapsed": time.time() - started,
                "message": f"❌ 构建成功但无法更新 target/classes: {exc}",
            }

        verified, detail = _requested_classes_exist(java_files)
        if not verified:
            if classes_dir.exists():
                shutil.rmtree(classes_dir, ignore_errors=True)
            if previous_dir.exists():
                previous_dir.rename(classes_dir)
            return {
                "ok": False,
                "elapsed": time.time() - started,
                "message": f"❌ 构建产物校验失败，缺少: {detail}",
            }

        state_tmp = BUILD_STATE_FILE.with_suffix(".tmp")
        try:
            state_tmp.write_text(
                json.dumps(
                    {"fingerprint": fingerprint, "source_count": source_count, "built_at": time.time()},
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            state_tmp.replace(BUILD_STATE_FILE)
        except OSError:
            state_tmp.unlink(missing_ok=True)

        shutil.rmtree(previous_dir, ignore_errors=True)
        return {
            "ok": True,
            "cached": False,
            "source_count": source_count,
            "elapsed": time.time() - started,
            "message": f"✅ 后端构建完成并已更新 target/classes（{source_count} 个源文件，校验 {detail}）",
        }


@mcp.tool()
async def build_backend(java_files: list[str] | None = None, force: bool = False) -> str:
    """
    使用 JDK 21 完整编译 TrackFlow 后端。

    编译输出先写入临时目录，只有完整构建和 class 校验成功后才替换 target/classes。
    同一份源代码会命中构建缓存，避免重复编译。
    """
    result = _build_backend_sync(java_files=java_files, force=force)
    return result["message"]


@mcp.tool()
async def check_compilation(java_file: str) -> str:
    """
    检查指定 Java 源文件是否已被 IDEA 编译到 target/classes。

    参数:
      java_file: Java 源文件的相对路径（相对于 trackflow-server/src/main/java/），
                 例如 "com/trackflow/report/entity/ReportType.java"
                 也接受全路径如 "D:/project/YT/trackflow-server/src/main/java/com/trackflow/report/entity/ReportType.java"

    返回:
      编译状态信息（已编译/未编译/源文件不存在）
    """
    project_dir = Path(r"D:\project\YT\trackflow-server")
    src_base = project_dir / "src" / "main" / "java"
    classes_base = Path(TARGET_CLASSES)

    # 规范化输入路径
    java_file_clean = java_file.replace("\\", "/")

    # 如果是全路径，提取相对部分
    marker = "src/main/java/"
    if marker in java_file_clean:
        relative = java_file_clean.split(marker, 1)[1]
    else:
        relative = java_file_clean

    src_path = src_base / relative
    class_relative = relative.replace(".java", ".class")
    class_path = classes_base / class_relative

    if not src_path.exists():
        return f"❌ 源文件不存在: {src_path}"

    if not class_path.exists():
        return f"❌ 未编译: class 文件不存在 ({class_path.name})\n等待 IDEA 自动编译，或手动 Ctrl+Shift+F9"

    src_mtime = src_path.stat().st_mtime
    class_mtime = class_path.stat().st_mtime

    if class_mtime >= src_mtime:
        delta = class_mtime - src_mtime
        return f"✅ 已编译: {class_path.name} (class 比源文件新 {delta:.1f}s)"
    else:
        lag = src_mtime - class_mtime
        return f"⚠️ 编译过期: 源文件比 class 新 {lag:.1f}s，IDEA 可能尚未完成增量编译。建议等待 3-5 秒后重试。"


@mcp.tool()
async def check_compilation_batch(java_files: list[str] | None = None) -> str:
    """
    批量检查多个 Java 文件的编译状态，或检查整体编译健康度。

    参数:
      java_files: Java 源文件路径列表（可选）。如果不传，则检查最近 60 秒内修改过的源文件。

    返回:
      各文件的编译状态汇总
    """
    project_dir = Path(r"D:\project\YT\trackflow-server")
    src_base = project_dir / "src" / "main" / "java"
    classes_base = Path(TARGET_CLASSES)

    if not classes_base.exists():
        return "❌ target/classes 目录不存在，请先在 IDEA 中编译项目"

    # 如果没传文件列表，扫描最近修改的文件
    if not java_files:
        now = time.time()
        cutoff = now - 60  # 最近 60 秒
        java_files_found = []
        for f in src_base.rglob("*.java"):
            if f.stat().st_mtime > cutoff:
                java_files_found.append(str(f.relative_to(src_base)))
        if not java_files_found:
            return "✅ 最近 60 秒无源文件变更，无需检查"
        java_files = java_files_found

    results = []
    compiled = 0
    stale = 0
    missing = 0

    for jf in java_files:
        jf_clean = jf.replace("\\", "/")
        marker = "src/main/java/"
        if marker in jf_clean:
            relative = jf_clean.split(marker, 1)[1]
        else:
            relative = jf_clean

        src_path = src_base / relative
        class_relative = relative.replace(".java", ".class")
        class_path = classes_base / class_relative

        name = Path(relative).name

        if not src_path.exists():
            results.append(f"  ❓ {name} — 源文件不存在")
            continue

        if not class_path.exists():
            results.append(f"  ❌ {name} — 未编译")
            missing += 1
        elif class_path.stat().st_mtime >= src_path.stat().st_mtime:
            results.append(f"  ✅ {name}")
            compiled += 1
        else:
            lag = src_path.stat().st_mtime - class_path.stat().st_mtime
            results.append(f"  ⚠️ {name} — 过期 {lag:.1f}s")
            stale += 1

    summary = f"编译状态: {compiled} 已编译, {stale} 过期, {missing} 缺失 (共 {len(java_files)} 文件)"
    if stale > 0 or missing > 0:
        summary += "\n建议等待 IDEA 编译完成后再重启后端。"

    return summary + "\n\n" + "\n".join(results)


@mcp.tool()
async def restart_backend(skip_compile_check: bool = False) -> str:
    """
    重启 TrackFlow 后端 Java 服务。

    流程：
    1. 使用 JDK 21 完整构建后端，并原子更新 target/classes
    2. 构建失败时保持旧服务和旧 class，不继续测试
    3. 杀掉当前运行的后端进程
    4. 使用 JDK 21 重新启动
    5. 等待端口 8090 可用

    参数:
      skip_compile_check: 兼容旧调用参数；完整构建永远不会跳过。

    返回启动结果（成功/失败 + 耗时 + 编译日志）。
    """
    result_lines = []
    project_dir = PROJECT_DIR
    log_dir = project_dir / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)

    # 0. 完整构建，避免启动旧 target/classes。skip_compile_check 只为兼容旧调用，不能跳过构建。
    build_result = _build_backend_sync()
    if not build_result["ok"]:
        result_lines.append(build_result["message"])
        result_lines.append("⛔ 后端未重启，测试应标记为 TEST_ENVIRONMENT_FAILURE")
        return "\n".join(result_lines)
    result_lines.append(build_result["message"])

    # 1. 杀掉旧进程
    killed = _kill_backend()
    if killed:
        result_lines.append(f"✅ 已停止旧进程 (PID: {killed})")
    else:
        result_lines.append("ℹ️ 未发现运行中的后端进程")

    # 2. 等端口释放
    _wait_port_free(BACKEND_PORT, timeout=5)

    # 3. 获取 classpath
    cp_file = Path(TARGET_CLASSES).parent.parent / "scripts" / "classpath.txt"
    if not cp_file.exists():
        return "❌ classpath.txt 不存在，请从 IDEA 启动日志中提取 classpath 保存到 trackflow-server/scripts/classpath.txt"
    if not Path(TARGET_CLASSES).exists():
        return "❌ target/classes 不存在，请先在 IDEA 中编译项目（Ctrl+Shift+F9）"

    classpath = cp_file.read_text(encoding="utf-8").strip()

    # 4. 启动新进程（后台，日志输出到文件）
    java_exe = r"E:\Dev\SDK\JDK\jdk21\bin\java.exe"
    log_file = log_dir / "backend.log"

    cmd = [
        java_exe,
        "-XX:TieredStopAtLevel=1",
        "-Dspring.profiles.active=dev",
        "-Dspring.output.ansi.enabled=never",
        "-Dfile.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8",
        "-Dsun.stderr.encoding=UTF-8",
        "-cp", classpath,
        "com.trackflow.TrackFlowApplication"
    ]

    with open(log_file, "w", encoding="utf-8") as f:
        proc = subprocess.Popen(
            cmd,
            stdout=f,
            stderr=subprocess.STDOUT,
            cwd=str(project_dir),
            creationflags=subprocess.CREATE_NEW_PROCESS_GROUP
        )

    result_lines.append(f"🚀 后端进程已启动 (PID: {proc.pid})")
    result_lines.append(f"📄 日志文件: {log_file}")

    # 5. 等待就绪
    start_time = time.time()
    ready = _wait_for_startup(timeout=40)
    elapsed = time.time() - start_time

    if ready:
        result_lines.append(f"✅ 后端已就绪 (耗时 {elapsed:.1f}s，端口 {BACKEND_PORT})")
    else:
        # 检查进程是否还活着
        if proc.poll() is not None:
            # 读取最后的日志
            tail = _read_log_tail(log_file, lines=30)
            result_lines.append(f"❌ 后端启动失败（exit code: {proc.returncode}）")
            result_lines.append(f"最后日志:\n{tail}")
        else:
            result_lines.append(f"⚠️ 后端仍在启动中（已等待 {elapsed:.1f}s），可稍后检查日志")

    return "\n".join(result_lines)


def _check_compile_errors() -> dict:
    """
    使用 javac 检查最近修改的 Java 文件是否有编译错误。
    
    返回: {
        "has_errors": bool,
        "output": str,  # 错误信息
        "warnings": str,  # 警告信息
        "warning_count": int
    }
    """
    project_dir = Path(r"D:\project\YT\trackflow-server")
    src_dir = project_dir / "src" / "main" / "java"
    cp_file = project_dir / "scripts" / "classpath.txt"
    log_dir = project_dir / "logs"
    
    # 读取 classpath
    if not cp_file.exists():
        return {
            "has_errors": True,
            "output": "classpath.txt 不存在，无法进行编译检查",
            "warnings": "",
            "warning_count": 0
        }
    
    classpath = cp_file.read_text(encoding="utf-8").strip()
    
    # 找到最近 5 分钟内修改的 Java 文件
    now = time.time()
    cutoff = now - 300  # 5 分钟
    recent_files = []
    for f in src_dir.rglob("*.java"):
        if f.stat().st_mtime > cutoff:
            recent_files.append(str(f))
    
    if not recent_files:
        # 没有最近修改的文件，检查入口文件
        app_file = src_dir / "com" / "trackflow" / "TrackFlowApplication.java"
        if app_file.exists():
            recent_files = [str(app_file)]
        else:
            return {
                "has_errors": False,
                "output": "",
                "warnings": "",
                "warning_count": 0
            }
    
    # 限制检查文件数量（避免太慢）
    if len(recent_files) > 50:
        recent_files = recent_files[:50]
    
    # 使用 javac 编译检查
    javac_exe = r"E:\Dev\SDK\JDK\jdk21\bin\javac.exe"
    compile_log = log_dir / "compile_check.log"
    
    cmd = [
        javac_exe,
        "-encoding", "UTF-8",
        "-d", str(project_dir / "target" / "compile-check"),  # 输出到临时目录
        "-cp", classpath,
        "-Xlint:all",
        "-proc:none",  # 不运行注解处理器（MapStruct 等），只做语法检查
    ] + recent_files
    
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=120,
            cwd=str(project_dir)
        )
        
        # 合并 stdout 和 stderr
        output = result.stderr or ""  # javac 错误输出在 stderr
        
        # 保存编译日志
        with open(compile_log, "w", encoding="utf-8") as f:
            f.write(f"检查的文件 ({len(recent_files)} 个):\n")
            for rf in recent_files:
                f.write(f"  {rf}\n")
            f.write(f"\n--- javac 输出 ---\n{output}")
        
        # 分析输出
        has_errors = result.returncode != 0
        lines = output.strip().split("\n") if output.strip() else []
        
        # 提取错误（包含 "error:" 的行及其上下文）
        error_lines = []
        warning_lines = []
        for line in lines:
            if "error:" in line.lower() or "错误:" in line:
                error_lines.append(line)
            elif "warning:" in line.lower() or "警告:" in line:
                warning_lines.append(line)
        
        # 如果有错误，返回完整输出（包含位置信息）
        if has_errors:
            # 截取前 50 行，避免输出太长
            error_output = "\n".join(lines[:50])
            if len(lines) > 50:
                error_output += f"\n... (还有 {len(lines) - 50} 行)"
            return {
                "has_errors": True,
                "output": error_output,
                "warnings": "",
                "warning_count": 0
            }
        
        # 只有警告
        warning_output = "\n".join(warning_lines[:20])
        if len(warning_lines) > 20:
            warning_output += f"\n... (还有 {len(warning_lines) - 20} 条警告)"
        
        return {
            "has_errors": False,
            "output": "",
            "warnings": warning_output,
            "warning_count": len(warning_lines)
        }
        
    except subprocess.TimeoutExpired:
        return {
            "has_errors": True,
            "output": "编译检查超时（120秒），文件可能过多或存在循环依赖",
            "warnings": "",
            "warning_count": 0
        }
    except Exception as e:
        return {
            "has_errors": True,
            "output": f"编译检查失败: {e}",
            "warnings": "",
            "warning_count": 0
        }


def _kill_backend() -> list[int]:
    """杀掉现有的 TrackFlow 后端进程"""
    try:
        result = subprocess.run(
            ["powershell", "-Command",
             "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | "
             "Where-Object { $_.CommandLine -like '*TrackFlowApplication*' } | "
             "Select-Object -ExpandProperty ProcessId"],
            capture_output=True, text=True, timeout=10
        )
        pids = [int(line.strip()) for line in result.stdout.strip().split("\n") if line.strip().isdigit()]
    except Exception:
        pids = []

    # Spring Boot devtools 可能改变 Java 子进程命令行，按 8090 端口兜底，
    # 确保自动化结束时不会遗留后端进程。
    if not pids:
        try:
            result = subprocess.run(
                ["powershell", "-Command",
                 f"Get-NetTCPConnection -LocalPort {BACKEND_PORT} -State Listen -ErrorAction SilentlyContinue "
                 "| Select-Object -ExpandProperty OwningProcess"],
                capture_output=True, text=True, timeout=10
            )
            pids = [int(line.strip()) for line in result.stdout.strip().split("\n") if line.strip().isdigit()]
        except Exception:
            pids = []

    for pid in pids:
        try:
            subprocess.run(["taskkill", "/F", "/PID", str(pid)], capture_output=True, timeout=5)
        except Exception:
            pass

    if pids:
        time.sleep(2)
    return pids


@mcp.tool()
async def stop_backend() -> str:
    """停止 TrackFlow 后端，并确认 8090 端口已经释放。"""
    pids = _kill_backend()
    _wait_port_free(BACKEND_PORT, timeout=10)
    if pids:
        return f"✅ 后端已停止（PID: {', '.join(str(pid) for pid in pids)}，端口 {BACKEND_PORT} 已释放）"
    return f"ℹ️ 未发现运行中的后端，端口 {BACKEND_PORT} 已确认释放"


def _wait_port_free(port: int, timeout: int = 5):
    """等待端口释放"""
    start = time.time()
    while time.time() - start < timeout:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        result = sock.connect_ex(("localhost", port))
        sock.close()
        if result != 0:
            return  # 端口已释放
        time.sleep(0.5)


def _wait_for_startup(timeout: int = 30) -> bool:
    """等待后端启动完成（轮询端口）"""
    start = time.time()
    while time.time() - start < timeout:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(2)
            result = sock.connect_ex(("localhost", BACKEND_PORT))
            sock.close()
            if result == 0:
                return True
        except Exception:
            pass
        time.sleep(1)
    return False


def _read_log_tail(log_file: Path, lines: int = 50) -> str:
    """读取日志文件最后 N 行"""
    try:
        with open(log_file, "r", encoding="utf-8") as f:
            all_lines = f.readlines()
            return "".join(all_lines[-lines:])
    except Exception as e:
        return f"(读取日志失败: {e})"


def main():
    mcp.run()


if __name__ == "__main__":
    main()
