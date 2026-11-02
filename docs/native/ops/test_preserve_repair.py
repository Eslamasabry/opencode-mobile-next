#!/usr/bin/env python3
"""Regression tests for preserve-repair.py.

Each test builds a temporary git fixture: a repository with a ``main``
branch, a linked worktree on a ``fix`` branch, and an incremental bundle
``main..fix`` produced inside the worktree (mirroring bundles left under
``native/build`` that the repair runner used to delete).

Run:
    python3 docs/native/ops/test_preserve_repair.py -v
"""

from __future__ import annotations

import importlib.util
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

TOOL = Path(__file__).resolve().parent / "preserve-repair.py"

_COMMIT_ENV = {
    "GIT_AUTHOR_NAME": "Fixture",
    "GIT_AUTHOR_EMAIL": "fixture@example.invalid",
    "GIT_COMMITTER_NAME": "Fixture",
    "GIT_COMMITTER_EMAIL": "fixture@example.invalid",
}


def _git_env(home: Path) -> dict:
    """Isolated environment for the fixture git commands (no user config)."""
    env = dict(os.environ)
    env.update(
        {
            "HOME": str(home),
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_SYSTEM": os.devnull,
            "GIT_TERMINAL_PROMPT": "0",
        }
    )
    env.update(_COMMIT_ENV)
    return env


class PreserveRepairTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="preserve-repair-test-"))
        self.home = self.tmp / "githome"
        self.home.mkdir()
        self.env = _git_env(self.home)

        self.repo = self.tmp / "repo"
        self._git("init", "-q", "-b", "main", str(self.repo))
        self._commit_in(self.repo, "a.txt", "base commit")

        self.worktree = self.tmp / "wt"
        self._git("-C", str(self.repo), "worktree", "add", str(self.worktree), "-b", "fix")
        (self.worktree / "build").mkdir()
        self._commit_in(self.worktree, "b.txt", "fix commit")

        self.bundle = self.worktree / "build" / "repair.bundle"
        self._git("-C", str(self.worktree), "bundle", "create", str(self.bundle),
                  "main..fix")

        self.archive = self.tmp / "archive-out"
        self.main_sha = self._rev_parse("main")
        self.fix_sha = self._rev_parse("fix")
        self.bundle_sha = hashlib.sha256(self.bundle.read_bytes()).hexdigest()

    def tearDown(self) -> None:
        shutil.rmtree(self.tmp, ignore_errors=True)

    # ---- fixture helpers -------------------------------------------------

    def _git(self, *args: str, cwd: Path | None = None) -> str:
        result = subprocess.run(
            ["git", *args],
            cwd=str(cwd or self.tmp),
            env=self.env,
            capture_output=True,
            text=True,
            errors="replace",
        )
        if result.returncode != 0:
            self.fail(
                f"fixture git command failed: git {' '.join(args)}\n"
                f"exit {result.returncode}\n{result.stdout}{result.stderr}"
            )
        return result.stdout

    def _commit_in(self, repo: Path, filename: str, message: str) -> None:
        (repo / filename).write_text(f"content of {filename}\n", encoding="utf-8")
        self._git("-C", str(repo), "add", filename)
        self._git("-C", str(repo), "commit", "-q", "-m", message)

    def _rev_parse(self, ref: str, repo: Path | None = None) -> str:
        return self._git("-C", str(repo or self.repo), "rev-parse", ref).strip()

    def _run_tool(self, *args: str, cwd: Path | None = None) -> subprocess.CompletedProcess:
        return subprocess.run(
            [sys.executable, str(TOOL), *args],
            cwd=str(cwd) if cwd else None,
            env=self.env,
            capture_output=True,
            text=True,
            errors="replace",
        )

    def _preserve(self, repository: Path | None = None, bundle: Path | None = None,
                  archive: Path | None = None, worktree: Path | None = None,
                  cwd: Path | None = None) -> subprocess.CompletedProcess:
        args = [
            "--repository", str(repository or self.repo),
            "--bundle", str(bundle or self.bundle),
            "--archive-dir", str(archive or self.archive),
        ]
        if worktree is not None:
            args += ["--worktree", str(worktree)]
        return self._run_tool(*args, cwd=cwd)

    def _archived_bundles(self, archive: Path | None = None) -> list:
        base = archive or self.archive
        if not base.is_dir():
            return []
        return sorted(p for p in base.glob("*.bundle"))

    def _archived_receipts(self, archive: Path | None = None) -> list:
        base = archive or self.archive
        if not base.is_dir():
            return []
        return sorted(p for p in base.glob("*.receipt.json"))

    # ---- tests -----------------------------------------------------------

    def test_valid_incremental_bundle_is_preserved_with_prereqs(self) -> None:
        result = self._preserve(worktree=self.worktree)

        self.assertEqual(result.returncode, 0, result.stderr)
        receipt = json.loads(result.stdout)
        self.assertEqual(receipt["schema"], "preserve-repair-receipt/1")

        archived = self._archived_bundles()
        self.assertEqual(len(archived), 1)
        expected_name = f"repair-{self.bundle_sha}.bundle"
        self.assertEqual(archived[0].name, expected_name)

        self.assertEqual(
            receipt["bundle"]["sha256"],
            hashlib.sha256(archived[0].read_bytes()).hexdigest(),
        )
        self.assertEqual(receipt["bundle"]["source"], str(self.bundle))
        self.assertEqual(receipt["bundle"]["destination"], str(archived[0]))
        self.assertEqual(receipt["verification"]["exit_code"], 0)
        self.assertEqual(
            receipt["heads"], [{"sha": self.fix_sha, "ref": "refs/heads/fix"}]
        )
        self.assertTrue(receipt["prerequisites"], "incremental bundle must list prereqs")
        self.assertEqual(receipt["prerequisites"][0]["sha"], self.main_sha)
        self.assertEqual(receipt["worktree"], str(self.worktree))

        receipt_file = self._archived_receipts()[0]
        self.assertEqual(receipt_file.read_bytes(), result.stdout.encode("utf-8"))
        self.assertEqual(receipt["receipt_path"], str(receipt_file))

        # The archived copy itself verifies against the repository.
        verify = subprocess.run(
            ["git", "bundle", "verify", str(archived[0])],
            cwd=str(self.repo), env=self.env, capture_output=True, text=True,
        )
        self.assertEqual(verify.returncode, 0, verify.stderr)

    def test_corrupt_bundle_is_refused_without_receipt(self) -> None:
        self.bundle.write_bytes(b"definitely not a git bundle\n" * 8)
        result = self._preserve(worktree=self.worktree)

        self.assertEqual(result.returncode, 1)
        self.assertIn("refusing", result.stderr)
        self.assertEqual(self._archived_bundles(), [])
        self.assertEqual(self._archived_receipts(), [])

    def test_missing_bundle_is_refused(self) -> None:
        result = self._preserve(bundle=self.worktree / "build" / "nope.bundle")

        self.assertEqual(result.returncode, 1)
        self.assertIn("bundle file not found", result.stderr)
        self.assertEqual(self._archived_bundles(), [])
        self.assertEqual(self._archived_receipts(), [])

    def test_archive_inside_worktree_is_refused(self) -> None:
        nested = self.worktree / "build" / "archive"
        nested.mkdir()
        result = self._preserve(archive=nested, worktree=self.worktree)

        self.assertEqual(result.returncode, 1)
        self.assertIn("inside the worktree", result.stderr)
        self.assertEqual(list(nested.iterdir()), [])

        # The worktree itself is equally invalid as an archive location.
        result = self._preserve(archive=self.worktree, worktree=self.worktree)
        self.assertEqual(result.returncode, 1)
        self.assertIn("inside the worktree", result.stderr)

    def test_archive_dir_that_is_a_file_is_refused(self) -> None:
        not_a_dir = self.tmp / "plain-file"
        not_a_dir.write_text("occupied\n", encoding="utf-8")
        result = self._preserve(archive=not_a_dir, worktree=self.worktree)

        self.assertEqual(result.returncode, 1)
        self.assertIn("not a directory", result.stderr)
        self.assertEqual(not_a_dir.read_text(), "occupied\n")

    def test_repeated_invocation_is_idempotent(self) -> None:
        first = self._preserve(worktree=self.worktree)
        self.assertEqual(first.returncode, 0, first.stderr)
        first_bytes = self._archived_bundles()[0].read_bytes()
        first_receipt = self._archived_receipts()[0].read_bytes()

        second = self._preserve(worktree=self.worktree)
        self.assertEqual(second.returncode, 0, second.stderr)

        self.assertEqual(len(self._archived_bundles()), 1)
        self.assertEqual(len(self._archived_receipts()), 1)
        self.assertEqual(self._archived_bundles()[0].read_bytes(), first_bytes)
        self.assertEqual(self._archived_receipts()[0].read_bytes(), first_receipt)
        self.assertEqual(first.stdout, second.stdout)

    def test_different_content_under_same_name_does_not_clobber(self) -> None:
        first = self._preserve(worktree=self.worktree)
        self.assertEqual(first.returncode, 0, first.stderr)
        first_archived = self._archived_bundles()[0]
        first_bytes = first_archived.read_bytes()

        # Produce new content at the SAME source path (a second fix commit).
        self._commit_in(self.worktree, "c.txt", "second fix commit")
        self._git("-C", str(self.worktree), "bundle", "create", str(self.bundle),
                  "main..fix")
        second_sha = hashlib.sha256(self.bundle.read_bytes()).hexdigest()
        self.assertNotEqual(second_sha, self.bundle_sha)

        second = self._preserve(worktree=self.worktree)
        self.assertEqual(second.returncode, 0, second.stderr)

        bundles = self._archived_bundles()
        receipts = self._archived_receipts()
        self.assertEqual(len(bundles), 2, "both versions must be kept")
        self.assertEqual(len(receipts), 2)
        self.assertEqual(first_archived.read_bytes(), first_bytes,
                         "earlier archived bytes must be untouched")
        names = {p.name for p in bundles}
        self.assertEqual(names, {
            f"repair-{self.bundle_sha}.bundle",
            f"repair-{second_sha}.bundle",
        })

    def test_existing_destination_with_different_bytes_is_not_clobbered(self) -> None:
        destination = self.archive / f"repair-{self.bundle_sha}.bundle"
        self.archive.mkdir(parents=True)
        destination.write_bytes(b"planted imposter with the identity name\n")
        planted = destination.read_bytes()

        result = self._preserve(worktree=self.worktree)

        self.assertEqual(result.returncode, 1)
        self.assertIn("refusing to clobber", result.stderr)
        self.assertEqual(destination.read_bytes(), planted)
        self.assertEqual(self._archived_receipts(), [])

    def test_preservation_survives_source_worktree_removal(self) -> None:
        result = self._preserve(worktree=self.worktree)
        self.assertEqual(result.returncode, 0, result.stderr)
        archived = self._archived_bundles()[0]
        receipt_path = self._archived_receipts()[0]
        receipt = json.loads(receipt_path.read_text(encoding="utf-8"))

        # Simulate the runner forcibly removing the worktree (this fixture's
        # own worktree directory, not a shared one).
        shutil.rmtree(self.worktree)

        self.assertTrue(archived.is_file())
        self.assertEqual(receipt["bundle"]["sha256"],
                         hashlib.sha256(archived.read_bytes()).hexdigest())

        # The archived bundle still verifies against the surviving repository.
        verify = subprocess.run(
            ["git", "bundle", "verify", str(archived)],
            cwd=str(self.repo), env=self.env, capture_output=True, text=True,
        )
        self.assertEqual(verify.returncode, 0, verify.stderr)

        # And it can still deliver the fix commits into a fresh clone.
        clone = self.tmp / "recovered"
        self._git("clone", "-q", str(self.repo), str(clone))
        self._git("-C", str(clone), "fetch", "-q", str(archived),
                  "refs/heads/fix:refs/heads/fix")
        self.assertEqual(self._rev_parse("fix", repo=clone), self.fix_sha)

    def test_unresolvable_prerequisite_is_refused(self) -> None:
        unrelated = self.tmp / "unrelated"
        self._git("init", "-q", "-b", "main", str(unrelated))
        self._commit_in(unrelated, "other.txt", "unrelated history")

        result = self._preserve(repository=unrelated, worktree=self.worktree)

        self.assertEqual(result.returncode, 1)
        self.assertIn("git bundle verify failed", result.stderr)
        self.assertEqual(self._archived_bundles(), [])
        self.assertEqual(self._archived_receipts(), [])

    def test_odd_paths_and_relative_bundle_path(self) -> None:
        weird = self.tmp / "odd dir '$HOME'; rm -rf nope | & (paren)"
        archive = weird / "repair archive"
        result = self._preserve(archive=archive, worktree=self.worktree,
                                cwd=self.worktree / "build")
        # NOTE: --bundle defaults to the absolute fixture path above; run a
        # second invocation passing a RELATIVE bundle path from that cwd.
        self.assertEqual(result.returncode, 0, result.stderr)

        relative = self._run_tool(
            "--repository", str(self.repo),
            "--bundle", "repair.bundle",
            "--archive-dir", str(archive),
            "--worktree", str(self.worktree),
            cwd=self.worktree / "build",
        )
        self.assertEqual(relative.returncode, 0, relative.stderr)
        receipt = json.loads(relative.stdout)
        self.assertEqual(receipt["bundle"]["source"], str(self.bundle),
                         "relative bundle path must resolve to the absolute file")
        self.assertEqual(len(self._archived_bundles(archive)), 1)
        self.assertEqual(len(self._archived_receipts(archive)), 1)

        # Nothing was ever shell-evaluated: the scary name is inert.
        self.assertFalse((self.worktree / "build" / "nope").exists())
        self.assertFalse((self.tmp / "nope").exists())


    def _tool_module(self):
        spec = importlib.util.spec_from_file_location("preserve_repair", TOOL)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_source_replaced_after_verification_cannot_change_archived_bytes(self):
        module = self._tool_module()
        original = self.bundle.read_bytes()
        run_git = module._run_git

        def replace_after_verify(args, cwd):
            result = run_git(args, cwd)
            if args[:2] == ["bundle", "verify"] and result.returncode == 0:
                self.bundle.write_bytes(b"replaced after verification")
            return result

        with patch.object(module, "_run_git", side_effect=replace_after_verify):
            receipt = module.preserve(str(self.repo), str(self.bundle),
                                      str(self.archive), str(self.worktree))
        archived = Path(receipt["bundle"]["destination"])
        self.assertEqual(archived.read_bytes(), original)
        self.assertEqual(hashlib.sha256(archived.read_bytes()).hexdigest(),
                         receipt["bundle"]["sha256"])
        self.assertEqual(archived.stat().st_size, receipt["bundle"]["size_bytes"])

    def test_concurrent_destination_creation_never_clobbers_the_winner(self):
        module = self._tool_module()
        replace = module.os.replace
        link = module.os.link
        planted = b"a concurrent writer owns this destination"

        def publish(operation):
            def run(source, destination, *args, **kwargs):
                destination = Path(destination)
                if destination.suffix == ".bundle" and not destination.exists():
                    destination.write_bytes(planted)
                return operation(source, destination, *args, **kwargs)
            return run

        with patch.object(module.os, "replace", side_effect=publish(replace)), \
             patch.object(module.os, "link", side_effect=publish(link)):
            with self.assertRaises(module.Refused):
                module.preserve(str(self.repo), str(self.bundle),
                                str(self.archive), str(self.worktree))
        self.assertEqual(self._archived_bundles()[0].read_bytes(), planted)
        self.assertEqual(self._archived_receipts(), [])

    def test_corrupt_existing_receipt_refuses_success_and_preserves_bytes(self):
        first = self._preserve(worktree=self.worktree)
        self.assertEqual(first.returncode, 0, first.stderr)
        receipt = self._archived_receipts()[0]
        corrupt = b'{"bundle":'
        receipt.write_bytes(corrupt)
        result = self._preserve(worktree=self.worktree)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(receipt.read_bytes(), corrupt)
        self.assertEqual(result.stdout, "")

    def test_existing_receipt_with_wrong_digest_refuses_success(self):
        first = self._preserve(worktree=self.worktree)
        self.assertEqual(first.returncode, 0, first.stderr)
        path = self._archived_receipts()[0]
        data = json.loads(path.read_text())
        data["bundle"]["sha256"] = "0" * 64
        planted = json.dumps(data).encode()
        path.write_bytes(planted)
        self.env["PYTHONOPTIMIZE"] = "1"
        result = self._preserve(worktree=self.worktree)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(path.read_bytes(), planted)

    def test_receipt_fsync_failure_cannot_publish_success(self):
        module = self._tool_module()
        self.archive.mkdir()
        destination = self.archive / "receipt.json"
        with patch.object(module.os, "fsync", side_effect=OSError("durability refused")):
            with self.assertRaises(OSError):
                module._atomic_write_bytes(destination, b'{}\n')
        self.assertFalse(destination.exists())

    def test_identical_bytes_from_another_source_return_persisted_provenance(self):
        first = self._preserve(worktree=self.worktree)
        self.assertEqual(first.returncode, 0, first.stderr)
        other = self.tmp / "other" / self.bundle.name
        other.parent.mkdir()
        shutil.copyfile(self.bundle, other)
        second = self._preserve(bundle=other, worktree=self.worktree)
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(json.loads(second.stdout), json.loads(first.stdout))
        self.assertEqual(self._archived_receipts()[0].read_bytes(), first.stdout.encode())

    def test_omitting_worktree_still_rejects_archive_inside_source_git_worktree(self):
        result = self._preserve(archive=self.worktree / "build/archive")
        self.assertEqual(result.returncode, 1)
        self.assertEqual(self._archived_receipts(self.worktree / "build/archive"), [])


if __name__ == "__main__":
    unittest.main()
