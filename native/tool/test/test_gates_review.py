"""Offline regressions named for docs/native/reviews/gates.md finding numbers.

No real remote or device is used. Sensitive-shaped scanner probes stay in memory.
"""
import contextlib
import fcntl
import signal
import time
import io
import os
from pathlib import Path
import runpy
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

TOOL = Path(__file__).resolve().parents[1]


class GatesReviewTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="oc-gates-review-")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        shutil.copytree(TOOL, self.repo / "native/tool", ignore=shutil.ignore_patterns("__pycache__"))
        self.env = dict(os.environ, OC_LOCK_WAIT="1", OC_MIN_FREE_MB="0", OC_T2_MIN_DISK_GB="0")
        self.env.pop("OC_VPS_HOST", None)
        self.env["HOME"] = str(self.root / "home")
        Path(self.env["HOME"]).mkdir()
        self.env["OC_BUILD_LOCK_FILE"] = str(self.root / "build.lock")
        self.git("init", "-q", "-b", "base")
        self.git("config", "user.name", "Gate Test")
        self.git("config", "user.email", "gate" + "@users.noreply.github.com")
        self.git("config", "commit.gpgsign", "false")
        self.commit()
        self.git("checkout", "-qb", "work")

    def run_cmd(self, *args, env=None, **kw):
        return subprocess.run(args, cwd=self.repo, env=env or self.env,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                              timeout=20, **kw)

    def git(self, *args):
        result = self.run_cmd("git", *args)
        self.assertEqual(result.returncode, 0, "fixture git command failed")
        return result.stdout.strip()

    def commit(self):
        self.git("add", "-A")
        self.git("commit", "-qm", "test(tool): fixture [skip ci]", "--allow-empty")

    def script(self, name, *args, env=None):
        return self.run_cmd("bash", str(self.repo / "native/tool" / name), *args, env=env)

    def executable(self, path, text):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("#!/usr/bin/env bash\nset -eu\n" + text)
        path.chmod(0o755)

    def test_GATES_01_reject_expanding_push_options(self):
        self.git("init", "--bare", "-q", str(self.root / "remote.git"))
        self.git("remote", "add", "test", str(self.root / "remote.git"))
        for option in ("--tags", "--mirror", "--all", "--follow-tags", "--repo=other"):
            with self.subTest(option=option):
                r = self.script("scan_push.sh", "--push", "test", "work:refs/heads/work", option)
                self.assertEqual(r.returncode, 2)

    def test_GATES_01_push_pins_scanned_commit_and_disables_follow_tags(self):
        self.git("init", "--bare", "-q", str(self.root / "remote.git"))
        self.git("remote", "add", "test", str(self.root / "remote.git"))
        self.git("config", "push.followTags", "true")
        self.git("tag", "-a", "unscanned", "-m", "tag metadata")
        scanned = self.git("rev-parse", "HEAD")
        self.git("commit", "--allow-empty", "-qm", "later [skip ci]")
        later = self.git("rev-parse", "HEAD")
        self.git("reset", "--hard", scanned)
        real_git = shutil.which("git")
        self.executable(self.root / "bin/git", f'''
for a in "$@"; do
  if [[ "$a" == push ]]; then
    {real_git} -C "$REVIEW_REPO" update-ref refs/heads/work "$REVIEW_LATER"
    break
  fi
done
exec {real_git} "$@"
''')
        env = dict(self.env, PATH=str(self.root / "bin") + ":" + self.env["PATH"],
                   REVIEW_REPO=str(self.repo), REVIEW_LATER=later)
        r = self.script("scan_push.sh", "--push", "test", "work:refs/heads/work", env=env)
        self.assertEqual(r.returncode, 0)
        self.assertEqual(self.git("--git-dir=" + str(self.root / "remote.git"), "rev-parse", "work"), scanned)
        self.assertEqual(self.git("--git-dir=" + str(self.root / "remote.git"), "tag"), "")

    def scanner(self):
        # Load the command's pure rules without writing synthetic addresses to disk.
        with patch.object(sys, "argv", ["scan_push.py", "--patch"]), \
             patch.object(sys, "stdin", io.TextIOWrapper(io.BytesIO(b""))), \
             patch.object(sys, "exit"), contextlib.redirect_stdout(io.StringIO()):
            return runpy.run_path(str(TOOL / "lib/scan_push.py"))

    def test_GATES_02_private_hosts_are_not_range_boundaries(self):
        scan = self.scanner()["private_ip"]
        for tail in ("0", "255", "7/12345", "7/24"):
            with self.subTest(case=tail):
                host = str(5 * 2) + ".4.5." + tail
                self.assertTrue(list(scan("https://" + host)), "private URL host was exempted")
        self.assertTrue(list(scan(str(5 * 2) + ".4.5.7/999")))
        self.assertFalse(list(scan(str(5 * 2) + ".0.0.0/8")))
        for suffix in ("/64", "/12345"):
            self.assertTrue(list(scan("fd" + "00::1" + suffix)))
        self.assertFalse(list(scan("fc" + "00::/7")))

    def test_GATES_03_forbidden_rename_and_empty_destination(self):
        for name in ("renamed.jks", "key.properties", "empty.keystore", "odd\nname.p12"):
            with self.subTest(name=name.replace("\n", "_")):
                self.git("reset", "--hard", "base")
                source = self.repo / "source.bin"
                source.write_bytes(b"opaque\x00bytes")
                self.commit()
                if name.startswith("empty"):
                    (self.repo / name).touch()
                else:
                    source.rename(self.repo / name)
                self.commit()
                self.assertEqual(self.script("scan_push.sh", "base").returncode, 1)

    def test_GATES_04_unrelated_base_cannot_pass_write_set_or_T1(self):
        self.git("checkout", "--orphan", "unrelated")
        self.git("rm", "-rqf", ".")
        (self.repo / "unrelated").touch()
        self.commit()
        self.git("checkout", "work")
        for script, args in (("check_write_set.sh", ("--globs", "native/**", "--base", "unrelated")),
                             ("t1.sh", ("--base", "unrelated", "--dry-run"))):
            with self.subTest(script=script):
                self.assertEqual(self.script(script, *args).returncode, 2)

    def fake_gradle(self):
        self.executable(self.repo / "native/gradlew", '''
printf '%s\\n' "$*" >>"$REVIEW_GRADLE_LOG"
if [[ "$*" == *'check --repo ..'* ]]; then
  test -f ../lib/l10n/app_en.arb
fi
''')
        self.executable(self.root / "jdk/bin/java", "echo '    java.specification.version = 21' >&2\n")
        self.env["JAVA_HOME"] = str(self.root / "jdk")
        self.env["REVIEW_GRADLE_LOG"] = str(self.root / "gradle.log")

    def device_fixture(self):
        self.fake_gradle()
        self.executable(self.root / "bin/aapt2", "echo \"package: name='test.app' versionCode='10000'\"\n")
        self.executable(self.root / "bin/apksigner", "echo 'Signer #1 certificate SHA-256 digest: " + "A" * 64 + "'\n")
        self.executable(self.root / "bin/adb", '''
case "$*" in
  *window_animation_scale" "1*) [[ "${REVIEW_FAIL_RESTORE:-0}" != 1 ]] ;;
  *get-state*) echo device ;;
  *ro.kernel.qemu*) echo 1 ;;
  *install*)
    if [[ "${REVIEW_BLOCK_INSTALL:-0}" == 1 ]]; then
      touch "$REVIEW_READY"
      trap 'touch "$REVIEW_CHILD_STOPPED"; exit 143' TERM
      while :; do sleep 0.05; done
    fi ;;
esac
''')
        self.executable(self.repo / "native/tool/qa/a11y_sweep.sh", "exit 0\n")
        flow = self.repo / "native/e2e/runner/run.py"
        flow.parent.mkdir(parents=True)
        flow.write_text("""import os, signal, time
from pathlib import Path
if os.environ.get('REVIEW_BLOCK_FLOW') == '1':
    child_ready = Path(os.environ['REVIEW_READY'] + '.child')
    if os.fork() == 0:
        def stopped(*_):
            time.sleep(.3)
            Path(os.environ['REVIEW_CHILD_STOPPED']).touch()
            exit(143)
        signal.signal(signal.SIGTERM, stopped)
        child_ready.touch()
        while True: time.sleep(.05)
    signal.signal(signal.SIGTERM, lambda *_: exit(143))
    while not child_ready.exists(): time.sleep(.01)
    Path(os.environ['REVIEW_READY']).touch()
    while True: time.sleep(.05)
""")
        for name in ("app.apk", "bench.apk"):
            (self.root / name).touch()
        self.env.update(PATH=str(self.root / "bin") + ":" + self.env["PATH"],
                        AAPT2=str(self.root / "bin/aapt2"), APKSIGNER=str(self.root / "bin/apksigner"),
                        ADB=str(self.root / "bin/adb"), OC_EMULATOR_LOCK=str(self.root / "emulator.lock"),
                        REVIEW_READY=str(self.root / "ready"), REVIEW_CHILD_STOPPED=str(self.root / "stopped"))

    def t3_args(self):
        return (str(self.root / "app.apk"), "--signer", "A" * 64)

    def wait_ready(self, process):
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            if (self.root / "ready").exists():
                return
            if process.poll() is not None:
                self.fail("mock ladder exited before reaching blocking step")
            time.sleep(.02)
        self.fail("mock ladder did not reach blocking step")

    def test_GATES_07_T2_and_T3_obey_shared_build_slot(self):
        self.device_fixture()
        with open(self.env["OC_BUILD_LOCK_FILE"], "w") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            for name, args in (("t2.sh", ("--where", "local", "--steps", "build-logic")),
                               ("t3.sh", self.t3_args())):
                with self.subTest(ladder=name):
                    result = self.script(name, *args)
                    self.assertEqual(result.returncode, 2)
                    self.assertFalse((self.root / "gradle.log").exists())
        self.assertEqual(self.script("t2.sh", "--where", "local", "--steps", "build-logic").returncode, 0)
        self.assertEqual(self.script("t3.sh", *self.t3_args()).returncode, 0)

    def test_GATES_08_T3_and_T4_cancel_children_before_done_file(self):
        self.device_fixture()
        self.executable(self.root / "bin/cut", "echo 0\n")
        real_ps = shutil.which("ps")
        self.executable(self.root / "bin/ps", f'''
if [[ "$*" == '-eo args=' ]]; then exit 0; fi
exec {real_ps} "$@"
''')
        perf = self.repo / "native/tool/perf"
        (perf / "journeys.tsv").write_text("PJ-01\tTest\nPJ-01f\tTest\n")
        self.executable(perf / "restore_device.sh", "exit 0\n")
        for script, args, block in (("t3.sh", self.t3_args(), "REVIEW_BLOCK_FLOW"),
                                    ("t4.sh", ("--apks", str(self.root)), "REVIEW_BLOCK_INSTALL")):
            with self.subTest(ladder=script):
                for f in ("ready", "ready.child", "stopped"):
                    (self.root / f).unlink(missing_ok=True)
                env = dict(self.env, **{block: "1"}, OC_RUN_ID="cancel")
                with open(self.root / "ladder.log", "w") as log:
                    process = subprocess.Popen(["bash", str(self.repo / "native/tool" / script), *args],
                                               cwd=self.repo, env=env, stdout=log, stderr=log, start_new_session=True)
                    try:
                        self.wait_ready(process)
                        process.send_signal(signal.SIGTERM)
                        process.wait(timeout=5)
                        done = self.repo / "native/build" / ("native-" + script[:2]) / "cancel/exit-code"
                        self.assertEqual(done.read_text().strip(), "2")
                        self.assertTrue((self.root / "stopped").exists())
                    finally:
                        try:
                            os.killpg(process.pid, signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                        process.wait()

    def test_GATES_08_cleanup_failure_cannot_pass(self):
        # Exercise the same cleanup hook used by both device ladders, including an ordinary successful exit.
        harness = self.repo / "cleanup.sh"
        harness.write_text('''source native/tool/lib/common.sh
oc_run_init native-t4 cleanup
cleanup() { return 1; }
OC_CLEANUP=cleanup
exit 0
''')
        self.assertEqual(self.run_cmd("bash", str(harness)).returncode, 2)
        self.assertEqual((self.repo / "native/build/native-t4/cleanup/exit-code").read_text().strip(), "2")
        self.device_fixture()
        self.env["REVIEW_FAIL_RESTORE"] = "1"
        self.assertEqual(self.script("t3.sh", *self.t3_args()).returncode, 2)

    def test_GATES_10_T1_compiles_android_without_host_test_sources(self):
        module = self.repo / "native/core/common"
        module.mkdir(parents=True)
        (module / "build.gradle.kts").write_text('plugins { id("oc.kmp.library") }\n')
        result = self.script("t1.sh", "--base", "base", "--where", "local", "--dry-run")
        self.assertEqual(result.returncode, 0)
        self.assertIn(":core:common:desktopTest", result.stdout)
        self.assertIn(":core:common:compileAndroidMain", result.stdout)

    def test_GATES_11_VPS_T1_checks_strings_against_local_repo_inputs(self):
        self.fake_gradle()
        module = self.repo / "native/ui/words"
        module.mkdir(parents=True)
        (module / "build.gradle.kts").write_text('plugins { id("oc.kmp.library") }\n')
        arbs = self.repo / "lib/l10n"
        arbs.mkdir(parents=True)
        (arbs / "app_en.arb").write_text("{}")
        self.executable(self.repo / "native/tool/vps_gradle.sh", '''
[[ "$*" != *'check --repo ..'* ]] || exit 1
exit 0
''')
        result = self.script("t1.sh", "--base", "base", "--where", "vps")
        self.assertEqual(result.returncode, 0)
        self.assertTrue((self.root / "gradle.log").exists())
        self.assertIn("check --repo ..", (self.root / "gradle.log").read_text())

    def vps_fixture(self):
        self.fake_gradle()
        remote = self.root / "vpshome"
        remote.mkdir()
        self.env.update(FAKE_VPS_HOME=str(remote), OC_VPS_HOST="vps",
                        PATH=str(self.root / "bin") + ":" + self.env["PATH"])
        self.executable(self.root / "bin/ssh", '''
while [[ "${1:-}" == -o ]]; do shift 2; done
shift
export HOME="$FAKE_VPS_HOME"
cd "$HOME"
if (($# == 1)); then exec bash -c "$1"; else exec "$@"; fi
''')
        self.executable(self.repo / "native/gradlew", '''
if [[ "$*" == *assemble* ]]; then
  test -f current-source || exit 1
  test "$(cat current-source)" == current || exit 1
fi
if [[ "$*" == *recordRoborazzi* ]]; then
  mkdir -p ui/kit/src/screenshots
  echo new-golden > ui/kit/src/screenshots/recorded.png
fi
if [[ "$*" == *desktopTest* && "${REVIEW_BLOCK_VPS:-0}" == 1 ]]; then
  touch "$REVIEW_READY"
  while [[ ! -f "$REVIEW_RELEASE" ]]; do sleep .02; done
fi
printf '%s\\n' "$*" >> "$REVIEW_GRADLE_LOG"
''')
        return remote

    def test_GATES_05_remote_lock_covers_source_sync(self):
        remote = self.vps_fixture()
        (self.repo / "native/current-source").write_text("before")
        self.assertEqual(self.script("vps_gradle.sh", "--unit", "u", "--", "help").returncode, 0)
        (self.repo / "native/current-source").write_text("after")
        with open(remote / ".oc-native-build.lock", "w") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            self.assertEqual(self.script("vps_gradle.sh", "--unit", "u", "--", "help").returncode, 2)
            self.assertEqual((remote / "wt/ocn-u/native/current-source").read_text(), "before")

    def test_GATES_05_default_workspace_identifies_worktree(self):
        self.vps_fixture()
        r = self.script("vps_gradle.sh", "--dry-run", "--", "help")
        self.assertEqual(r.returncode, 0)
        self.assertNotIn("ocn-adhoc", r.stdout)
        other = self.root / "other"
        shutil.copytree(self.repo, other)
        r2 = subprocess.run(["bash", str(other / "native/tool/vps_gradle.sh"), "--dry-run", "--", "help"],
                            cwd=other, env=self.env, stdout=subprocess.PIPE, text=True, timeout=10)
        # Remote command contains the chosen per-worktree workspace.
        self.assertNotEqual(r.stdout, r2.stdout)

    def test_GATES_05_T2_holds_remote_exclusion_between_steps(self):
        remote = self.vps_fixture()
        tasks = self.repo / "native/tool/ladder/t2.tasks"
        tasks.write_text("tests\t:core:common:desktopTest\nassemble\t:androidApp:assemblePreviewRelease\n")
        (self.repo / "native/current-source").write_text("current")
        self.executable(self.root / "bin/ssh", '''
while [[ "${1:-}" == -o ]]; do shift 2; done
shift
export HOME="$FAKE_VPS_HOME"
cd "$HOME"
if (($# != 1)); then exec "$@"; fi
set +e
bash -c "$1"
rc=$?
if [[ "$1" == *gradlew*desktopTest* ]]; then
  flock -n "$HOME/.oc-native-t2.lock" touch "$REVIEW_INTERLEAVED"
fi
exit "$rc"
''')
        self.env.update(REVIEW_INTERLEAVED=str(self.root / "interleaved"),
                        REVIEW_BLOCK_VPS="1", REVIEW_READY=str(self.root / "ready"),
                        REVIEW_RELEASE=str(self.root / "release"), OC_RUN_ID="batch")
        with open(self.root / "t2.log", "w") as log:
            process = subprocess.Popen(["bash", str(self.repo / "native/tool/t2.sh"), "--where", "vps"],
                                       cwd=self.repo, env=self.env, stdout=log, stderr=log, start_new_session=True)
            try:
                self.wait_ready(process)
                # A test-level lock failure must happen before any source is replaced, for both lanes.
                for lock_name in (".oc-native-t2.lock", ".oc-native-build.lock"):
                    with open(remote / lock_name, "w") as lock:
                        with self.assertRaises(BlockingIOError):
                            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                (self.root / "release").touch()
                process.wait(timeout=10)
                self.assertEqual(process.returncode, 0)
                self.assertFalse((self.root / "interleaved").exists())
            finally:
                (self.root / "release").touch()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()

    def test_GATES_06_T2_synchronizes_APK_workspace_before_assembly(self):
        self.vps_fixture()
        (self.repo / "native/current-source").write_text("current")
        result = self.script("t2.sh", "--where", "vps", "--steps", "assemble")
        self.assertEqual(result.returncode, 0)
        self.assertIn("assemblePreviewRelease", (self.root / "gradle.log").read_text())

    def test_GATES_09_record_returns_only_selected_module_goldens(self):
        self.vps_fixture()
        unrelated = self.repo / "native/ui/other/src/screenshots/keep.png"
        unrelated.parent.mkdir(parents=True)
        unrelated.write_text("keep")
        result = self.script("vps_gradle.sh", "--unit", "u", "--", ":ui:kit:recordRoborazziAndroidHostTest")
        self.assertEqual(result.returncode, 0)
        recorded = self.repo / "native/ui/kit/src/screenshots/recorded.png"
        self.assertTrue(recorded.exists())
        self.assertEqual(recorded.read_text().strip(), "new-golden")
        self.assertEqual(unrelated.read_text(), "keep")

    def test_GATES_09_failed_golden_retrieval_cannot_pass(self):
        self.vps_fixture()
        real_rsync = shutil.which("rsync")
        self.executable(self.root / "bin/rsync", f'''
for a in "$@"; do
  if [[ "$a" == vps:*src/screenshots/* ]]; then exit 23; fi
done
exec {real_rsync} "$@"
''')
        result = self.script("vps_gradle.sh", "--unit", "u", "--", ":ui:kit:recordRoborazziAndroidHostTest")
        self.assertEqual(result.returncode, 2)

    def test_GATES_05_remote_lock_covers_result_collection(self):
        remote = self.vps_fixture()
        real_rsync = shutil.which("rsync")
        self.executable(self.root / "bin/rsync", f'''
for a in "$@"; do
  if [[ "$a" == vps:* ]]; then
    if flock -n "$FAKE_VPS_HOME/.oc-native-build.lock" true; then exit 23; fi
    touch "$REVIEW_PULL_LOCKED"
    break
  fi
done
exec {real_rsync} "$@"
''')
        self.env["REVIEW_PULL_LOCKED"] = str(self.root / "pull-locked")
        self.assertEqual(self.script("vps_gradle.sh", "--unit", "u", "--", "help").returncode, 0)
        self.assertTrue((self.root / "pull-locked").exists())

    def test_GATES_06_no_sync_refuses_missing_stale_or_modified_source(self):
        remote = self.vps_fixture()
        args = ("--unit", "u", "--no-sync", "--", "help")
        self.assertEqual(self.script("vps_gradle.sh", *args).returncode, 2)
        (self.repo / "native/current-source").write_text("current")
        self.assertEqual(self.script("vps_gradle.sh", "--unit", "u", "--", "help").returncode, 0)
        self.assertEqual(self.script("vps_gradle.sh", *args).returncode, 0)
        (remote / "wt/ocn-u/native/current-source").write_text("stale")
        self.assertEqual(self.script("vps_gradle.sh", *args).returncode, 2)

    def test_GATES_12_short_private_hostname_is_fully_masked(self):
        mask = self.scanner()["mask"]
        value = "p.ts" + ".net"
        self.assertNotIn(value, mask(value, "private-host"))


if __name__ == "__main__":
    unittest.main()
