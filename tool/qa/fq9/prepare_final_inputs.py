"""Prepare reviewed upgrade inputs using host APK checks only; never use ADB."""

import argparse
import json
import os
from pathlib import Path
import re
import sys

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[3]))

from tool.qa.fq9.common import Artifact, DriverFailure, LOCAL_SIGNER, load_json
from tool.qa.fq9.ports import verify_artifact


STORAGE_ROOT = Path("/home/eslam/Storage")
BASELINE_SHA256 = "e63fb2e4ff32280ad4c739aee9c17db508eab2e99a42573c4e83bd66dc0babb0"
FIXTURE = {
    "engine": "opencode",
    "run_id": "fq9-2202-next-retained-a",
    "project_backing": "files/projects",
    "guest_root": "/root/projects",
    "title_suffix": "-retained",
    "no_reply": True,
    "marker": "FQ9_RETAINED_HISTORY: local upgrade fixture; do not execute anything.",
}


def _reviewed_plan(path):
    plan = load_json(path)
    if (
        type(plan) is not dict
        or set(plan) != {"schema", "baseline", "fixture"}
        or type(plan["schema"]) is not int
        or plan["schema"] != 1
        or type(plan["fixture"]) is not dict
        or set(plan["fixture"]) != set(FIXTURE)
        or any(
            type(plan["fixture"][key]) is not type(value)
            or plan["fixture"][key] != value
            for key, value in FIXTURE.items()
        )
    ):
        raise DriverFailure("reviewed_plan_invalid")
    baseline = plan["baseline"]
    expected = {
        "build": 2202,
        "version": "1.2.0",
        "sha256": BASELINE_SHA256,
        "signer": LOCAL_SIGNER,
        "origin": "previous-approved",
    }
    if (
        type(baseline) is not dict
        or set(baseline) != {*expected, "apk"}
        or any(
            type(baseline[key]) is not type(value) or baseline[key] != value
            for key, value in expected.items()
        )
        or type(baseline["apk"]) is not str
        or not Path(baseline["apk"]).is_absolute()
    ):
        raise DriverFailure("reviewed_baseline_invalid")
    return plan


def _output_path(output, storage_root):
    output, storage_root = Path(output), Path(storage_root)
    if (
        not output.is_absolute()
        or ".." in output.parts
        or output == storage_root
        or not output.is_relative_to(storage_root)
    ):
        raise DriverFailure("private_output_invalid")
    # Reject linked ancestors, including dangling links, and all checkout roots.
    # A linked-worktree .git file counts just as a normal repository directory.
    for ancestor in (output, *output.parents):
        if ancestor.is_symlink() or (ancestor / ".git").exists():
            raise DriverFailure("private_output_invalid")
    if output.exists() or not output.parent.is_dir():
        raise DriverFailure("private_output_unavailable")
    return output


def _safe_apk_path(value):
    path = Path(value)
    if (
        not path.is_absolute()
        or ".." in path.parts
        or any(part.is_symlink() for part in (path, *path.parents))
        or not path.is_file()
    ):
        raise DriverFailure("artifact_unavailable")
    return path


def _write_json(path, value):
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, "w") as stream:
        json.dump(value, stream, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())


def prepare(
    plan_path,
    candidate_apk,
    candidate_build,
    candidate_sha256,
    output,
    *,
    storage_root=STORAGE_ROOT,
):
    """Validate two APKs and emit private inputs, without seeding a device.

    ``storage_root`` is injectable for filesystem-only tests. Production callers
    use the fixed Storage root. The seed receipt path is reserved, never created.
    """
    plan = _reviewed_plan(plan_path)
    if (
        type(candidate_build) is not int
        or not 2202 < candidate_build <= 999999
        or type(candidate_sha256) is not str
        or re.fullmatch(r"[0-9a-f]{64}", candidate_sha256) is None
        or not Path(candidate_apk).is_absolute()
    ):
        raise DriverFailure("candidate_input_invalid")
    output = _output_path(output, storage_root)
    baseline_path = _safe_apk_path(plan["baseline"]["apk"])
    candidate_path = _safe_apk_path(candidate_apk)
    previous = Artifact(**{**plan["baseline"], "apk": baseline_path})
    candidate = Artifact(
        candidate_path,
        candidate_build,
        "1.2.0",
        candidate_sha256,
        LOCAL_SIGNER,
        "coordinator-approved",
    )
    # verify_artifact checks package, version, certificate and the caller's hash,
    # including a second digest after host aapt/apksigner identity inspection.
    verify_artifact(previous)
    verify_artifact(candidate)
    candidate_row = {
        "apk": str(candidate.apk),
        "build": candidate.build,
        "version": candidate.version,
        "sha256": candidate.sha256,
        "signer": candidate.signer,
        "origin": candidate.origin,
    }
    manifest = {
        "candidate": candidate_row,
        "normal": candidate_row,
        "previous": plan["baseline"],
    }
    rows = {
        "fq9-upgrade": {
            "manifest": str(output / "fq9-artifacts.json"),
            "seed_history_receipt": str(output / "history.json"),
            "run_id": FIXTURE["run_id"],
        }
    }
    created = []
    try:
        output.mkdir(mode=0o700)
        for filename, value in (
            ("fq9-artifacts.json", manifest),
            ("rows.json", rows),
            ("fixture-plan.json", plan),
        ):
            path = output / filename
            created.append(path)
            _write_json(path, value)
        fd = os.open(output, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    except OSError:
        # Remove only files created by this invocation; never reuse a directory.
        for path in created:
            path.unlink(missing_ok=True)
        if created:
            output.rmdir()
        raise DriverFailure("private_output_unavailable") from None
    return rows


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--candidate-apk", type=Path, required=True)
    parser.add_argument("--candidate-build", type=int, required=True)
    parser.add_argument("--candidate-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        result = prepare(
            args.plan,
            args.candidate_apk,
            args.candidate_build,
            args.candidate_sha256,
            args.output,
        )
    except (DriverFailure, OSError, TypeError, ValueError):
        print(json.dumps({"state": "blocked", "code": "final_inputs_unavailable"}))
        return 1
    print(json.dumps({"state": "prepared", "rows": result}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
