import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from .common import DriverFailure
from .ports import AndroidPorts


class FixtureProjectTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.files = Path(self.tmp.name) / "files"
        self.projects = self.files / "projects"
        self.legacy = self.files / "linux/ubuntu/root/projects"
        self.projects.mkdir(parents=True)
        (self.projects / "user-project").mkdir()
        self.legacy.mkdir(parents=True)
        self.port = AndroidPorts("emulator-5554", "fq9-fixture")
        self.port.locked = True
        self.port._uid = os.getuid()

        def as_app(script):
            result = subprocess.run(["sh", "-c", script], capture_output=True)
            if result.returncode:
                raise DriverFailure("command_failed")
            return result.stdout

        self.port.as_app = as_app

    def test_seed_uses_persistent_backing_and_keeps_hidden_legacy_empty(self):
        with patch("tool.qa.fq9.ports.FILES", str(self.files)):
            directory = self.port.prepare_fixture_project()
        self.assertEqual(directory, "/root/projects/fq9-fixture")
        self.assertTrue((self.projects / "fq9-fixture").is_dir())
        self.assertEqual(list(self.legacy.iterdir()), [])
        self.assertEqual((self.projects / "fq9-fixture").stat().st_mode & 0o777, 0o700)
        self.assertTrue((self.projects / "user-project").is_dir())

    def test_existing_or_symlink_project_is_preserved(self):
        fixture = self.projects / "fq9-fixture"
        fixture.symlink_to(self.legacy, target_is_directory=True)
        with patch("tool.qa.fq9.ports.FILES", str(self.files)):
            with self.assertRaises(DriverFailure):
                self.port.prepare_fixture_project()
        self.assertTrue(fixture.is_symlink())
        self.assertEqual(list(self.legacy.iterdir()), [])

    def test_missing_or_symlink_backing_refuses_without_legacy_fallback(self):
        (self.projects / "user-project").rmdir()
        self.projects.rmdir()
        for linked in (False, True):
            if linked:
                self.projects.symlink_to(self.legacy, target_is_directory=True)
            with patch("tool.qa.fq9.ports.FILES", str(self.files)):
                with self.assertRaises(DriverFailure):
                    self.port.prepare_fixture_project()
            self.assertEqual(list(self.legacy.iterdir()), [])

    def test_no_device_call_without_lock_uid_or_safe_run_id(self):
        for field, value in [("locked", False), ("_uid", None), ("run_id", "../user")]:
            p = AndroidPorts("emulator-5554", "fq9-fixture")
            p.locked, p._uid = True, os.getuid()
            setattr(p, field, value)
            p.as_app = lambda _: self.fail("device call after failed preflight")
            with self.assertRaises(DriverFailure):
                p.prepare_fixture_project()

    def test_original_legacy_location_mutant_fails_the_preservation_regression(self):
        import inspect
        import textwrap
        from . import ports

        source = textwrap.dedent(
            inspect.getsource(AndroidPorts.prepare_fixture_project)
        )
        source = source.replace(
            'projects = FILES + "/projects"',
            'projects = FILES + "/linux/ubuntu/root/projects"',
        )
        namespace = vars(ports)
        exec(compile(source, "<legacy-location-red-control>", "exec"), namespace)
        case = FixtureProjectTests(
            "test_seed_uses_persistent_backing_and_keeps_hidden_legacy_empty"
        )
        with patch.object(
            AndroidPorts,
            "prepare_fixture_project",
            namespace["prepare_fixture_project"],
        ):
            result = unittest.TestResult()
            case.run(result)
        self.assertEqual(len(result.failures), 1)
        self.assertEqual(result.errors, [])


class SeedHistoryTests(unittest.TestCase):
    def port(self):
        import types

        p = types.SimpleNamespace(run_id="fq9-fixture", history=None)
        self.calls = []
        p.require_idle_setup = lambda: self.calls.append("idle")
        p._connect = lambda engine: self.calls.append(engine)
        p.prepare_fixture_project = (
            lambda: self.calls.append("mapped_project") or "/root/projects/fq9-fixture"
        )

        def protocol(method, path, **kwargs):
            self.calls.append((method, path, kwargs))
            if path == "/session/status":
                return {}
            if path == "/session":
                return dict(
                    id="ses_1",
                    title="fq9-fixture-retained",
                    directory="/root/projects/fq9-fixture",
                )
            return dict(info=dict(id="msg_1", sessionID="ses_1", role="user"), parts=[])

        p.protocol = protocol
        return p

    def test_receipt_precedes_prompt_and_guest_directory_uses_app_mapping(self):
        from .fixture import seed_history_fixture

        p = self.port()
        saved = []

        def save(value):
            self.calls.append("receipt")
            saved.append(value)

        receipt = seed_history_fixture(p, save)
        self.assertEqual(saved, [receipt])
        self.assertEqual(p.history, receipt)
        create, prompt = [c for c in self.calls if type(c) is tuple and c[0] == "POST"]
        self.assertEqual(create[2]["query"]["directory"], "/root/projects/fq9-fixture")
        self.assertTrue(prompt[2]["body"]["noReply"])
        self.assertLess(self.calls.index("receipt"), self.calls.index(prompt))
        self.assertLess(self.calls.index("mapped_project"), self.calls.index(create))

    def test_active_turn_does_not_create_a_fixture(self):
        from .fixture import seed_history_fixture

        p = self.port()
        p.protocol = lambda *a, **kw: {"ses_foreign": {"type": "busy"}}
        with self.assertRaisesRegex(DriverFailure, "another_live_turn"):
            seed_history_fixture(p, lambda _: self.fail("receipt after refusal"))
        self.assertNotIn("mapped_project", self.calls)

    def test_failed_prompt_keeps_the_exact_private_receipt(self):
        from .fixture import seed_history_fixture

        p = self.port()
        original = p.protocol

        def protocol(method, path, **kwargs):
            if path.endswith("/message"):
                raise DriverFailure("protocol_response_invalid")
            return original(method, path, **kwargs)

        p.protocol = protocol
        saved = []
        with self.assertRaises(DriverFailure):
            seed_history_fixture(p, saved.append)
        self.assertEqual(
            saved[0]["sessions"], [{"id": "ses_1", "title": "fq9-fixture-retained"}]
        )
