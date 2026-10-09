import argparse
import io
import json
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import MagicMock, Mock, patch
import urllib.error

from . import run
from .common import Artifact, CANDIDATE_BUILD, DriverFailure, LOCAL_SIGNER
from .runtime import AndroidRuntimeMixin, safe_protocol_failure


class ProtocolFailureTests(unittest.TestCase):
    def port(self):
        port = AndroidRuntimeMixin()
        port._forward = 12345
        port._password = b"private-password"
        return port

    def fail_request(
        self, *, error=None, raw=None, method="GET", path="/global/health"
    ):
        port = self.port()
        opener = MagicMock()
        if error is not None:
            opener.open.side_effect = error
        else:
            response = opener.open.return_value.__enter__.return_value = Mock()
            response.status = 200
            response.read.return_value = raw
        with patch("urllib.request.build_opener", return_value=opener):
            with self.assertRaises(DriverFailure) as caught:
                port.protocol(method, path, query={"private": "query-secret"})
        self.assertEqual(caught.exception.code, "protocol_response_invalid")
        return port._protocol_failure_facts

    def test_http_status_without_sensitive_response_details(self):
        error = urllib.error.HTTPError(
            "https://private-url/secret",
            404,
            "secret-error",
            {"secret": "header"},
            io.BytesIO(b"private-body"),
        )
        facts = self.fail_request(error=error)
        self.assertEqual(
            facts, {"stage": "oc1_health", "kind": "http", "httpStatus": 404}
        )
        self.assertNotIn("secret", json.dumps(facts))
        self.assertNotIn("private", json.dumps(facts))

    def test_bad_json_is_distinct_from_transport(self):
        self.assertEqual(
            self.fail_request(raw=b"private-invalid-json", path="/session/status"),
            {"stage": "session_status", "kind": "invalid_json", "httpStatus": 200},
        )
        self.assertEqual(
            self.fail_request(error=urllib.error.URLError("private-password")),
            {"stage": "oc1_health", "kind": "transport"},
        )

    def test_dynamic_session_id_and_unknown_path_are_not_exported(self):
        for path, stage in (
            ("/session/private-id/message", "session_message_create"),
            ("/secret/password", "other"),
        ):
            self.assertEqual(
                self.fail_request(error=OSError("secret"), method="POST", path=path),
                {"stage": stage, "kind": "transport"},
            )

    def test_projection_refuses_unrecognized_fields_values_and_status(self):
        for value in (
            {"stage": "secret", "kind": "transport"},
            {"stage": "oc1_health", "kind": "secret"},
            {"stage": "oc1_health", "kind": "http", "httpStatus": "secret"},
            {"stage": "oc1_health", "kind": "http", "httpStatus": True},
            {"stage": "oc1_health", "kind": "http", "httpStatus": 999},
            {"stage": "oc1_health", "kind": "http", "body": "secret"},
        ):
            self.assertIsNone(safe_protocol_failure(value))

    def test_run_exports_only_safe_failure_facts_without_changing_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            artifact = Artifact(
                Path("/unused"),
                CANDIDATE_BUILD,
                "1.2.0",
                "a" * 64,
                LOCAL_SIGNER,
                "coordinator-approved",
            )
            for number, facts in enumerate(
                (
                    {"stage": "oc1_health", "kind": "http", "httpStatus": 404},
                    {"stage": "oc1_health", "kind": "http", "body": "secret"},
                )
            ):
                device = types.SimpleNamespace(
                    locked=False,
                    mutated=False,
                    _protocol_failure_facts=facts,
                    device_ready=Mock(
                        side_effect=DriverFailure("protocol_response_invalid")
                    ),
                    close_protocol=Mock(),
                )
                args = argparse.Namespace(
                    case="upgrade",
                    serial="emulator-5554",
                    run_id="fq9-safe-failure",
                    dedicated_avd=None,
                )
                with patch.object(run, "LOCK", root / "lock"):
                    result = run.run_locked(
                        args,
                        {"candidate": artifact, "normal": artifact},
                        None,
                        root / f"report-{number}.json",
                        port_factory=lambda *a, **kw: device,
                    )
                self.assertEqual(result["code"], "protocol_response_invalid")
                self.assertFalse(result["automatedChecksPassed"])
                if number == 0:
                    self.assertEqual(result["protocolFailure"], facts)
                else:
                    self.assertNotIn("protocolFailure", result)
                self.assertNotIn("secret", json.dumps(result))


if __name__ == "__main__":
    unittest.main()
