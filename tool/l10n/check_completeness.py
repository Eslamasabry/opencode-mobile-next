#!/usr/bin/env python3
"""Reject missing Arabic ARB messages, with exact documented exceptions only."""

import argparse
import json
from pathlib import Path
import sys


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


def check(source, target, allowlist, todo):
    source_keys = message_keys(source)
    target_keys = message_keys(target)
    config = read_object(allowlist)
    if set(config) != {"source_document", "missing_arabic_keys"}:
        raise InvalidInput("Allowlist needs source_document and missing_arabic_keys only")
    if config["source_document"] != "docs/localization-todo.md":
        raise InvalidInput("Allowlist must cite docs/localization-todo.md")
    exceptions = config["missing_arabic_keys"]
    if not isinstance(exceptions, dict):
        raise InvalidInput("missing_arabic_keys must map exact keys to reasons")
    try:
        documented = todo.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        raise InvalidInput("Cannot read localization-todo document") from error
    for key, reason in exceptions.items():
        if key not in source_keys:
            raise InvalidInput(f"Allowlist key is absent from English: {key}")
        if key in target_keys:
            raise InvalidInput(f"Remove translated allowlist key: {key}")
        if not isinstance(reason, str) or not reason.strip():
            raise InvalidInput(f"Allowlist key needs a reason: {key}")
        if f"`{key}`" not in documented:
            raise InvalidInput(f"Document the exact allowlist key in localization-todo: {key}")
    missing = sorted(source_keys - target_keys - exceptions.keys())
    return source_keys, target_keys, exceptions, missing


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=root / "lib/l10n/app_en.arb")
    parser.add_argument("--target", type=Path, default=root / "lib/l10n/app_ar.arb")
    parser.add_argument("--allowlist", type=Path, default=root / "tool/l10n/missing_arabic_allowlist.json")
    parser.add_argument("--todo", type=Path, default=root / "docs/localization-todo.md")
    args = parser.parse_args()
    try:
        source, target, exceptions, missing = check(args.source, args.target, args.allowlist, args.todo)
    except InvalidInput as error:
        print(f"Localization completeness: invalid input: {error}", file=sys.stderr)
        return 2
    print(f"Localization completeness: {len(source)} English, {len(target)} Arabic, "
          f"{len(exceptions)} documented exceptions, {len(missing)} missing")
    if missing:
        for key in missing[:25]:
            print(f"  {key}")
        if len(missing) > 25:
            print(f"  ... {len(missing) - 25} more missing keys")
        print("Add the missing Arabic messages; exceptions require exact keys and reasons "
              "in tool/l10n/missing_arabic_allowlist.json and docs/localization-todo.md.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
