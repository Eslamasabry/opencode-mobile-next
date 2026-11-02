#!/usr/bin/env python3
"""Preserve a Git bundle repair artifact before any cleanup happens.

The repair runner executes workers in a shared Git worktree and then removes
it. Bundles that workers leave under the worktree (for example under
``native/build``) are deleted with it. This tool verifies a finished bundle
and copies it into an archive directory OUTSIDE the worktree, together with a
deterministic JSON receipt, so the repair result survives worktree removal.

It is standalone (stdlib only) and read-only towards Git: the only git
commands it runs inspect bundle verification, heads, and the source worktree. It
never deletes a worktree, never runs clean/reset/stash, never updates any
ref, never pushes, and never writes Git configuration. Only its own temporary
files are removed.

Usage:
    python3 preserve-repair.py \
        --repository  <repo that can resolve the bundle's prerequisites> \
        --bundle      <bundle file to preserve> \
        --archive-dir <directory outside the worktree> \
        [--worktree   <worktree the bundle was produced in>]

Exit codes:
    0  preserved (or already preserved: safe to re-run)
    1  refused (missing/corrupt bundle, unresolvable prerequisites, archive
       nested in the worktree, archive path not a directory, or an existing
       destination file with different bytes)
    2  usage error (argparse)

On success the receipt JSON is printed to stdout and written next to the
archived bundle as ``<stem>-<sha256>.receipt.json``. The receipt is a pure
function of the preserved bytes and the given paths: re-running the same
command produces byte-identical output and does not touch existing files.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

SCHEMA = "preserve-repair-receipt/1"
_COPY_CHUNK = 1 << 20
_HEADER_LIMIT = 1 << 20
_VERIFY_DETAIL_LIMIT = 400


class Refused(Exception):
    """A safety or validation rule blocked preservation."""


def _canonical_json(obj: object) -> str:
    return json.dumps(obj, indent=2, sort_keys=True, ensure_ascii=False) + "\n"


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        while True:
            block = handle.read(_COPY_CHUNK)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def _fsync_directory(path: Path) -> None:
    """Require durable directory entries before reporting success."""
    flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
    fd = os.open(str(path), flags)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def _atomic_publish_with(destination: Path, writer) -> None:
    """Publish a complete, synced file without replacing another writer."""
    fd, tmp_name = tempfile.mkstemp(
        dir=str(destination.parent), prefix=".preserve-tmp-", suffix=".part"
    )
    os.close(fd)
    tmp_path = Path(tmp_name)
    try:
        writer(tmp_path)
        try:
            os.link(tmp_path, destination)
        except FileExistsError:
            if (destination.is_symlink() or not destination.is_file()
                    or _sha256_file(destination) != _sha256_file(tmp_path)):
                raise Refused(
                    f"destination {destination} exists with different content; "
                    "refusing to clobber it"
                )
        _fsync_directory(destination.parent)
    finally:
        tmp_path.unlink(missing_ok=True)


def _atomic_copy(source: Path, destination: Path) -> None:
    def writer(tmp: Path) -> None:
        with open(source, "rb") as src, open(tmp, "wb") as dst:
            while True:
                block = src.read(_COPY_CHUNK)
                if not block:
                    break
                dst.write(block)
            dst.flush()
            os.fsync(dst.fileno())

    _atomic_publish_with(destination, writer)


def _atomic_write_bytes(destination: Path, payload: bytes) -> None:
    def writer(tmp: Path) -> None:
        with open(tmp, "wb") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())

    _atomic_publish_with(destination, writer)


def _run_git(args: list, cwd: Path) -> subprocess.CompletedProcess:
    """Run git read-only. Per-invocation ``safe.directory`` only; no config
    files are read or written beyond what git itself requires, and nothing is
    committed, merged or pushed."""
    env = dict(os.environ)
    env["GIT_TERMINAL_PROMPT"] = "0"
    command = ["git", "-c", "safe.directory=" + str(cwd), *args]
    return subprocess.run(
        command,
        cwd=str(cwd),
        env=env,
        capture_output=True,
        text=True,
        errors="replace",
    )


def _read_bundle_header_lines(path: Path) -> list:
    """Return the textual header lines of a bundle file (before the blank
    line that separates them from the binary pack)."""
    lines = []
    with open(path, "rb") as handle:
        while handle.tell() <= _HEADER_LIMIT:
            raw = handle.readline(65536)
            if not raw:
                break
            if raw.strip(b"\r\n") == b"":
                break
            lines.append(raw.rstrip(b"\r\n"))
    return lines


def _parse_prerequisites(path: Path) -> list:
    """Best-effort extraction of required prerequisite commits.

    Bundle headers list prerequisites as ``-<object name> <note>`` lines.
    ``git bundle verify`` remains the authority on whether a repository can
    actually resolve them.
    """
    prerequisites = []
    try:
        for raw in _read_bundle_header_lines(path):
            if not raw.startswith(b"-"):
                continue
            text = raw[1:].decode("utf-8", "replace").strip()
            if not text:
                continue
            parts = text.split(None, 1)
            entry = {"sha": parts[0]}
            if len(parts) > 1:
                entry["note"] = parts[1]
            prerequisites.append(entry)
    except OSError:
        return []
    return prerequisites


def _parse_heads(list_heads_stdout: str) -> list:
    """Parse ``git bundle list-heads`` output: one ``<sha> <ref>`` per line."""
    heads = []
    for line in list_heads_stdout.splitlines():
        parts = line.split(None, 1)
        if len(parts) == 2 and len(parts[0]) in (40, 64) and all(
            ch in "0123456789abcdefABCDEF" for ch in parts[0]
        ):
            heads.append({"sha": parts[0].lower(), "ref": parts[1].strip()})
    return heads


def _shorten(detail: str) -> str:
    detail = " ".join(detail.split())
    if len(detail) > _VERIFY_DETAIL_LIMIT:
        detail = detail[:_VERIFY_DETAIL_LIMIT] + " ...[truncated]"
    return detail


def _validated_receipt(path: Path, expected: dict) -> dict:
    """The first valid provenance record wins; never invent a replacement."""
    try:
        if path.is_symlink():
            raise ValueError("receipt is a symlink")
        stored = json.loads(path.read_text(encoding="utf-8"))
        valid = (
            stored["schema"] == SCHEMA
            and stored["receipt_path"] == str(path)
            and all(stored["bundle"][key] == expected["bundle"][key]
                    for key in ("sha256", "size_bytes", "destination", "original_name"))
            and stored["verification"]["exit_code"] == 0
            and stored["verification"]["command"] == "git bundle verify"
            and isinstance(stored["verification"]["repository"], str)
            and isinstance(stored["bundle"]["source"], str)
            and stored["heads"] == expected["heads"]
            and stored["prerequisites"] == expected["prerequisites"]
        )
        if not valid:
            raise ValueError("receipt contradicts verified artifact")
    except (OSError, ValueError, KeyError, TypeError) as exc:
        raise Refused(f"existing receipt {path} is invalid or contradictory") from exc
    _fsync_directory(path.parent)
    return stored


def preserve(repository: str, bundle: str, archive_dir: str, worktree: str | None) -> dict:
    """Verify and publish one immutable snapshot, then its durable receipt."""
    bundle_path = Path(bundle).expanduser().resolve()
    if not bundle_path.is_file():
        raise Refused(f"bundle file not found: {bundle_path}")
    repository_path = Path(repository).expanduser().resolve()
    if not repository_path.is_dir():
        raise Refused(f"repository directory not found: {repository_path}")
    worktree_path = Path(worktree).expanduser().resolve() if worktree else None
    if worktree_path is None:
        detected = _run_git(["rev-parse", "--show-toplevel"], bundle_path.parent)
        if detected.returncode == 0:
            worktree_path = Path(detected.stdout.strip()).resolve()
    archive_path = Path(archive_dir).expanduser().resolve()
    if worktree_path is not None and archive_path.is_relative_to(worktree_path):
        raise Refused(
            f"archive directory {archive_path} is inside the worktree "
            f"{worktree_path}; archived artifacts must survive worktree removal"
        )
    if archive_path.exists() and not archive_path.is_dir():
        raise Refused(f"archive path is not a directory: {archive_path}")
    archive_path.mkdir(parents=True, exist_ok=True)

    fd, name = tempfile.mkstemp(dir=archive_path, prefix=".verify-", suffix=".part")
    snapshot = Path(name)
    try:
        # Never reopen the mutable source for verification, metadata, or publication.
        with os.fdopen(fd, "wb") as output, bundle_path.open("rb") as source:
            while block := source.read(_COPY_CHUNK):
                output.write(block)
            output.flush()
            os.fsync(output.fileno())
        digest = _sha256_file(snapshot)
        size = snapshot.stat().st_size
        verify = _run_git(["bundle", "verify", str(snapshot)], repository_path)
        if verify.returncode != 0:
            detail = _shorten(verify.stderr or verify.stdout or "no diagnostics")
            raise Refused(f"git bundle verify failed with exit code {verify.returncode}: {detail}")
        listed = _run_git(["bundle", "list-heads", str(snapshot)], repository_path)
        if listed.returncode != 0:
            raise Refused("git bundle list-heads failed")
        heads = _parse_heads(listed.stdout)
        if not heads:
            raise Refused("verified bundle contains no recoverable heads")
        prerequisites = _parse_prerequisites(snapshot)
        destination = archive_path / f"{bundle_path.stem}-{digest}.bundle"
        _atomic_copy(snapshot, destination)
        receipt_path = archive_path / f"{bundle_path.stem}-{digest}.receipt.json"
        receipt = {
            "schema": SCHEMA,
            "bundle": {
                "sha256": digest, "size_bytes": size,
                "source": str(bundle_path), "destination": str(destination),
                "original_name": bundle_path.name,
            },
            "verification": {
                "repository": str(repository_path),
                "command": "git bundle verify", "exit_code": verify.returncode,
            },
            "heads": heads, "prerequisites": prerequisites,
            "worktree": str(worktree_path) if worktree_path else None,
            "receipt_path": str(receipt_path),
        }
        if receipt_path.exists() or receipt_path.is_symlink():
            return _validated_receipt(receipt_path, receipt)
        try:
            _atomic_write_bytes(receipt_path, _canonical_json(receipt).encode("utf-8"))
        except Refused:
            # A racing writer may have installed valid original provenance.
            if receipt_path.exists() or receipt_path.is_symlink():
                return _validated_receipt(receipt_path, receipt)
            raise
        return _validated_receipt(receipt_path, receipt)
    finally:
        snapshot.unlink(missing_ok=True)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        prog="preserve-repair.py",
        description=(
            "Verify a Git bundle with git bundle verify and copy it, atomically "
            "and content-addressed, into an archive directory outside the "
            "worktree, with a deterministic JSON receipt. Never modifies any "
            "Git repository and never deletes anything."
        ),
    )
    parser.add_argument(
        "--repository",
        required=True,
        metavar="DIR",
        help="git repository able to resolve the bundle's prerequisites "
        "(git bundle verify runs here)",
    )
    parser.add_argument(
        "--bundle",
        required=True,
        metavar="FILE",
        help="bundle file to preserve; relative paths are resolved",
    )
    parser.add_argument(
        "--archive-dir",
        required=True,
        metavar="DIR",
        help="archive directory; must be outside --worktree when --worktree "
        "is given (created if missing)",
    )
    parser.add_argument(
        "--worktree",
        metavar="DIR",
        help="worktree the bundle was produced in; its removal must not be "
        "able to delete the archive",
    )
    args = parser.parse_args(argv)

    try:
        receipt = preserve(args.repository, args.bundle, args.archive_dir, args.worktree)
    except Refused as exc:
        print(f"preserve-repair: refusing: {exc}", file=sys.stderr)
        return 1
    except OSError as exc:
        print(f"preserve-repair: error: {exc}", file=sys.stderr)
        return 1

    sys.stdout.write(_canonical_json(receipt))
    return 0


if __name__ == "__main__":
    sys.exit(main())
