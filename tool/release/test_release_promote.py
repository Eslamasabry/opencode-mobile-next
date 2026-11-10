#!/usr/bin/env python3
"""Contract for ``scripts/release.sh promote <tag> [--publish]`` (E02-S90).

CI publishes every new release as a GitHub pre-release; ``promote`` re-verifies
the published assets (SHA256SUMS, signer, package, version, quiet period) and
only with ``--publish`` marks the release non-pre-release and latest.

Run with ``python3 -m unittest discover -s tool/release -p 'test_*.py'``.
Everything runs in a temp copy of the scripts with mocked ``git``, ``gh``,
``apksigner`` and ``aapt``: no network, GitHub, signing key or build.
"""

from __future__ import annotations

import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest

REPO = Path(__file__).resolve().parents[2]
TAG = "v1.0.43+49"
VERSION = "1.0.43+49"
PUBLIC_SIGNER = "842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"
OLD_SIGNER = "2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC"
APKS = [
    f"opencode-mobile-{VERSION}.apk",
    f"opencode-mobile-{VERSION}-arm64-v8a.apk",
    f"opencode-mobile-{VERSION}-armeabi-v7a.apk",
]

GIT_MOCK = """\
#!/usr/bin/env bash
set -euo pipefail
case "$1" in
  status) printf '%s' "${MOCK_DIRTY:-}" ;;
  symbolic-ref) echo "${MOCK_BRANCH:-master}" ;;
  rev-list) echo "0 0" ;;
  remote) echo "https://github.com/Eslamasabry/opencode-mobile-next.git" ;;
  fetch) [[ "${MOCK_TAG_MISSING:-false}" == false || "$*" != *refs/tags/* ]] ;;
  merge-base) [[ "${MOCK_NOT_ON_MASTER:-false}" == false ]] ;;
  rev-parse)
    case "$2" in
      --is-inside-work-tree) echo true ;;
      --abbrev-ref) echo origin/master ;;
      --verify)
        if [[ "$3" == FETCH_HEAD* ]]; then
          echo "${MOCK_REMOTE_TAG:-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa}"
        else
          echo aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        fi ;;
      *) echo aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ;;
    esac ;;
  *) exit 99 ;;
esac
"""

GH_MOCK = """\
#!/usr/bin/env python3
import json, os, shutil, sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
args = sys.argv[1:]
root = Path(os.environ["MOCK_ROOT"])
with (root / "commands.log").open("a") as log:
    log.write(" ".join(args) + "\\n")
def flag(name):
    return os.environ.get(name) == "true"
promoted = (root / "promoted").exists()
def release():
    counter = root / "release-fetch-count"
    count = int(counter.read_text()) + 1 if counter.exists() else 1
    counter.write_text(str(count))
    names = sorted(p.name for p in (root / "assets").iterdir())
    stamp = "2026-11-03T10:00:00Z"
    if flag("MOCK_ASSETS_CHANGE") and count > 1:
        stamp = "2026-11-06T10:00:00Z"
    published = datetime.now(timezone.utc) - timedelta(hours=float(os.environ.get("MOCK_AGE_HOURS", "48")))
    return dict(
        id=11, tag_name=os.environ.get("MOCK_TAG_NAME", "%(tag)s"), draft=False,
        prerelease=not (promoted or flag("MOCK_ALREADY_LATEST")),
        published_at=published.strftime("%%Y-%%m-%%dT%%H:%%M:%%SZ"),
        assets=[dict(id=i + 1, name=n, size=1, digest=None, updated_at=stamp) for i, n in enumerate(names)],
    )
if args[0] == "api":
    path = args[1]
    assert path.startswith("repos/Eslamasabry/opencode-mobile-next/releases/"), path
    if path.endswith("/releases/tags/%(tag)s"):
        if flag("MOCK_DRAFT"):
            print("HTTP 404: Not Found", file=sys.stderr)
            sys.exit(1)
        print(json.dumps(release()))
    elif path.endswith("/releases/latest"):
        if promoted and not flag("MOCK_EDIT_IGNORED"):
            print(json.dumps(dict(id=11, tag_name="%(tag)s")))
        else:
            print(json.dumps(dict(id=7, tag_name=os.environ.get("MOCK_LATEST_TAG", "v1.0.42+47"))))
    else:
        raise SystemExit("unexpected api " + path)
elif args[:2] == ["release", "download"]:
    assert args[2] == "%(tag)s"
    assert args[args.index("--repo") + 1] == "Eslamasabry/opencode-mobile-next"
    target = Path(args[args.index("--dir") + 1])
    for item in (root / "assets").iterdir():
        shutil.copyfile(item, target / item.name)
    if flag("MOCK_TAMPERED_APK"):
        (target / "%(apk)s").write_bytes(b"tampered bytes")
elif args[:2] == ["release", "edit"]:
    assert args[2] == "%(tag)s"
    assert "--prerelease=false" in args and "--latest" in args, args
    assert "--draft" not in args and "--notes-file" not in args, args
    (root / "promoted").touch()
else:
    raise SystemExit("unexpected gh " + " ".join(args))
""" % {"tag": TAG, "apk": APKS[0]}

APKSIGNER_MOCK = """\
#!/usr/bin/env bash
echo "Signer #1 certificate SHA-256 digest: ${MOCK_CERT:-%s}"
""" % PUBLIC_SIGNER

AAPT_MOCK = """\
#!/usr/bin/env bash
echo "package: name='io.github.eslamasabry.opencode_mobile' versionCode='${MOCK_CODE:-49}' versionName='1.0.43'"
"""


class PromoteTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix="promote-"))
        self.addCleanup(shutil.rmtree, self.root, True)
        scripts = self.root / "scripts"
        scripts.mkdir()
        for name in ("release.sh", "release_github.sh", "verify_github_release.py"):
            shutil.copy(REPO / "scripts" / name, scripts / name)
        # pubspec has moved on since the release: promote must use the tag.
        (self.root / "pubspec.yaml").write_text("version: 1.0.44+50\n")
        bin_dir = self.root / "mock-bin"
        tools = self.root / "sdk/build-tools/99.0.0"
        bin_dir.mkdir()
        tools.mkdir(parents=True)
        mocks = {"git": GIT_MOCK, "gh": GH_MOCK, "apksigner": APKSIGNER_MOCK, "aapt": AAPT_MOCK}
        for tool in ("flutter", "shorebird"):
            mocks[tool] = '#!/usr/bin/env bash\necho "Unexpected build process" >&2\nexit 99\n'
        for name, body in mocks.items():
            path = bin_dir / name
            path.write_text(body)
            path.chmod(0o755)
        for name in ("apksigner", "aapt"):
            shutil.copy(bin_dir / name, tools / name)
        assets = self.root / "assets"
        assets.mkdir()
        lines = []
        for name in APKS:
            data = f"fixture bytes of {name}\n".encode()
            (assets / name).write_bytes(data)
            lines.append(f"{hashlib.sha256(data).hexdigest()}  {name}\n")
        (assets / "SHA256SUMS").write_text("".join(lines))

    def run_release(self, *args, **env):
        environment = {
            key: value for key, value in os.environ.items()
            if not key.startswith(("MOCK_", "OC_"))
        }
        environment.update(
            PATH=f"{self.root / 'mock-bin'}:{os.environ['PATH']}",
            MOCK_ROOT=str(self.root),
            ANDROID_HOME=str(self.root / "sdk"),
            HOME=str(self.root),
        )
        environment.update(env)
        return subprocess.run(
            ["bash", str(self.root / "scripts/release.sh"), *args],
            env=environment, capture_output=True, text=True, timeout=60,
        )

    def log(self):
        path = self.root / "commands.log"
        return path.read_text() if path.exists() else ""

    def assert_ok(self, result):
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def assert_refused(self, result, message):
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(message, result.stdout + result.stderr)
        self.assertNotIn("release edit", self.log(), "a refused promotion changed the release")
        self.assertFalse((self.root / "promoted").exists())

    # --- the happy paths -------------------------------------------------

    def test_dry_run_verifies_and_changes_nothing(self):
        result = self.run_release("promote", TAG)
        self.assert_ok(result)
        self.assertIn("Verified pre-release", result.stdout)
        self.assertIn(f"promote {TAG} --publish", result.stdout)
        self.assertIn(f"release download {TAG}", self.log())
        self.assertNotIn("release edit", self.log())

    def test_publish_rechecks_then_marks_latest(self):
        result = self.run_release("promote", TAG, "--publish")
        self.assert_ok(result)
        self.assertIn("to latest", result.stdout)
        edits = [line for line in self.log().splitlines() if line.startswith("release edit")]
        self.assertEqual(len(edits), 1)
        self.assertIn("--prerelease=false --latest", edits[0])
        # Fetched to verify, to recheck right before the edit, and to prove it.
        self.assertEqual((self.root / "release-fetch-count").read_text(), "3")

    def test_quiet_period_can_be_lowered_explicitly(self):
        self.assert_ok(self.run_release("promote", TAG, MOCK_AGE_HOURS="2", OC_PROMOTE_MIN_HOURS="0"))

    # --- refusals ----------------------------------------------------------

    def test_refuses_a_bad_checksum(self):
        sums = self.root / "assets/SHA256SUMS"
        sums.write_text(sums.read_text().replace(sums.read_text()[:64], "0" * 64, 1))
        for args in ((), ("--publish",)):
            self.assert_refused(self.run_release("promote", TAG, *args), "APK checksum mismatch")

    def test_refuses_a_tampered_apk(self):
        self.assert_refused(
            self.run_release("promote", TAG, "--publish", MOCK_TAMPERED_APK="true"),
            "APK checksum mismatch",
        )

    def test_refuses_a_checksum_manifest_missing_an_apk(self):
        sums = self.root / "assets/SHA256SUMS"
        sums.write_text("".join(sums.read_text().splitlines(True)[:2]))
        self.assert_refused(self.run_release("promote", TAG), "must list exactly the candidate APKs")

    def test_refuses_the_wrong_signer(self):
        for args in ((), ("--publish",)):
            self.assert_refused(
                self.run_release("promote", TAG, *args, MOCK_CERT=OLD_SIGNER),
                "certificate does not match the public upgrade lineage",
            )

    def test_refuses_the_wrong_version_code(self):
        self.assert_refused(self.run_release("promote", TAG, "--publish", MOCK_CODE="48"), "expected 1.0.43+49")

    def test_refuses_a_draft(self):
        self.assert_refused(self.run_release("promote", TAG, "--publish", MOCK_DRAFT="true"), "a draft cannot be promoted")

    def test_refuses_a_release_that_is_already_latest(self):
        self.assert_refused(
            self.run_release("promote", TAG, "--publish", MOCK_ALREADY_LATEST="true"),
            "not a pre-release",
        )

    def test_refuses_before_the_quiet_period(self):
        self.assert_refused(
            self.run_release("promote", TAG, "--publish", MOCK_AGE_HOURS="3"),
            "quiet hours",
        )

    def test_refuses_to_move_latest_backwards(self):
        self.assert_refused(
            self.run_release("promote", TAG, "--publish", MOCK_LATEST_TAG="v1.0.44+50"),
            "refusing to move latest backwards",
        )

    def test_refuses_assets_that_change_before_the_edit(self):
        self.assert_refused(
            self.run_release("promote", TAG, "--publish", MOCK_ASSETS_CHANGE="true"),
            "Release assets changed during verification",
        )

    def test_refuses_a_moved_tag_or_one_not_on_master(self):
        self.assert_refused(
            self.run_release("promote", TAG, "--publish", MOCK_REMOTE_TAG="b" * 40),
            "does not match the immutable tag",
        )
        self.assert_refused(
            self.run_release("promote", TAG, "--publish", MOCK_NOT_ON_MASTER="true"),
            "is not part of master",
        )
        self.assert_refused(
            self.run_release("promote", TAG, "--publish", MOCK_TAG_MISSING="true"),
            "missing from origin",
        )

    def test_refuses_outside_a_clean_master(self):
        self.assert_refused(self.run_release("promote", TAG, "--publish", MOCK_BRANCH="dev"), "must run from master")
        self.assert_refused(
            self.run_release("promote", TAG, "--publish", MOCK_DIRTY=" M pubspec.yaml"),
            "not clean",
        )

    def test_fails_if_the_edit_did_not_make_it_latest(self):
        result = self.run_release("promote", TAG, "--publish", MOCK_EDIT_IGNORED="true")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Release is not latest", result.stderr)

    def test_strict_arguments(self):
        for args in (("promote",), ("promote", TAG, "--yes"), ("promote", TAG, "--publish", "x")):
            result = self.run_release(*args)
            self.assertEqual(result.returncode, 64, args)
            self.assertIn("./scripts/release.sh promote <vX.Y.Z+N> [--publish]", result.stderr)
        for tag in ("1.0.43+49", "v1.0.43", "latest", "--publish"):
            self.assert_refused(self.run_release("promote", tag), "vX.Y.Z+N form")
        self.assertEqual(self.log(), "")

    def test_github_mode_no_longer_publishes(self):
        result = self.run_release("github", "--publish")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("release.sh promote v1.0.44+50 --publish", result.stderr)
        self.assertEqual(self.log(), "")


class WorkflowTest(unittest.TestCase):
    """android-release.yml publishes a pre-release, never latest or a draft to flip."""

    def test_workflow_publishes_a_pre_release(self):
        workflow = (REPO / ".github/workflows/android-release.yml").read_text()
        step = workflow.split("      - name: Publish GitHub pre-release\n", 1)[1]
        self.assertIn("--verify-tag --prerelease --latest=false", step)
        self.assertIn("--draft=false --prerelease --latest=false", step)
        self.assertNotIn("--prerelease=false", workflow)
        self.assertNotIn("--latest\n", workflow.replace("--latest=false", ""))
        # The verification steps before it stay in place and blocking.
        for kept in (
            "SIDELOAD_CERT_SHA256: 842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C",
            "- name: Verify APK signer and version",
            "versionCode mismatch",
            "sha256sum opencode-mobile-\"$version\"*.apk > SHA256SUMS",
            "- name: Build and upload Shorebird release APK",
        ):
            self.assertIn(kept, workflow)
        verify = workflow.split("- name: Verify APK signer and version\n", 1)[1].split("- name:", 1)[0]
        self.assertNotIn("continue-on-error", verify)


if __name__ == "__main__":
    unittest.main()
