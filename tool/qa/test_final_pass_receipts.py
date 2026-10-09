"""Receipt regressions found by the real final pass; no device operations."""

from contextlib import chdir, contextmanager
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest

from tool.qa import final_pass as batch
from tool.qa.fq3.test_update_matrix import model_scoped_run, updater


class ReceiptRegressionTests(unittest.TestCase):
    def test_relative_output_becomes_absolute_before_driver_dispatch(self):
        with tempfile.TemporaryDirectory() as directory, chdir(directory):
            root = Path(directory)
            args = batch.parser().parse_args(
                [
                    "--execute",
                    "--candidate-apk",
                    "/candidate.apk",
                    "--candidate-build",
                    "2203",
                    "--output",
                    "evidence",
                    "--fast",
                ]
            )
            seen = []

            @contextmanager
            def lock():
                yield 99

            def command(argv, **kwargs):
                return SimpleNamespace(returncode=0, stdout=b"versionCode=2203")

            def dispatch(row, config, context):
                seen.append(context.output)
                receipt = context.output / (row + ".json")
                receipt.write_text("{}")
                return {
                    "status": "pass",
                    "reason": "verified",
                    "receipts": [str(receipt)],
                }

            self.assertEqual(
                batch.execute(
                    args,
                    root=root,
                    lock=lock,
                    command=command,
                    verify=lambda _: {},
                    dispatch=dispatch,
                ),
                0,
            )
            self.assertTrue(seen)
            self.assertTrue(all(path == root / "evidence" for path in seen))

    def test_current_model_scoped_receipts_pass_the_fixed_path_guard(self):
        for build, name in ((2202, "FQ3d"), (2203, "FQ3e")):
            with self.subTest(build=build):
                run = model_scoped_run()
                run["appBuild"] = build
                run["evidence"] = f"docs/qa/{name}-2026-10-09/{run['runID']}.json"
                self.assertEqual(updater.validate_run(run), run)
                run["evidence"] = f"docs/qa/FQ3c-2026-10-08/{run['runID']}.json"
                with self.assertRaises(updater.InvalidEvidence):
                    updater.validate_run(run)
