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
from .runtime import (
    AndroidRuntimeMixin,
    ProtocolHTTPFailure,
    ProtocolResponseFailure,
    safe_protocol_failure,
)


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

    def test_oc1_invalid_json_or_404_detects_oc2_without_switching(self):
        for failure in (
            ProtocolResponseFailure("invalid_json", "oc1_health", 200),
            ProtocolHTTPFailure(404, "oc1_health"),
        ):
            port = self.port()
            port._protocol_failure_facts = failure.safe_facts
            port.protocol = Mock(side_effect=[failure, {"version": "2.0.10"}])
            with self.assertRaisesRegex(
                DriverFailure, "app_managed_engine_unavailable"
            ):
                port._connect("opencode")
            self.assertEqual(port.protocol.call_args_list[1].args, ("GET", "/api/info"))
            self.assertEqual(port.protocol.call_count, 2)
            self.assertEqual(port._protocol_failure_facts, failure.safe_facts)
            self.assertEqual(
                port._runtime_mismatch,
                {"expected": "opencode1", "observed": "opencode2"},
            )
            self.assertFalse(hasattr(port, "_runtime_version"))

    def test_inconclusive_generation_probe_preserves_original_failure(self):
        for response in (
            {},
            {"version": "1.18.32"},
            {"version": "2.invalid"},
            {"version": 2},
            ["2.0.10"],
            DriverFailure("protocol_response_invalid"),
        ):
            port = self.port()
            failure = ProtocolHTTPFailure(404, "oc1_health")
            original = failure.safe_facts
            port._protocol_failure_facts = original

            def protocol(method, path):
                if path == "/global/health":
                    raise failure
                port._protocol_failure_facts = {
                    "stage": "oc2_info",
                    "kind": "transport",
                }
                if isinstance(response, Exception):
                    raise response
                return response

            port.protocol = protocol
            with self.assertRaises(ProtocolHTTPFailure) as caught:
                port._connect("opencode")
            self.assertIs(caught.exception, failure)
            self.assertEqual(port._protocol_failure_facts, original)
            self.assertIsNone(port._runtime_mismatch)

    def test_auth_and_transport_failures_never_probe_another_generation(self):
        for failure in (
            ProtocolHTTPFailure(401, "oc1_health"),
            ProtocolHTTPFailure(403, "oc1_health"),
            ProtocolHTTPFailure(503, "oc1_health"),
            ProtocolResponseFailure("transport", "oc1_health"),
        ):
            port = self.port()
            port.protocol = Mock(side_effect=failure)
            with self.assertRaises(DriverFailure) as caught:
                port._connect("opencode")
            self.assertIs(caught.exception, failure)
            port.protocol.assert_called_once_with("GET", "/global/health")
            self.assertIsNone(port._runtime_mismatch)

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
                    _runtime_mismatch={
                        "expected": "opencode1",
                        "observed": "opencode2",
                    },
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
                self.assertEqual(
                    result["runtimeMismatch"],
                    {"expected": "opencode1", "observed": "opencode2"},
                )
                if number == 0:
                    self.assertEqual(result["protocolFailure"], facts)
                else:
                    self.assertNotIn("protocolFailure", result)
                self.assertNotIn("secret", json.dumps(result))


if __name__ == "__main__":
    unittest.main()
