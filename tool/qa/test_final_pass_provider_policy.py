"""Offline rerun policy: baseline selection and provider blocks stay distinct."""
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest

from tool.qa import final_pass_protocols
from tool.qa.fq3.test_update_matrix import model_scoped_run


class ProviderPolicyTests(unittest.TestCase):
    def run_case(self, *, provider_block=False, app_failure=False):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            run_id = "fq3-rerun-policy"
            report = model_scoped_run()
            report.update(runID=run_id, appBuild=2203,
                          evidence=f"docs/qa/FQ3e-2026-10-09/{run_id}.json")
            if provider_block:
                report["engines"]["opencode"]["results"]["stream"] = {
                    "state": "blocked", "code": "provider_unavailable",
                    "classification": "provider", "originalCode": "oc1_prompt_error",
                    "facts": {},
                }
            if app_failure:
                report["engines"]["opencode"]["results"]["create"] = {
                    "state": "fail", "code": "oc1_invalid_session", "facts": {},
                }
            calls = []

            def command(argv, **kwargs):
                calls.append(argv)
                receipt = root / report["evidence"]
                receipt.parent.mkdir(parents=True)
                receipt.write_text(json.dumps(report))
                return SimpleNamespace(returncode=1 if provider_block or app_failure else 0)

            context = SimpleNamespace(root=root, candidate_build=2203,
                                      run_id="rerun-policy", lock_fd=9, command=command)
            result = final_pass_protocols.run("fq3", {
                "oc1_model": "opencode/big-pickle",
                "oc2_model": "opencode/big-pickle",
            }, context)
            return result, calls

    def test_legacy_oc1_override_does_not_disable_baseline_policy(self):
        result, calls = self.run_case()
        self.assertEqual(result["status"], "pass")
        self.assertEqual(len(calls), 1)
        self.assertNotIn("--oc1-model", calls[0])
        self.assertIn("--oc2-model", calls[0])

    def test_provider_only_failure_is_blocked_not_app_failure(self):
        result, _ = self.run_case(provider_block=True)
        self.assertEqual(result["status"], "blocked")
        self.assertEqual(result["reason"], "fq3_provider_unavailable")
        self.assertTrue(result["data"]["safe_to_continue"])

    def test_real_assertion_failure_still_fails_when_provider_is_blocked(self):
        result, _ = self.run_case(provider_block=True, app_failure=True)
        self.assertEqual(result["status"], "fail")
        self.assertEqual(result["reason"], "fq3_checks_failed")


if __name__ == "__main__":
    unittest.main()
