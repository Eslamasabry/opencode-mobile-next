"""Offline fixtures only: no provider, release, network or real executable calls."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import struct
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("byo_release", ROOT / "scripts/byo-host/build_release.py")
RELEASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RELEASE)


def fixture(architecture, filename="opencode", extra=False):
    data = bytearray(128)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<H", data, 18, 62 if architecture == "amd64" else 183)
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode="w:gz") as archive:
        info = tarfile.TarInfo(filename)
        info.size = len(data)
        archive.addfile(info, io.BytesIO(data))
        if extra:
            link = tarfile.TarInfo("bad")
            link.type = tarfile.SYMTYPE
            link.linkname = "../../etc/passwd"
            archive.addfile(link)
    return output.getvalue()


class ReleasePipelineTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name)
        self.pins = json.loads((ROOT / "scripts/byo-host/release-pins.json").read_text())
        self.archives = {arch: fixture(arch) for arch in ("amd64", "arm64")}
        for arch, pin in self.pins["artifacts"].items():
            pin["sha256"] = hashlib.sha256(self.archives[arch]).hexdigest()
        self.urls = {pin["url"]: self.archives[arch] for arch, pin in self.pins["artifacts"].items()}
        self.repo = "Eslamasabry/opencode-mobile-next"
        self.tag = "v1.2.3+4"

    def build(self):
        return RELEASE.build(self.pins, self.tag, self.repo, self.path, fetch=self.urls.__getitem__)

    def reviewed(self):
        manifest = self.build()
        self.pins["appVersions"][manifest["appVersion"]] = {
            arch: pin["sha256"] for arch, pin in manifest["artifacts"].items()}
        return manifest

    @staticmethod
    def response(state=None, status=200):
        text = f"HTTP/2.0 {status} status\r\nx-test: yes\r\n\r\n" + json.dumps(state)
        return subprocess.CompletedProcess([], 0 if status == 200 else 1, text, "")

    def test_pinned_source_before_unpack(self):
        with self.assertRaisesRegex(ValueError, "upstreamChecksumMismatch"):
            RELEASE.verified_binary(b"bad", "a" * 64)

    def test_malicious_member_rejected(self):
        for data in (fixture("amd64", "../opencode"), fixture("amd64", extra=True)):
            with self.assertRaisesRegex(ValueError, "invalidUpstreamArchive"):
                RELEASE.verified_binary(data, hashlib.sha256(data).hexdigest())

    def test_bundle_and_manifest_reproducible(self):
        one = self.build()
        before = {p.name: p.read_bytes() for p in self.path.iterdir()}
        two = self.build()
        self.assertEqual(one, two)
        self.assertEqual(before, {p.name: p.read_bytes() for p in self.path.iterdir()})
        self.assertEqual(one["bundleVersion"], RELEASE.packager().BUNDLE_VERSION)
        self.assertEqual(set(one["artifacts"]), {"amd64", "arm64"})
        for pin in one["artifacts"].values():
            self.assertIn("/releases/download/v1.2.3+4/", pin["url"])

    def test_architecture_mismatch_rejected(self):
        self.urls[self.pins["artifacts"]["amd64"]["url"]] = self.archives["arm64"]
        self.pins["artifacts"]["amd64"]["sha256"] = hashlib.sha256(self.archives["arm64"]).hexdigest()
        with self.assertRaisesRegex(ValueError, "unsupportedBinary"):
            self.build()

    def test_review_absent_blocks_attachment_before_gh(self):
        manifest = self.build()
        with self.assertRaisesRegex(ValueError, "bundleReviewRequired"):
            RELEASE.attach(self.pins, manifest, self.path, self.tag, self.repo,
                           run=lambda *_args, **_kwargs: self.fail("gh must not run"))

    def test_modified_reviewed_bundle_rejected(self):
        manifest = self.reviewed()
        (self.path / manifest["artifacts"]["amd64"]["filename"]).write_bytes(b"modified")
        with self.assertRaisesRegex(ValueError, "bundleChecksumMismatch"):
            RELEASE.verify_reviewed(self.pins, manifest, self.path)

    def test_published_release_refused(self):
        manifest = self.reviewed()
        with self.assertRaisesRegex(ValueError, "draftRequired"):
            RELEASE.attach(self.pins, manifest, self.path, self.tag, self.repo,
                           run=lambda *_args, **_kwargs: self.response({"draft": False, "tag_name": self.tag}))

    def test_create_missing_release_always_draft_verifytag(self):
        manifest = self.reviewed()
        calls = []
        state = {"draft": True, "tag_name": self.tag, "assets": []}
        def run(args, **_kwargs):
            calls.append(args)
            if args[1] == "api":
                return self.response(None, 404) if len(calls) == 1 else self.response(state)
            return subprocess.CompletedProcess(args, 0, "", "")
        RELEASE.attach(self.pins, manifest, self.path, self.tag, self.repo, run=run)
        create = next(args for args in calls if args[1:3] == ["release", "create"])
        self.assertIn("--draft", create)
        self.assertIn("--verify-tag", create)
        self.assertFalse(any("--clobber" in args or "edit" in args for args in calls))
        self.assertEqual(sum(args[1:3] == ["release", "upload"] for args in calls), 4)

    def test_api_uncertain_not_treated_as_missing(self):
        manifest = self.reviewed()
        with self.assertRaisesRegex(ValueError, "releaseApiFailed"):
            RELEASE.attach(self.pins, manifest, self.path, self.tag, self.repo,
                           run=lambda *_args, **_kwargs: self.response(None, 403))

    def test_non_tag_and_pubspec_mismatch_rejected(self):
        pubspec = self.path / "pubspec.yaml"
        pubspec.write_text("version: 1.2.3+4\n")
        self.assertEqual(RELEASE.validate_release_identity(self.tag, self.repo, pubspec), "1.2.3+4")
        for tag in ("main", "v1.2.3", "v1.2.3+5", "v1.2.3+4;bad"):
            with self.assertRaises(ValueError):
                RELEASE.validate_release_identity(tag, self.repo, pubspec)

    def test_existing_different_asset_never_clobbered(self):
        manifest = self.reviewed()
        name = manifest["artifacts"]["amd64"]["filename"]
        state = {"draft": True, "tag_name": self.tag,
                 "assets": [{"name": name, "digest": "sha256:" + "0" * 64}]}
        with self.assertRaisesRegex(ValueError, "existingAssetMismatch"):
            RELEASE.attach(self.pins, manifest, self.path, self.tag, self.repo,
                           run=lambda *_args, **_kwargs: self.response(state))

    def test_draft_published_between_uploads_refused(self):
        manifest = self.reviewed()
        state = {"draft": True, "tag_name": self.tag, "assets": []}
        calls = []
        def run(args, **_kwargs):
            calls.append(args)
            if args[1] == "api":
                # Initial lookup, draft assertion, first pre-upload assertion.
                return self.response(dict(state, draft=len(calls) <= 3))
            return subprocess.CompletedProcess(args, 0, "", "")
        with self.assertRaisesRegex(ValueError, "draftRequired"):
            RELEASE.attach(self.pins, manifest, self.path, self.tag, self.repo, run=run)
        self.assertEqual(sum(args[1:3] == ["release", "upload"] for args in calls), 1)

    def test_identical_uploaded_assets_are_idempotent(self):
        manifest = self.reviewed()
        files = [p for p in self.path.iterdir() if p.is_file()]
        state = {"draft": True, "tag_name": self.tag, "assets": [
            {"name": p.name, "digest": "sha256:" + hashlib.sha256(p.read_bytes()).hexdigest()}
            for p in files]}
        calls = []
        def run(args, **_kwargs):
            calls.append(args)
            self.assertEqual(args[1], "api")
            return self.response(state)
        RELEASE.attach(self.pins, manifest, self.path, self.tag, self.repo, run=run)
        self.assertEqual(len(calls), 2)

    def test_workflow_only_canonical_tags_and_pinned_action(self):
        source = (ROOT / ".github/workflows/byo-host-bundle.yml").read_text()
        self.assertNotIn("workflow_dispatch", source)
        self.assertNotIn("pull_request", source)
        self.assertIn("github.repository == 'Eslamasabry/opencode-mobile-next'", source)
        self.assertIn('group: android-release-${{ github.ref }}', source)
        self.assertIn("persist-credentials: false", source)
        self.assertRegex(source, r"actions/checkout@[a-f0-9]{40}")
        self.assertNotIn("--clobber", source)


if __name__ == "__main__":
    unittest.main()
