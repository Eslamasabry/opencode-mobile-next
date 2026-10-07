#!/usr/bin/env python3
"""Reject new Arabic translation debt and stale entries in its shrinking baseline."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys


BASELINE_HEADER = (
    "# Arabic missing-message debt baseline.\n"
    "# This baseline may only shrink; translate new English keys in the same change.\n"
    "# Prune translated or removed keys with tool/l10n/update_arabic_baseline.py.\n"
)
BASELINE_REPO_PATH = "tool/l10n/arabic_missing_baseline.txt"


class InvalidInput(ValueError):
    pass


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise InvalidInput(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def read_object(path):
    try:
        value = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)
    except (OSError, UnicodeError, ValueError) as error:
        # Never dump JSON values into CI logs.
        if isinstance(error, InvalidInput):
            detail = str(error)
        else:
            detail = type(error).__name__
        raise InvalidInput(f"Cannot read {path.name}: {detail}") from error
    if not isinstance(value, dict):
        raise InvalidInput(f"{path.name} must contain a JSON object")
    return value


def message_keys(path):
    data = read_object(path)
    messages = {key for key in data if not key.startswith("@")}
    if not messages:
        raise InvalidInput(f"{path.name} contains no message keys")
    for key in sorted(messages):
        if not isinstance(data[key], str) or not data[key].strip():
            raise InvalidInput(f"{path.name}: {key} needs a nonempty message")
    return messages


def read_baseline(path):
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        raise InvalidInput(f"Cannot read {path.name}: {type(error).__name__}") from error
    return parse_baseline(text)


def parse_baseline(text):
    if not text.startswith(BASELINE_HEADER):
        raise InvalidInput("Baseline needs the shrinking-debt policy header")
    keys = []
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", line) is None:
            raise InvalidInput("Baseline entries must be exact ARB message keys")
        keys.append(line)
    if len(keys) != len(set(keys)):
        raise InvalidInput("Baseline contains duplicate keys")
    if keys != sorted(keys):
        raise InvalidInput("Baseline keys must be sorted")
    return set(keys)


def baseline_growth(debt, base_ref):
    # Verify to a commit ID first: neither ref text nor paths are shell code.
    revision = subprocess.run(
        ["git", "rev-parse", "--verify", "--end-of-options", f"{base_ref}^{{commit}}"],
        capture_output=True, text=True, check=False,
    )
    if revision.returncode:
        raise InvalidInput("Cannot verify baseline base reference as a commit")
    commit = revision.stdout.strip()
    if re.fullmatch(r"[0-9a-f]{40}|[0-9a-f]{64}", commit) is None:
        raise InvalidInput("Baseline base reference did not resolve to a commit ID")
    listing = subprocess.run(
        ["git", "ls-tree", "--name-only", commit, "--", BASELINE_REPO_PATH],
        capture_output=True, text=True, check=False,
    )
    if listing.returncode:
        raise InvalidInput("Cannot inspect base revision's baseline")
    if not listing.stdout.strip():
        print("Base revision has no Arabic baseline; validating the approved initial introduction.")
        return []
    previous = subprocess.run(
        ["git", "show", f"{commit}:{BASELINE_REPO_PATH}"],
        capture_output=True, text=True, check=False,
    )
    if previous.returncode:
        raise InvalidInput("Cannot read base revision's baseline")
    return sorted(debt - parse_baseline(previous.stdout))


def check(source, target, baseline):
    source_keys = message_keys(source)
    target_keys = message_keys(target)
    debt = read_baseline(baseline)
    missing = source_keys - target_keys
    new_missing = sorted(missing - debt)
    stale = sorted(debt - missing)
    return source_keys, target_keys, debt, new_missing, stale


def print_keys(label, keys):
    if not keys:
        return
    print(f"{label}:")
    for key in keys[:25]:
        print(f"  {key}")
    if len(keys) > 25:
        print(f"  ... {len(keys) - 25} more keys")


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=root / "lib/l10n/app_en.arb")
    parser.add_argument("--target", type=Path, default=root / "lib/l10n/app_ar.arb")
    parser.add_argument("--baseline", type=Path, default=root / "tool/l10n/arabic_missing_baseline.txt")
    parser.add_argument("--base-ref", help="Git commit/ref for CI's baseline-may-only-shrink check")
    args = parser.parse_args()
    try:
        source, target, debt, new_missing, stale = check(args.source, args.target, args.baseline)
        growth = baseline_growth(debt, args.base_ref) if args.base_ref is not None else []
    except (InvalidInput, OSError, UnicodeError) as error:
        detail = str(error) if isinstance(error, InvalidInput) else type(error).__name__
        print(f"Localization completeness: invalid input: {detail}", file=sys.stderr)
        return 2
    print(f"Localization completeness: {len(source)} English, {len(target)} Arabic, "
          f"{len(debt)} baseline debt, {len(new_missing)} new missing, {len(stale)} stale baseline")
    if new_missing or stale or growth:
        print_keys("Add Arabic messages for new missing keys", new_missing)
        print_keys("Prune translated or removed baseline keys", stale)
        print_keys("Baseline may not grow compared with the base revision", growth)
        if stale:
            print("Run python3 tool/l10n/update_arabic_baseline.py after new missing keys are translated.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
