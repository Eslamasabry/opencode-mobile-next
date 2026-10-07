"""Exercise the Kotlin gate wrapper, with optional real pinned-CLI regression."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("kotlin_static_analysis.sh").resolve()
ROOT = SCRIPT.parents[2]


class KotlinStaticAnalysisTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="oc-bd5-")
        self.root = Path(self.temp.name)
        self.source = self.root / "src"
        self.source.mkdir()
        self.fixture = self.source / "KotlinGateFixture.kt"
        self.clean = (
            "class KotlinGateFixture {\n"
            "    fun message(value: String): String = value.trim()\n"
            "}\n"
        )
        self.fixture.write_text(self.clean)
        self.baseline = self.root / "baseline.xml"
        self.baseline.write_text(
            '<?xml version="1.0"?>\n<SmellBaseline>'
            "<ManuallySuppressedIssues/><CurrentIssues/></SmellBaseline>\n"
        )

    def tearDown(self):
        self.temp.cleanup()

    def run_gate(self, *arguments, **environment):
        return subprocess.run(
            [str(SCRIPT), *arguments],
            text=True,
            capture_output=True,
            env={**os.environ, **environment},
            timeout=600,
        )

    def test_help_does_not_download_or_analyze(self):
        cache = self.root / "cache"
        result = self.run_gate("--help", OC_DETEKT_CACHE_DIR=str(cache))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("normal gate never updates a baseline", result.stdout)
        self.assertFalse(cache.exists())

    def test_missing_and_unknown_options_fail(self):
        for arguments in [("--baseline",), ("--max-issues", "99")]:
            with self.subTest(arguments=arguments):
                self.assertEqual(self.run_gate(*arguments).returncode, 64)

    def test_missing_baseline_fails_before_download(self):
        cache = self.root / "cache"
        result = self.run_gate(
            "--input", str(self.source),
            "--baseline", str(self.root / "missing.xml"),
            OC_DETEKT_CACHE_DIR=str(cache),
        )
        self.assertEqual(result.returncode, 66, result.stderr)
        self.assertIn("Reviewed baseline is missing", result.stderr)
        self.assertFalse(cache.exists())

    def test_no_kotlin_sources_fails_before_download(self):
        self.fixture.unlink()
        result = self.run_gate("--input", str(self.source))
        self.assertEqual(result.returncode, 66, result.stderr)
        self.assertIn("contains no source files", result.stderr)

    def test_corrupt_cache_never_executes_java(self):
        cache = self.root / "cache"
        cache.mkdir()
        (cache / "detekt-cli-1.23.8-all.jar").write_bytes(b"corrupt")
        binaries = self.root / "bin"
        binaries.mkdir()
        marker = self.root / "java-ran"
        fake_java = binaries / "java"
        fake_java.write_text(f'#!/bin/sh\ntouch "{marker}"\n')
        fake_java.chmod(0o755)
        result = self.run_gate(
            "--input", str(self.source), "--baseline", str(self.baseline),
            OC_DETEKT_CACHE_DIR=str(cache),
            PATH=f"{binaries}{os.pathsep}{os.environ['PATH']}",
        )
        self.assertEqual(result.returncode, 65, result.stderr)
        self.assertIn("Cached detekt checksum mismatch", result.stderr)
        self.assertFalse(marker.exists())

    @unittest.skipUnless(
        os.environ.get("OC_DETEKT_INTEGRATION") == "1",
        "Set OC_DETEKT_INTEGRATION=1 for real pinned Java CLI regression",
    )
    def test_new_issue_fails_then_restoring_fix_passes(self):
        arguments = (
            "--input", str(self.source),
            "--baseline", str(self.baseline),
            "--report-dir", str(self.root / "reports"),
        )
        baseline_before = self.baseline.read_bytes()
        clean = self.run_gate(*arguments)
        self.assertEqual(clean.returncode, 0, clean.stdout + clean.stderr)

        # Removing the safe implementation introduces a new syntax-only issue.
        self.fixture.write_text(
            "class KotlinGateFixture {\n"
            "    fun count(input: Int): Int = input + 567\n"
            "}\n"
        )
        failing = self.run_gate(*arguments)
        self.assertEqual(failing.returncode, 2, failing.stdout + failing.stderr)
        self.assertIn("MagicNumber", failing.stdout + failing.stderr)
        self.assertEqual(self.baseline.read_bytes(), baseline_before)

        self.fixture.write_text(self.clean)
        restored = self.run_gate(*arguments)
        self.assertEqual(restored.returncode, 0, restored.stdout + restored.stderr)
        self.assertEqual(self.baseline.read_bytes(), baseline_before)

    @unittest.skipUnless(
        os.environ.get("OC_DETEKT_INTEGRATION") == "1",
        "Set OC_DETEKT_INTEGRATION=1 for real pinned Java CLI regression",
    )
    def test_established_namespace_is_valid_but_other_underscores_fail(self):
        self.fixture.unlink()
        package = "io.github.eslamasabry.opencode_mobile"
        folder = self.source.joinpath(*package.split("."))
        folder.mkdir(parents=True)
        source = folder / "KotlinGateFixture.kt"
        source.write_text(f"package {package}\n\n{self.clean}")
        arguments = (
            "--input", str(self.source),
            "--baseline", str(self.baseline),
            "--report-dir", str(self.root / "reports"),
        )
        accepted = self.run_gate(*arguments)
        self.assertEqual(accepted.returncode, 0, accepted.stdout + accepted.stderr)

        source.unlink()
        invalid = self.source / "bad_package"
        invalid.mkdir()
        (invalid / "KotlinGateFixture.kt").write_text(
            f"package bad_package\n\n{self.clean}"
        )
        rejected = self.run_gate(*arguments)
        self.assertEqual(rejected.returncode, 2, rejected.stdout + rejected.stderr)
        self.assertIn("PackageNaming", rejected.stdout + rejected.stderr)


if __name__ == "__main__":
    unittest.main()
