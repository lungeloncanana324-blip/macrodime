#!/usr/bin/env python3
"""Fails if the app's own writing contains a dash character.

The house rule for MacroDime's copy, code comments and documents: no em dash,
en dash, figure dash, horizontal bar or minus sign. The ASCII hyphen is the only
dash allowed, including in numeric ranges such as 15-30.

Characters are built from their code points below rather than typed, and the
check also catches the six-character escape form, because a dash written as an
escape in source still renders as a dash on screen. That gap once let three em
dashes survive a "clean" sweep of the iOS app.

    python3 scripts/check_dashes.py            # the whole repository
    python3 scripts/check_dashes.py android    # one folder
"""

import pathlib
import re
import sys

CODE_POINTS = (0x2012, 0x2013, 0x2014, 0x2015, 0x2212)
BACKSLASH = chr(92)

LITERAL = "[" + "".join(chr(point) for point in CODE_POINTS) + "]"
ESCAPED = re.escape(BACKSLASH) + "u(" + "|".join(f"{point:04x}" for point in CODE_POINTS) + ")"
PATTERN = re.compile(LITERAL + "|" + ESCAPED, re.IGNORECASE)

SUFFIXES = {
    ".swift", ".kt", ".kts", ".md", ".yml", ".yaml", ".toml", ".xml",
    ".html", ".py", ".sh", ".ps1", ".pro", ".properties",
}
SKIPPED = {".git", "build", ".build", ".gradle", ".kotlin", "DerivedData"}


def main() -> int:
    roots = [pathlib.Path(arg) for arg in sys.argv[1:]] or [pathlib.Path(".")]
    hits = []
    for root in roots:
        for path in sorted(root.rglob("*")):
            if not path.is_file() or path.suffix not in SUFFIXES or SKIPPED & set(path.parts):
                continue
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if PATTERN.search(line):
                    hits.append(f"{path}:{number}: {line.strip()[:120]}")
    print("\n".join(hits) if hits else "No dash characters found.")
    return 1 if hits else 0


if __name__ == "__main__":
    sys.exit(main())
