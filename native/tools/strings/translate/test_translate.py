#!/usr/bin/env python3
"""Unit tests for translate.py (including Gap A: untracked shipped keys)."""
import os
import tempfile
import unittest
from unittest import mock

import translate as tr


class TranslateUntrackedTest(unittest.TestCase):
    def test_analyse_flags_untracked_existing_key(self):
        with tempfile.TemporaryDirectory() as tmp:
            native = os.path.join(tmp, "native")
            strings = os.path.join(native, "tools", "strings")
            state_dir = os.path.join(strings, "translation-state")
            res_en = os.path.join(native, "ui", "words", "src", "commonMain", "composeResources", "values")
            res_ar = os.path.join(native, "ui", "words", "src", "commonMain", "composeResources", "values-ar")
            os.makedirs(state_dir)
            os.makedirs(res_en)
            os.makedirs(res_ar)
            with open(os.path.join(res_en, "strings_common.xml"), "w", encoding="utf-8") as f:
                f.write('<resources>\n    <string name="hello">Hello</string>\n</resources>\n')
            with open(os.path.join(res_ar, "strings_common.xml"), "w", encoding="utf-8") as f:
                f.write('<resources>\n    <string name="hello">مرحبا</string>\n</resources>\n')
            # No entry in translation-state/ar.tsv!
            with open(os.path.join(state_dir, "ar.tsv"), "w", encoding="utf-8") as f:
                f.write("")

            with mock.patch.object(tr, "NATIVE", native), \
                 mock.patch.object(tr, "STRINGS", strings), \
                 mock.patch.object(tr, "STATE_DIR", state_dir), \
                 mock.patch.object(tr, "ROOTS", [("words", "ui/words/src/commonMain/composeResources", "cmp")]):
                work, total, done = tr.analyse("ar")

            self.assertEqual(total, 1)
            self.assertEqual(done, 0)
            self.assertEqual(len(work), 1)
            self.assertEqual(work[0][3], "hello")
            self.assertEqual(work[0][8], "untracked")


if __name__ == "__main__":
    unittest.main()
