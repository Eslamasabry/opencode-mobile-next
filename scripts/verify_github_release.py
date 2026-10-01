#!/usr/bin/env python3
"""Validate GitHub publication metadata and bytes; never accesses credentials."""

import hashlib
import json
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
    "Verify generated OpenCode SDK integrity",
    "Test generated OpenCode SDK",
    "Analyze generated OpenCode SDK",
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
    "Create draft stable GitHub release",
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


def verify_artifact(folder, name):
    manifest = (folder / "SHA256SUMS").read_text().strip()
    match = re.fullmatch(r"([0-9a-f]{64})  " + re.escape(name), manifest)
    require(match is not None, "Checksum manifest must contain exactly the candidate APK")
    with (folder / name).open("rb") as apk:
        hasher = hashlib.sha256()
        for chunk in iter(lambda: apk.read(1024 * 1024), b""):
            hasher.update(chunk)
        digest = hasher.hexdigest()
    require(digest == match.group(1), "APK checksum mismatch")
    return digest


def main():
    action, directory, head, version = sys.argv[1:]
    root = Path(directory)
    release = read_json(root, "release.json")
    require(release.get("tag_name") == f"v{version}", "Release tag mismatch")
    require(isinstance(release.get("id"), int) and release["id"] > 0, "Missing release identity")
    expected_assets = {f"opencode-mobile-{version}.apk", "SHA256SUMS"}
    assets = release.get("assets")
    require(isinstance(assets, list), "Release asset inventory is missing")
    require(
        len(assets) == len(expected_assets)
        and all(isinstance(asset, dict) for asset in assets)
        and {asset.get("name") for asset in assets} == expected_assets,
        "Release must contain exactly one candidate APK and one SHA256SUMS asset",
    )
    if action == "preflight":
        previous = root / "release-verified.json"
        if previous.exists():
            verified = read_json(root, "release-verified.json")
            fields = ("id", "name", "size", "digest", "updated_at")
            def identities(data):
                return sorted(
                    (tuple(asset.get(key) for key in fields) for asset in data.get("assets", [])),
                    key=repr,
                )
            require(release["id"] == verified["id"] and identities(release) == identities(verified), "Draft assets changed during verification")
        require(release.get("draft") is True, "Refusing to overwrite a published release")
        require(release.get("prerelease") is False, "Expected a stable draft")
        build = read_json(root, "build.json")
        verify_run(build, "android-release", head)
        require(build.get("head_branch") == f"v{version}", "Release build did not run on the candidate tag")
        verify_release_jobs(read_json(root, "build-jobs.json"))
        verify_run(read_json(root, "quality.json"), "android-quality", head)
        verify_quality_jobs(read_json(root, "jobs.json"), workflow_shard_count(QUALITY_WORKFLOW))
    elif action == "artifacts":
        name = f"opencode-mobile-{version}.apk"
        require(
            verify_artifact(root / "ci", name) == verify_artifact(root / "draft", name),
            "Draft APK differs from the signed CI artifact",
        )
        notes = verify_notes(root, version)
        require(f"Source commit: `{head}`" in notes, "Release notes have no matching source evidence")
        require(shorebird_provenance(version) in notes, "Release notes have no Shorebird release provenance")
    elif action == "published":
        require(release.get("draft") is False, "Release remains a draft")
        require(release.get("prerelease") is False, "Release remains a prerelease")
        require(read_json(root, "latest.json").get("id") == release.get("id"), "Release is not latest")
        verify_notes(root, version)
    else:
        raise SystemExit("Unknown verification action")


if __name__ == "__main__":
    main()
