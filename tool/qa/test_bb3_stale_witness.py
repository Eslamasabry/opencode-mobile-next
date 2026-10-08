"""Mock-only witness regressions. Never invokes adb or subprocesses."""
import copy
import subprocess
import unittest
from unittest import mock

try:
    import bb3_stale_witness as witness
except ModuleNotFoundError:
    from tool.qa import bb3_stale_witness as witness


def identity(pid, parent=1, ticks=100, group=None, session=None, state="S"):
    return dict(pid=pid, startTicks=ticks, parent=parent, group=group or pid,
                session=session or pid, state=state)


def stat(value):
    fields = [value["state"], str(value["parent"]), str(value["group"]), str(value["session"])]
    fields += ["0"] * 15 + [str(value["startTicks"])]
    return f'{value["pid"]} (controlled fixture) ' + ' '.join(fields)


def metadata():
    native = f"/data/app/~~fixture/{witness.PACKAGE}-fixture/lib/x86_64"
    return dict(version=1, uid=10234, app={k: v for k, v in identity(42).items() if k != "state"},
                command=[native + "/libproot.so", "--root-id", "--kill-on-exit",
                         f"--rootfs=/data/user/0/{witness.PACKAGE}/files/linux/ubuntu",
                         "/bin/sleep", "180"],
                environment=dict(PROOT_LOADER=native + "/libproot-loader.so",
                    PROOT_TMP_DIR=f"/data/user/0/{witness.PACKAGE}/cache/proot-tmp",
                    LD_LIBRARY_PATH=native))


class MockDevice:
    serial = "emulator-5554"
    def adb(self, *args, **kwargs):
        raise AssertionError("mock witness must not reach adb")


class Fixture(witness.ControlledStaleWitness):
    def __init__(self, value=None):
        super().__init__(MockDevice(), metadata() if value is None else value)
        self.identities = {42: identity(42), 70: identity(70), 80: identity(80, parent=70),
                           81: identity(81, parent=80, group=80, session=80, ticks=101)}
        self.uid_values = {}
        self.cgroups = {42: "0::/uid_10234/pid_42", 80: "0::/shell"}
        self.argv = {80: list(self.command)}
        self.env_values = {}
        self.candidate_pids = [80]
        self.signals = []
        self.launches = []
        self.ignore_term = False

    def _identity(self, pid):
        value = self.identities.get(pid)
        return dict(value) if value else None

    def _read(self, pid, name, limit=witness.MAX_READ):
        if name == "status":
            return "Uid:\t" + "\t".join(str(v) for v in self.uid_values.get(pid, [self.uid] * 4)) + "\n"
        if name == "cmdline":
            return "\0".join(self.argv.get(pid, ["/bin/sleep", "180"])) + "\0"
        if name == "environ":
            return self.env_values.get(pid, "\0".join(f"{k}={v}" for k, v in
                (self.environment | {witness.NONCE_KEY: self.nonce}).items()) + "\0")
        if name == "cgroup":
            return self.cgroups.get(pid, "0::/shell")
        raise AssertionError(name)

    def _shell(self, script):
        self.launches.append(script)
        return stat(self.identities[70])

    def _candidates(self):
        return self.candidate_pids

    def _inventory(self):
        return {pid: dict(value) for pid, value in self.identities.items()
                if pid not in (42, 70) and value["state"] not in {"Z", "X"}}

    def _signal(self, expected, signal):
        self.signals.append((expected["pid"], expected["startTicks"], signal))
        if signal == "KILL" or not self.ignore_term:
            self.identities.pop(expected["pid"], None)
            if expected["pid"] == 80:
                self.identities.pop(70, None)


class WitnessTests(unittest.TestCase):
    def setUp(self):
        self.sleep = mock.patch.object(witness.time, "sleep", return_value=None)
        self.sleep.start()
        self.addCleanup(self.sleep.stop)

    def refuse(self, function, code):
        with self.assertRaises(witness.Refused) as caught:
            function()
        self.assertEqual(str(caught.exception), code)

    def test_metadata_rejects_missing_unknown_and_wrong_types(self):
        cases = [None, {}, metadata() | {"raw": "private"}, metadata() | {"version": True},
                 metadata() | {"uid": "10234"}, metadata() | {"uid": 0}]
        for value in cases:
            with self.subTest(value=type(value).__name__), self.assertRaises(witness.Refused):
                witness.ControlledStaleWitness(MockDevice(), value)

    def test_only_emulator_5554_is_permitted(self):
        device = MockDevice()
        device.serial = "physical-device"
        self.refuse(lambda: witness.ControlledStaleWitness(device, metadata()), "witness_device_invalid")

    def test_invalid_app_identity_is_refused(self):
        for app in [None, {}, identity(1), identity(42) | {"startTicks": "100"}]:
            self.refuse(lambda: Fixture(metadata() | {"app": app}), "witness_app_invalid")

    def test_android_app_zero_session_and_parent_are_valid_metadata(self):
        value = metadata()
        value["app"]["session"] = 0
        value["app"]["parent"] = 0
        fixture = Fixture(value)
        fixture.identities[42]["session"] = 0
        fixture.identities[42]["parent"] = 0
        self.assertEqual(fixture.start()["session"], 80)

    def test_app_negative_session_parent_and_zero_group_are_invalid(self):
        for key, invalid in (("session", -1), ("parent", -1), ("group", 0),
                             ("session", True), ("parent", "0")):
            value = metadata()
            value["app"][key] = invalid
            self.refuse(lambda: Fixture(value), "witness_app_invalid")

    def test_private_proot_rootfs_and_fixed_sleep_are_required(self):
        for command in [["/bin/sh", "-c", "sleep 180"],
                        ["/vendor/libproot.so", "--rootfs=/other", "/bin/sleep", "180"],
                        metadata()["command"][:-1] + ["181"],
                        [v.replace("/files/linux/ubuntu", "/files/linux/../ubuntu") for v in metadata()["command"]],
                        [v.replace(witness.PACKAGE, "other.package") for v in metadata()["command"]]]:
            with self.assertRaises(witness.Refused):
                Fixture(metadata() | {"command": command})

    def test_environment_rejects_extra_wrong_native_and_wrong_private_tmp(self):
        for env in [metadata()["environment"] | {"HOME": "/root"},
                    metadata()["environment"] | {"PROOT_LOADER": "/other/libproot-loader.so"},
                    metadata()["environment"] | {"PROOT_TMP_DIR": "/tmp"}]:
            self.refuse(lambda: Fixture(metadata() | {"environment": env}), "witness_environment_invalid")

    def test_protected_wrapper_is_unavailable_before_any_launch(self):
        value = metadata()
        value["command"] = [value["environment"]["LD_LIBRARY_PATH"] + "/libaiteam_sandbox.so",
                            "--", *value["command"]]
        self.refuse(lambda: Fixture(value), "witness_capability_unavailable")

    def test_app_pid_reuse_and_changed_tuple_refuse_before_launch(self):
        for field in ("startTicks", "parent", "group", "session"):
            fixture = Fixture()
            fixture.identities[42][field] += 1
            self.refuse(fixture.start, "witness_app_changed")
            self.assertEqual(fixture.launches, [])

    def test_app_uid_must_match_all_four_ids(self):
        fixture = Fixture()
        fixture.uid_values[42] = [fixture.uid, fixture.uid, 0, fixture.uid]
        self.refuse(fixture.start, "witness_app_uid_invalid")
        self.assertEqual(fixture.launches, [])

    def test_start_returns_only_identity_and_handles_su_fork(self):
        fixture = Fixture()
        # Returned controlled parent 70 and actual root 80 are distinct.
        result = fixture.start()
        self.assertEqual(result, identity(80, parent=70))
        self.assertEqual(set(result), {"pid", "startTicks", "parent", "group", "session", "state"})
        self.assertEqual(fixture.parent["pid"], 70)
        launch = fixture.launches[0]
        self.assertIn(f"su {fixture.uid} /system/bin/sh -c", launch)
        self.assertIn("exec /system/bin/setsid /system/bin/env -i", launch)
        self.assertIn("</dev/null >/dev/null 2>&1", launch)
        self.assertNotIn("runcon", launch)
        self.assertEqual(set(fixture.witnessed), {80, 81})

    def test_unique_nonce_handles_reparented_root_after_su_exit(self):
        fixture = Fixture()
        fixture.identities[80]["parent"] = 1
        self.assertEqual(fixture.start()["parent"], 1)

    def test_wrong_uid_or_extra_environment_never_publishes_root(self):
        for change in ("uid", "env"):
            fixture = Fixture()
            if change == "uid":
                fixture.uid_values[80] = [2000] * 4
            else:
                fixture.env_values[80] = "untrusted=yes\0"
            self.refuse(fixture.start, "witness_root_ownership_invalid")
            self.assertIsNone(fixture.root)
            self.assertEqual(fixture.signals, [])

    def test_group_and_session_must_equal_root_pid(self):
        for field in ("group", "session"):
            fixture = Fixture()
            fixture.identities.pop(81)
            fixture.identities[80][field] = 70
            self.refuse(fixture.start, "witness_session_invalid")
            self.assertEqual(fixture.signals, [])

    def test_same_cgroup_refuses_but_retains_proven_identity_for_cleanup(self):
        fixture = Fixture()
        fixture.cgroups[80] = fixture.cgroups[42]
        self.refuse(fixture.start, "witness_app_cgroup_shared")
        self.assertEqual(fixture.root["pid"], 80)
        fixture.cleanup()
        self.assertEqual([value[0] for value in fixture.signals], [81, 80])

    def test_malformed_cgroups_are_refused(self):
        fixture = Fixture()
        fixture.cgroups[80] = "malformed"
        self.refuse(fixture.start, "witness_cgroup_invalid")

    def test_app_cgroup_subtree_is_not_outside_the_app(self):
        fixture = Fixture()
        fixture.cgroups[80] = fixture.cgroups[42] + "/child"
        self.refuse(fixture.start, "witness_app_cgroup_shared")

    def test_cgroup_hierarchy_mismatch_and_root_only_are_unproven(self):
        self.refuse(lambda: witness.outside_app_cgroup("0::/app", "1:cpu:/shell"),
                    "witness_cgroup_invalid")
        self.assertFalse(witness.outside_app_cgroup("0::/", "0::/shell"))

    def test_wrong_actual_argv_does_not_become_a_witness(self):
        fixture = Fixture()
        fixture.argv[80] = ["/bin/sleep", "180"]
        self.refuse(fixture.start, "witness_start_timeout")
        self.assertEqual(fixture.signals, [])

    def test_discovery_timeout_after_launch_still_drains_proven_tree(self):
        fixture = Fixture()
        fixture._candidates = mock.Mock(side_effect=[witness.Refused("witness_read_failed"), [80]])
        self.refuse(fixture.start, "witness_read_failed")
        self.assertIsNotNone(fixture.parent)
        self.assertIsNone(fixture.root)
        fixture.cleanup()
        self.assertEqual(fixture.signals, [(81, 101, "TERM"), (80, 100, "TERM")])
        self.assertTrue(fixture.drained)

    def test_launched_but_undiscoverable_root_is_explicit_cleanup_refusal(self):
        fixture = Fixture()
        fixture._candidates = mock.Mock(side_effect=witness.Refused("witness_read_failed"))
        self.refuse(fixture.start, "witness_read_failed")
        self.refuse(fixture.cleanup, "witness_cleanup_unproven")
        self.assertFalse(fixture.drained)
        self.assertEqual(fixture.signals, [])
        self.assertIn(70, fixture.identities)

    def test_empty_discovery_after_launch_never_claims_clean(self):
        fixture = Fixture()
        fixture.candidate_pids = []
        self.refuse(fixture.start, "witness_start_timeout")
        self.refuse(fixture.cleanup, "witness_cleanup_unproven")
        self.assertFalse(fixture.drained)

    def test_failed_launcher_reply_is_also_treated_as_possible_launch(self):
        fixture = Fixture()
        fixture._shell = mock.Mock(side_effect=witness.Refused("witness_read_failed"))
        self.refuse(fixture.start, "witness_launch_unavailable")
        self.assertTrue(fixture.launch_attempted)
        self.assertIsNone(fixture.parent)
        fixture.cleanup()
        self.assertTrue(fixture.drained)
        self.assertNotIn(70, [value[0] for value in fixture.signals])

    def test_multiple_exact_nonce_roots_are_refused(self):
        fixture = Fixture()
        fixture.identities[90] = identity(90)
        fixture.argv[90] = list(fixture.command)
        fixture.candidate_pids += [90]
        self.refuse(fixture.start, "witness_root_ambiguous")
        self.assertIsNone(fixture.root)

    def test_child_escaped_session_or_group_refuses_all_signals(self):
        for field in ("session", "group"):
            fixture = Fixture()
            fixture.identities[81][field] = 81
            self.refuse(fixture.start, "witness_child_ownership_unknown")
            self.refuse(fixture.cleanup, "witness_child_ownership_unknown")
            self.assertEqual(fixture.signals, [])

    def test_child_with_other_uid_is_never_signaled(self):
        fixture = Fixture()
        fixture.uid_values[81] = [2000] * 4
        self.refuse(fixture.start, "witness_child_ownership_unknown")
        self.refuse(fixture.cleanup, "witness_child_ownership_unknown")
        self.assertEqual(fixture.signals, [])

    def test_unobserved_reparented_session_member_is_not_assumed_owned(self):
        fixture = Fixture()
        fixture.identities[81]["parent"] = 1
        self.refuse(fixture.start, "witness_child_ownership_unknown")
        self.assertEqual(fixture.signals, [])

    def test_cleanup_signals_only_witnessed_children_root_last(self):
        fixture = Fixture()
        fixture.identities[99] = identity(99)
        fixture.start()
        fixture.cleanup()
        self.assertEqual(fixture.signals, [(81, 101, "TERM"), (80, 100, "TERM")])
        self.assertIn(42, fixture.identities)
        self.assertIn(99, fixture.identities)
        self.assertIsNone(fixture.root)
        fixture.cleanup()  # Idempotent; no further signals.
        self.assertEqual(len(fixture.signals), 2)

    def test_cleanup_escalates_only_exact_owned_identities(self):
        fixture = Fixture()
        fixture.ignore_term = True
        fixture.start()
        fixture.cleanup()
        self.assertEqual(fixture.signals, [(81, 101, "TERM"), (80, 100, "TERM"),
                                          (81, 101, "KILL"), (80, 100, "KILL")])

    def test_root_pid_reuse_is_refused_without_signal(self):
        fixture = Fixture()
        fixture.start()
        fixture.identities[80]["startTicks"] += 2
        self.refuse(fixture.cleanup, "witness_child_identity_changed")
        self.assertEqual(fixture.signals, [])

    def test_reused_child_pid_is_never_signaled(self):
        fixture = Fixture()
        fixture.start()
        fixture.identities[81] = identity(81, ticks=900)
        fixture.cleanup()
        self.assertNotIn(81, [value[0] for value in fixture.signals])
        self.assertIn(81, fixture.identities)

    def test_parent_pid_reuse_is_preserved_not_signaled(self):
        fixture = Fixture()
        fixture.start()
        def signal(expected, kind):
            fixture.signals.append((expected["pid"], expected["startTicks"], kind))
            fixture.identities.pop(expected["pid"], None)
            if expected["pid"] == 80:
                fixture.identities[70] = identity(70, ticks=900)
        fixture._signal = signal
        fixture.cleanup()
        self.assertEqual(fixture.identities[70]["startTicks"], 900)
        self.assertNotIn(70, [value[0] for value in fixture.signals])

    def test_parent_must_drain_naturally_and_is_never_signaled(self):
        fixture = Fixture()
        fixture.start()
        def signal(expected, kind):
            fixture.signals.append((expected["pid"], expected["startTicks"], kind))
            fixture.identities.pop(expected["pid"], None)
        fixture._signal = signal
        self.refuse(fixture.cleanup, "witness_parent_not_drained")
        self.assertNotIn(70, [value[0] for value in fixture.signals])

    def test_cleanup_is_bounded_even_when_owned_process_ignores_signals(self):
        fixture = Fixture()
        fixture.start()
        fixture._signal = lambda expected, kind: fixture.signals.append((expected["pid"], kind))
        self.refuse(fixture.cleanup, "witness_cleanup_timeout")
        self.assertEqual(len(fixture.signals), 40)

    def test_cleanup_does_not_expose_device_signal_errors(self):
        fixture = Fixture()
        fixture.start()
        fixture._signal = mock.Mock(side_effect=OSError("private environment"))
        self.refuse(fixture.cleanup, "witness_cleanup_signal_failed")

    def test_identity_change_during_private_reads_is_refused(self):
        fixture = Fixture()
        original = fixture._read
        def read(pid, name, limit=witness.MAX_READ):
            value = original(pid, name, limit)
            if pid == 80 and name == "cgroup":
                fixture.identities[80]["startTicks"] += 1
            return value
        fixture._read = read
        self.refuse(fixture.start, "witness_identity_changed")

    def test_shell_bounds_and_sanitizes_errors(self):
        device = MockDevice()
        helper = witness.ControlledStaleWitness(device, metadata())
        for result in [subprocess.CompletedProcess([], 1, "private argv", "secret error"),
                       subprocess.CompletedProcess([], 0, "x" * (witness.MAX_READ + 1), "")]:
            with mock.patch.object(device, "adb", return_value=result):
                self.refuse(lambda: helper._shell("true"), "witness_read_failed")
        with mock.patch.object(device, "adb", side_effect=OSError("secret error")):
            self.refuse(lambda: helper._shell("true"), "witness_read_failed")
        with mock.patch.object(device, "adb", return_value=subprocess.CompletedProcess([], 0, "", "")) as adb:
            helper._shell("true")
            self.assertEqual(adb.call_args.kwargs["timeout"], 2)
            self.assertEqual(adb.call_args.args[:5], ("shell", "su", "0", "sh", "-c"))

    def test_signal_shell_rechecks_ticks_uid_and_never_names(self):
        helper = witness.ControlledStaleWitness(MockDevice(), metadata())
        with mock.patch.object(helper, "_shell", return_value="") as shell:
            helper._signal(identity(80), "KILL")
        script = shell.call_args.args[0]
        self.assertIn("/proc/80/stat", script)
        self.assertIn('[ "$1" = 100 ]', script)
        self.assertIn("/proc/80/status", script)
        self.assertIn("kill -KILL 80", script)
        self.assertNotIn("pkill", script)
        self.assertNotIn("killall", script)

    def test_expired_deadline_does_not_invoke_device(self):
        device = MockDevice()
        helper = witness.ControlledStaleWitness(device, metadata())
        helper.deadline = 0
        with mock.patch.object(device, "adb") as adb:
            self.refuse(lambda: helper._shell("true"), "witness_deadline_exceeded")
            adb.assert_not_called()

    def test_candidate_and_inventory_reads_reject_unbounded_or_malformed_ids(self):
        helper = witness.ControlledStaleWitness(MockDevice(), metadata())
        for raw in ("not-a-pid", "1", " ".join(str(100 + n) for n in range(129))):
            with mock.patch.object(helper, "_shell", return_value=raw):
                self.refuse(helper._candidates, "witness_candidates_invalid")
        with mock.patch.object(helper, "_shell", return_value="malformed stat"):
            self.refuse(helper._inventory, "witness_inventory_invalid")

    def test_uid_scan_uses_builtins_before_private_environment_reads(self):
        helper = witness.ControlledStaleWitness(MockDevice(), metadata())
        with mock.patch.object(helper, "_shell", return_value="") as shell:
            helper._candidates()
            helper._inventory()
        for call in shell.call_args_list:
            script = call.args[0]
            self.assertTrue("while read -r key ruid euid suid fsuid extra" in script,
                            "builtin_uid_scan_missing")
            self.assertTrue('[ $k -le 128 ]' in script, "status_line_bound_missing")
            self.assertTrue('[ $n -le 2048 ]' in script, "proc_count_bound_missing")
            self.assertTrue('if [ -z "$u" ]; then [ ! -d "$d" ] || exit 70;' in script,
                            "live_unreadable_status_must_refuse_inventory")
            self.assertTrue("awk" not in script, "per_pid_awk_not_permitted")
            self.assertNotRegex(script.replace('$((', ''), r'\$\(',
                                "per_pid_command_substitution_not_permitted")
        candidate_script = shell.call_args_list[0].args[0]
        self.assertLess(candidate_script.index('done < "$d/status"'), candidate_script.index('"$d/environ"'))
        self.assertTrue('else [ ! -d "$d" ] || exit 70; fi;' in shell.call_args_list[1].args[0],
                        "live_unreadable_stat_must_refuse_inventory")

    def test_start_and_cleanup_have_fixed_bounded_multiread_deadlines(self):
        fixture = Fixture()
        with mock.patch.object(witness.time, "monotonic", return_value=100):
            fixture.start()
            self.assertEqual(fixture.deadline, 115)
            fixture.cleanup()
            self.assertEqual(fixture.deadline, 115)
        helper = witness.ControlledStaleWitness(MockDevice(), metadata())
        helper.deadline = 100.75
        with mock.patch.object(witness.time, "monotonic", return_value=100), \
             mock.patch.object(helper.device, "adb", return_value=subprocess.CompletedProcess([], 0, "", "")) as adb:
            helper._shell("true")
            self.assertEqual(adb.call_args.kwargs["timeout"], .75)

    def test_native_read_limit_and_missing_kernel_identity(self):
        helper = witness.ControlledStaleWitness(MockDevice(), metadata())
        with mock.patch.object(helper, "_shell", return_value="x" * 4097):
            self.refuse(lambda: helper._read(80, "status", 4096), "witness_read_overflow")
            self.refuse(lambda: helper._identity(80), "witness_identity_invalid")
        with mock.patch.object(helper, "_shell", return_value=""):
            self.assertIsNone(helper._identity(80))


if __name__ == "__main__":
    unittest.main()
