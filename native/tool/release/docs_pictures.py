#!/usr/bin/env python3
"""Report stale, current, frozen, and unlisted first-run docs pictures (B-604)."""

from pathlib import Path
import posixpath
import sys

LEDGER = "docs/native/qa/s31-app/PICTURES.tsv"
PICTURES_DIR = "docs/native/qa/s31-app"


def load_rows(ledger_path: Path) -> list[tuple[str, str, str]]:
    rows: list[tuple[str, str, str]] = []
    text = ledger_path.read_text(encoding="utf-8")
    for lineno, raw in enumerate(text.splitlines(), start=1):
        if not raw.strip() or raw.startswith("#"):
            continue
        parts = raw.split("\t")
        if len(parts) != 3:
            print(
                f"{LEDGER}:{lineno}: expected 3 tab-separated columns, got {len(parts)}",
                file=sys.stderr,
            )
            sys.exit(2)
        rows.append((parts[0], parts[1], parts[2]))
    return rows


def main(argv: list[str]) -> int:
    if len(argv) != 3 or not argv[1] or not argv[2]:
        print(f"usage: {argv[0]} <root> <versionName>", file=sys.stderr)
        return 2

    root = Path(argv[1]).resolve()
    version = argv[2]
    ledger_path = root / LEDGER
    if not ledger_path.is_file():
        print(f"no ledger at {LEDGER}", file=sys.stderr)
        return 2

    rows = load_rows(ledger_path)
    listed = {pic for pic, _, _ in rows}

    current = 0
    stale = 0
    not_redrawn = 0
    unlisted = 0

    for pic, drawn_for, way in rows:
        if way.startswith("none:"):
            reason = way[len("none:") :].strip()
            print(f"NOT REDRAWN  {pic}: {reason}")
            not_redrawn += 1
        elif drawn_for == version:
            print(f"current      {pic}")
            current += 1
        else:
            drawn_label = "an unknown release" if drawn_for == "-" else drawn_for
            pic_dir = posixpath.dirname(pic)
            out_dir = (root / pic_dir).as_posix()
            print(
                f"STALE        {pic} (drawn for {drawn_label}): "
                f"cd native && OC_S31_SHOTS={out_dir} ./gradlew {way}, "
                f"then set its row to {version}"
            )
            stale += 1

    pics_root = root / PICTURES_DIR
    on_disk = sorted(
        p.relative_to(root).as_posix()
        for p in pics_root.rglob("*.png")
        if p.is_file()
    )
    for pic in on_disk:
        if pic not in listed:
            print(f"UNLISTED     {pic}: add a row to {LEDGER}")
            unlisted += 1

    print(f"{current} current, {stale} stale, {not_redrawn} not redrawn, {unlisted} unlisted")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
