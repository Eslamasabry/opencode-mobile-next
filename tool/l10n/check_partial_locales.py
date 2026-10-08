#!/usr/bin/env python3
"""Check the partly translated locales (es, ja, pt, ru, zh) against English.

These languages carry the first-run and chat-core messages only
(docs/l10n/fg5-key-set.md); every other message falls back to English at run
time, so unlike Arabic they are not required to translate each new English
key. What this gate guarantees instead:

* every key in tool/l10n/partial_locales_core_keys.txt is translated in every
  one of the locales (the committed "core" set);
* that set may only grow: with --base-ref, a key the base revision listed may
  not leave the file;
* no locale has a key English lacks, an empty or non-text message, a
  duplicate key, or a wrong @@locale;
* every translated message keeps English's ICU arguments and their
  plural/select structure, so a placeholder cannot be lost or renamed.

Output has counts and at most 25 key names per category, never message text.
"""

import argparse
from pathlib import Path
import re
import subprocess
import sys

from check_completeness import InvalidInput, message_keys, read_object

LOCALES = ("es", "ja", "pt", "ru", "zh")
CORE_REPO_PATH = "tool/l10n/partial_locales_core_keys.txt"
CORE_HEADER = (
    "# Core messages every partly translated locale (es, ja, pt, ru, zh) must carry.\n"
    "# This set may only grow: translate a message in all five before adding it here.\n"
    "# Source and reasons: docs/l10n/fg5-key-set.md. Checked by tool/l10n/check_partial_locales.py.\n"
)

_ARGUMENT = re.compile(r"\s*([A-Za-z_][A-Za-z0-9_]*)\s*(?:,\s*(plural|select)\s*,(.*))?$", re.S)
_CASE = re.compile(r"\s*([^\s{]+)\s*\{")


def _closing(text, start):
    """Index just past the brace that closes the one at text[start]."""
    depth = 0
    for index in range(start, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return index + 1
    return -1


def icu_shape(message):
    """(arguments, structure, problem) of an ICU message.

    arguments is the set of argument names anywhere in the message; structure
    the sorted list of (name, plural|select) found; problem a short reason
    when the braces or a plural/select body are malformed, else None.
    """
    names, structure = set(), []

    def walk(text):
        position = 0
        while position < len(text):
            char = text[position]
            if char == "}":
                return "unbalanced braces"
            if char != "{":
                position += 1
                continue
            end = _closing(text, position)
            if end < 0:
                return "unbalanced braces"
            argument = _ARGUMENT.match(text[position + 1 : end - 1])
            if argument is None:
                return "malformed argument"
            names.add(argument.group(1))
            if argument.group(2):
                kind, body = argument.group(2), argument.group(3)
                structure.append((argument.group(1), kind))
                cases, cursor = [], 0
                while True:
                    case = _CASE.match(body, cursor)
                    if case is None:
                        break
                    open_at = case.end() - 1
                    close = _closing(body, open_at)
                    if close < 0:
                        return "unbalanced braces"
                    cases.append(case.group(1))
                    problem = walk(body[open_at + 1 : close - 1])
                    if problem:
                        return problem
                    cursor = close
                if body[cursor:].strip():
                    return f"unexpected text in {kind}"
                if "other" not in cases:
                    return f"{kind} without other"
            position = end
        return None

    problem = walk(message)
    return names, sorted(structure), problem


def read_core(path):
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        raise InvalidInput(f"Cannot read {path.name}: {type(error).__name__}") from error
    return parse_core(text)


def parse_core(text):
    if not text.startswith(CORE_HEADER):
        raise InvalidInput("Core key list needs its policy header")
    keys = []
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", line) is None:
            raise InvalidInput("Core entries must be exact ARB message keys")
        keys.append(line)
    if len(keys) != len(set(keys)):
        raise InvalidInput("Core key list contains duplicate keys")
    if keys != sorted(keys):
        raise InvalidInput("Core key list must be sorted")
    return set(keys)


def core_shrink(core, base_ref):
    """Keys the base revision's core list had that this one lost."""
    revision = subprocess.run(
        ["git", "rev-parse", "--verify", "--end-of-options", f"{base_ref}^{{commit}}"],
        capture_output=True, text=True, check=False,
    )
    if revision.returncode:
        raise InvalidInput("Cannot verify core-list base reference as a commit")
    commit = revision.stdout.strip()
    if re.fullmatch(r"[0-9a-f]{40}|[0-9a-f]{64}", commit) is None:
        raise InvalidInput("Core-list base reference did not resolve to a commit ID")
    listing = subprocess.run(
        ["git", "ls-tree", "--name-only", commit, "--", CORE_REPO_PATH],
        capture_output=True, text=True, check=False,
    )
    if listing.returncode:
        raise InvalidInput("Cannot inspect base revision's core list")
    if not listing.stdout.strip():
        print("Base revision has no core list; validating the introduction.")
        return []
    previous = subprocess.run(
        ["git", "show", f"{commit}:{CORE_REPO_PATH}"],
        capture_output=True, text=True, check=False,
    )
    if previous.returncode:
        raise InvalidInput("Cannot read base revision's core list")
    return sorted(parse_core(previous.stdout) - core)


def check_locale(english, locale_file, locale, core):
    """(problems by category, translated key count) for one locale ARB."""
    data = read_object(locale_file)
    problems = {}

    def add(category, key):
        problems.setdefault(category, []).append(key)

    if data.get("@@locale") != locale:
        add("wrong @@locale", "@@locale")
    messages = message_keys(locale_file)  # also rejects empty / non-text values
    for key in sorted(messages):
        if key not in english:
            add("key English does not have", key)
            continue
        names, structure, problem = icu_shape(data[key])
        e_names, e_structure, _ = icu_shape(english[key])
        if problem:
            add(f"malformed message ({problem})", key)
        elif names != e_names:
            add("arguments differ from English", key)
        elif structure != e_structure:
            add("plural/select differs from English", key)
    for key in sorted(core - messages):
        add("core key not translated", key)
    return problems, len(messages)


def print_keys(label, keys):
    print(f"{label}:")
    for key in keys[:25]:
        print(f"  {key}")
    if len(keys) > 25:
        print(f"  ... {len(keys) - 25} more keys")


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--l10n-dir", type=Path, default=root / "lib/l10n")
    parser.add_argument("--core", type=Path, default=root / CORE_REPO_PATH)
    parser.add_argument("--base-ref", help="Git commit/ref for the core-list-may-only-grow check")
    args = parser.parse_args()
    try:
        english_data = read_object(args.l10n_dir / "app_en.arb")
        english = {k: v for k, v in english_data.items() if not k.startswith("@") and isinstance(v, str)}
        core = read_core(args.core)
        missing_from_english = sorted(core - set(english))
        shrink = core_shrink(core, args.base_ref) if args.base_ref is not None else []
        failed = bool(missing_from_english or shrink)
        if missing_from_english:
            print_keys("Core keys English does not have", missing_from_english)
        if shrink:
            print_keys("Core list may not shrink compared with the base revision", shrink)
        for locale in LOCALES:
            problems, translated = check_locale(english, args.l10n_dir / f"app_{locale}.arb", locale, core)
            share = 100 * translated / len(english)
            print(
                f"Partial locale {locale}: {translated} of {len(english)} messages "
                f"({share:.1f} %), {len(english) - translated} fall back to English, "
                f"core {len(core)}, {sum(len(v) for v in problems.values())} problems"
            )
            for category, keys in sorted(problems.items()):
                print_keys(f"  {locale}: {category}", keys)
                failed = True
    except (InvalidInput, OSError, UnicodeError) as error:
        detail = str(error) if isinstance(error, InvalidInput) else type(error).__name__
        print(f"Partial locale check: invalid input: {detail}", file=sys.stderr)
        return 2
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
