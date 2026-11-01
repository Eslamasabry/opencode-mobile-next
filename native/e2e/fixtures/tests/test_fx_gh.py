"""FX-GH self-tests: GitHub device flow, /user and releases/latest stub (E2E-15 s4, E2E-39 s13).

Rows served: B-SEC-084 (GitHub sign-in: approve, deny, expire, late token
ignored, token rejected signs out), B-MIGR-012 (update notice reads releases/latest).
"""

import unittest

from tests.fxtest import FixtureCase

CLIENT = {"client_id": "Iv1.fixture", "scope": "repo"}


class FxGhTest(FixtureCase):
    FIXTURE = "FX-GH"

    def setUp(self) -> None:
        self.ctl("reset")

    def device_code(self) -> dict:
        r = self.http("POST", "/login/device/code", CLIENT, form=True, headers={"Accept": "application/json"})
        self.assertEqual(r.status, 200)
        return r.json()

    def poll(self, device_code: str) -> dict:
        r = self.http("POST", "/login/oauth/access_token", {
            "client_id": CLIENT["client_id"], "device_code": device_code,
            "grant_type": "urn:ietf:params:oauth:grant-type:device_code"}, form=True,
            headers={"Accept": "application/json"})
        self.assertEqual(r.status, 200)  # GitHub answers errors with 200 + {"error"}
        return r.json()

    def test_FX_GH_issues_a_code_with_an_https_verification_uri(self) -> None:
        code = self.device_code()
        self.assertRegex(code["user_code"], r"^[A-Z0-9]{4}-[A-Z0-9]{4}$")
        self.assertTrue(code["verification_uri"].startswith("https://"))
        self.assertIsInstance(code["interval"], int)
        self.assertIsInstance(code["expires_in"], int)
        self.assertEqual(self.ctl("state", method="GET").json()["pending"][0]["user_code"], code["user_code"])

    def test_B_SEC_084_pending_then_approve_returns_a_token_and_user(self) -> None:
        code = self.device_code()
        self.assertEqual(self.poll(code["device_code"])["error"], "authorization_pending")
        self.assertEqual(self.ctl("approve", {}).status, 200)
        token = self.poll(code["device_code"])
        self.assertEqual(token["token_type"], "bearer")
        self.assertTrue(token["access_token"])
        user = self.http("GET", "/user", headers={"Authorization": "Bearer " + token["access_token"]})
        self.assertEqual(user.status, 200)
        self.assertEqual(user.json()["login"], "fx-octocat")
        # a used device code cannot be redeemed twice
        self.assertEqual(self.poll(code["device_code"])["error"], "expired_token")

    def test_B_SEC_084_deny_and_expire_end_the_flow(self) -> None:
        code = self.device_code()
        self.ctl("deny", {"user_code": code["user_code"]})
        self.assertEqual(self.poll(code["device_code"])["error"], "access_denied")
        code = self.device_code()
        self.ctl("expire", {})
        self.assertEqual(self.poll(code["device_code"])["error"], "expired_token")

    def test_FX_GH_slow_down_once_on_demand(self) -> None:
        code = self.device_code()
        self.ctl("slow-down", {"interval": 9})
        reply = self.poll(code["device_code"])
        self.assertEqual(reply["error"], "slow_down")
        self.assertEqual(reply["interval"], 9)
        self.assertEqual(self.poll(code["device_code"])["error"], "authorization_pending")

    def test_B_SEC_084_rejected_token_answers_401(self) -> None:
        code = self.device_code()
        self.ctl("approve", {})
        token = self.poll(code["device_code"])["access_token"]
        self.ctl("reject-tokens", {"on": True})
        self.assertEqual(self.http("GET", "/user", headers={"Authorization": "Bearer " + token}).status, 401)
        self.assertEqual(self.http("GET", "/user").status, 401)

    def test_FX_GH_unknown_device_code_is_refused(self) -> None:
        self.assertEqual(self.poll("nope")["error"], "incorrect_device_code")

    def test_FX_GH_device_flow_disabled_on_demand(self) -> None:
        self.ctl("disable-device-flow", {"on": True})
        self.assertEqual(self.device_code()["error"], "device_flow_disabled")

    def test_B_MIGR_012_releases_latest_is_settable(self) -> None:
        path = "/repos/Eslamasabry/opencode-mobile-next/releases/latest"
        r = self.http("GET", path)
        self.assertEqual(r.status, 200)
        self.assertIn("tag_name", r.json())
        self.ctl("set-release", {"tag_name": "v9.9.9", "body": "Fixture notes"})
        body = self.http("GET", path).json()
        self.assertEqual(body["tag_name"], "v9.9.9")
        self.assertTrue(body["assets"][0]["browser_download_url"].startswith("http://"))

    def test_FX_GH_log_and_files_never_hold_the_token(self) -> None:
        code = self.device_code()
        self.ctl("approve", {})
        token = self.poll(code["device_code"])["access_token"]
        self.http("GET", "/user", headers={"Authorization": "Bearer " + token})
        log = self.requests_log()
        self.assertTrue(any(e["path"] == "/user" and e["auth"] == "bearer" for e in log))
        self.assert_no_secret_in_run_dir(token, code["device_code"])


if __name__ == "__main__":
    unittest.main()
