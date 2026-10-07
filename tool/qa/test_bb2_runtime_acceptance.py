"""Pure host regression: no adb/device or real lock acquisition."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("acceptance", Path(__file__).with_name("bb2_runtime_acceptance.py"))
acceptance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(acceptance)


class RestorationEvidenceTest(unittest.TestCase):
    def test_connected_evidence_must_belong_to_open_code_two(self):
        self.assertFalse(acceptance.connected_open_code_two(["BD9 local fixture\nConnected", "In-app Ubuntu\nOpenCode 2\nStopped"], {"Connected"}))
        self.assertTrue(acceptance.connected_open_code_two(["In-app Ubuntu\nConnected · OpenCode 2 · Running"], {"Connected"}))

    def test_primary_install_failure_survives_failed_finally_restore(self):
        original = '<map><boolean name="wanted" value="true"/><boolean name="userStopped" value="false"/><string name="owner">person</string><string name="oc.builtinRecoveryBudget.person">{"version":1,"attempts":1,"pending":false}</string></map>'
        writes = []

        def fake_run(command, **kwargs):
            output, code = "", 0
            if command[0].endswith("apksigner"):
                output = "Signer #1 certificate SHA-256 digest: " + acceptance.CERT
            elif command[0].endswith("aapt"):
                output = "package: name='test' versionCode='2201'"
            elif "get-state" in command:
                output = "device"
            elif "dumpsys" in command:
                output = "versionCode=2201"
            elif "install" in command:
                code, output = 1, "INSTALL_FAILED_VERSION_DOWNGRADE"
            elif "cat" in command:
                output = original
            elif "sh" in command:
                writes.append(ET.fromstring(kwargs["input"]))
            elif "uiautomator" in command:
                code = 1
            return subprocess.CompletedProcess(command, code, output, "")

        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / "result.txt"
            argv = ["acceptance", "--apk", "target.apk", "--runner-apk", "runner.apk",
                    "--apksigner", "/fake/apksigner", "--out", str(out)]
            with mock.patch.object(sys, "argv", argv), mock.patch.object(acceptance.subprocess, "run", fake_run), \
                    mock.patch.object(acceptance.fcntl, "flock"), mock.patch.object(acceptance.time, "sleep"):
                self.assertEqual(1, acceptance.main())
            evidence = out.read_text()
            self.assertIn("FAIL primary_QA_install_version_downgrade_refused", evidence)
            self.assertIn("FAIL restore_ui_dump_failed", evidence)
            self.assertEqual(1, len(writes))
            budget = next(node for node in writes[0] if node.attrib["name"] == "oc.builtinRecoveryBudget.person")
            self.assertEqual('{"version":1,"attempts":1,"pending":false}', budget.text)


if __name__ == "__main__":
    unittest.main()
