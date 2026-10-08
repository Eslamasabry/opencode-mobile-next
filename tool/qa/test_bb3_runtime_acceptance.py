"""Pure host regressions: mocks only, no adb, emulator lock, or subprocess execution."""
import importlib.util
import io
import sys
import types
import hashlib
import json
import os
import tempfile
import fcntl
from pathlib import Path
import subprocess
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("bb3_acceptance", Path(__file__).with_name("bb3_runtime_acceptance.py"))
acceptance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(acceptance)


class ReclaimProofTest(unittest.TestCase):
    def stale_fixture(self):
        app = dict(pid=42, startTicks=123, parent=1, group=42, session=0, state="S")
        witness = dict(pid=71, startTicks=145, parent=1, group=71, session=71, state="S")
        fixture = dict(stage="armed", app=app, members=[dict(app, pid=43)], activityAbsent=True,
                       stickyArmed=True, actualOpenCodeHealthy=True, serverExecutable="fixed")
        committed = fixture | dict(staleIdentity=True, staleWitness=witness)
        device = mock.Mock()
        device.cat.side_effect = [None, json.dumps(fixture), "{}", json.dumps(committed)]
        device.app_identity.return_value = app
        device.identity.return_value = witness
        device.instrument.return_value = dict(bb3WitnessCommandSaved="true", bb3StaleWitnessCommitted="true")
        helper = mock.Mock()
        helper.start.return_value = witness
        return device, helper, fixture, committed

    def test_stale_prepare_requires_live_independent_witness_receipt(self):
        device, helper, fixture, committed = self.stale_fixture()
        with mock.patch.object(acceptance, "controlled_stale_witness", return_value=helper):
            session = acceptance.Session(device, [])
            session.prepare(stale=True)
        helper.start.assert_called_once()
        self.assertEqual(committed, session.fixture)
        self.assertIs(helper, session.stale_witness)
        self.assertIn("bb3StaleWitness", [call.args[0] for call in device.instrument.call_args_list])

    def test_stale_prepare_refuses_reused_witness_pid(self):
        device, helper, fixture, committed = self.stale_fixture()
        device.identity.return_value = dict(committed["staleWitness"], startTicks=999)
        with mock.patch.object(acceptance, "controlled_stale_witness", return_value=helper):
            session = acceptance.Session(device, [])
            with self.assertRaisesRegex(acceptance.Refused, "stale_witness_receipt_unproven"):
                session.prepare(stale=True)
        self.assertIs(helper, session.stale_witness)
        device.signal.assert_not_called()

    def test_failed_stale_launch_retains_helper_for_cleanup(self):
        device, helper, fixture, committed = self.stale_fixture()
        helper.start.side_effect = acceptance.Refused("witness_start_timeout")
        with mock.patch.object(acceptance, "controlled_stale_witness", return_value=helper):
            session = acceptance.Session(device, [])
            with self.assertRaisesRegex(acceptance.Refused, "witness_start_timeout"):
                session.prepare(stale=True)
        device.identity.return_value = None
        device.instrument.return_value = {"bb3CleanupComplete": "true"}
        with mock.patch.object(acceptance.time, "sleep"):
            session.cleanup()
        helper.cleanup.assert_called_once()

    def test_app_identity_waits_out_transient_fork_without_selecting_arbitrary_pid(self):
        device = acceptance.Device()
        app = {"pid": 42, "startTicks": 123, "state": "S"}
        replies = [subprocess.CompletedProcess([], 0, "42 43\n", ""),
                   subprocess.CompletedProcess([], 0, "42\n", "")]
        with mock.patch.object(device, "adb", side_effect=replies), \
                mock.patch.object(device, "identity", return_value=app) as identify, \
                mock.patch.object(acceptance.time, "sleep"):
            self.assertEqual(app, device.app_identity())
        identify.assert_called_once_with(42)

    def test_app_identity_refuses_persistent_multiple_matches(self):
        device = acceptance.Device()
        with mock.patch.object(device, "adb", return_value=subprocess.CompletedProcess([], 0, "42 43\n", "")), \
                mock.patch.object(device, "identity") as identify, \
                mock.patch.object(acceptance.time, "sleep"):
            with self.assertRaisesRegex(acceptance.Refused, "app_identity_ambiguous"):
                device.app_identity()
        identify.assert_not_called()

    def test_mapped_server_accepts_both_android_private_directory_aliases(self):
        device = acceptance.Device()
        identity = {"pid": 42, "startTicks": 123, "state": "S"}
        for base in [acceptance.PRIVATE, "/data/data/" + acceptance.PACKAGE]:
            executable = base + "/files/linux/ubuntu/opt/opencode2/bin/opencode2"
            maps = "1000-2000 r-xp 00000000 00:00 1 " + executable + "\n"
            with mock.patch.object(device, "identity", return_value=identity), \
                    mock.patch.object(device, "cat", return_value=maps):
                self.assertEqual(identity, device.mapped_server([identity], executable))

    def test_mapped_server_refuses_other_app_and_traversal(self):
        device = acceptance.Device()
        identity = {"pid": 42, "startTicks": 123, "state": "S"}
        for executable in ["/data/data/other.app/files/linux/ubuntu/opt/opencode2",
                           acceptance.PRIVATE + "/files/linux/ubuntu/../../other/opencode2"]:
            maps = "1000-2000 r-xp 00000000 00:00 1 " + executable + "\n"
            with mock.patch.object(device, "identity", return_value=identity), \
                    mock.patch.object(device, "cat", return_value=maps):
                with self.assertRaisesRegex(acceptance.Refused, "server_executable_invalid"):
                    device.mapped_server([identity], executable)

    def test_preparation_refuses_stringified_app_identity_before_signaling(self):
        device = mock.Mock()
        app = {"pid": 42, "startTicks": 123, "state": "S"}
        fixture = dict(stage="armed", app="{pid=42, startTicks=123}",
                       members=[dict(app, pid=43)], activityAbsent=True)
        device.cat.side_effect = [None, json.dumps(fixture)]
        device.app_identity.return_value = app
        session = acceptance.Session(device, [])
        with self.assertRaisesRegex(acceptance.Refused, "fixture_app_identity_invalid"):
            session.prepare()
        device.signal.assert_not_called()

    def test_inherited_batch_lock_uses_same_open_description(self):
        with tempfile.NamedTemporaryFile() as owned:
            fcntl.flock(owned, fcntl.LOCK_EX)
            with acceptance.emulator_session_lock(owned.fileno(), owned.name):
                with open(owned.name, "a") as contender:
                    with self.assertRaises(BlockingIOError):
                        fcntl.flock(contender, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with open(owned.name, "a") as contender:
                with self.assertRaises(BlockingIOError):
                    fcntl.flock(contender, fcntl.LOCK_EX | fcntl.LOCK_NB)

    def test_inherited_batch_lock_rejects_other_file(self):
        with tempfile.NamedTemporaryFile() as expected, tempfile.NamedTemporaryFile() as other:
            with self.assertRaisesRegex(acceptance.Refused, "inherited_lock_wrong_file"):
                with acceptance.emulator_session_lock(other.fileno(), expected.name):
                    self.fail("Different lock admitted")

    def test_instrumentation_attach_never_replaces_normal_process(self):
        device = acceptance.Device()
        current = {"pid": 42, "startTicks": 123, "state": "S"}
        def framework(*command, **kwargs):
            # Model AMS default instrumentation force-stop, unlike --no-restart attach.
            if "--no-restart" not in command:
                current["startTicks"] += 1
            return subprocess.CompletedProcess(command, 0,
                "INSTRUMENTATION_RESULT: builtinRuntimeResult=PASS\nINSTRUMENTATION_CODE: -1\n", "")
        with mock.patch.object(device, "app_identity", side_effect=lambda: dict(current)), \
                mock.patch.object(device, "detached", return_value=True), \
                mock.patch.object(device, "adb", side_effect=framework):
            device.instrument("bb3Stop", expected_app=dict(current))
        self.assertEqual(123, current["startTicks"])

    def test_instrumentation_refuses_absent_or_replaced_app(self):
        device = acceptance.Device()
        with mock.patch.object(device, "app_identity", return_value=None), mock.patch.object(device, "adb") as adb:
            with self.assertRaisesRegex(acceptance.Refused, "requires_live_normal_app"):
                device.instrument("bb3Timeout")
            adb.assert_not_called()
        identity = {"pid": 42, "startTicks": 123, "state": "S"}
        replacement = dict(identity, startTicks=124)
        with mock.patch.object(device, "app_identity", return_value=replacement):
            with self.assertRaisesRegex(acceptance.Refused, "instrumentation_replaced_app"):
                device.wait_detached(identity)

    def test_ams_dump_requires_current_process_and_rejects_active_instrumentation(self):
        identity = {"pid": 42, "startTicks": 123}
        body = f"ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)\nProcessRecord{{abc 42:{acceptance.PACKAGE}/u0a217}}\n"
        self.assertTrue(acceptance.instrumentation_detached(body, identity))
        self.assertFalse(acceptance.instrumentation_detached(body + "mInstr=ActiveInstrumentation{abc 1 procs}", identity))
        self.assertFalse(acceptance.instrumentation_detached(body + "mActiveInstrumentation = nonnull", identity))
        with self.assertRaises(acceptance.Refused):
            acceptance.instrumentation_detached("", identity)
        with self.assertRaises(acceptance.Refused):
            acceptance.instrumentation_detached(body, dict(identity, pid=43))

    def test_host_never_kills_app_with_active_instrumentation(self):
        device = mock.Mock()
        app = {"pid": 42, "startTicks": 123, "state": "S"}
        device.app_identity.return_value = app
        device.activity_absent.return_value = True
        device.detached.return_value = False
        with self.assertRaisesRegex(acceptance.Refused, "instrumentation_active_before_app_death"):
            acceptance.Session(device, []).kill_app(app)
        device.signal.assert_not_called()

    def test_preparation_finishes_attach_before_host_can_kill_normal_app(self):
        device = mock.Mock()
        app = {"pid": 42, "startTicks": 123, "state": "S"}
        fixture = dict(stage="prepared", app=app, members=[dict(app, pid=43)], activityAbsent=True)
        finished = []
        def instrument(*args, **kwargs):
            self.assertEqual(app, kwargs["expected_app"])
            finished.append(True)
        def read(*args, **kwargs):
            if kwargs.get("required") is False:
                return None
            self.assertTrue(finished)
            return json.dumps(fixture)
        device.instrument.side_effect = instrument
        device.cat.side_effect = read
        device.app_identity.return_value = app
        device.detached.return_value = True
        device.activity_absent.return_value = True
        session = acceptance.Session(device, [])
        session.prepare("prepared")
        self.assertEqual(fixture, session.fixture)
        device.signal.assert_not_called()

    def test_installed_candidate_requires_exact_bytes_without_installing(self):
        device = mock.Mock()
        apk = mock.Mock()
        apk.read_bytes.return_value = b"candidate"
        device.installed_hash.return_value = hashlib.sha256(b"candidate").hexdigest()
        acceptance.require_installed_candidates(device, [(acceptance.PACKAGE, apk)])
        device.installed_hash.return_value = hashlib.sha256(b"different build").hexdigest()
        with self.assertRaisesRegex(acceptance.Refused, "installed_candidate_hash_mismatch"):
            acceptance.require_installed_candidates(device, [(acceptance.PACKAGE, apk)])
        device.adb.assert_not_called()

    def test_default_policy_requires_enabled_real_binding_and_strict_marker(self):
        native = ET.fromstring('<map><string name="owner">person</string><boolean name="enabled" value="true"/></map>')
        flutter = ET.fromstring('<map><string name="flutter.oc.builtinRecovery.person">{"version":2,"nativeAuthority":true}</string></map>')
        self.assertTrue(acceptance.baseline_policy_valid(native, flutter))
        native[1].set("value", "false")
        self.assertFalse(acceptance.baseline_policy_valid(native, flutter))
        native[1].set("value", "true")
        for invalid in ['{}', '{"version":"2","nativeAuthority":true}', '{"version":2,"nativeAuthority":"true"}']:
            flutter[0].text = invalid
            self.assertFalse(acceptance.baseline_policy_valid(native, flutter))

    def test_present_policy_never_falls_back_to_default_on_invalid_data(self):
        native = ET.fromstring('<map><string name="owner">person</string><boolean name="enabled" value="true"/></map>')
        flutter = ET.fromstring('<map><string name="flutter.oc.builtinRecovery.person">{"version":2,"nativeAuthority":true}</string></map>')
        policy = ET.SubElement(flutter, "string", name="flutter.oc.automation.person")
        valid = dict(version=1, supervision="high", behaviors=dict(restartPhoneServer=True, pollRestartHealth=True))
        policy.text = json.dumps(valid)
        self.assertTrue(acceptance.baseline_policy_valid(native, flutter))
        for invalid in ['broken', 'null', '{}', json.dumps(dict(valid, version=True)),
                        json.dumps(dict(valid, behaviors=dict(restartPhoneServer="true", pollRestartHealth=True))),
                        json.dumps(dict(valid, supervision="off"))]:
            policy.text = invalid
            self.assertFalse(acceptance.baseline_policy_valid(native, flutter))

    def test_health_alone_never_proves_os_recovery(self):
        good = [True, True, True, True, True, 1, 1]
        self.assertTrue(acceptance.recovery_proven(*good))
        for index in range(5):
            missing = list(good)
            missing[index] = False
            self.assertFalse(acceptance.recovery_proven(*missing))
        self.assertFalse(acceptance.recovery_proven(True, True, True, True, True, 2, 1))

    def test_reused_pid_or_zombie_never_counts_as_original_identity(self):
        original = {"pid": 42, "startTicks": 123}
        self.assertTrue(acceptance.same_process(original, {"pid": 42, "startTicks": 123, "state": "S"}))
        self.assertFalse(acceptance.same_process(original, {"pid": 42, "startTicks": 124, "state": "S"}))
        self.assertFalse(acceptance.same_process(original, {"pid": 42, "startTicks": 123, "state": "Z"}))
        self.assertFalse(acceptance.same_process(original, None))

    def test_stat_parser_handles_parentheses_and_spaces_in_comm(self):
        fields = ["S", "7", "8", "9"] + ["0"] * 15 + ["12345"]
        parsed = acceptance.parse_stat("42 (server ) with spaces)) " + " ".join(fields))
        self.assertEqual({"pid": 42, "parent": 7, "group": 8, "session": 9,
                          "startTicks": 12345, "state": "S"}, parsed)

    def test_higher_person_budget_survives_restore_and_fixture_keys_are_removed(self):
        original = ET.fromstring('<map><string name="owner">person</string><long name="runtimeGeneration" value="4"/>'
                                 '<string name="oc.builtinRecoveryBudget.person">{"attempts":1,"revision":2}</string></map>')
        current = ET.fromstring('<map><string name="owner">qa_bb3_reclaim</string><long name="runtimeGeneration" value="7"/>'
                                '<string name="oc.builtinRecoveryBudget.person">{"attempts":2,"revision":3}</string>'
                                '<string name="oc.builtinRuntimeOwnership.qa_bb3_reclaim">fixture</string>'
                                '<string name="restoreOwner">qa_bb3_reclaim</string></map>')
        values = acceptance.preference_values(acceptance.merge_person_preferences(original, current))
        self.assertEqual("person", values["owner"].text)
        self.assertEqual('{"attempts":2,"revision":3}', values["oc.builtinRecoveryBudget.person"].text)
        self.assertEqual("7", values["runtimeGeneration"].attrib["value"])
        self.assertFalse(any(key.endswith(".qa_bb3_reclaim") for key in values))
        self.assertEqual("false", values["wanted"].attrib["value"])
        self.assertEqual("true", values["userStopped"].attrib["value"])

    def test_primary_failure_is_retained_when_cleanup_also_fails(self):
        session = mock.Mock()
        session.cleanup.side_effect = acceptance.Refused("cleanup_failed")
        evidence = []
        with self.assertRaisesRegex(acceptance.Refused, "os_recovery_timeout"):
            with acceptance.with_cleanup(session, evidence):
                raise acceptance.Refused("os_recovery_timeout")
        session.cleanup.assert_called_once()
        self.assertEqual(["FAIL fixture_cleanup_cleanup_failed"], evidence)

    def test_untrusted_status_or_account_text_is_not_evidence(self):
        text = "\n".join([
            "INSTRUMENTATION_STATUS: account=private-account",
            "INSTRUMENTATION_STATUS: bb3StopReason=raw error with details",
            "INSTRUMENTATION_STATUS: bb3StopRevoked=true",
            "INSTRUMENTATION_RESULT: builtinRuntimeResult=PASS",
        ])
        self.assertEqual({"bb3StopRevoked": "true", "builtinRuntimeResult": "PASS"}, acceptance.parse_status(text))

    def test_authenticated_health_keeps_password_device_local_and_requires_real_health(self):
        device = acceptance.Device()
        response = 'HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{"version":"2.0.0","healthy":true}'
        with mock.patch.object(device, "adb", return_value=subprocess.CompletedProcess([], 0, response, "")) as adb:
            self.assertTrue(device.healthy())
        command = adb.call_args.args
        self.assertEqual(("shell", "sh", "-c"), command[:3])
        self.assertIn("server.password", command[3])
        self.assertIn("Authorization: Basic %s", command[3])
        self.assertNotIn("cat", command[:3])
        failure = 'HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{"account":"private"}'
        with mock.patch.object(device, "adb", return_value=subprocess.CompletedProcess([], 0, failure, "")):
            self.assertFalse(device.healthy())

    def test_v1_health_without_version_cannot_certify_OpenCode2(self):
        device = acceptance.Device()
        response = 'HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{"healthy":true}'
        with mock.patch.object(device, "adb", return_value=subprocess.CompletedProcess([], 0, response, "")):
            self.assertFalse(device.healthy())

    def test_signal_requires_exact_identity_and_shell_preserves_one_script(self):
        device = acceptance.Device()
        with mock.patch.object(device, "adb", return_value=subprocess.CompletedProcess([], 0, "", "")) as adb:
            device.signal({"pid": 42, "startTicks": 123}, "KILL")
        script = adb.call_args.args[3]
        self.assertTrue(script.startswith("'") and script.endswith("'"))
        self.assertIn("/proc/42/stat", script)
        self.assertIn("shift 19", script)
        self.assertIn("kill -KILL 42", script)
        with self.assertRaises(acceptance.Refused):
            device.signal({"pid": 1, "startTicks": 123}, "KILL")


class RestoreDeathWaitTest(unittest.TestCase):
    class Finished(Exception):
        pass

    def original(self):
        return ET.fromstring('<map><string name="owner">existing</string></map>')

    def replies(self, pids):
        device=mock.Mock();events=[];remaining=list(pids)
        def adb(*args, **kwargs):
            if args[:3] == ('shell','am','force-stop'):
                events.append('force_stop')
                return subprocess.CompletedProcess([],0,'','')
            if args[:2] == ('shell','pidof'):
                value=remaining.pop(0) if remaining else None
                events.append('live' if value else 'gone')
                return subprocess.CompletedProcess([],0 if value else 1,value or '','')
            return subprocess.CompletedProcess([],0,'','')
        device.adb.side_effect=adb
        def native():
            events.append('native_snapshot')
            return self.original()
        device.native.side_effect=native
        device.app_identity.return_value=None
        device.run.return_value=subprocess.CompletedProcess([],0,'','')
        device.ensure_normal_app.side_effect=self.Finished()
        return device,events

    def test_delayed_death_precedes_snapshot_and_write(self):
        device,events=self.replies(['42 43\n','42\n',None])
        with mock.patch.object(acceptance.time,'sleep'), self.assertRaises(self.Finished):
            acceptance.restore_person(device,self.original(),[])
        self.assertEqual(events,['force_stop','live','live','gone','native_snapshot'])
        device.run.assert_called_once()
        self.assertEqual(device.app_identity.call_count,3)
        self.assertEqual(device.run.call_args.kwargs['timeout'],5)
        device.instrument.assert_not_called()

    def test_permanent_app_refuses_in_ten_seconds_without_snapshot_or_write(self):
        device,events=self.replies(['42\n'])
        elapsed=[0.0]
        def adb(*args,**kwargs):
            if args[:2] == ('shell','pidof'):
                elapsed[0]+=min(.2,kwargs['timeout'])
                return subprocess.CompletedProcess([],0,'42\n','')
            return subprocess.CompletedProcess([],0,'','')
        device.adb.side_effect=adb
        def sleep(delay):elapsed[0]+=delay
        with mock.patch.object(acceptance.time,'monotonic',side_effect=lambda:elapsed[0]),\
             mock.patch.object(acceptance.time,'sleep',side_effect=sleep),\
             self.assertRaisesRegex(acceptance.Refused,'restore_app_death_timeout'):
            acceptance.restore_person(device,self.original(),[])
        self.assertLessEqual(elapsed[0],10)
        device.native.assert_not_called();device.run.assert_not_called();device.ensure_normal_app.assert_not_called()
        device.instrument.assert_not_called()

    def test_respawn_before_snapshot_refuses_without_read_or_write(self):
        device,_=self.replies([None]);device.app_identity.return_value={'pid':99,'startTicks':123,'state':'S'}
        with self.assertRaisesRegex(acceptance.Refused,'restore_metadata_writer_active'):
            acceptance.restore_person(device,self.original(),[])
        device.native.assert_not_called();device.run.assert_not_called()

    def test_respawn_between_snapshot_and_write_refuses_write(self):
        device,_=self.replies([None]);device.app_identity.side_effect=[None,{'pid':99,'startTicks':123,'state':'S'}]
        with self.assertRaisesRegex(acceptance.Refused,'restore_metadata_writer_active'):
            acceptance.restore_person(device,self.original(),[])
        device.native.assert_called_once();device.run.assert_not_called()

    def test_respawn_after_write_refuses_success_and_preflight(self):
        device,_=self.replies([None]);device.app_identity.side_effect=[None,None,{'pid':99,'startTicks':123,'state':'S'}]
        evidence=[]
        with self.assertRaisesRegex(acceptance.Refused,'restore_metadata_writer_active'):
            acceptance.restore_person(device,self.original(),evidence,baseline={})
        device.run.assert_called_once();device.ensure_normal_app.assert_not_called();device.instrument.assert_not_called()
        self.assertEqual(evidence,[])

    def test_failed_pidof_never_counts_as_absence(self):
        device=mock.Mock()
        device.adb.return_value=subprocess.CompletedProcess([],1,'','private adb error')
        with self.assertRaisesRegex(acceptance.Refused,'^restore_app_state_unavailable$'):
            acceptance.wait_for_app_death(device)

    def test_timeout_reads_remain_bounded(self):
        device=mock.Mock();elapsed=[0.0]
        def adb(*args,**kwargs):
            elapsed[0]+=kwargs['timeout']
            raise subprocess.TimeoutExpired(['private'],kwargs['timeout'])
        device.adb.side_effect=adb
        with mock.patch.object(acceptance.time,'monotonic',side_effect=lambda:elapsed[0]),\
             self.assertRaisesRegex(acceptance.Refused,'restore_app_death_timeout'):
            acceptance.wait_for_app_death(device)
        self.assertEqual(elapsed[0],10)
        self.assertEqual(device.adb.call_count,5)


def restore_red_proof():
    global acceptance
    restored=acceptance
    source=Path(__file__).with_name('bb3_runtime_acceptance.py').read_text()
    mutations=[
        ('confirmed_death_wait','    wait_for_app_death(device)\n','', 'test_delayed_death_precedes_snapshot_and_write'),
        ('permanent_death_refusal','    wait_for_app_death(device)\n','', 'test_permanent_app_refuses_in_ten_seconds_without_snapshot_or_write'),
        ('before_snapshot_absence', '    require(device.app_identity() is None, "restore_metadata_writer_active")\n    merged', '    merged', 'test_respawn_before_snapshot_refuses_without_read_or_write'),
        ('before_write_absence', '    require(device.app_identity() is None, "restore_metadata_writer_active")\n    result = device.run', '    result = device.run', 'test_respawn_between_snapshot_and_write_refuses_write'),
        ('after_write_absence', '    require(device.app_identity() is None, "restore_metadata_writer_active")\n    evidence.append', '    evidence.append', 'test_respawn_after_write_refuses_success_and_preflight'),
    ]
    for name,before,after,test in mutations:
        assert before in source
        module=types.ModuleType('bb3_restore_removed_fix');module.__file__=str(Path(__file__).with_name('bb3_runtime_acceptance.py'))
        exec(compile(source.replace(before,after),module.__file__,'exec'),module.__dict__)
        acceptance=module
        result=unittest.TextTestRunner(stream=io.StringIO()).run(unittest.TestSuite([RestoreDeathWaitTest(test)]))
        assert not result.wasSuccessful(), 'removed fix unexpectedly green: '+name
        print('PASS removed_fix_red '+name)
    acceptance=restored
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__]))
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    if sys.argv[1:] == ["--restore-red-proof"]:
        raise SystemExit(restore_red_proof())
    unittest.main()
