import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from . import prepare_final_inputs as prep
from .common import DriverFailure, LOCAL_SIGNER


class PrepareFinalInputsTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.plan = {
            "schema": 1,
            "baseline": {
                "apk": str(self.root / "oc-2202.apk"),
                "build": 2202,
                "version": "1.2.0",
                "sha256": prep.BASELINE_SHA256,
                "signer": LOCAL_SIGNER,
                "origin": "previous-approved",
            },
            "fixture": copy.deepcopy(prep.FIXTURE),
        }
        self.plan_path = self.root / "plan.json"
        self.output = self.root / "inputs"
        self.candidate = self.root / "oc-next.apk"
        Path(self.plan["baseline"]["apk"]).write_bytes(b"baseline fixture")
        self.candidate.write_bytes(b"candidate fixture")
        self.candidate_hash = "a" * 64
        self.check = patch.object(prep, "verify_artifact").start()
        self.addCleanup(patch.stopall)

    def prepare(self, **changes):
        self.plan_path.write_text(json.dumps(self.plan))
        values = dict(
            plan_path=self.plan_path,
            candidate_apk=self.candidate,
            candidate_build=2203,
            candidate_sha256=self.candidate_hash,
            output=self.output,
            storage_root=self.root,
        )
        values.update(changes)
        return prep.prepare(**values)

    def test_committed_reviewed_plan_matches_the_real_seed_contract(self):
        from . import fixture, ports
        import os

        plan = prep._reviewed_plan(
            Path(prep.__file__).resolve().parents[3]
            / "docs/qa/FQ9-final-inputs-2026-10-09/reviewed-plan.json"
        )
        backing = self.root / "files/projects"
        backing.mkdir(parents=True)
        legacy = self.root / "files/linux/ubuntu/root/projects"
        legacy.mkdir(parents=True)
        run_id = plan["fixture"]["run_id"]
        port = ports.AndroidPorts("emulator-5554", run_id)
        port.locked, port._uid = True, os.getuid()
        events = []
        port.as_app = lambda command: subprocess.run(
            ["sh", "-c", command], check=True, capture_output=True
        ).stdout
        port.require_idle_setup = lambda: None
        port._connect = lambda engine: events.append(engine)

        def protocol(method, path, **kwargs):
            if path == "/session/status":
                return {}
            if path == "/session":
                return {
                    "id": "ses_fixture",
                    "title": run_id + "-retained",
                    "directory": "/root/projects/" + run_id,
                }
            self.assertEqual(events[-1], "receipt_saved")
            self.assertEqual(
                kwargs["body"],
                {
                    "noReply": True,
                    "parts": [{"type": "text", "text": plan["fixture"]["marker"]}],
                },
            )
            return {
                "info": {
                    "id": "msg_fixture",
                    "sessionID": "ses_fixture",
                    "role": "user",
                }
            }

        port.protocol = protocol
        with patch.object(ports, "FILES", str(self.root / "files")):
            receipt = fixture.seed_history_fixture(
                port, lambda value: events.append("receipt_saved")
            )
        self.assertEqual(
            receipt["directory"], plan["fixture"]["guest_root"] + "/" + run_id
        )
        self.assertTrue((backing / run_id).is_dir())
        self.assertEqual(list(legacy.iterdir()), [])

    def test_prepares_exact_rows_without_seeding_or_creating_history(self):
        rows = self.prepare()
        self.assertEqual(self.check.call_count, 2)
        previous, candidate = [call.args[0] for call in self.check.call_args_list]
        self.assertEqual(previous.build, 2202)
        self.assertEqual(previous.sha256, prep.BASELINE_SHA256)
        self.assertEqual(candidate.build, 2203)
        self.assertEqual(candidate.sha256, self.candidate_hash)
        self.assertEqual(candidate.signer, LOCAL_SIGNER)
        self.assertEqual(candidate.version, "1.2.0")
        self.assertEqual(candidate.origin, "coordinator-approved")
        manifest = json.loads((self.output / "fq9-artifacts.json").read_text())
        self.assertEqual(manifest["candidate"], manifest["normal"])
        self.assertEqual(manifest["previous"], self.plan["baseline"])
        self.assertEqual(
            rows["fq9-upgrade"]["seed_history_receipt"],
            str(self.output / "history.json"),
        )
        self.assertEqual(rows["fq9-upgrade"]["run_id"], prep.FIXTURE["run_id"])
        self.assertEqual(json.loads((self.output / "rows.json").read_text()), rows)
        self.assertEqual(
            json.loads((self.output / "fixture-plan.json").read_text()), self.plan
        )
        from tool.qa.final_pass import load_inputs
        from .common import load_manifest

        self.assertEqual(load_inputs(self.output / "rows.json"), rows)
        parsed = load_manifest(self.output / "fq9-artifacts.json", candidate_build=2203)
        self.assertEqual(parsed["previous"].build, 2202)
        self.assertEqual(parsed["normal"], parsed["candidate"])
        self.assertFalse((self.output / "history.json").exists())
        self.assertEqual(self.output.stat().st_mode & 0o777, 0o700)
        for file in self.output.iterdir():
            self.assertEqual(file.stat().st_mode & 0o777, 0o600)

    def test_reviewed_baseline_and_every_fixture_field_are_frozen(self):
        original = copy.deepcopy(self.plan)
        variants = []
        for key in original["baseline"]:
            if key != "apk":
                variant = copy.deepcopy(original)
                variant["baseline"][key] = "changed"
                variants.append(variant)
        for key, value in original["fixture"].items():
            variant = copy.deepcopy(original)
            variant["fixture"][key] = False if value is True else "changed"
            variants.append(variant)
        variant = copy.deepcopy(original)
        variant["fixture"]["no_reply"] = 1
        variants.append(variant)
        for variant in variants:
            with self.subTest(variant=variant):
                self.plan = variant
                with self.assertRaises(DriverFailure):
                    self.prepare()
                self.assertFalse(self.output.exists())
        self.check.assert_not_called()

    def test_candidate_requires_explicit_newer_build_and_lowercase_hash(self):
        for values in (
            {"candidate_build": 2202},
            {"candidate_build": True},
            {"candidate_build": 1000000},
            {"candidate_sha256": ""},
            {"candidate_sha256": "A" * 64},
            {"candidate_apk": Path("relative.apk")},
        ):
            with self.subTest(values=values), self.assertRaises(DriverFailure):
                self.prepare(**values)
        self.check.assert_not_called()

    def test_apk_identity_failure_does_not_create_outputs(self):
        for failed_call in (0, 1):
            self.check.side_effect = (
                [DriverFailure("artifact_hash_mismatch")]
                if failed_call == 0
                else [None, DriverFailure("artifact_signer_mismatch")]
            )
            with self.assertRaises(DriverFailure):
                self.prepare()
            self.assertFalse(self.output.exists())
            self.check.reset_mock()

    def test_apk_paths_refuse_missing_direct_links_and_linked_ancestors(self):
        actual = self.root / "artifacts"
        actual.mkdir()
        (actual / "candidate.apk").write_bytes(b"candidate fixture")
        linked_dir = self.root / "linked-artifacts"
        linked_dir.symlink_to(actual, target_is_directory=True)
        direct_link = self.root / "candidate-link.apk"
        direct_link.symlink_to(self.candidate)
        for candidate in (
            self.root / "missing.apk",
            actual,
            direct_link,
            linked_dir / "candidate.apk",
        ):
            with self.subTest(candidate=candidate), self.assertRaises(DriverFailure):
                self.prepare(candidate_apk=candidate)
        self.plan["baseline"]["apk"] = str(direct_link)
        with self.assertRaises(DriverFailure):
            self.prepare()
        self.plan["baseline"]["apk"] = str(linked_dir / "candidate.apk")
        with self.assertRaises(DriverFailure):
            self.prepare()
        self.check.assert_not_called()
        self.assertFalse(self.output.exists())

    def test_direct_file_cli_help_and_missing_arguments_are_offline(self):
        script = str(Path(prep.__file__).resolve())
        for args, code in ((["--help"], 0), ([], 2)):
            with self.subTest(args=args):
                result = subprocess.run(
                    [sys.executable, script, *args],
                    cwd=self.root,
                    capture_output=True,
                    timeout=10,
                    check=False,
                )
                self.assertEqual(result.returncode, code, result.stderr.decode())
                self.assertNotIn(b"Traceback", result.stderr)
        self.assertFalse(self.output.exists())

    def test_output_refuses_existing_outside_relative_and_missing_parent(self):
        existing = self.root / "existing"
        existing.mkdir()
        sentinel = existing / "preserve"
        sentinel.write_text("owned elsewhere")
        for output in (
            existing,
            Path("relative"),
            self.root.parent / "outside",
            self.root / "missing" / "inputs",
            self.root / ".." / self.root.name / "inputs",
        ):
            with self.subTest(output=output), self.assertRaises(DriverFailure):
                self.prepare(output=output)
        self.assertEqual(sentinel.read_text(), "owned elsewhere")
        self.check.assert_not_called()

    def test_output_refuses_symlink_ancestors_and_worktrees(self):
        destination = self.root / "private"
        destination.mkdir()
        linked = self.root / "linked"
        linked.symlink_to(destination, target_is_directory=True)
        dangling = self.root / "dangling"
        dangling.symlink_to(self.root / "absent", target_is_directory=True)
        for output in (linked / "inputs", dangling / "inputs"):
            with self.assertRaises(DriverFailure):
                self.prepare(output=output)
        for git_kind in ("file", "directory"):
            checkout = self.root / git_kind
            checkout.mkdir()
            git = checkout / ".git"
            if git_kind == "file":
                git.write_text("gitdir: elsewhere")
            else:
                git.mkdir()
            with self.assertRaises(DriverFailure):
                self.prepare(output=checkout / "inputs")
        self.check.assert_not_called()

    def test_existing_output_is_never_overwritten(self):
        self.prepare()
        before = (self.output / "fq9-artifacts.json").read_bytes()
        with self.assertRaises(DriverFailure):
            self.prepare()
        self.assertEqual((self.output / "fq9-artifacts.json").read_bytes(), before)


if __name__ == "__main__":
    unittest.main()
