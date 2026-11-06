"""E00-S09 upgrade gate: the APK refusal rules, the plan it runs, the per-assertion verdicts and the evidence record.
No device: aapt2/apksigner are small fakes on PATH and adb is recorded."""
import datetime as dt
import io
import json
import os
import pathlib
import stat
import sys
import tempfile
import types
import unittest
from contextlib import redirect_stdout
from unittest import mock

from runner import hooks, run, upgrade

PKG = upgrade.APP_ID
OWNER = "1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C"
CI = "842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"


def colon(sha):
    return ":".join(sha[i:i + 2] for i in range(0, len(sha), 2)).lower()


FAKE_TOOL = r'''#!{py}
import json, os, sys
apk = sys.argv[-1]
spec = json.load(open(apk + ".json"))
if os.path.basename(sys.argv[0]) == "aapt2":
    print("package: name='%s' versionCode='%d' versionName='%s' platformBuildVersionName='16'"
          % (spec["package"], spec["vc"], spec["vn"]))
else:
    if not spec.get("valid", True):
        print("DOES NOT VERIFY"); sys.exit(1)
    for i, s in enumerate(spec["signers"], 1):
        print("Signer #%d certificate DN: CN=x" % i)
        print("Signer #%d certificate SHA-256 digest: %s" % (i, s))
'''


class Fixture(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        bin_dir = self.tmp / "bin"
        bin_dir.mkdir()
        for name in ("aapt2", "apksigner"):
            p = bin_dir / name
            p.write_text(FAKE_TOOL.replace("{py}", sys.executable))
            p.chmod(p.stat().st_mode | stat.S_IEXEC)
        self.env = mock.patch.dict(os.environ, {"PATH": f"{bin_dir}:{os.environ['PATH']}",
                                                "ANDROID_AVD_HOME": str(self.tmp / "avd")})
        self.env.start()
        self.addCleanup(self.env.stop)

    def apk(self, name, vc, signers=(OWNER,), package=PKG, vn="2.1.4", valid=True):
        path = self.tmp / name
        path.write_bytes(b"PK\x03\x04" + name.encode())
        (self.tmp / (name + ".json")).write_text(json.dumps(
            {"package": package, "vc": vc, "vn": vn, "signers": [colon(s) for s in signers], "valid": valid}))
        return str(path)

    def main(self, *argv):
        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = upgrade.main(list(argv))
        return rc, buf.getvalue()


class ApkGateTest(Fixture):
    def facts(self, apk):
        return upgrade.apk_facts(apk, upgrade.find_tool("aapt2"), upgrade.find_tool("apksigner"))

    def test_B_MIGR_004_same_signer_same_package_higher_version_passes(self):
        lines = upgrade.apk_gates(self.facts(self.apk("r.apk", 62)), self.facts(self.apk("n.apk", 10062)), OWNER)
        self.assertTrue(all(l.ok for l in lines), [l.text() for l in lines])

    def test_B_MIGR_004_a_different_signer_is_refused(self):
        lines = upgrade.apk_gates(self.facts(self.apk("r.apk", 62)),
                                  self.facts(self.apk("n.apk", 10062, signers=(CI,))), OWNER)
        bad = {l.id for l in lines if not l.ok}
        self.assertEqual({"signer-native", "same-signer"}, bad)

    def test_B_MIGR_004_a_second_signer_or_a_broken_signature_is_refused(self):
        two = upgrade.apk_gates(self.facts(self.apk("r.apk", 62)),
                                self.facts(self.apk("n.apk", 10062, signers=(OWNER, CI))), OWNER)
        self.assertIn("signer-native", {l.id for l in two if not l.ok})
        broken = upgrade.apk_gates(self.facts(self.apk("r.apk", 62, valid=False)),
                                   self.facts(self.apk("n.apk", 10062)), OWNER)
        self.assertIn("signer-flutter", {l.id for l in broken if not l.ok})

    def test_B_MIGR_004_native_version_code_must_be_higher_not_equal(self):
        for vc in (62, 61):
            lines = upgrade.apk_gates(self.facts(self.apk("r.apk", 62)), self.facts(self.apk("n.apk", vc)), OWNER)
            self.assertEqual(["version-order"], [l.id for l in lines if not l.ok], vc)

    def test_B_MIGR_004_a_different_package_is_refused(self):
        lines = upgrade.apk_gates(self.facts(self.apk("r.apk", 62)),
                                  self.facts(self.apk("n.apk", 10062, package=PKG + ".native")), OWNER)
        self.assertEqual(["same-package"], [l.id for l in lines if not l.ok])

    def test_signer_digests_are_compared_without_colons_or_case(self):
        self.assertEqual([OWNER], upgrade.parse_signers(f"Signer #1 certificate SHA-256 digest: {colon(OWNER)}\n"))

    def test_B_MIGR_004_the_script_refuses_with_exit_2_and_a_fail_line(self):
        rc, out = self.main("--flutter-apk", self.apk("r.apk", 62), "--native-apk",
                            self.apk("n.apk", 10062, signers=(CI,)), "--expect-signer", OWNER, "--avd", "OCN_A")
        self.assertEqual(2, rc)
        self.assertIn("FAIL signer-native:", out)
        self.assertIn("REFUSED", out)

    def test_a_missing_apk_is_refused(self):
        rc, out = self.main("--flutter-apk", str(self.tmp / "nope.apk"), "--native-apk", self.apk("n.apk", 10062),
                            "--expect-signer", OWNER, "--avd", "OCN_A")
        self.assertEqual(2, rc)
        self.assertIn("FAIL apks:", out)


class PlanTest(Fixture):
    def plan(self, *extra):
        rc, out = self.main("--flutter-apk", self.apk("r.apk", 62), "--native-apk", self.apk("n.apk", 10062),
                            "--expect-signer", OWNER, "--seed-home", str(self.tmp / "seeds"), "--dry-run", *extra)
        self.assertEqual(0, rc, out)
        return json.loads(out[out.index("{"):])

    def test_fresh_avd_on_port_5584_runs_part_a_and_saves_the_snapshot(self):
        p = self.plan("--avd", "OCN_A")
        self.assertFalse(p["restore"])
        self.assertEqual("emulator-5584", p["serial"])
        self.assertIn("-wipe-data", p["emulator"])
        self.assertEqual("5584", p["emulator"][p["emulator"].index("-port") + 1])
        self.assertIn("-no-window", p["emulator"])
        cmd = p["runPy"]
        self.assertEqual("E2E-39", cmd[cmd.index("--flows") + 1])
        self.assertEqual("native", cmd[cmd.index("--app") + 1])
        self.assertIn("--apk", cmd)
        self.assertEqual("flutter-2.1.4-seeded", cmd[cmd.index("--save-seed-snapshot") + 1])
        self.assertIn("--no-retry", cmd)
        self.assertNotIn("--seeded", cmd)

    def test_a_seeded_snapshot_with_its_fixture_state_is_restored_and_part_a_skipped(self):
        (self.tmp / "avd/OCN_A.avd/snapshots/flutter-2.1.4-seeded").mkdir(parents=True)
        (self.tmp / "seeds/flutter-2.1.4-seeded/fx").mkdir(parents=True)
        (self.tmp / "seeds/flutter-2.1.4-seeded/fx/secrets.env").write_text("FX_OC2_PASSWORD=x\n")
        p = self.plan("--avd", "OCN_A")
        self.assertTrue(p["restore"])
        self.assertEqual("flutter-2.1.4-seeded", p["emulator"][p["emulator"].index("-snapshot") + 1])
        self.assertNotIn("-wipe-data", p["emulator"])
        self.assertIn("--seeded", p["runPy"])
        self.assertNotIn("--apk", p["runPy"])
        self.assertNotIn("--save-seed-snapshot", p["runPy"])

    def test_a_snapshot_without_its_fixture_state_is_not_restored(self):
        (self.tmp / "avd/OCN_A.avd/snapshots/flutter-2.1.4-seeded").mkdir(parents=True)
        self.assertFalse(self.plan("--avd", "OCN_A")["restore"])

    def test_reseed_runs_part_a_even_with_a_snapshot(self):
        (self.tmp / "avd/OCN_A.avd/snapshots/flutter-2.1.4-seeded").mkdir(parents=True)
        (self.tmp / "seeds/flutter-2.1.4-seeded/fx").mkdir(parents=True)
        (self.tmp / "seeds/flutter-2.1.4-seeded/fx/secrets.env").write_text("FX_OC2_PASSWORD=x\n")
        self.assertFalse(self.plan("--avd", "OCN_A", "--reseed")["restore"])

    def test_an_avd_or_use_running_is_required(self):
        rc, out = self.main("--flutter-apk", self.apk("r.apk", 62), "--native-apk", self.apk("n.apk", 10062),
                            "--expect-signer", OWNER)
        self.assertEqual(2, rc)
        self.assertIn("FAIL emulator:", out)

    def test_only_a_folder_this_script_made_is_emptied(self):
        mine, theirs = self.tmp / "mine", self.tmp / "theirs"
        upgrade.reset_owned_dir(mine)
        (mine / "old").write_text("x")
        upgrade.reset_owned_dir(mine)
        self.assertFalse((mine / "old").exists())
        theirs.mkdir()
        (theirs / "keep").write_text("x")
        with self.assertRaises(upgrade.Refused):
            upgrade.reset_owned_dir(theirs)
        self.assertTrue((theirs / "keep").exists())

    def test_fixture_passwords_map_to_the_flow_variables_and_are_written_private(self):
        fx = self.tmp / "fx"
        fx.mkdir()
        (fx / "secrets.env").write_text("FX_OC2_PASSWORD=a1\nFX_OC2_B_PASSWORD=b2\nFX_GIT_PASSWORD=c3\n")
        env = upgrade.fixtures_env(fx)
        self.assertEqual({"SERVER_PASSWORD": "a1", "SERVER_B_PASSWORD": "b2"}, env)
        upgrade.write_private_json(fx / "e.json", {"env": env})
        self.assertEqual(0o600, (fx / "e.json").stat().st_mode & 0o777)


class VerdictTest(unittest.TestCase):
    def debug_tree(self, commands):
        root = pathlib.Path(tempfile.mkdtemp())
        d = root / "E2E-39" / "attempt-1" / "debug"
        d.mkdir(parents=True)
        (d / "commands-(E2E-39-upgrade-in-place).json").write_text(json.dumps(commands))
        return root

    def cmd(self, label, status):
        return {"command": {"runFlowCommand": {"label": label, "commands": []}}, "metadata": {"status": status}}

    def test_each_part_c_assertion_takes_the_status_of_its_labelled_step(self):
        root = self.debug_tree([self.cmd("S4 Part C on native", "COMPLETED"), self.cmd("S6 servers", "COMPLETED"),
                                self.cmd("UC-terminal pwd && ls", "FAILED"), self.cmd("S12 schedule", "SKIPPED")])
        out = {l.id: l for l in upgrade.ui_verdicts(upgrade.maestro_statuses(root), flow_passed=False)}
        self.assertTrue(out["conversations"].ok)
        self.assertTrue(out["servers"].ok)
        self.assertFalse(out["terminal"].ok)
        self.assertFalse(out["schedule"].ok)  # S12 never completed
        self.assertIn("not reached", out["voice-ready"].detail)

    def test_every_ui_assertion_label_exists_in_the_flow(self):
        flow = (upgrade.E2E / "flows/upgrade/E2E-39-upgrade-in-place.yaml").read_text()
        for aid, _, labels in upgrade.UI_ASSERTIONS:
            for label in labels:
                self.assertIn(f'label: "{label} ', flow, (aid, label))

    def test_every_screen_is_photographed_by_part_c(self):
        part_c = (upgrade.E2E / "flows/upgrade/_parts/partC-assert.yaml").read_text()
        for screen in upgrade.SCREENS:
            self.assertIn(f"'-{screen}'", part_c, screen)

    def test_missing_device_state_is_a_failure(self):
        out = upgrade.state_verdicts(pathlib.Path(tempfile.mkdtemp()))
        self.assertEqual(1, len(out))
        self.assertFalse(out[0].ok)

    def test_lines_print_pass_or_fail_first(self):
        self.assertEqual("PASS a: b", upgrade.Line("a", "b", True).text())
        self.assertEqual("FAIL a: b (c)", upgrade.Line("a", "b", False, "c").text())


class RecordTest(unittest.TestCase):
    facts = {"file": "x.apk", "package": PKG, "versionName": "2.1.4", "versionCode": 62, "sha256": "ab",
             "signers": [OWNER]}

    def test_the_record_names_versions_signers_and_a_verdict_per_check(self):
        dest = pathlib.Path(tempfile.mkdtemp()) / "native-switch-2026-11-06"
        native = dict(self.facts, versionCode=10062, versionName="3.0.0")
        readme = upgrade.write_record(
            dest, flutter=self.facts, native=native, expect_signer=colon(OWNER), avd="OCN_A",
            seeded_from="flutter-2.1.4-seeded", gates=[upgrade.Line("same-signer", "Same signer", True)],
            verdicts=[upgrade.Line("terminal", "pwd", False, "a|b")], shots=[("native-chat.png", "The chat.")],
            run_out=pathlib.Path("/out"), today=dt.date(2026, 11, 6))
        text = readme.read_text()
        self.assertIn("**Verdict: FAIL**", text)
        self.assertIn("2.1.4 (62)", text)
        self.assertIn("3.0.0 (10062)", text)
        self.assertIn(OWNER, text)
        self.assertIn("| terminal | pwd | **FAIL** | a/b |", text)
        self.assertIn("`screens/native-chat.png`: The chat.", text)

    def test_an_earlier_record_of_the_same_day_is_never_overwritten(self):
        root = pathlib.Path(tempfile.mkdtemp())
        with mock.patch.object(upgrade, "REPO", root):
            first = upgrade.evidence_dir(types.SimpleNamespace(evidence_dir=None), dt.date(2026, 11, 6))
            first.mkdir(parents=True)
            (first / "README.md").write_text("x")
            second = upgrade.evidence_dir(types.SimpleNamespace(evidence_dir=None), dt.date(2026, 11, 6))
        self.assertEqual("native-switch-2026-11-06", first.name)
        self.assertEqual("native-switch-2026-11-06-2", second.name)


class RunnerSupportTest(unittest.TestCase):
    def test_the_runner_accepts_the_gates_lock_descriptor_only_for_the_same_file(self):
        tmp = pathlib.Path(tempfile.mkdtemp())
        lock, other = tmp / "emu.lock", tmp / "other.lock"
        other.write_text("")
        with upgrade.hold_lock(str(lock)) as fd:
            with mock.patch.dict(os.environ, {"OC_EMULATOR_LOCK_FD": str(fd)}):
                with run.emulator_lock(str(lock)):
                    pass
                with self.assertRaises(run.Refused):
                    with run.emulator_lock(str(other)):
                        pass
            with self.assertRaises(run.Refused):  # without the descriptor, the held lock blocks a second run
                with run.emulator_lock(str(lock)):
                    pass

    def test_upgrade_capture_is_an_allowlisted_root_hook(self):
        self.assertTrue(hooks.HOOKS["upgrade-capture"].root)
        self.assertEqual("upgrade_capture", hooks.HOOKS["upgrade-capture"].host)

    def test_B_MIGR_004_install_native_saves_the_snapshot_and_state_before_installing(self):
        calls = []

        def adb(args, check=True, timeout=120):
            calls.append(args)
            return "versionCode=62\n" if args[:3] == ["shell", "dumpsys", "package"] else ""

        state = pathlib.Path(tempfile.mkdtemp())
        args = run.parse_args(["--app", "native", "--flows", "E2E-39", "--native-apk", "n.apk", "--state-dir",
                               str(state), "--save-seed-snapshot", "flutter-2.1.4-seeded"])
        ctx = run.HookContext(adb, PKG, args, [])
        self.assertEqual({"installed": True}, ctx.run("install-native", {}))
        self.assertEqual(["emu", "avd", "snapshot", "save", "flutter-2.1.4-seeded"], calls[0])
        self.assertEqual(["install", "-r", "n.apk"], calls[-1])
        self.assertEqual(62, json.loads((state / "before.json").read_text())["versionCode"])

    def test_upgrade_capture_refuses_an_unknown_phase(self):
        args = run.parse_args(["--app", "native", "--flows", "E2E-39", "--state-dir", tempfile.mkdtemp()])
        ctx = run.HookContext(lambda *a, **k: "", PKG, args, [])
        with self.assertRaises(hooks.HookError):
            ctx.run("upgrade-capture", {"phase": "../../etc"})

    def test_seeded_runs_tell_the_flow_to_skip_part_a(self):
        flow = (upgrade.E2E / "flows/upgrade/E2E-39-upgrade-in-place.yaml").read_text()
        self.assertIn("${SEEDED != 'true'}", flow)
        self.assertTrue(run.parse_args(["--app", "native", "--flows", "E2E-39", "--seeded"]).seeded)


if __name__ == "__main__":
    unittest.main()
