#!/usr/bin/env python3
"""Fails if a source link in the app or the store description does not open.

Google Play rejected MacroDime twice under the Misleading Claims policy. The
second time, on 7 Oct 2026, the reason was a "broken or inaccessible source
link". Every link opened in a desktop browser, but www.bls.gov answers
automated visitors (curl, Python, headless Chrome and Edge) with a 403 "Access
Denied" page, and Play checks the links with a machine. So this script fetches
each link the way a machine does, with Python's own HTTP client, and passes
only a 200 that is not a block page. It names itself as a generic bot rather
than as Python: www.who.int refuses the literal "Python-urllib" but opens for
headless browsers, so that name would fail a link Google can open, while
www.bls.gov refuses both.

It reads the links from where they are published: the url of every source in
DataSources.kt, which the Sources screen shows, and every address in the full
description in docs/play-store-listing.md. links.yml runs it from GitHub's
servers, which is closer to how Google sees a page than a home connection.

    python3 scripts/check_source_links.py
"""

import pathlib
import re
import sys
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA_SOURCES = ROOT / "android/core/src/main/kotlin/com/lungelo/macrodime/engine/DataSources.kt"
LISTING = ROOT / "docs/play-store-listing.md"

# What block pages say instead of the content. Akamai, which fronts www.bls.gov,
# titles its page "Access Denied".
BLOCKED = re.compile(r"<title>\s*(Access Denied|Attention Required|Just a moment)", re.IGNORECASE)
TRIES = 3
USER_AGENT = "Mozilla/5.0 (compatible; MacroDime source link check)"


def source_links() -> list[str]:
    return re.findall(r'url = "(https://[^"]+)"', DATA_SOURCES.read_text(encoding="utf-8"))


def description_links() -> list[str]:
    listing = LISTING.read_text(encoding="utf-8").replace("\r\n", "\n")
    section = listing.split("### Full description (4,000)", 1)[1]
    description = section.split("```\n", 1)[1].split("```", 1)[0]
    return re.findall(r"https?://\S+", description)


def check(url: str) -> str | None:
    """None if the page opened, otherwise what went wrong."""
    problem = "not tried"
    for attempt in range(TRIES):
        if attempt:
            time.sleep(5 * attempt)
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=30) as response:
                page = response.read(200_000).decode("utf-8", errors="replace")
                if response.status != 200:
                    problem = f"HTTP {response.status}"
                elif BLOCKED.search(page):
                    return f"a block page: {BLOCKED.search(page).group(1)}"
                else:
                    return None
        except urllib.error.HTTPError as error:
            problem = f"HTTP {error.code}"
            if error.code in (401, 403, 404, 410):
                return problem
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            problem = f"no answer: {error}"
    return problem


def main() -> int:
    in_app, in_listing = source_links(), description_links()
    if not in_app or not in_listing:
        print(f"found {len(in_app)} links in DataSources.kt and {len(in_listing)} in the description; expected some of each")
        return 1
    failures = 0
    for url in dict.fromkeys(in_app + in_listing):
        where = " and ".join(name for name, links in (("app", in_app), ("description", in_listing)) if url in links)
        problem = check(url)
        print(f"{'ok  ' if problem is None else 'FAIL'}  {url}  ({where}){'' if problem is None else ': ' + problem}")
        failures += problem is not None
    if failures:
        print(f"\n{failures} link(s) would fail Google Play's source link check.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
