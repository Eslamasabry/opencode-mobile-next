"""FX-USAGE self-tests: /ocmn/quota/v1 snapshots (E2E-33).

Rows served: B-AGENT-073 (plan-usage reading per server), B-NAV-079
(Subscriptions states: numbers, stale and passed reset, expired sign-in, offline).
The snapshot rules mirror ProviderQuotaSnapshot.fromJson
(lib/domain/provider_quota.dart) so every 'ok' answer is one the app accepts.
"""

import re
import time
import unittest

from tests.fxtest import FixtureCase, basic

SOURCES = {"codex": "codex.wham", "minimax": "minimax.tokenPlan", "glm": "glm.codingPlan", "claude": "claude.oauth"}
PATHS = {"codex": "/ocmn/quota/v1", "minimax": "/ocmn/quota/v1/minimax", "glm": "/ocmn/quota/v1/glm",
         "claude": "/ocmn/quota/v1/claude"}


def check_snapshot(test: unittest.TestCase, snap: dict, provider: str) -> None:
    """The parser's rules, in Python."""
    test.assertEqual(snap["schemaVersion"], 1)
    test.assertEqual(snap["provider"], provider)
    test.assertEqual(snap["source"], SOURCES[provider])
    test.assertGreaterEqual(snap["expiresAtMs"], snap["fetchedAtMs"])
    account = snap["account"]
    if account.get("ref") is not None:
        test.assertRegex(account["ref"], r"^[a-f0-9]{64}$")
    if account["status"] in ("matched", "sourceBound"):
        test.assertIsNotNone(account.get("ref"))
    if provider == "codex":
        test.assertNotEqual(account["status"], "sourceBound")
    else:
        test.assertIsNone(snap.get("ordinaryUsageAllowed"))
    attributed = account["status"] == "matched" or (provider != "codex" and account["status"] == "sourceBound")
    if snap["status"] != "ok" or not attributed:
        test.assertEqual(snap["windows"], [])
        test.assertIsNone(snap.get("ordinaryUsageAllowed"))
    ids = set()
    for w in snap["windows"]:
        test.assertTrue(re.match(r"^[a-zA-Z0-9._/-]{1,64}$", w["id"]))
        test.assertNotIn(w["id"], ids)
        ids.add(w["id"])
        test.assertEqual(w["status"] == "reported", w.get("usedPercent") is not None)
        if w.get("usedPercent") is not None:
            test.assertTrue(0 <= w["usedPercent"] <= 100)


class FxUsageTest(FixtureCase):
    FIXTURE = "FX-USAGE"
    EXTRA_ENV = {"FX_OC2_PASSWORD": "fx-usage-test-password-A1"}

    def setUp(self) -> None:
        self.ctl("reset")
        self.auth = basic("opencode", "fx-usage-test-password-A1")

    def read(self, provider: str):
        return self.http("GET", PATHS[provider], headers=self.auth)

    def test_B_NAV_079_codex_and_minimax_readings_parse(self) -> None:
        for provider in ("codex", "minimax"):
            r = self.read(provider)
            self.assertEqual(r.status, 200, provider)
            snap = r.json()
            check_snapshot(self, snap, provider)
            self.assertEqual(snap["status"], "ok")
            self.assertEqual(snap["freshness"], "fresh")
            self.assertTrue(any(w["status"] == "reported" for w in snap["windows"]))

    def test_B_NAV_079_expired_sign_in_reads_auth_required_without_numbers(self) -> None:
        self.ctl("set", {"provider": "codex", "mode": "expired"})
        snap = self.read("codex").json()
        check_snapshot(self, snap, "codex")
        self.assertEqual(snap["status"], "authRequired")
        self.assertEqual(snap["windows"], [])

    def test_B_NAV_079_stale_reading_has_a_passed_reset(self) -> None:
        self.ctl("set", {"provider": "minimax", "mode": "stale"})
        snap = self.read("minimax").json()
        check_snapshot(self, snap, "minimax")
        self.assertEqual(snap["freshness"], "stale")
        now = int(time.time() * 1000)
        self.assertTrue(any(w.get("resetsAtMs", now) < now for w in snap["windows"]))

    def test_B_NAV_079_offline_closes_without_an_answer(self) -> None:
        self.ctl("set", {"provider": "codex", "mode": "offline"})
        with self.assertRaises(Exception):
            self.read("codex")
        self.assertEqual(self.read("minimax").status, 200)

    def test_FX_USAGE_unconfigured_and_unsupported(self) -> None:
        self.ctl("set", {"provider": "minimax", "mode": "unconfigured"})
        snap = self.read("minimax").json()
        check_snapshot(self, snap, "minimax")
        self.assertEqual(snap["status"], "unconfigured")
        self.assertEqual(self.read("claude").status, 404)

    def test_B_AGENT_073_wrong_or_missing_password_is_401(self) -> None:
        self.assertEqual(self.http("GET", PATHS["codex"]).status, 401)
        self.assertEqual(self.http("GET", PATHS["codex"], headers=basic("opencode", "wrong")).status, 401)

    def test_B_AGENT_073_reads_are_counted_per_provider(self) -> None:
        self.read("codex")
        self.read("codex")
        counts = self.ctl("state", method="GET").json()["reads"]
        self.assertEqual(counts["codex"], 2)
        self.assertEqual(counts.get("minimax", 0), 0)

    def test_FX_USAGE_set_rejects_unknown_mode(self) -> None:
        self.assertEqual(self.ctl("set", {"provider": "codex", "mode": "nonsense"}).status, 400)

    def test_FX_USAGE_password_never_logged(self) -> None:
        self.read("codex")
        self.assertTrue(any(e["auth"] == "basic" for e in self.requests_log()))
        self.assert_no_secret_in_run_dir("fx-usage-test-password-A1")


if __name__ == "__main__":
    unittest.main()
