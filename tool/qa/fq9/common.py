"""Frozen FQ9 inputs and fixed failure tokens; no device access at import time."""

from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re

PACKAGE = "io.github.eslamasabry.opencode_mobile"
SHARED_SERIAL = "emulator-5554"
LOCK = Path("/home/eslam/Storage/tmp/oc-emulator.lock")
CANDIDATE_BUILD = 2202
LOCAL_SIGNER = "1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c"
STABLE_SIGNER = "2d010c2103cb2f78abaaca690ead4d45f8003a6c0a02082cd2a2ae62fd18d0ec"


class DriverFailure(ValueError):
    def __init__(self, code):
        # Drivers pass only authored constants. The CLI separately whitelists
        # codes before exporting them; arbitrary exception bodies never escape.
        if not isinstance(code, str) or not re.fullmatch("[a-z][a-z0-9_]{0,79}", code):
            code = "driver_failure"
        self.code = code
        super().__init__(code)


@dataclass(frozen=True)
class Artifact:
    apk: Path
    build: int
    version: str
    sha256: str
    signer: str
    origin: str


def digest_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path, *, limit=32768):
    try:
        with Path(path).open("rb") as stream:
            raw = stream.read(limit + 1)
        if len(raw) > limit:
            raise DriverFailure("input_too_large")

        def unique(pairs):
            value = {}
            for key, item in pairs:
                if key in value:
                    raise DriverFailure("duplicate_input_field")
                value[key] = item
            return value

        return json.loads(raw, object_pairs_hook=unique)
    except DriverFailure:
        raise
    except (OSError, ValueError, UnicodeError):
        raise DriverFailure("input_unavailable_or_invalid") from None


def load_manifest(path, *, candidate_build=CANDIDATE_BUILD):
    if (
        type(candidate_build) is not int
        or not CANDIDATE_BUILD <= candidate_build <= 999999
    ):
        raise DriverFailure("invalid_candidate_build")
    value = load_json(path)
    if (
        type(value) is not dict
        or set(value) - {"candidate", "normal", "previous", "stable", "publishedSource"}
        or not {"candidate", "normal"} <= set(value)
    ):
        raise DriverFailure("invalid_manifest")
    artifacts = {}
    for name in ("candidate", "normal", "previous", "stable"):
        if name not in value:
            continue
        raw = value[name]
        if type(raw) is not dict or set(raw) != {
            "apk",
            "build",
            "version",
            "sha256",
            "signer",
            "origin",
        }:
            raise DriverFailure("invalid_artifact")
        if (
            type(raw["apk"]) is not str
            or not Path(raw["apk"]).is_absolute()
            or type(raw["build"]) is not int
            or not 1 <= raw["build"] <= 999999
            or type(raw["version"]) is not str
            or not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", raw["version"])
            or any(
                type(raw[k]) is not str or not re.fullmatch("[0-9a-f]{64}", raw[k])
                for k in ("sha256", "signer")
            )
            or raw["origin"]
            != (
                "published-stable"
                if name == "stable"
                else "previous-approved"
                if name == "previous"
                else "coordinator-approved"
            )
        ):
            raise DriverFailure("invalid_artifact_identity")
        artifacts[name] = Artifact(
            Path(raw["apk"]),
            raw["build"],
            raw["version"],
            raw["sha256"],
            raw["signer"],
            raw["origin"],
        )
    candidate, normal = artifacts["candidate"], artifacts["normal"]
    if candidate.build != candidate_build or normal != candidate:
        raise DriverFailure("normal_candidate_mismatch")
    if candidate_build != CANDIDATE_BUILD and (
        "previous" not in artifacts
        or artifacts["previous"].build != CANDIDATE_BUILD
        or artifacts["previous"].signer != candidate.signer
    ):
        raise DriverFailure("invalid_upgrade_baseline")
    if candidate.signer not in (LOCAL_SIGNER, STABLE_SIGNER):
        raise DriverFailure("unapproved_candidate_signer")
    if "stable" in artifacts:
        # Provenance is a coordinator-reviewed receipt, not inferred from name.
        source = value.get("publishedSource")
        if (
            artifacts["stable"].version != "1.2.0"
            or type(source) is not str
            or not re.fullmatch(
                r"https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/download/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\.apk",
                source,
            )
        ):
            raise DriverFailure("published_stable_receipt_required")
    elif "publishedSource" in value:
        raise DriverFailure("unexpected_published_source")
    return artifacts


def load_session_receipt(path, *, run_id, live=False):
    value = load_json(path)
    expected = {"engine", "directory", "sessions"}
    if (
        type(value) is not dict
        or set(value) != expected
        or value["engine"] not in ("opencode", "opencode2")
    ):
        raise DriverFailure("invalid_session_receipt")
    directory = value["directory"]
    if (
        type(directory) is not str
        or not directory.startswith("/root/projects/")
        or not re.fullmatch(r"/root/projects/[A-Za-z0-9_-]{1,100}", directory)
    ):
        raise DriverFailure("invalid_session_directory")
    sessions = value["sessions"]
    if (
        type(sessions) is not list
        or not 1 <= len(sessions) <= 8
        or (live and len(sessions) != 1)
    ):
        raise DriverFailure("invalid_session_receipt")
    for session in sessions:
        fields = {"id", "title", "promptID"} if live else {"id", "title"}
        if type(session) is not dict or set(session) != fields:
            raise DriverFailure("invalid_session_receipt")
        if any(
            type(session[k]) is not str
            or not re.fullmatch("[A-Za-z0-9_-]{1,100}", session[k])
            for k in fields - {"title"}
        ):
            raise DriverFailure("invalid_session_receipt")
        if type(session["title"]) is not str or not re.fullmatch(
            r"[A-Za-z0-9 _.-]{1,160}", session["title"]
        ):
            raise DriverFailure("invalid_session_receipt")
        if live and session["title"] != f"{run_id}-background":
            raise DriverFailure("fixture_title_mismatch")
    if len({s["id"] for s in sessions}) != len(sessions):
        raise DriverFailure("duplicate_session_receipt")
    return value
