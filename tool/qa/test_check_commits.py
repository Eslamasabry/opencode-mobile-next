"""Exercise the gate against actual git ranges, never this checkout's history."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('check_commits.sh').resolve()
VALID = 'fix(qa): cover the gate [skip ci]\n\nReject malformed messages.\n\nCo-Authored-By: Codex <codex@openai.com>\n'

class CommitGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.git('init', '-q')
        self.git('config', 'user.name', 'Gate fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('commit', '--allow-empty', '-q', '-m', VALID)
        self.base = self.git('rev-parse', 'HEAD').strip()

    def tearDown(self):
        self.tmp.cleanup()

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True)

    def check(self, message):
        self.git('commit', '--allow-empty', '-q', '-m', message)
        return subprocess.run(['bash', str(SCRIPT), '--no-format', self.base, 'HEAD'], cwd=self.root, text=True, capture_output=True)

    def test_valid_range(self):
        self.assertEqual(self.check(VALID).returncode, 0)

    def test_malformed_subject(self):
        result = self.check(VALID.replace('fix(qa): cover the gate', 'random message'))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('malformed subject', result.stdout)

    def test_skip_ci_required(self):
        self.assertNotEqual(self.check(VALID.replace('[skip ci]', '')).returncode, 0)

    def test_format_checked_at_requested_head(self):
        source = self.root / 'bad.dart'
        source.write_text('void main(){print("bad");}\n')
        self.git('add', 'bad.dart')
        self.git('commit', '-q', '-m', VALID)
        # Real pinned formatter, only a tiny synthetic file.
        dart = Path.home() / '.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/dart'
        result = subprocess.run(['bash', str(SCRIPT), self.base, 'HEAD'], cwd=self.root, text=True, capture_output=True, env={**os.environ, 'DART': str(dart)})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('format: not formatted', result.stdout)

if __name__ == '__main__':
    unittest.main()
