"""Offline upgrade orchestration; the root-owned ports hold the device lock.

Retention is limited to the selected data represented by the injected snapshot.
ProfileStore (lib/state/profiles.dart) separates profile/preferences metadata
from flutter_secure_storage, persists model/variant choices per profile, and
intentionally retires legacy quota/glass preferences during load. Whole-file
identity is therefore not proof of semantic preference migration. Histories
come from the domain gateways' server-backed message reads; selected runtime
file preservation does not establish usable or complete conversation history.
Unchanged encrypted storage is not a Keystore decrypt or sign-in assertion.

Snapshots remain opaque and memory-only. Ports must make capture atomic or
clean their own partially created sentinel when capture raises without returning
its handle. Every returned handle is cleaned on success and failure. This module
never restores by uninstalling, clearing data or downgrading a modern baseline.
"""

try:
    from .common import Artifact, DriverFailure
except ImportError:
    from common import Artifact, DriverFailure


FAIL_CODES = frozenset(
    {
        "upgrade_artifact_invalid",
        "upgrade_previous_required",
        "upgrade_published_baseline_invalid",
        "upgrade_candidate_origin_invalid",
        "upgrade_signer_mismatch",
        "upgrade_identity_failed",
        "upgrade_identity_invalid",
        "upgrade_previous_mismatch",
        "upgrade_capture_failed",
        "upgrade_install_failed",
        "upgrade_candidate_mismatch",
        "upgrade_uid_changed",
        "upgrade_launch_failed",
        "upgrade_verification_failed",
        "upgrade_preservation_invalid",
        "upgrade_preservation_failed",
        "upgrade_sentinel_cleanup_failed",
    }
)

_PRESERVATION_KEYS = (
    "sentinel",
    "profiles",
    "preferences",
    "secureStorage",
    "histories",
)
_IDENTITY_KEYS = ("build", "version", "sha256", "signer")


def _call(function, code, *args):
    try:
        return function(*args)
    except Exception:
        # Even a port's DriverFailure is not trusted to contain a safe token.
        raise DriverFailure(code) from None


def _identity(ports):
    identity = _call(ports.installed_identity, "upgrade_identity_failed")
    if (
        not isinstance(identity, dict)
        or any(key not in identity for key in _IDENTITY_KEYS)
        or type(identity.get("uid")) is not int
        or identity["uid"] < 0
    ):
        raise DriverFailure("upgrade_identity_invalid")
    # Keep an independent view even if a port reuses its mutable backing map.
    return {key: identity[key] for key in (*_IDENTITY_KEYS, "uid")}


def _matches(identity, artifact):
    return all(
        type(identity[key]) is type(getattr(artifact, key))
        and identity[key] == getattr(artifact, key)
        for key in _IDENTITY_KEYS
    )


def run_upgrade(ports, previous, candidate, *, published=False):
    """Verify an existing baseline, install its approved update, check a subset.

    Root verifies APK bytes/package/signatures before providing these artifacts
    and implements install_update strictly as install -r -d. Adapter exceptions
    and malformed observations become fixed failures without reflecting payloads.
    A successful result verifies only the selected preservation booleans, never
    actual credential usability or semantic completeness of server histories.
    """
    if previous is None:
        raise DriverFailure("upgrade_previous_required")
    if not isinstance(previous, Artifact) or not isinstance(candidate, Artifact):
        raise DriverFailure("upgrade_artifact_invalid")
    if type(published) is not bool or (
        published
        and (previous.version != "1.2.0" or previous.origin != "published-stable")
    ):
        raise DriverFailure("upgrade_published_baseline_invalid")
    if candidate.origin != "coordinator-approved":
        raise DriverFailure("upgrade_candidate_origin_invalid")
    if previous.signer != candidate.signer:
        raise DriverFailure("upgrade_signer_mismatch")

    before = _identity(ports)
    if not _matches(before, previous):
        # Do not install a previous APK to manufacture a stable baseline.
        raise DriverFailure("upgrade_previous_mismatch")

    snapshot = _call(ports.capture_preservation, "upgrade_capture_failed")
    failure = None
    result = None
    try:
        _call(ports.install_update, "upgrade_install_failed", candidate)
        after = _identity(ports)
        if not _matches(after, candidate):
            raise DriverFailure("upgrade_candidate_mismatch")
        if after["uid"] != before["uid"]:
            raise DriverFailure("upgrade_uid_changed")
        _call(ports.launch, "upgrade_launch_failed")
        retained = _call(
            ports.verify_preservation,
            "upgrade_verification_failed",
            snapshot,
        )
        if (
            not isinstance(retained, dict)
            or set(retained) != set(_PRESERVATION_KEYS)
            or any(type(retained[key]) is not bool for key in _PRESERVATION_KEYS)
        ):
            raise DriverFailure("upgrade_preservation_invalid")
        if not all(retained.values()):
            raise DriverFailure("upgrade_preservation_failed")
        result = {
            "state": "pass",
            "code": "verified",
            "facts": {
                **{key: retained[key] for key in _PRESERVATION_KEYS},
                "uidRetained": True,
                "publishedBaseline": published,
                "keystoreSignInVerified": False,
                "semanticHistoryVerified": False,
            },
        }
    except DriverFailure as error:
        failure = error
    finally:
        # Cleanup failure prevents a pass even when every preservation check
        # succeeded; it also takes precedence over another fixed failure.
        _call(
            ports.remove_sentinel,
            "upgrade_sentinel_cleanup_failed",
            snapshot,
        )
    if failure is not None:
        raise failure from None
    return result
