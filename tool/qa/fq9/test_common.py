import json
from pathlib import Path
import tempfile
import unittest
from .common import (
    DriverFailure,
    LOCAL_SIGNER,
    STABLE_SIGNER,
    load_manifest,
    load_session_receipt,
)


class CommonTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "input.json"
        self.artifact = dict(
            apk="/unused/candidate.apk",
            build=2197,
            version="1.2.0",
            sha256="a" * 64,
            signer=LOCAL_SIGNER,
            origin="coordinator-approved",
        )

    def write(self, value):
        self.path.write_text(json.dumps(value))
        return self.path

    def manifest(self, **extra):
        return dict(candidate=dict(self.artifact), normal=dict(self.artifact), **extra)

    def test_valid_candidate_and_reviewed_published_origin(self):
        stable = dict(
            self.artifact,
            apk="/unused/stable.apk",
            build=52,
            signer=STABLE_SIGNER,
            origin="published-stable",
        )
        parsed = load_manifest(
            self.write(
                self.manifest(
                    stable=stable,
                    publishedSource="https://github.com/owner/repo/releases/download/v1.2.0/app.apk",
                )
            )
        )
        self.assertEqual(parsed["stable"].version, "1.2.0")
        self.assertNotEqual(parsed["stable"].signer, parsed["candidate"].signer)

    def test_candidate_and_normal_must_be_identical_2197(self):
        for field, value in [
            ("build", 2196),
            ("sha256", "b" * 64),
            ("apk", "/unused/other.apk"),
        ]:
            with self.subTest(field=field):
                data = self.manifest()
                data["normal"][field] = value
                with self.assertRaisesRegex(DriverFailure, "normal_candidate_mismatch"):
                    load_manifest(self.write(data))
        data = self.manifest()
        data["candidate"]["build"] = True
        with self.assertRaisesRegex(DriverFailure, "invalid_artifact_identity"):
            load_manifest(self.write(data))

    def test_origin_and_signer_do_not_come_from_filename(self):
        for field, value, code in [
            ("origin", "published-stable", "invalid_artifact_identity"),
            ("signer", "b" * 64, "unapproved_candidate_signer"),
            ("apk", "relative.apk", "invalid_artifact_identity"),
        ]:
            data = self.manifest()
            data["candidate"][field] = value
            data["normal"][field] = value
            with self.subTest(field=field), self.assertRaisesRegex(DriverFailure, code):
                load_manifest(self.write(data))

    def test_published_receipt_needs_exact_version_and_asset_url(self):
        stable = dict(self.artifact, origin="published-stable")
        for source in [
            None,
            "http://github.com/x/y/releases/download/v1.2.0/app.apk",
            "https://github.com/x/y",
            "https://evil.test/app.apk",
        ]:
            with (
                self.subTest(source=source),
                self.assertRaisesRegex(
                    DriverFailure, "published_stable_receipt_required"
                ),
            ):
                load_manifest(
                    self.write(self.manifest(stable=stable, publishedSource=source))
                )

    def test_duplicate_and_oversized_inputs_rejected(self):
        self.path.write_text('{"candidate":{},"candidate":{}}')
        with self.assertRaisesRegex(DriverFailure, "duplicate_input_field"):
            load_manifest(self.path)
        self.path.write_text(" " * 32769)
        with self.assertRaisesRegex(DriverFailure, "input_too_large"):
            load_manifest(self.path)

    def test_live_receipt_bound_to_unique_run_and_exact_fixture_title(self):
        value = dict(
            engine="opencode",
            directory="/root/projects/fq9-case",
            sessions=[
                dict(id="ses_fixture", title="fq9-case-background", promptID="msg_1")
            ],
        )
        self.assertEqual(
            load_session_receipt(self.write(value), run_id="fq9-case", live=True)[
                "engine"
            ],
            "opencode",
        )
        for key, changed in [
            ("directory", "/root/projects/../shared"),
            (
                "sessions",
                [dict(id="ses_fixture", title="old-background", promptID="msg_1")],
            ),
        ]:
            invalid = dict(value)
            invalid[key] = changed
            with self.subTest(key=key), self.assertRaises(DriverFailure):
                load_session_receipt(self.write(invalid), run_id="fq9-case", live=True)

    def test_receipt_rejects_extra_secret_and_duplicate_session_fields(self):
        value = dict(
            engine="opencode",
            directory="/root/projects/x",
            sessions=[dict(id="ses_fixture", title="FQ9 preserved")],
        )
        invalid = dict(value, password="sensitive")
        with self.assertRaisesRegex(DriverFailure, "invalid_session_receipt"):
            load_session_receipt(self.write(invalid), run_id="fq9-case")
        value["sessions"] *= 2
        with self.assertRaisesRegex(DriverFailure, "duplicate_session_receipt"):
            load_session_receipt(self.write(value), run_id="fq9-case")


if __name__ == "__main__":
    unittest.main()
