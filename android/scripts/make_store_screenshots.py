#!/usr/bin/env python3
"""Frames the app's screens as Play Store screenshots, with a caption on each.

The screens come from ScreenshotTest (Robolectric, the real Compose UI with the
demo week from DemoData), so run that first:

    ./gradlew :app:testDebugUnitTest --tests '*LightScreenshotTest*' --tests '*DarkScreenshotTest*'
    python android/scripts/make_store_screenshots.py

Each one is drawn as an HTML page in the app's own type, colours and phone
shape, and rendered by headless Edge to 1080 x 1920 (9:16, inside Play's 2:1
limit) in android/play-screenshots/store/, which is git-ignored like the rest
of that folder. Captions follow the app's writing rule: no dash characters.
"""

import base64
import subprocess
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "android" / "app" / "build" / "screenshots"
FONT = ROOT / "android" / "app" / "src" / "main" / "res" / "font"
OUT = ROOT / "android" / "play-screenshots" / "store"

INK, PAPER, BASIL, BASIL_BRIGHT, MUTED = "#161614", "#F6F4EF", "#2E7A4E", "#6CC791", "#67635B"

# (file name, source render, caption with the accent in <em>, light or dark)
FRAMES = [
    ("1-plan-your-week.png", "light-tab-1-today.png", "Your macros and <em>your money</em>, on one screen", "light"),
    ("2-every-meal-priced.png", "light-tab-2-plan.png", "Every meal <em>priced</em> to your daily budget", "light"),
    ("3-cheaper-swaps.png", "light-sheet-swap-review.png", "<em>Cheaper swaps</em> that keep your macros", "light"),
    ("4-shopping-list.png", "light-tab-3-groceries.png", "A shopping list <em>built from your week</em>", "light"),
    ("5-day-and-night.png", "dark-tab-1-today.png", "Easy to read<br><em>day or night</em>", "dark"),
]


def data_uri(path: Path, mime: str) -> str:
    return f"data:{mime};base64," + base64.b64encode(path.read_bytes()).decode()


def status_bar(colour: str) -> str:
    """9:41, signal, wifi and a full battery, as a phone in demo mode shows them."""
    return f"""
<div class="status" style="color:{colour}">
  <span>9:41</span>
  <svg width="120" height="34" viewBox="0 0 120 34" fill="{colour}">
    <path d="M4 26h5v6H4zM12 20h5v12h-5zM20 13h5v19h-5zM28 6h5v26h-5z"/>
    <path d="M58 30l-5-6a8 8 0 0 1 10 0z M51 21l-4-4a17 17 0 0 1 22 0l-4 4a12 12 0 0 0-14 0z M45 15l-4-4a26 26 0 0 1 34 0l-4 4a20 20 0 0 0-26 0z" transform="translate(4 0)"/>
    <rect x="86" y="8" width="26" height="20" rx="4" fill="none" stroke="{colour}" stroke-width="3"/>
    <rect x="90" y="12" width="18" height="12" rx="2"/>
    <rect x="113" y="14" width="3" height="8" rx="1"/>
  </svg>
</div>"""


def page(source: Path, caption: str, mode: str) -> str:
    bg, fg, accent, bezel = (PAPER, INK, BASIL, INK) if mode == "light" else (INK, PAPER, BASIL_BRIGHT, "#2C2B28")
    # The status bar takes the colour of the screen's own top edge.
    from PIL import Image

    top = Image.open(source).convert("RGB").getpixel((20, 4))
    screen_top = "#%02X%02X%02X" % top
    icons = INK if sum(top) > 380 else PAPER
    font = FONT.as_uri()
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: J; src: url({font}/jakarta_extrabold.ttf); font-weight: 800; }}
@font-face {{ font-family: J; src: url({font}/jakarta_semibold.ttf); font-weight: 600; }}
html, body {{ margin: 0; width: 1080px; height: 1920px; overflow: hidden; }}
body {{ background: {bg}; font-family: J, sans-serif; position: relative; }}
h1 {{ position: absolute; top: 132px; left: 90px; right: 90px; margin: 0; text-align: center;
      font-weight: 800; font-size: 80px; line-height: 1.06; letter-spacing: -0.025em; color: {fg}; }}
h1 em {{ font-style: normal; color: {accent}; }}
.phone {{ position: absolute; top: 450px; left: 110px; width: 860px; height: 1700px; box-sizing: border-box;
          border: 16px solid {bezel}; border-radius: 84px; overflow: hidden; background: {screen_top};
          box-shadow: 0 40px 90px rgba(0,0,0,{0.16 if mode == "light" else 0.5}); }}
.status {{ height: 86px; display: flex; align-items: center; justify-content: space-between; padding: 6px 52px 0 58px;
           box-sizing: border-box; font-weight: 600; font-size: 34px; }}
.screen {{ display: block; width: 828px; }}
</style></head><body>
<h1>{caption}</h1>
<div class="phone">{status_bar(icons)}<img class="screen" src="{data_uri(source, 'image/png')}"></div>
</body></html>"""


def render(html: str, out: Path) -> None:
    edge = next(p for p in [Path(r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"),
                            Path(r"C:/Program Files/Microsoft/Edge/Application/msedge.exe")] if p.exists())
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        src = Path(tmp) / "frame.html"
        src.write_text(html, encoding="utf-8")
        if out.exists():
            out.unlink()
        subprocess.run(
            [str(edge), "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
             "--allow-file-access-from-files", f"--user-data-dir={Path(tmp) / 'profile'}",
             "--window-size=1080,1920", f"--screenshot={out}", src.as_uri()],
            capture_output=True, timeout=120,
        )
        for _ in range(60):
            if out.exists() and out.stat().st_size > 0:
                break
            time.sleep(0.5)
        else:
            raise SystemExit(f"Edge did not write {out}")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, source, caption, mode in FRAMES:
        path = SHOTS / source
        if not path.exists():
            raise SystemExit(f"{path} is missing: run the screenshot tests first.")
        render(page(path, caption, mode), OUT / name)
        print("wrote", OUT / name)


if __name__ == "__main__":
    main()
