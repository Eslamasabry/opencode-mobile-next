"""Offline launcher safety checks; no binary, socket, or test runner is started."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


_PATH = Path(__file__).resolve().parents[1] / "tool/qa/fq3d_run.py"
_SPEC = importlib.util.spec_from_file_location("fq3d_launcher_under_test", _PATH)
launcher = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(launcher)

_SECRET = "A" * 43
_INHERITED_SECRET = "inherited-password-must-not-reach-server"
_LISTENING = b"server listening on http://127.0.0.1:4097\n"


class _OwnedProcess:
    def __init__(self, *, stubborn=False, terminate_error=False, kill_error=False):
        self.pid = 73421
        self.stdout = mock.Mock(name="owned_stdout")
        self.stderr = mock.Mock(name="owned_stderr")
        self.returncode = None
        self.actions = []
        self.stubborn = stubborn
        self.terminate_error = terminate_error
        self.kill_error = kill_error
        self.directory = None
        self.directory_at_termination = None

    def poll(self):
        return self.returncode

    def terminate(self):
        self.actions.append("terminate")
        self.directory_at_termination = self.directory.is_dir()
        if self.terminate_error:
            raise OSError("private cleanup error " + _SECRET)
        if not self.stubborn:
            self.returncode = -15

    def kill(self):
        self.actions.append("kill")
        if self.kill_error:
            raise OSError("private kill error " + _SECRET)
        self.returncode = -9

    def wait(self, timeout):
        self.actions.append(("wait", timeout))
        if self.returncode is None:
            raise subprocess.TimeoutExpired("owned-process", timeout)
        return self.returncode


class _Selector:
    def __init__(self, process, chunks):
        self.process = process
        self.chunks = list(chunks)
        self.keys = []
        self.current = b""

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def register(self, pipe, events, data):
        self.keys.append(
            SimpleNamespace(fileobj=pipe, fd=len(self.keys) + 40, data=data)
        )

    def unregister(self, pipe):
        pass

    def select(self, timeout):
        if not self.chunks:
            # A startup process exiting is deterministic; no real clock wait.
            self.process.returncode = 23
            return []
        stream, self.current = self.chunks.pop(0)
        return [(self.keys[stream], 1)]

    def read(self, fd, size):
        return self.current


class LauncherSafetyTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="fq3d-launcher-unit-")
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.binary = self.directory / "pinned-opencode"
        self.binary.write_bytes(b"offline binary fixture; never executable")
        self.output = self.directory / "evidence.json"
        self.process = _OwnedProcess()
        self.chunks = [(0, _LISTENING), (1, f"server password {_SECRET}\n".encode())]
        self.dart_calls = []
        self.version_stdout = b"opencode v2.0.10\n"
        self.child_status = 0
        self.server_environment = None
        self.threads = []

    def _spawn(self, *args, **kwargs):
        # Popen receives a snapshot at launch; later child-env additions must
        # not make a mocked server appear to have inherited the Dart secret.
        self.server_environment = kwargs["env"].copy()
        self.process.directory = Path(kwargs["cwd"])
        # A failed-stop fixture may deliberately retain the launch directory.
        self.addCleanup(shutil.rmtree, self.process.directory.parent, True)
        return self.process

    def _thread(self, **kwargs):
        thread = mock.Mock(name="offline_drain_thread")
        self.threads.append((thread, kwargs))
        return thread

    def _mkdtemp(self, *, prefix):
        self.assertEqual(prefix, "oc-fq3d-pc-")
        owned = self.directory / "owned-launch"
        owned.mkdir()
        return str(owned)

    def _subprocess_run(self, args, **kwargs):
        if args == [str(self.binary), "--version"]:
            return subprocess.CompletedProcess(
                args, 0, stdout=self.version_stdout, stderr=b""
            )
        self.assertEqual(args[0], "/offline/pinned/flutter")
        self.dart_calls.append((args, kwargs))
        self.output.write_text(json.dumps({"observedVersion": "2.0.10", "cases": {}}))
        return subprocess.CompletedProcess(args, self.child_status)

    def _run(self, *, occupied=False):
        selector = _Selector(self.process, self.chunks)
        stdout, stderr = io.StringIO(), io.StringIO()
        tick = iter(range(10000))
        with contextlib.ExitStack() as stack:
            stack.enter_context(mock.patch.object(launcher, "BINARY", self.binary))
            stack.enter_context(
                mock.patch.object(launcher, "FLUTTER", "/offline/pinned/flutter")
            )
            stack.enter_context(mock.patch.object(launcher, "OUTPUT", self.output))
            environment = {
                "PATH": "/offline/path",
                "OPENCODE_PASSWORD": _INHERITED_SECRET,
                "OPENCODE_SERVER_PASSWORD": _INHERITED_SECRET,
                "FQ3D_PASSWORD": _INHERITED_SECRET,
            }
            stack.enter_context(
                mock.patch.dict(launcher.os.environ, environment, clear=True)
            )
            connection = stack.enter_context(
                mock.patch.object(launcher.socket, "create_connection")
            )
            if not occupied:
                connection.side_effect = ConnectionRefusedError()
            else:
                connection.return_value.__enter__.return_value = mock.Mock(
                    name="unrelated_listener"
                )
            spawn = stack.enter_context(
                mock.patch.object(launcher.subprocess, "Popen", side_effect=self._spawn)
            )
            commands = stack.enter_context(
                mock.patch.object(
                    launcher.subprocess, "run", side_effect=self._subprocess_run
                )
            )
            stack.enter_context(
                mock.patch.object(
                    launcher.selectors, "DefaultSelector", return_value=selector
                )
            )
            stack.enter_context(
                mock.patch.object(launcher.os, "read", side_effect=selector.read)
            )
            stack.enter_context(
                mock.patch.object(
                    launcher.time, "monotonic", side_effect=lambda: next(tick)
                )
            )
            stack.enter_context(
                mock.patch.object(
                    launcher.threading, "Thread", side_effect=self._thread
                )
            )
            stack.enter_context(
                mock.patch.object(
                    launcher.tempfile, "mkdtemp", side_effect=self._mkdtemp
                )
            )
            stack.enter_context(contextlib.redirect_stdout(stdout))
            stack.enter_context(contextlib.redirect_stderr(stderr))
            result = launcher.run()
        artifact = self.output.read_text()
        for secret in (_SECRET, _INHERITED_SECRET):
            self.assertNotIn(secret, artifact)
            self.assertNotIn(secret, stdout.getvalue())
            self.assertNotIn(secret, stderr.getvalue())
        return result, json.loads(artifact), spawn, commands

    def test_occupied_port_never_launches_or_stops_an_unrelated_process(self):
        status, report, spawn, commands = self._run(occupied=True)
        self.assertEqual(status, 1)
        self.assertEqual(report["stopReason"], "port_4097_already_owned")
        spawn.assert_not_called()
        commands.assert_not_called()
        self.assertEqual(self.process.actions, [])
        self.process.stdout.close.assert_not_called()

    def test_missing_startup_password_never_invokes_dart_and_cleans_owned_process(self):
        self.chunks = [(0, _LISTENING)]
        status, report, spawn, _ = self._run()
        self.assertEqual(status, 1)
        self.assertEqual(report["stopReason"], "pc_server_startup_exited")
        self.assertEqual(self.dart_calls, [])
        spawn.assert_called_once()
        self.assertEqual(self.process.actions, ["terminate", ("wait", 8)])
        self.assertTrue(report["ownedServerExited"])
        self.assertTrue(self.process.directory_at_termination)
        self.assertFalse(self.process.directory.parent.exists())
        self.process.stdout.close.assert_called_once()
        self.process.stderr.close.assert_called_once()

    def test_success_hands_secret_to_dart_environment_only(self):
        status, report, spawn, _ = self._run()
        self.assertEqual(status, 0)
        self.assertEqual(len(self.dart_calls), 1)
        command, options = self.dart_calls[0]
        self.assertIn("tool/qa/fq3d_live_test.dart", command)
        self.assertNotIn(_SECRET, " ".join(command))
        self.assertEqual(options["env"]["FQ3D_PASSWORD"], _SECRET)
        self.assertEqual(options["env"]["FQ3D_LIVE"], "1")
        self.assertEqual(options["env"]["FQ3D_PID"], str(self.process.pid))
        self.assertTrue(options["env"]["FQ3D_DIRECTORY"].endswith("/project"))
        for key in ("OPENCODE_PASSWORD", "OPENCODE_SERVER_PASSWORD", "FQ3D_PASSWORD"):
            self.assertNotIn(key, self.server_environment)
        self.assertEqual(
            spawn.call_args.args[0][1:],
            ["serve", "--port", "4097", "--hostname", "127.0.0.1"],
        )
        self.assertTrue(Path(spawn.call_args.args[0][0]).name == "opencode2")
        self.assertEqual(self.process.actions, ["terminate", ("wait", 8)])
        self.assertTrue(report["startupPasswordParsed"])
        self.assertTrue(report["startupListeningLineSeen"])
        self.assertTrue(report["ownedServerExited"])
        self.assertEqual(len(self.threads), 2)
        self.assertEqual(
            [kwargs["args"][0] for _, kwargs in self.threads],
            [self.process.stdout, self.process.stderr],
        )
        for thread, kwargs in self.threads:
            self.assertTrue(kwargs["daemon"])
            thread.start.assert_called_once_with()
            thread.join.assert_called_once_with(timeout=1)

    def test_stubborn_owned_process_is_killed_after_bounded_terminate_wait(self):
        self.process = _OwnedProcess(stubborn=True)
        status, report, _, _ = self._run()
        self.assertEqual(status, 0)
        self.assertEqual(
            self.process.actions, ["terminate", ("wait", 8), "kill", ("wait", 5)]
        )
        self.assertTrue(report["ownedServerExited"])
        self.assertEqual(report["ownedServerPID"], self.process.pid)
        self.assertEqual(report["serverExitCode"], -9)

    def test_dart_failure_still_terminates_only_the_owned_process(self):
        self.child_status = 7
        status, report, _, _ = self._run()
        self.assertEqual(status, 7)
        self.assertEqual(self.process.actions, ["terminate", ("wait", 8)])
        self.assertTrue(report["ownedServerExited"])

    def test_wrong_binary_version_never_starts_a_server_or_dart(self):
        self.version_stdout = b"opencode v2.0.11\n"
        status, report, spawn, commands = self._run()
        self.assertEqual(status, 1)
        self.assertEqual(report["stopReason"], "binary_version_mismatch")
        spawn.assert_not_called()
        self.assertEqual(commands.call_count, 1)
        self.assertEqual(self.process.actions, [])

    def test_duplicate_startup_password_is_rejected_without_dart(self):
        secret_line = f"server password {_SECRET}\n".encode()
        self.chunks = [(0, secret_line + secret_line + _LISTENING)]
        status, report, _, _ = self._run()
        self.assertEqual(status, 1)
        self.assertEqual(report["stopReason"], "duplicate_startup_password")
        self.assertEqual(self.dart_calls, [])
        self.assertTrue(report["ownedServerExited"])

    def test_password_without_owned_listening_line_never_invokes_dart(self):
        self.chunks = [(0, f"server password {_SECRET}\n".encode())]
        status, report, _, _ = self._run()
        self.assertEqual(status, 1)
        self.assertEqual(report["stopReason"], "pc_server_startup_exited")
        self.assertEqual(self.dart_calls, [])
        self.assertTrue(report["ownedServerExited"])

    def test_malformed_password_line_cannot_supply_child_credentials(self):
        self.chunks = [(0, _LISTENING + f"server password {_SECRET}X\n".encode())]
        status, report, _, _ = self._run()
        self.assertEqual(status, 1)
        self.assertEqual(report["stopReason"], "pc_server_startup_exited")
        self.assertEqual(self.dart_calls, [])
        self.assertTrue(report["ownedServerExited"])

    def test_unterminated_startup_line_is_bounded_before_dart(self):
        self.chunks = [(0, b"x" * 4096)] * 17
        status, report, _, _ = self._run()
        self.assertEqual(status, 1)
        self.assertEqual(report["stopReason"], "startup_output_bound")
        self.assertEqual(self.dart_calls, [])
        self.assertTrue(report["ownedServerExited"])

    def test_termination_error_recovers_by_killing_the_exact_owned_process(self):
        self.process = _OwnedProcess(terminate_error=True)
        status, report, _, _ = self._run()
        self.assertEqual(status, 0)
        self.assertEqual(self.process.actions, ["terminate", "kill", ("wait", 5)])
        self.assertTrue(report["ownedServerExited"])
        self.process.stdout.close.assert_called_once()
        self.process.stderr.close.assert_called_once()

    def test_failed_owned_stop_writes_safe_failure_and_retains_live_project(self):
        self.process = _OwnedProcess(terminate_error=True, kill_error=True)
        status, report, _, _ = self._run()
        self.assertEqual(status, 1)
        self.assertEqual(self.process.actions, ["terminate", "kill"])
        self.assertFalse(report["ownedServerExited"])
        self.assertFalse(report["cleanupOK"])
        self.assertTrue(self.process.directory.is_dir())
        self.process.stdout.close.assert_called_once()
        self.process.stderr.close.assert_called_once()


if __name__ == "__main__":
    unittest.main()
