"""The FB1 install diagnostic never exports arbitrary adb output."""

import json
import tempfile
from pathlib import Path
import unittest
from tool.qa import fb1_first_run as fb1
from tool.qa.fq9.common import DriverFailure


class InstallDiagnosticTest(unittest.TestCase):
    def test_native_error_from_stderr_is_retained_without_details(self):
        from tool.qa.fb1_install_diagnostic import project

        result = project(
            1,
            b"",
            b"adb: failed: Failure [INSTALL_FAILED_NO_MATCHING_ABIS: private-path token-secret res=-113]",
        )
        self.assertEqual(result["androidError"], "INSTALL_FAILED_NO_MATCHING_ABIS")
        self.assertEqual(result["nativeResult"], -113)
        self.assertNotIn("private", json.dumps(result))
        self.assertNotIn("token-secret", json.dumps(result))

    def test_success_and_unknown_output_are_not_confused(self):
        from tool.qa.fb1_install_diagnostic import project

        self.assertTrue(
            project(0, b"Performing Streamed Install\nSuccess\n", b"")["success"]
        )
        self.assertFalse(project(0, b"private unknown", b"")["success"])
        self.assertIsNone(project(1, b"", b"private error")["androidError"])

    def test_install_success_survives_later_identity_failure_in_report(self):
        from tool.qa.test_fb1_first_run import FakePorts, CANDIDATE

        ports = FakePorts()

        def failed(_):
            ports.install_diagnostic = {
                "returnCode": 0,
                "success": True,
                "androidError": None,
                "nativeResult": None,
            }
            raise DriverFailure("installed_identity_unavailable")

        ports.install_fresh = failed
        with tempfile.TemporaryDirectory() as directory:
            report = fb1.run_first_run(ports, CANDIDATE, Path(directory) / "new")
        self.assertEqual(report["installDiagnostic"]["success"], True)
        self.assertEqual(
            report["installVerificationError"], "installed_identity_unavailable"
        )
