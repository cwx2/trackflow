"""
YouTrack Server 文档爬虫（Playwright 版）
==========================================
JetBrains 文档是 SPA，需要等 JS 渲染后才能拿到内容。
使用系统 Playwright 浏览器爬取，转为 Markdown，图片下载本地。

输出目录：D:/project/YT/youtrack-docs/
  pages/   → markdown 文件
  images/  → 本地图片
  index.md → 所有页面索引

用法：
  python scripts/crawl_youtrack_docs.py [--max N] [--delay N]
"""

import argparse
import asyncio
import hashlib
import os
import re
import sys
import time
import urllib.parse
from collections import deque
from pathlib import Path

import requests
import markdownify
from bs4 import BeautifulSoup
from playwright.sync_api import sync_playwright, Page

# ── 配置 ──────────────────────────────────────────────────────
BASE_URL = "https://www.jetbrains.com/help/youtrack/server/"
OUTPUT_DIR = Path("D:/project/YT/youtrack-docs")
PAGES_DIR = OUTPUT_DIR / "pages"
IMAGES_DIR = OUTPUT_DIR / "images"
INDEX_FILE = OUTPUT_DIR / "index.md"
LOG_FILE = OUTPUT_DIR / "crawl.log"

REQUEST_DELAY = 1.0   # 页面加载后等待时间（秒）
MAX_PAGES = 0          # 0 = 不限
MAX_RETRIES = 2

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
}

# ── 初始化 ────────────────────────────────────────────────────
PAGES_DIR.mkdir(parents=True, exist_ok=True)
IMAGES_DIR.mkdir(parents=True, exist_ok=True)

http_session = requests.Session()
http_session.headers.update(HEADERS)

visited_urls: set[str] = set()
failed_urls: list[str] = []
page_index: list[dict] = []
log_lines: list[str] = []


def log(msg: str):
    print(msg, flush=True)
    log_lines.append(msg)


# ── 工具函数 ──────────────────────────────────────────────────

def normalize_url(url: str, base: str = BASE_URL) -> str | None:
    url = urllib.parse.urljoin(base, url).split("#")[0].split("?")[0]
    if not url.startswith(BASE_URL):
        return None
    # 排除非文档页面
    if re.search(r"\.(png|jpg|jpeg|gif|svg|css|js|ico|woff|woff2|pdf|zip)$", url, re.I):
        return None
    return url


def url_to_filename(url: str) -> str:
    path = url.replace(BASE_URL, "").strip("/").replace("/", "__")
    if not path:
        path = "index"
    return path + ".md"


def download_image(img_url: str) -> str:
    """下载图片到本地，返回 file:/// URI"""
    img_url_abs = urllib.parse.urljoin(BASE_URL, img_url)
    if not img_url_abs.startswith("http"):
        return img_url
    ext = os.path.splitext(urllib.parse.urlparse(img_url_abs).path)[1] or ".png"
    name = hashlib.md5(img_url_abs.encode()).hexdigest()[:12] + ext
    local_path = IMAGES_DIR / name
    if local_path.exists():
        return local_path.as_uri()
    try:
        resp = http_session.get(img_url_abs, timeout=15)
        if resp.status_code == 200:
            local_path.write_bytes(resp.content)
            return local_path.as_uri()
    except Exception as e:
        log(f"  ⚠ 图片下载失败: {img_url_abs} — {e}")
    return img_url_abs


def html_to_markdown(html: str, page_url: str) -> tuple[str, str]:
    """提取主体内容，转 Markdown，图片本地化"""
    soup = BeautifulSoup(html, "html.parser")

    title_tag = soup.find("h1") or soup.find("title")
    title = title_tag.get_text(strip=True) if title_tag else "Untitled"

    # JetBrains 文档内容容器（多个候选）
    main = (
        soup.find("article")
        or soup.find("div", {"class": re.compile(r"\barticle\b|\bcontent\b", re.I)})
        or soup.find("main")
        or soup.body
    )
    if not main:
        return title, ""

    # 下载图片并替换为本地路径
    for img in main.find_all("img"):
        src = img.get("src") or img.get("data-src") or ""
        if src and not src.startswith("data:"):
            local_uri = download_image(src)
            img["src"] = local_uri
            # 保留 alt 作为备注
            alt = img.get("alt", "")
            img["alt"] = alt or "图片"

    # 清理噪音元素
    for tag in main.find_all(["nav", "footer", "script", "style", "noscript", "aside"]):
        tag.decompose()
    for tag in main.find_all(attrs={"class": re.compile(
            r"breadcrumb|sidebar|navigation|toc|feedback|cookie|promo|banner|ad\b", re.I)}):
        tag.decompose()

    # 转 Markdown（保留链接）
    md = markdownify.markdownify(
        str(main),
        heading_style="ATX",
        bullets="-",
        newline_style="backslash",
    )
    md = re.sub(r"\n{3,}", "\n\n", md).strip()

    header = f"# {title}\n\n> 来源：{page_url}\n\n"
    return title, header + md


def extract_links(html: str, base_url: str) -> list[str]:
    soup = BeautifulSoup(html, "html.parser")
    links = []
    for a in soup.find_all("a", href=True):
        norm = normalize_url(a["href"], base_url)
        if norm and norm not in visited_urls:
            links.append(norm)
    return list(set(links))


# ── 主爬取逻辑 ────────────────────────────────────────────────

def crawl(max_pages: int = 0, delay: float = 1.0):
    global MAX_PAGES, REQUEST_DELAY
    MAX_PAGES = max_pages
    REQUEST_DELAY = delay

    queue = deque([BASE_URL])
    total = 0

    log(f"开始爬取 YouTrack Server 文档（Playwright 版）")
    log(f"输出目录: {OUTPUT_DIR}")
    log(f"最大页数: {MAX_PAGES or '不限'}")
    log(f"页面间隔: {REQUEST_DELAY}s")
    log("=" * 60)

    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True)
        context = browser.new_context(
            user_agent=HEADERS["User-Agent"],
            viewport={"width": 1280, "height": 900},
        )
        page = context.new_page()

        # 屏蔽不必要的资源（加速）
        page.route(
            re.compile(r"\.(css|woff2?|ttf|eot)(\?.*)?$"),
            lambda route: route.abort()
        )

        while queue:
            url = queue.popleft()
            if url in visited_urls:
                continue
            if MAX_PAGES and total >= MAX_PAGES:
                log(f"\n已达到最大页面数 {MAX_PAGES}，停止")
                break

            visited_urls.add(url)
            total += 1
            log(f"[{total}] {url}")

            # 加载页面：用 domcontentloaded 避免 networkidle 超时
            # JetBrains 文档是 SPA，networkidle 永远不触发
            try:
                page.goto(url, wait_until="domcontentloaded", timeout=30000)
                # 等待主内容区出现（article 或 h1 渲染完毕）
                try:
                    page.wait_for_selector("article, h1, main", timeout=10000)
                except Exception:
                    pass  # 等不到也继续，拿现有内容
                time.sleep(REQUEST_DELAY)
                html = page.content()
            except Exception as e:
                log(f"  ⚠ 加载失败: {e}")
                failed_urls.append(url)
                continue

            # 发现新链接
            new_links = extract_links(html, url)
            added = 0
            for link in new_links:
                if link not in visited_urls:
                    queue.append(link)
                    added += 1
            if added:
                log(f"  + 发现 {added} 个新链接，队列: {len(queue)}")

            # 转 Markdown 并保存
            try:
                title, md_content = html_to_markdown(html, url)
            except Exception as e:
                log(f"  ⚠ 转换失败: {e}")
                failed_urls.append(url)
                continue

            if len(md_content.strip()) < 100:
                log(f"  ⚠ 内容过少，跳过")
                continue

            filename = url_to_filename(url)
            out_path = PAGES_DIR / filename
            out_path.write_text(md_content, encoding="utf-8")
            page_index.append({"url": url, "title": title, "filename": filename})
            log(f"  ✓ {title[:70]}")

        browser.close()

    write_index()
    write_log()

    log("\n" + "=" * 60)
    log(f"爬取完成！共 {total} 页，失败 {len(failed_urls)} 页")
    log(f"Markdown 文件: {PAGES_DIR}")
    log(f"图片文件: {IMAGES_DIR} ({sum(1 for _ in IMAGES_DIR.iterdir())} 张)")
    log(f"索引文件: {INDEX_FILE}")
    if failed_urls:
        log(f"\n失败页面 ({len(failed_urls)}):")
        for u in failed_urls[:20]:
            log(f"  {u}")


def write_index():
    lines = [
        "# YouTrack Server 文档索引\n\n",
        f"> 共 {len(page_index)} 个页面\n",
        f"> 图片目录: {IMAGES_DIR.as_uri()}\n\n",
    ]
    for item in sorted(page_index, key=lambda x: x["filename"]):
        local_path = (PAGES_DIR / item["filename"]).as_uri()
        lines.append(f"- [{item['title']}]({local_path})\n")
    INDEX_FILE.write_text("".join(lines), encoding="utf-8")
    log(f"\n索引已写入: {INDEX_FILE}")


def write_log():
    log_path = OUTPUT_DIR / "crawl.log"
    log_path.write_text("\n".join(log_lines), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="爬取 YouTrack Server 文档")
    parser.add_argument("--max", type=int, default=0, help="最大页面数（0=不限）")
    parser.add_argument("--delay", type=float, default=1.0, help="页面间隔秒数")
    args = parser.parse_args()
    crawl(max_pages=args.max, delay=args.delay)
