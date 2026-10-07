#!/usr/bin/env python3
"""Prune resolved Arabic translation debt; refuse additions before any write."""

import argparse
import os
from pathlib import Path
import sys
import tempfile

from check_completeness import BASELINE_HEADER, InvalidInput, check, print_keys


def update(source, target, baseline):
    source_keys, target_keys, debt, new_missing, stale = check(source, target, baseline)
    if new_missing:
        print_keys("Refusing baseline growth; translate these new missing keys", new_missing)
        return 1
    remaining = source_keys - target_keys
    content = BASELINE_HEADER + "".join(f"{key}\n" for key in sorted(remaining))
    if stale:
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="w", encoding="utf-8", dir=baseline.parent,
                prefix=".arabic_missing_", delete=False,
            ) as stream:
                temporary = Path(stream.name)
                stream.write(content)
                stream.flush()
                os.fsync(stream.fileno())
            temporary.replace(baseline)
        finally:
            if temporary is not None and temporary.exists():
                temporary.unlink()
    print(f"Arabic baseline: {len(debt)} -> {len(remaining)} missing keys; {len(stale)} pruned")
    return 0


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=root / "lib/l10n/app_en.arb")
    parser.add_argument("--target", type=Path, default=root / "lib/l10n/app_ar.arb")
    parser.add_argument("--baseline", type=Path, default=root / "tool/l10n/arabic_missing_baseline.txt")
    args = parser.parse_args()
    try:
        return update(args.source, args.target, args.baseline)
    except (InvalidInput, OSError) as error:
        detail = str(error) if isinstance(error, InvalidInput) else type(error).__name__
        print(f"Arabic baseline: invalid input: {detail}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
