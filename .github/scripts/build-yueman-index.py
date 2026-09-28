"""
Builds the keyword search index for the 漫画大全 (yueman) extension.

The site's own search is disabled, so this crawls every category listing on the mobile site and
writes one line per manga:

    id <TAB> title <TAB> cover path <TAB> last update (YYYY-MM-DD)

sorted by last update, newest first. Usage:

    python build-yueman-index.py <output file>
"""

import concurrent.futures
import html as html_lib
import re
import sys
import time
import urllib.request
from pathlib import Path

BASE_URL = "http://m.yueman1.cc"
COVER_HOST = "http://www.yueman1.cc"
CATEGORY_IDS = range(1, 15)
USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
)
WORKERS = 4
RETRIES = 4
# Refuse to publish an index that shrank this much: the site layout probably changed.
MIN_KEEP_RATIO = 0.8

ITEM_REGEX = re.compile(r'<li><p class="fl cover">(.*?)</li>', re.S)
ID_REGEX = re.compile(r'href="/manhua/(\d+)\.html"')
TITLE_REGEX = re.compile(r'<dt><a [^>]*title="([^"]*)"')
COVER_REGEX = re.compile(r'<img src="([^"]*)"')
DATE_REGEX = re.compile(r"(\d{4}-\d{2}-\d{2})")
PAGE_COUNT_REGEX = re.compile(r"共<strong>(\d+)</strong>页")


def fetch(url: str) -> str:
    for attempt in range(RETRIES):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.read().decode("gbk", errors="replace")
        except Exception as error:  # noqa: BLE001 - retry any network failure
            if attempt == RETRIES - 1:
                raise RuntimeError(f"{url}: {error}") from error
            time.sleep(2 ** attempt * 5)
    raise AssertionError("unreachable")


def category_url(category: int, page: int) -> str:
    return f"{BASE_URL}/mmm/{category}/" + (f"{page}.html" if page > 1 else "")


def parse_items(html: str) -> tuple[list[tuple[int, str, str, str]], int]:
    """Returns the listed manga and how many entries were skipped (e.g. links to other sites)."""
    items = []
    skipped = 0
    for block in ITEM_REGEX.findall(html):
        manga_id = ID_REGEX.search(block)
        title = TITLE_REGEX.search(block)
        if not manga_id or not title:
            skipped += 1
            continue
        cover = COVER_REGEX.search(block)
        date = DATE_REGEX.search(block)
        clean_title = re.sub(r"\s+", " ", html_lib.unescape(title.group(1))).strip()
        cover_path = cover.group(1).removeprefix(COVER_HOST) if cover else ""
        items.append((int(manga_id.group(1)), clean_title, cover_path, date.group(1) if date else ""))
    return items, skipped


def main() -> None:
    output = Path(sys.argv[1])

    first_pages = {category: fetch(category_url(category, 1)) for category in CATEGORY_IDS}
    urls = []
    for category, html in first_pages.items():
        match = PAGE_COUNT_REGEX.search(html)
        page_count = int(match.group(1)) if match else 1
        urls += [category_url(category, page) for page in range(2, page_count + 1)]
    print(f"Fetching {len(urls) + len(first_pages)} listing pages", flush=True)

    pages = list(first_pages.values())
    with concurrent.futures.ThreadPoolExecutor(WORKERS) as executor:
        for index, html in enumerate(executor.map(fetch, urls), start=1):
            pages.append(html)
            if index % 200 == 0:
                print(f"  {index}/{len(urls)}", flush=True)

    manga: dict[int, tuple[int, str, str, str]] = {}
    skipped = 0
    for html in pages:
        items, page_skipped = parse_items(html)
        skipped += page_skipped
        for item in items:
            existing = manga.get(item[0])
            if existing is None or item[3] > existing[3]:
                manga[item[0]] = item

    if output.exists():
        previous = sum(1 for _ in output.open(encoding="utf-8"))
        if len(manga) < previous * MIN_KEEP_RATIO:
            raise RuntimeError(f"Index shrank from {previous} to {len(manga)} entries; not publishing")

    rows = sorted(manga.values(), key=lambda item: (item[3], item[0]), reverse=True)
    with output.open("w", encoding="utf-8", newline="\n") as f:
        for manga_id, title, cover, date in rows:
            f.write(f"{manga_id}\t{title.replace(chr(9), ' ')}\t{cover}\t{date}\n")
    print(f"Wrote {len(rows)} manga to {output} (skipped {skipped} entries without a yueman id)")


if __name__ == "__main__":
    main()
