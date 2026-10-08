#!/usr/bin/env python3
"""Rebuild the CJK fallback fonts the language goldens render with.

Android falls back to its system Noto Sans CJK for Japanese and Chinese; the
test engine has no such font, so goldens of lib/l10n/app_ja.arb and
app_zh.arb would show empty boxes. This keeps just the glyphs those two files
(and ASCII) use, from the system's Noto Sans CJK (SIL OFL 1.1), into
test/fixtures/fonts/. Re-run it after translations add characters:

    python3 -m venv /tmp/fonttools-venv && /tmp/fonttools-venv/bin/pip install fonttools
    /tmp/fonttools-venv/bin/python tool/l10n/subset_cjk_fixtures.py \\
        [/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc]

The regional face matters: Han characters differ between Japanese (JP) and
Simplified Chinese (SC), so each language gets its own subset.
"""

import json
from pathlib import Path
import sys

from fontTools import subset
from fontTools.ttLib import TTCollection

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(sys.argv[1] if len(sys.argv) > 1 else "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc")
FACES = {"ja": "Noto Sans CJK JP", "zh": "Noto Sans CJK SC"}
OUTPUT = {"ja": "NotoSansCJKjp-Regular-subset.otf", "zh": "NotoSansCJKsc-Regular-subset.otf"}
EXTRA = "".join(chr(c) for c in range(0x20, 0x7F)) + "·…—–“”‘’«»¿¡•×→←↑↓✓"


def main():
    collection = TTCollection(str(SOURCE))
    for language, family in FACES.items():
        arb = json.loads((ROOT / f"lib/l10n/app_{language}.arb").read_text(encoding="utf-8"))
        text = "".join(v for k, v in arb.items() if not k.startswith("@") and isinstance(v, str)) + EXTRA
        font = next(f for f in collection.fonts if f["name"].getDebugName(1) == family)
        options = subset.Options()
        options.layout_features = []
        options.hinting = False
        options.glyph_names = False
        options.notdef_outline = True
        options.name_IDs = [1, 2, 3, 4, 6]
        options.drop_tables += ["DSIG"]
        subsetter = subset.Subsetter(options)
        subsetter.populate(text=text)
        subsetter.subset(font)
        target = ROOT / "test/fixtures/fonts" / OUTPUT[language]
        font.flavor = None
        font.save(target)
        print(f"{target.relative_to(ROOT)}: {target.stat().st_size} bytes, {len(set(text))} characters")


if __name__ == "__main__":
    main()
