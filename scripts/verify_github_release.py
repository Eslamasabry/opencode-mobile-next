#!/usr/bin/env python3
"""Validate GitHub publication metadata and bytes; never accesses credentials."""

from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import sys


def require(condition, message):
    if not condition:
        raise SystemExit(f"ERROR: {message}")


def read_json(root, name):
    return json.loads((root / name).read_text())


def verify_run(run, workflow, head):
    require(run.get("head_sha") == head, "CI source differs from candidate")
    require(run.get("path") == f".github/workflows/{workflow}.yml", "Wrong CI workflow")
    require(
        run.get("status") == "completed" and run.get("conclusion") == "success",
        "CI run did not complete successfully",
    )
    require(run.get("event") in {"push", "workflow_dispatch"}, "Untrusted CI event")


QUALITY_WORKFLOW = Path(".github/workflows/android-quality.yml")
# Parallel jobs of one android-quality run, by job name: each must succeed with
# these steps successful. The test job is a matrix of shards that together run
# every test file exactly once, so every shard of the workflow must be present.
CHECKS_STEPS = {
    "Analyze",
    "Check the serial test runner",
    "Run Android release lint",
}
TEST_STEPS = {"Test"}
BUILD_STEPS = {"Compile test-signed release APK", "Verify release artifact exists"}
GATE_STEPS = {"Require every quality job"}
SHARD_NAME = re.compile(r"test \(shard (\d+)/(\d+)\)")


# The android-release run must have built the APK as an uploaded Shorebird
# release, so the published version can receive `shorebird patch` updates. A
# plain `flutter build apk` (the pre-Shorebird step) or a Shorebird dry-run is
# not patchable and is refused.
RELEASE_JOB = "build"
RELEASE_STEPS = {
    "Require the release signing and Shorebird secrets",
    "Build and upload Shorebird release APK",
    "Verify APK signer and version",
    "Publish GitHub pre-release",
}
UNPATCHABLE_BUILD_STEPS = {
    "Compile signed release APK",
    "Build Shorebird dry-run APK (nothing uploaded)",
}


def shorebird_provenance(version):
    return f"Shorebird release {version}, Flutter "


def verify_release_jobs(data):
    jobs = data.get("jobs")
    require(isinstance(jobs, list) and jobs, "Release build run has no jobs")
    require(
        data.get("total_count", len(jobs)) == len(jobs),
        "Release build job list is incomplete",
    )
    matches = [job for job in jobs if isinstance(job, dict) and job.get("name") == RELEASE_JOB]
    require(len(matches) == 1, f"Release build job {RELEASE_JOB!r} is missing or duplicated")
    job = matches[0]
    steps = [step for step in job.get("steps", []) if isinstance(step, dict)]
    succeeded = {step.get("name") for step in steps if step.get("conclusion") == "success"}
    require(
        not succeeded & UNPATCHABLE_BUILD_STEPS,
        "Release APK was not built as an uploaded Shorebird release (plain flutter build or dry-run)",
    )
    require(
        job.get("conclusion") == "success" and RELEASE_STEPS <= succeeded,
        "Release build did not upload a Shorebird release and verify its APK",
    )


def workflow_shard_count(path):
    """Number of test shards the candidate's quality workflow declares."""
    require(path.is_file(), f"Missing {path}")
    found = re.findall(r"^\s*shard:\s*\[([^\]]*)\]\s*$", path.read_text(), re.MULTILINE)
    require(len(found) == 1, "Quality workflow must declare exactly one test shard matrix")
    values = [value.strip() for value in found[0].split(",") if value.strip()]
    require(
        values and values == [str(index) for index in range(1, len(values) + 1)],
        "Quality workflow shards must be numbered 1..N",
    )
    return len(values)


def verify_quality_jobs(data, shard_count):
    jobs = data.get("jobs")
    require(isinstance(jobs, list) and jobs, "Quality run has no jobs")
    require(
        data.get("total_count", len(jobs)) == len(jobs),
        "Quality job list is incomplete",
    )

    def single(name):
        matches = [job for job in jobs if isinstance(job, dict) and job.get("name") == name]
        require(len(matches) == 1, f"Quality job {name!r} is missing or duplicated")
        return matches[0]

    def passed(name, steps):
        job = single(name)
        succeeded = {
            step.get("name") for step in job.get("steps", [])
            if isinstance(step, dict) and step.get("conclusion") == "success"
        }
        require(
            job.get("conclusion") == "success" and steps <= succeeded,
            f"Quality job {name!r} did not pass all of its steps (APK-only and partial runs are insufficient)",
        )

    passed("checks", CHECKS_STEPS)
    passed("build", BUILD_STEPS)
    passed("gate", GATE_STEPS)
    shards = [
        SHARD_NAME.fullmatch(job.get("name", "")) for job in jobs if isinstance(job, dict)
    ]
    require(
        all(int(match.group(2)) == shard_count for match in shards if match),
        f"Test shards differ from the workflow's {shard_count}",
    )
    for index in range(1, shard_count + 1):
        passed(f"test (shard {index}/{shard_count})", TEST_STEPS)


def verify_notes(root, version):
    committed = Path(f"docs/releases/v{version}.md").read_text().strip()
    require(committed.startswith(f"# OpenCode Mobile {version}\n"), "Missing versioned stable notes")
    notes = (root / "ci/RELEASE_NOTES.md").read_text().strip()
    require(notes.startswith(committed + "\n"), "CI notes differ from committed release notes")
    release = read_json(root, "release.json")
    require(release.get("body", "").strip() == notes, "Draft body differs from verified CI notes")
    return notes


ABIS = ("arm64-v8a", "armeabi-v7a")


def apk_names(version):
    """The universal APK of record and the per-ABI APKs derived from it."""
    return [f"opencode-mobile-{version}.apk"] + [f"opencode-mobile-{version}-{abi}.apk" for abi in ABIS]


def verify_artifact(folder, name, names):
    lines = (folder / "SHA256SUMS").read_text().strip().splitlines()
    entries = {}
    for line in lines:
        match = re.fullmatch(r"([0-9a-f]{64})  (\S+)", line)
        require(match is not None, "Checksum manifest has a malformed line")
        entries[match.group(2)] = match.group(1)
    require(set(entries) == set(names), "Checksum manifest must list exactly the candidate APKs")
    with (folder / name).open("rb") as apk:
        hasher = hashlib.sha256()
        for chunk in iter(lambda: apk.read(1024 * 1024), b""):
            hasher.update(chunk)
        digest = hasher.hexdigest()
    require(digest == entries[name], "APK checksum mismatch")
    return digest


TAG_BUILD = re.compile(r"v\d+\.\d+\.\d+\+([1-9]\d*)")
# A promoted release must have been public as a pre-release this long.
DEFAULT_PROMOTE_MIN_HOURS = 24


def asset_identities(data):
    fields = ("id", "name", "size", "digest", "updated_at")
    return sorted(
        (tuple(asset.get(key) for key in fields) for asset in data.get("assets", [])),
        key=repr,
    )


def require_unchanged(root, release):
    """After a first verification, the release and its assets must not move."""
    previous = root / "release-verified.json"
    if previous.exists():
        verified = read_json(root, "release-verified.json")
        require(
            release["id"] == verified["id"] and asset_identities(release) == asset_identities(verified),
            "Release assets changed during verification",
        )


def promote_min_hours():
    raw = os.environ.get("OC_PROMOTE_MIN_HOURS", str(DEFAULT_PROMOTE_MIN_HOURS))
    require(re.fullmatch(r"\d{1,4}", raw) is not None, "OC_PROMOTE_MIN_HOURS must be a whole number of hours")
    return int(raw)


def verify_promotable(root, release, version):
    """A published pre-release, quiet long enough, with intact checksummed APKs."""
    require(release.get("draft") is False, "A draft is not promotable; CI publishes a pre-release")
    require(release.get("prerelease") is True, "Release is not a pre-release (already promoted?)")
    published = release.get("published_at")
    require(isinstance(published, str), "Release has no publication time")
    published_at = datetime.fromisoformat(published.replace("Z", "+00:00"))
    age_hours = (datetime.now(timezone.utc) - published_at).total_seconds() / 3600
    min_hours = promote_min_hours()
    require(
        age_hours >= min_hours,
        f"Pre-release is {age_hours:.1f} h old; promote after {min_hours} quiet hours (OC_PROMOTE_MIN_HOURS)",
    )
    names = apk_names(version)
    for name in names:
        verify_artifact(root / "assets", name, names)
    latest = read_json(root, "previous-latest.json")
    latest_tag = latest.get("tag_name")
    if isinstance(latest_tag, str) and latest.get("id") is not None:
        match = TAG_BUILD.match(latest_tag)
        own = int(TAG_BUILD.fullmatch(release["tag_name"]).group(1))
        require(
            match is None or int(match.group(1)) < own,
            f"Latest release {latest_tag} is not older than {release['tag_name']}; refusing to move latest backwards",
        )


def main():
    action, directory, head, version = sys.argv[1:]
    root = Path(directory)
    release = read_json(root, "release.json")
    require(release.get("tag_name") == f"v{version}", "Release tag mismatch")
    require(isinstance(release.get("id"), int) and release["id"] > 0, "Missing release identity")
    expected_assets = set(apk_names(version)) | {"SHA256SUMS"}
    assets = release.get("assets")
    require(isinstance(assets, list), "Release asset inventory is missing")
    require(
        len(assets) == len(expected_assets)
        and all(isinstance(asset, dict) for asset in assets)
        and {asset.get("name") for asset in assets} == expected_assets,
        "Release must contain exactly the candidate APKs and one SHA256SUMS asset",
    )
    if action == "preflight":
        # CI publishes every new release as a pre-release (E02-S90); this
        # proves that pre-release's provenance. `promote` makes it latest.
        require_unchanged(root, release)
        require(release.get("draft") is False, "Expected the CI-published pre-release, found a draft")
        require(release.get("prerelease") is True, "Release is no longer a pre-release (already promoted)")
        build = read_json(root, "build.json")
        verify_run(build, "android-release", head)
        require(build.get("head_branch") == f"v{version}", "Release build did not run on the candidate tag")
        verify_release_jobs(read_json(root, "build-jobs.json"))
        verify_run(read_json(root, "quality.json"), "android-quality", head)
        verify_quality_jobs(read_json(root, "jobs.json"), workflow_shard_count(QUALITY_WORKFLOW))
    elif action == "artifacts":
        names = apk_names(version)
        for name in names:
            require(
                verify_artifact(root / "ci", name, names) == verify_artifact(root / "draft", name, names),
                f"Draft {name} differs from the signed CI artifact",
            )
        notes = verify_notes(root, version)
        require(f"Source commit: `{head}`" in notes, "Release notes have no matching source evidence")
        require(shorebird_provenance(version) in notes, "Release notes have no Shorebird release provenance")
    elif action == "promote":
        require_unchanged(root, release)
        verify_promotable(root, release, version)
    elif action == "promoted":
        require_unchanged(root, release)
        require(release.get("draft") is False, "Release is a draft")
        require(release.get("prerelease") is False, "Release remains a pre-release")
        require(read_json(root, "latest.json").get("id") == release.get("id"), "Release is not latest")
    else:
        raise SystemExit("Unknown verification action")


if __name__ == "__main__":
    main()
