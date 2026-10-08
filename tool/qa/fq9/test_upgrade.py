"""Deterministic upgrade fixtures; no APK, ADB, secrets or device processes."""

import json
from pathlib import Path
import unittest

try:
    from .common import Artifact, DriverFailure
    from .upgrade import FAIL_CODES, run_upgrade
except ImportError:
    from common import Artifact, DriverFailure
    from upgrade import FAIL_CODES, run_upgrade


# Synthetic reviewed identities test the protocol; none asserts a real release.
def _artifact(
    *, build=2196, version="1.2.0", digest="a", signer="c", origin="previous-approved"
):
    return Artifact(
        apk=Path(f"/reviewed/app-{build}.apk"),
        build=build,
        version=version,
        sha256=digest * 64,
        signer=signer * 64,
        origin=origin,
    )


PREVIOUS = _artifact()
CANDIDATE = _artifact(
    build=2197, version="1.2.0", digest="b", origin="coordinator-approved"
)
STABLE = _artifact(build=120, version="1.2.0", origin="published-stable")


def _identity(artifact, uid=10217):
    return {
        key: getattr(artifact, key) for key in ("build", "version", "sha256", "signer")
    } | {"uid": uid}


class _Ports:
    def __init__(self, previous=PREVIOUS):
        self.identity = _identity(previous)
        self.snapshot = object()
        self.calls = []
        self.fail_at = None
        self.retained = {
            key: True
            for key in (
                "sentinel",
                "profiles",
                "preferences",
                "secureStorage",
                "histories",
            )
        }
        self.after_override = {}
        self.removed = False
        self.cleanup_fails = False

    def _step(self, name):
        self.calls.append(name)
        if self.fail_at == name:
            raise RuntimeError("private transcript and Bearer test-secret")

    def installed_identity(self):
        self._step("identity")
        return dict(self.identity)

    def capture_preservation(self):
        self._step("capture")
        return self.snapshot

    def install_update(self, artifact):
        self._step("install")
        if artifact is not CANDIDATE:
            raise AssertionError("No baseline installation permitted")
        self.identity = _identity(artifact) | self.after_override

    def launch(self):
        self._step("launch")

    def verify_preservation(self, snapshot):
        self._step("verify")
        if snapshot is not self.snapshot:
            raise AssertionError("Opaque handle changed")
        return self.retained

    def remove_sentinel(self, snapshot):
        self._step("cleanup")
        if snapshot is not self.snapshot:
            raise AssertionError("Unowned cleanup")
        if self.cleanup_fails:
            raise RuntimeError("private cleanup details")
        self.removed = True


class UpgradeTest(unittest.TestCase):
    def refused(self, ports, code, previous=PREVIOUS, candidate=CANDIDATE, **kwargs):
        with self.assertRaises(DriverFailure) as caught:
            run_upgrade(ports, previous, candidate, **kwargs)
        self.assertEqual(caught.exception.code, code)
        self.assertIn(code, FAIL_CODES)
        self.assertNotIn("test-secret", str(caught.exception))

    def test_update_preserves_selected_subset_and_never_serializes_snapshot(self):
        ports = _Ports()
        result = run_upgrade(ports, PREVIOUS, CANDIDATE)
        self.assertEqual(
            ports.calls,
            [
                "identity",
                "capture",
                "install",
                "identity",
                "launch",
                "verify",
                "cleanup",
            ],
        )
        self.assertTrue(ports.removed)
        self.assertEqual(result["state"], "pass")
        self.assertEqual(result["code"], "verified")
        self.assertTrue(result["facts"]["secureStorage"])
        self.assertTrue(result["facts"]["histories"])
        self.assertFalse(result["facts"]["keystoreSignInVerified"])
        self.assertFalse(result["facts"]["semanticHistoryVerified"])
        self.assertTrue(all(type(value) is bool for value in result["facts"].values()))
        encoded = json.dumps(result)
        for private in (
            "test-secret",
            "/reviewed",
            PREVIOUS.sha256,
            PREVIOUS.signer,
            "10217",
        ):
            self.assertNotIn(private, encoded)

    def test_signer_mismatch_is_blocked_before_any_mutation(self):
        ports = _Ports()
        self.refused(
            ports,
            "upgrade_signer_mismatch",
            candidate=_artifact(build=2197, signer="d", origin="coordinator-approved"),
        )
        self.assertEqual(ports.calls, [])

    def test_missing_previous_and_unapproved_candidate_do_not_mutate(self):
        for previous, candidate, code in [
            (None, CANDIDATE, "upgrade_previous_required"),
            (PREVIOUS, None, "upgrade_artifact_invalid"),
            (
                PREVIOUS,
                _artifact(origin="unreviewed"),
                "upgrade_candidate_origin_invalid",
            ),
        ]:
            with self.subTest(code=code):
                ports = _Ports()
                self.refused(ports, code, previous=previous, candidate=candidate)
                self.assertEqual(ports.calls, [])

    def test_every_previous_identity_field_must_match_before_capture(self):
        for key, value in (
            ("build", 2197),
            ("version", "other"),
            ("sha256", "d" * 64),
            ("signer", "d" * 64),
            ("build", str(PREVIOUS.build)),
        ):
            with self.subTest(key=key, value=value):
                ports = _Ports()
                ports.identity[key] = value
                self.refused(ports, "upgrade_previous_mismatch")
                self.assertEqual(ports.calls, ["identity"])

    def test_missing_or_malformed_installed_identity_is_not_a_baseline(self):
        for identity in (
            {},
            _identity(PREVIOUS) | {"uid": True},
            _identity(PREVIOUS) | {"uid": -1},
        ):
            with self.subTest(identity=identity):
                ports = _Ports()
                ports.identity = identity
                self.refused(ports, "upgrade_identity_invalid")
                self.assertEqual(ports.calls, ["identity"])

    def test_published_stable_requires_reviewed_origin_and_actual_version(self):
        for previous in (
            _artifact(version="1.2.0", origin="previous-approved"),
            _artifact(version="1.2.1", origin="published-stable"),
        ):
            with self.subTest(previous=previous):
                ports = _Ports(previous)
                self.refused(
                    ports,
                    "upgrade_published_baseline_invalid",
                    previous=previous,
                    published=True,
                )
                self.assertEqual(ports.calls, [])
        ports = _Ports(STABLE)
        result = run_upgrade(ports, STABLE, CANDIDATE, published=True)
        self.assertTrue(result["facts"]["publishedBaseline"])
        self.assertEqual(ports.calls.count("install"), 1)

    def test_modern_install_is_never_downgraded_to_manufacture_stable_baseline(self):
        ports = _Ports(PREVIOUS)
        self.refused(
            ports, "upgrade_previous_mismatch", previous=STABLE, published=True
        )
        self.assertEqual(ports.calls, ["identity"])

    def test_install_and_launch_errors_cleanup_exact_snapshot(self):
        for step, code in (
            ("install", "upgrade_install_failed"),
            ("launch", "upgrade_launch_failed"),
            ("verify", "upgrade_verification_failed"),
        ):
            with self.subTest(step=step):
                ports = _Ports()
                ports.fail_at = step
                self.refused(ports, code)
                self.assertEqual(ports.calls[-1], "cleanup")
                self.assertTrue(ports.removed)

    def test_identity_and_capture_errors_fail_without_an_unknown_cleanup_handle(self):
        for step, code in (
            ("identity", "upgrade_identity_failed"),
            ("capture", "upgrade_capture_failed"),
        ):
            with self.subTest(step=step):
                ports = _Ports()
                ports.fail_at = step
                self.refused(ports, code)
                self.assertNotIn("install", ports.calls)
                self.assertNotIn("cleanup", ports.calls)

    def test_actual_candidate_identity_and_uid_are_verified_before_launch(self):
        for override, code in (
            ({"sha256": "a" * 64}, "upgrade_candidate_mismatch"),
            ({"uid": 10218}, "upgrade_uid_changed"),
        ):
            with self.subTest(override=override):
                ports = _Ports()
                ports.after_override = override
                self.refused(ports, code)
                self.assertNotIn("launch", ports.calls)
                self.assertTrue(ports.removed)

    def test_any_selected_preservation_loss_refuses_after_cleanup(self):
        for key in _Ports().retained:
            with self.subTest(key=key):
                ports = _Ports()
                ports.retained[key] = False
                self.refused(ports, "upgrade_preservation_failed")
                self.assertTrue(ports.removed)

    def test_missing_nonboolean_or_extra_preservation_fields_are_not_truth(self):
        for retained in (
            {},
            _Ports().retained | {"profiles": 1},
            _Ports().retained | {"rawSecret": "test-secret"},
        ):
            with self.subTest(retained=retained):
                ports = _Ports()
                ports.retained = retained
                self.refused(ports, "upgrade_preservation_invalid")
                self.assertTrue(ports.removed)

    def test_cleanup_failure_blocks_success_and_is_not_hidden_by_prior_failure(self):
        for fail_at in (None, "install", "verify"):
            with self.subTest(fail_at=fail_at):
                ports = _Ports()
                ports.fail_at = fail_at
                ports.cleanup_fails = True
                self.refused(ports, "upgrade_sentinel_cleanup_failed")
                self.assertEqual(ports.calls.count("cleanup"), 1)


if __name__ == "__main__":
    unittest.main()
