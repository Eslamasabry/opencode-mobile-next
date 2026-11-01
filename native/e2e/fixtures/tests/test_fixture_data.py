"""fixture-data.json stays equal to what the servers actually serve, and ports
the flows' URLs name stay equal to ports.json (plan 14 §4.2 rule 3, §4.4)."""

import json
import sys
import unittest

from tests.fxtest import ROOT

sys.path.insert(0, str(ROOT / "servers"))

import fx_auth  # noqa: E402
import fx_codex  # noqa: E402
import fx_gh  # noqa: E402
import fx_oc2  # noqa: E402
import fx_oc2_b  # noqa: E402
import fx_paseo  # noqa: E402
import fx_tls  # noqa: E402
import stub_model  # noqa: E402

DATA = json.loads((ROOT / "fixture-data.json").read_text(encoding="utf-8"))
PORTS = json.loads((ROOT / "ports.json").read_text(encoding="utf-8"))


def titles(sessions):
    return {s.key: s.title for s in sessions}


class FixtureDataTest(unittest.TestCase):
    def test_FXDATA_session_titles_match_the_seeds(self) -> None:
        a, b = titles(fx_oc2.OC2_SESSIONS), titles(fx_oc2_b.B_SESSIONS)
        self.assertEqual(DATA["sessionSharedLogin"], a["shared-login"])
        self.assertEqual(DATA["sessionSharedLoginB"], b["shared-login"])
        self.assertEqual(DATA["sessionSharedNotes"], a["shared-notes"])
        self.assertEqual(DATA["sessionOtherPlan"], a["other-plan"])
        self.assertEqual(DATA["sessionIsoTitleA"], a["iso-title"])
        self.assertEqual(DATA["sessionIsoTitleB"], b["iso-title"])
        self.assertEqual(DATA["sessionMarkupTitle"], a["markup-title"])
        self.assertEqual(DATA["sessionMarkupTitle"], b["markup-title"])
        self.assertEqual(DATA["sessionLongHistory"], fx_oc2.LONG_HISTORY.title)

    def test_FXDATA_stub_model_replies(self) -> None:
        self.assertEqual(DATA["fakeProviderKey"], stub_model.FAKE_KEY)
        reply = stub_model.plan({"messages": [{"role": "user", "content": DATA["promptEcho"]}]})
        self.assertEqual("".join(c for c, _ in reply.chunks).strip(), DATA["replyEcho"])
        for key in ("promptLong", "promptCut", "promptSlowFinish", "promptAskEdit", "promptAskShell",
                    "promptQuestion", "promptChoice", "promptChoice4", "promptSecret", "promptFilesEdit"):
            self.assertTrue(DATA[key].startswith("e2e:"), key)

    def test_FXDATA_fixed_values(self) -> None:
        self.assertEqual(DATA["ghLogin"], fx_gh.LOGIN)
        self.assertEqual(DATA["authCode"], fx_auth.FIXED_CODE)
        self.assertEqual(DATA["codexToken"], fx_codex.TOKEN)
        self.assertEqual(DATA["paseoVersion"], fx_paseo.DAEMON_VERSION)
        self.assertEqual(DATA["tlsPublicName"], fx_tls.DEFAULT_PUBLIC_NAME)

    def test_FXDATA_urls_use_ports_json(self) -> None:
        self.assertEqual(DATA["oc2Url"], f"http://10.0.2.2:{PORTS['FX-OC2']['front']}")
        self.assertEqual(DATA["oc2BUrl"], f"http://10.0.2.2:{PORTS['FX-OC2-B']['front']}")
        self.assertEqual(DATA["codexUrl"], f"ws://10.0.2.2:{PORTS['FX-CODEX']['ws']}")
        self.assertEqual(DATA["paseoUrl"], f"ws://10.0.2.2:{PORTS['FX-PASEO']['daemon']}")
        self.assertEqual(DATA["gitHttpUrl"], f"http://10.0.2.2:{PORTS['FX-GIT']['http']}/app.git")
        self.assertEqual(DATA["gitDaemonUrl"], f"git://10.0.2.2:{PORTS['FX-GIT']['daemon']}/app.git")
        self.assertEqual(DATA["linuxDownloadBase"], f"http://10.0.2.2:{PORTS['FX-LINUXDL']['http']}")

    def test_FXDATA_ports_are_unique(self) -> None:
        seen = {}
        for fixture, table in PORTS.items():
            if fixture.startswith("_"):
                continue
            for name, port in table.items():
                self.assertNotIn(port, seen, f"{fixture}.{name} reuses {seen.get(port)}")
                seen[port] = f"{fixture}.{name}"

    def test_FXDATA_every_fixture_in_the_unit_has_a_server(self) -> None:
        unit = ["FX-OC2", "FX-OC2-B", "FX-TLS", "FX-PASEO", "FX-CODEX", "FX-GH", "FX-GIT", "FX-LINUXDL",
                "FX-USAGE", "FX-AUTH"]
        for fixture in unit:
            self.assertIn(fixture, PORTS)
            self.assertTrue((ROOT / "servers" / (fixture.lower().replace("-", "_") + ".py")).exists(), fixture)


if __name__ == "__main__":
    unittest.main()
