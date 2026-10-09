"""Mocked recorder checks only: no adb, ffmpeg, emulator or wall-clock waits."""

from contextlib import contextmanager, redirect_stderr, redirect_stdout
import io
import json
from pathlib import Path
import signal
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from tool.qa import record_demo_gif as recorder


class Clock:
    now = 0

    def read(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class Process:
    def __init__(self, clock, duration, *, stuck=False):
        self.clock, self.duration, self.stuck = clock, duration, stuck
        self.signals = []
        self.done = False
        self.pid = 12345

    def poll(self):
        return 0 if self.done or (not self.stuck and self.clock.now >= self.duration) else None

    def wait(self, timeout):
        if self.poll() is None:
            raise subprocess.TimeoutExpired("owned-recorder", timeout)
        return 0

    def send_signal(self, value):
        self.signals.append(value)
        self.done = True

    def terminate(self):
        self.signals.append(signal.SIGTERM)
        self.done = True

    def kill(self):
        self.signals.append(signal.SIGKILL)
        self.done = True


class RecorderTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.private = self.root / "private"
        self.clock = Clock()
        self.commands = []
        self.events = []
        self.emitted = []
        self.process = Process(self.clock, 60)
        self.candidate = self.root / "candidate.apk"
        self.normal = self.root / "normal.apk"
        self.candidate.write_bytes(b"mock-apk")
        self.normal.write_bytes(b"mock-apk")

    def options(self, *extra):
        return recorder.parser().parse_args([
            "--record", "--attest-synthetic-demo", "--duration", "60",
            "--private-dir", str(self.private), *extra,
        ])

    @contextmanager
    def lock(self):
        self.events.append("locked")
        try:
            yield
        finally:
            self.events.append("unlocked")

    def run_command(self, command, **kwargs):
        self.assertIsInstance(command, list)
        self.assertNotIn("shell", kwargs)
        self.assertEqual(self.events[-1], "locked")
        self.commands.append(command)
        if command[0] == "adb":
            self.assertEqual(command[:3], ["adb", "-s", "emulator-5554"])
            if command[3] == "pull":
                Path(command[-1]).write_bytes(b"private-video")
        elif command[0] == "ffmpeg":
            Path(command[-1]).write_bytes(b"private-image")
        return SimpleNamespace(returncode=0, stdout=b"device\n" if command[-1] == "get-state" else b"")

    def popen(self, command, **kwargs):
        self.assertEqual(self.events[-1], "locked")
        self.assertNotIn("shell", kwargs)
        self.commands.append(command)
        return self.process

    def capture(self, options=None, **kwargs):
        with patch.object(recorder, "NORMAL_APK", self.normal):
            return recorder.record(
                options or self.options(), run=kwargs.get("run", self.run_command),
                popen=self.popen, clock=self.clock.read,
                sleep=kwargs.get("sleep", self.clock.sleep), lock=self.lock,
                prompt=lambda _: "", emit=self.emitted.append,
            )

    def test_dry_run_has_no_subprocess_lock_write_or_prompt(self):
        with patch.object(recorder, "record") as record, \
             patch.object(recorder.subprocess, "run") as run, \
             patch.object(recorder.subprocess, "Popen") as popen, \
             patch.object(recorder, "emulator_lock") as lock, \
             redirect_stdout(io.StringIO()) as output:
            self.assertEqual(recorder.main(["--private-dir", str(self.private)]), 0)
        for call in (record, run, popen, lock):
            call.assert_not_called()
        self.assertFalse(self.private.exists())
        plan = json.loads(output.getvalue())
        self.assertEqual(plan["mode"], "dry-run")
        self.assertEqual(plan["serial"], "emulator-5554")
        self.assertEqual(plan["duration_seconds"], 75)

    def test_duration_bounds_and_target_cannot_be_overridden(self):
        for args in (["--duration", "59"], ["--duration", "91"],
                     ["--duration", "1.5"], ["--serial", "physical-device"],
                     ["--output", "public/demo.gif"]):
            with redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                recorder.parser().parse_args(args)

    def test_record_requires_explicit_synthetic_attestation(self):
        options = self.options()
        options.attest_synthetic_demo = False
        with self.assertRaises(recorder.RecorderError):
            self.capture(options)
        self.assertEqual(self.commands, [])
        self.assertEqual(self.events, [])

    def test_private_paths_reject_public_modes_repo_symlinks_and_traversal(self):
        self.private.mkdir(mode=0o755)
        self.private.chmod(0o755)
        with self.assertRaises(recorder.RecorderError):
            recorder.private_path(self.private)
        for path in (recorder.REPO / "docs/demo", self.root / "../escape",
                     self.root / "bad\nname"):
            with self.assertRaises(recorder.RecorderError):
                recorder.private_path(path)
        link = self.root / "linked"
        link.symlink_to(self.private, target_is_directory=True)
        with self.assertRaises(recorder.RecorderError):
            recorder.private_path(link / "child")

    def test_success_records_private_unique_files_and_cleans_owned_remote(self):
        gif = self.capture()
        self.assertTrue(gif.is_file())
        self.assertEqual(gif.parent.stat().st_mode & 0o777, 0o700)
        self.assertEqual(gif.stat().st_mode & 0o777, 0o600)
        self.assertFalse(json.loads((gif.parent / "REVIEW_REQUIRED.json").read_text())["reviewed_for_export"])
        self.assertEqual(self.events, ["locked", "unlocked"])
        capture = next(c for c in self.commands if "screenrecord" in c)
        self.assertRegex(capture[-1], r"^/sdcard/oc-demo-[a-f0-9]{32}\.mp4$")
        self.assertIn(["adb", "-s", "emulator-5554", "shell", "rm", "-f", capture[-1]], self.commands)
        self.assertGreaterEqual(self.clock.now, 60)
        self.assertTrue(any("Pick the demo agent" in line for line in self.emitted))
        self.assertTrue(any("Approve a harmless" in line for line in self.emitted))
        self.assertFalse(any("uninstall" in c or "clear" in c for c in self.commands))
        self.assertFalse(any("install" in c for c in self.commands))

    def test_ffmpeg_uses_two_bounded_passes_crop_and_no_overwrite(self):
        commands = recorder.conversion_commands(Path("a.mp4"), Path("p.png"), Path("g.gif"), 40, 80)
        self.assertEqual(len(commands), 2)
        for command in commands:
            self.assertIn("-n", command)
            self.assertNotIn("-y", command)
            self.assertEqual(command[command.index("-threads") + 1], "2")
            self.assertIn("crop=iw:ih-40-80:0:40", " ".join(command))
            self.assertIn("fps=10,scale=480:-2", " ".join(command))
        self.assertIn("palettegen", " ".join(commands[0]))
        self.assertIn("paletteuse", " ".join(commands[1]))

    def test_failed_candidate_install_still_restores_normal_before_unlock(self):
        def failing(command, **kwargs):
            result = self.run_command(command, **kwargs)
            if command[-1] == str(self.candidate):
                return SimpleNamespace(returncode=1, stdout=b"sensitive failure text")
            return result
        with self.assertRaises(recorder.RecorderError) as caught:
            self.capture(self.options("--candidate-apk", str(self.candidate)), run=failing)
        self.assertNotIn("sensitive", str(caught.exception))
        self.assertEqual(self.commands[-1], recorder.adb("install", "-r", self.normal))
        self.assertEqual(self.events, ["locked", "unlocked"])

    def test_conversion_failure_cleans_recording_and_restores_normal(self):
        def failing(command, **kwargs):
            result = self.run_command(command, **kwargs)
            if command[0] == "ffmpeg":
                return SimpleNamespace(returncode=1, stdout=b"account-details")
            return result
        with self.assertRaises(recorder.RecorderError):
            self.capture(self.options("--candidate-apk", str(self.candidate)), run=failing)
        self.assertEqual(self.commands[-1], recorder.adb("install", "-r", self.normal))
        self.assertEqual(self.commands[-2][3:6], ["shell", "rm", "-f"])
        self.assertEqual(self.events[-1], "unlocked")

    def test_interruption_signals_only_owned_process_then_restores(self):
        interrupted = False
        def sleep(seconds):
            nonlocal interrupted
            if not interrupted:
                interrupted = True
                raise KeyboardInterrupt
            self.clock.sleep(seconds)
        with self.assertRaises(KeyboardInterrupt):
            self.capture(self.options("--candidate-apk", str(self.candidate)), sleep=sleep)
        self.assertEqual(self.process.signals, [signal.SIGINT])
        self.assertEqual(self.commands[-1], recorder.adb("install", "-r", self.normal))
        self.assertGreaterEqual(self.clock.now, 61)
        self.assertEqual(self.events[-1], "unlocked")

    def test_restore_failure_is_reported_without_raw_output_and_unlocks(self):
        def failing(command, **kwargs):
            result = self.run_command(command, **kwargs)
            if command[-1] == str(self.normal):
                return SimpleNamespace(returncode=1, stdout=b"private-device-info")
            return result
        with self.assertRaisesRegex(recorder.RecorderError, "normal app restoration"):
            self.capture(self.options("--candidate-apk", str(self.candidate)), run=failing)
        self.assertEqual(self.events[-1], "unlocked")
        self.assertFalse(any("private-device" in text for text in self.emitted))

    def test_stop_owned_uses_exact_process_sigint_and_bounded_fallback(self):
        process = Mock()
        process.poll.return_value = None
        process.wait.side_effect = [subprocess.TimeoutExpired("owned", 5),
                                    subprocess.TimeoutExpired("owned", 3), 0]
        recorder.stop_owned(process)
        process.send_signal.assert_called_once_with(signal.SIGINT)
        process.terminate.assert_called_once_with()
        process.kill.assert_called_once_with()
        self.assertEqual([c.kwargs["timeout"] for c in process.wait.call_args_list], [5, 3, 3])

    def test_lock_timeout_closes_fd_and_never_unlocks_unowned_lock(self):
        with patch.object(recorder.os, "open", return_value=44), \
             patch.object(recorder.os, "fstat", return_value=SimpleNamespace(st_mode=0o100600)), \
             patch.object(recorder.os, "close") as close, \
             patch.object(recorder.fcntl, "flock", side_effect=BlockingIOError) as flock:
            with self.assertRaises(recorder.RecorderError):
                with recorder.emulator_lock(clock=self.clock.read, sleep=lambda _: self.clock.sleep(3600)):
                    self.fail("An occupied lock must not be entered")
        close.assert_called_once_with(44)
        self.assertTrue(all(c.args[1] != recorder.fcntl.LOCK_UN for c in flock.call_args_list))


if __name__ == "__main__":
    unittest.main()
