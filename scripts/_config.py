"""
配置模块：所有常量、路径、锁、SKILLS 声明、日志初始化。
其他模块从此处 import，避免循环依赖。
"""

import logging
import threading
from logging.handlers import TimedRotatingFileHandler
from pathlib import Path
import re as _re

# ============ 路径 ============

WORKSPACE = Path(__file__).parent.parent
REQUIREMENTS_BASE = WORKSPACE / "requirements"
REVIEW_DIR   = REQUIREMENTS_BASE / "review"
DEVELOP_DIR  = REQUIREMENTS_BASE / "develop"
IMPLEMENT_DIR = REQUIREMENTS_BASE / "implement"
REJECTED_DIR = REQUIREMENTS_BASE / "rejected"
WORKING_DIR  = REQUIREMENTS_BASE / "working"
SCREENSHOT_DIR = WORKSPACE / "test"
KIRO_CLI = "kiro-cli"

for _d in [REVIEW_DIR, DEVELOP_DIR, IMPLEMENT_DIR, REJECTED_DIR, WORKING_DIR]:
    _d.mkdir(parents=True, exist_ok=True)

# ============ 阈值 / 模型 ============

MIN_DEVELOP_QUEUE = 6       # develop 低于此数时触发生产
TIMEOUT_SECONDS   = 2400    # 单次 kiro-cli 超时（40 分钟）
MAX_RETRIES       = 5       # 需求最大失败重试次数
COOLDOWN_SECONDS  = 5
MAX_TEST_RETRIES   = 3      # 测试最多重试轮数
MAX_REVIEW_RETRIES = 2      # 审核最多重试轮数
REVIEW_BATCH_SIZE  = 5      # 每批审核需求数

# 模型（None = kiro-cli 默认）
# 当前 Kiro CLI 账户只暴露 auto；显式写入 auto，避免传入不存在的模型名导致
# 测试/审核阶段在启动后立即失败并反复重试。
KIRO_MODEL     = "auto"               # 测试/审核/生产
KIRO_MODEL_FIX = "auto"               # 修需求

# ============ Playwright ============

# worker 端口映射：producer-1→9101, consumer-1→9111, reviewer→9121
PLAYWRIGHT_PORT_BASE = 9100

# ============ Skills ============

SKILLS: dict[str, dict] = {
    "write-requirement":   {"path": ".kiro/skills/write-requirement/SKILL.md"},
    "tech-requirement":    {"path": ".kiro/skills/tech-requirement/SKILL.md"},
    "review-requirement":  {"path": ".kiro/skills/review-requirement/SKILL.md"},
    "fix-requirement":     {"path": ".kiro/skills/fix-requirement/SKILL.md"},
    "fix-requirement-auto":{"path": ".kiro/skills/fix-requirement-auto/SKILL.md"},
    "e2e-test":            {"path": ".kiro/skills/e2e-test/SKILL.md"},
    "code-review":         {"path": ".kiro/skills/code-review/SKILL.md"},
}

# ============ 角色工作流 ============

ROLE_WORKFLOW_DIR = Path(__file__).parent / "role-workflows"


def make_workflow_prompt(workflow_file: str, workflow_section: str = "",
                         skill: str = "write-requirement") -> dict:
    """构造生产者配置项"""
    return {"skill": skill, "workflow_file": workflow_file, "workflow_section": workflow_section}


# 生产者配置池（重复出现 = 权重更高）
PRODUCER_CONFIGS: list[dict] = [
    make_workflow_prompt("developer.md"),
    make_workflow_prompt("developer.md"),
    make_workflow_prompt("developer.md"),
    make_workflow_prompt("tech_lead.md"),
    make_workflow_prompt("tech_lead.md"),
    make_workflow_prompt("product_manager.md"),
    make_workflow_prompt("product_manager.md"),
    make_workflow_prompt("tester.md"),
    make_workflow_prompt("tester.md"),
    make_workflow_prompt("admin.md"),
    make_workflow_prompt("observer.md"),
    make_workflow_prompt("observer.md"),
]

# ============ 锁 ============

_claim_lock   = threading.Lock()   # 原子领取需求
_review_lock  = threading.Lock()   # 防止并发审核
_consumer_lock = threading.Lock()  # 共享 Git 工作区，消费者流水线必须串行
_retry_lock   = threading.Lock()   # 保护 _retry_counts
_retry_counts: dict[str, int] = {} # 需求失败次数

# ============ 日志 ============

_LOG_DIR = WORKSPACE / "scripts" / "log"
_LOG_DIR.mkdir(parents=True, exist_ok=True)

_fmt = logging.Formatter("%(asctime)s [%(levelname)s] [%(threadName)s] %(message)s")

_console_handler = logging.StreamHandler()
_console_handler.setLevel(logging.INFO)
_console_handler.setFormatter(_fmt)

_file_handler = TimedRotatingFileHandler(
    _LOG_DIR / "auto_iterate_parallel.log",
    when="midnight", interval=1, backupCount=30,
    encoding="utf-8", utc=False,
)
_file_handler.suffix = "%Y-%m-%d"
_file_handler.setLevel(logging.DEBUG)
_file_handler.setFormatter(_fmt)

logging.basicConfig(level=logging.DEBUG, handlers=[_console_handler, _file_handler])
log = logging.getLogger("auto_iterate")

_root = logging.getLogger()
_root.setLevel(logging.DEBUG)
if not any(isinstance(h, TimedRotatingFileHandler) for h in _root.handlers):
    _root.addHandler(_file_handler)
if not any(isinstance(h, logging.StreamHandler) and not isinstance(h, TimedRotatingFileHandler)
           for h in _root.handlers):
    _root.addHandler(_console_handler)

# ============ ANSI 清除 ============

_ANSI_RE = _re.compile(r'\x1b\[[0-9;?]*[A-Za-z]|\x1b\][^\x07]*\x07|\x1b[()][A-B0-2]')


def strip_ansi(text: str) -> str:
    """移除所有 ANSI 转义码"""
    return _ANSI_RE.sub('', text)
