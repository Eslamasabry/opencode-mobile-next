#!/usr/bin/env python3
"""Two-layer release notes: "What changed for you", then the technical list.

  python3 tool/release/release_notes.py new 1.3.0+53 [--since v1.2.0+52]
      Writes docs/releases/v1.3.0+53.md from docs/releases/TEMPLATE.md and
      drafts the technical list from the commits since the last release tag.
  python3 tool/release/release_notes.py check docs/releases/v1.3.0+53.md
      Fails unless the notes have both layers, in that order, and the top
      layer is plain words short enough for a store changelog.
  python3 tool/release/release_notes.py store docs/releases/v1.3.0+53.md [--write]
      Prints the store changelog made from "What changed for you"; --write
      saves it as fastlane/metadata/android/en-US/changelogs/<build>.txt.

scripts/release.sh runs `check` before a release or sideload build. Nothing
here publishes, tags or talks to GitHub.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = ROOT / "docs" / "releases" / "TEMPLATE.md"
USER_HEADING = "## What changed for you"
TECH_HEADING = "## Technical changes"
STORE_LIMIT = 500
MIN_BULLETS = 2
MAX_BULLETS = 5
VERSION_RE = re.compile(r"^\d+\.\d+\.\d+\+[1-9]\d*$")

# Words that belong in the technical list, not in what a user reads first.
INTERNAL_WORDS = (
    "refactor",
    "gateway",
    "controller",
    "endpoint",
    "sse",
    "riverpod",
    "widget test",
    "codepath",
)
INTERNAL_PATTERNS = (
    (re.compile(r"`"), "code formatting (backticks)"),
    (re.compile(r"\b[\w/]+\.(dart|kt|py|sh|yml|yaml|json)\b"), "a file name"),
    (re.compile(r"\b(lib|test|tool|android)/"), "a source path"),
    (re.compile(r"\b[0-9a-f]{7,40}\b"), "a commit hash"),
)

COMMIT_SECTIONS = {
    "feat": "Added",
    "fix": "Fixed",
    "perf": "Changed",
}
COMMIT_RE = re.compile(r"^(feat|fix|perf)(\([^)]*\))?!?:\s*(.+)$")


@dataclass
class Notes:
    title: str
    user: list[str] = field(default_factory=list)
    user_index: int = -1
    tech_index: int = -1
    text: str = ""


def _strip_comments(text: str) -> str:
    return re.sub(r"<!--.*?-->", "", text, flags=re.S)


def parse(text: str) -> Notes:
    clean = _strip_comments(text)
    lines = clean.splitlines()
    notes = Notes(title=lines[0].strip() if lines else "", text=clean)
    section = None
    for index, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("## "):
            section = stripped
            if stripped == USER_HEADING and notes.user_index < 0:
                notes.user_index = index
            elif stripped == TECH_HEADING and notes.tech_index < 0:
                notes.tech_index = index
            continue
        if section == USER_HEADING and stripped.startswith("- "):
            notes.user.append(stripped[2:].strip())
        elif section == USER_HEADING and notes.user and line.startswith("  "):
            # A wrapped bullet continues on an indented line.
            notes.user[-1] += " " + stripped
    return notes


def plain(bullet: str) -> str:
    """A bullet as a store shows it: no Markdown emphasis or links."""
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", bullet)
    text = re.sub(r"\*(.+?)\*", r"\1", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    return text.strip()


def store_text(notes: Notes) -> str:
    return "\n".join(f"• {plain(bullet)}" for bullet in notes.user)


def check(path: Path) -> list[str]:
    """Every problem with the notes at [path]; empty when they pass."""
    problems: list[str] = []
    if not path.is_file():
        return [f"{path} does not exist"]
    notes = parse(path.read_text(encoding="utf-8"))
    match = re.fullmatch(r"v(.+)\.md", path.name)
    if match and VERSION_RE.match(match.group(1)):
        expected = f"# OpenCode Mobile {match.group(1)}"
        if notes.title != expected:
            problems.append(f"the first line must be '{expected}'")
    if notes.user_index < 0:
        problems.append(f"missing '{USER_HEADING}'")
    if notes.tech_index < 0:
        problems.append(f"missing '{TECH_HEADING}'")
    if 0 <= notes.tech_index < notes.user_index:
        problems.append(f"'{USER_HEADING}' must come before '{TECH_HEADING}'")
    if notes.user_index >= 0 and not (
        MIN_BULLETS <= len(notes.user) <= MAX_BULLETS
    ):
        problems.append(
            f"'{USER_HEADING}' needs {MIN_BULLETS} to {MAX_BULLETS} bullets, "
            f"found {len(notes.user)}"
        )
    for bullet in notes.user:
        lowered = bullet.lower()
        for word in INTERNAL_WORDS:
            if re.search(rf"\b{re.escape(word)}\b", lowered):
                problems.append(f"internal word '{word}' in: {bullet}")
        for pattern, what in INTERNAL_PATTERNS:
            if pattern.search(bullet):
                problems.append(f"{what} in: {bullet}")
    if notes.user:
        length = len(store_text(notes))
        if length > STORE_LIMIT:
            problems.append(
                f"'{USER_HEADING}' is {length} characters as a store "
                f"changelog; keep it under {STORE_LIMIT}"
            )
    # "…" alone is the template's empty bullet; inside a sentence it is text.
    if "X.Y.Z" in notes.text:
        problems.append("template placeholder 'X.Y.Z' left in")
    if re.search(r"^\s*- (\*\*)?…(\*\*)?\s*$", notes.text, flags=re.M):
        problems.append("template placeholder '- …' left in")
    if "One sentence a user would repeat" in notes.text:
        problems.append("template placeholder summary sentence left in")
    return problems


def _git(*args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=ROOT, check=True, capture_output=True, text=True
    ).stdout


def last_tag() -> str | None:
    try:
        return _git("describe", "--tags", "--abbrev=0", "--match", "v*").strip()
    except subprocess.CalledProcessError:
        return None


def draft_technical(subjects: list[str]) -> dict[str, list[str]]:
    """Conventional commit subjects grouped under the template's headings."""
    groups: dict[str, list[str]] = {name: [] for name in ("Added", "Changed", "Fixed")}
    seen: set[str] = set()
    for subject in subjects:
        subject = subject.replace("[skip ci]", "").strip()
        match = COMMIT_RE.match(subject)
        if not match:
            continue
        text = match.group(3).strip()
        text = text[:1].upper() + text[1:]
        if text in seen:
            continue
        seen.add(text)
        groups[COMMIT_SECTIONS[match.group(1)]].append(text)
    return groups


def render_new(version: str, template: str, groups: dict[str, list[str]]) -> str:
    text = template.replace("X.Y.Z+N", version)
    for heading, items in groups.items():
        if not items:
            continue
        listed = "\n".join(f"- {item}" for item in items)
        text = text.replace(f"### {heading}\n- …", f"### {heading}\n{listed}", 1)
    return text


def command_new(args: argparse.Namespace) -> int:
    if not VERSION_RE.match(args.version):
        print(f"version must look like 1.3.0+53, got {args.version}", file=sys.stderr)
        return 64
    target = ROOT / "docs" / "releases" / f"v{args.version}.md"
    if target.exists():
        print(f"{target.relative_to(ROOT)} already exists", file=sys.stderr)
        return 1
    since = args.since or last_tag()
    subjects: list[str] = []
    if since:
        subjects = _git(
            "log", "--no-merges", "--format=%s", f"{since}..HEAD"
        ).splitlines()
    text = render_new(
        args.version, TEMPLATE.read_text(encoding="utf-8"), draft_technical(subjects)
    )
    target.write_text(text, encoding="utf-8")
    print(f"wrote {target.relative_to(ROOT)} (commits since {since or 'the start'})")
    print("next: write 'What changed for you', trim the technical list, then run check")
    return 0


def command_check(args: argparse.Namespace) -> int:
    path = Path(args.file)
    problems = check(path)
    for problem in problems:
        print(f"{path}: {problem}", file=sys.stderr)
    if not problems:
        print(f"{path}: two-layer release notes OK")
    return 1 if problems else 0


def command_store(args: argparse.Namespace) -> int:
    path = Path(args.file)
    problems = check(path)
    if problems:
        for problem in problems:
            print(f"{path}: {problem}", file=sys.stderr)
        return 1
    text = store_text(parse(path.read_text(encoding="utf-8")))
    match = re.fullmatch(r"v\d+\.\d+\.\d+\+([1-9]\d*)\.md", path.name)
    if args.write:
        if not match:
            print("--write needs a file named vX.Y.Z+N.md", file=sys.stderr)
            return 64
        out = (
            ROOT
            / "fastlane/metadata/android/en-US/changelogs"
            / f"{match.group(1)}.txt"
        )
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text + "\n", encoding="utf-8")
        print(f"wrote {out.relative_to(ROOT)}; add the Arabic one by hand")
    else:
        print(text)
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    new = commands.add_parser("new", help="draft notes for a version")
    new.add_argument("version")
    new.add_argument("--since", help="tag or commit to list changes from")
    new.set_defaults(run=command_new)
    checker = commands.add_parser("check", help="validate notes")
    checker.add_argument("file")
    checker.set_defaults(run=command_check)
    store = commands.add_parser("store", help="store changelog from notes")
    store.add_argument("file")
    store.add_argument("--write", action="store_true")
    store.set_defaults(run=command_store)
    args = parser.parse_args(argv)
    return args.run(args)


if __name__ == "__main__":
    sys.exit(main())
