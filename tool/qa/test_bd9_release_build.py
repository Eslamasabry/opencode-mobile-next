"""Offline command-plan tests; never run Flutter, Gradle or device commands."""
import contextlib
import io
from pathlib import Path
import shlex
import tempfile
import unittest
from unittest.mock import patch

from tool.qa import bd9_release_build as planner


class ReleaseBuildPlanTest(unittest.TestCase):
    def setUp(self):
        self.root = Path('/tmp/bd9 synthetic worktree')

    def test_normal_release_regenerates_registrant_without_qa_inputs(self):
        commands = planner.build_commands('normal', repo_root=self.root)
        self.assertEqual(commands, [[
            planner.PINNED_FLUTTER, 'build', 'apk', '--release',
            '--build-number', '2201', '--target', 'lib/main.dart',
        ]])
        self.assertNotIn('--no-pub', commands[0])
        self.assertFalse(any('ocBd9Smoke' in arg for arg in commands[0]))
        self.assertFalse(any('integration_test' in arg for arg in commands[0]))

    def test_qa_release_and_instrumentation_match_target_and_build_number(self):
        commands = planner.build_commands('qa', repo_root=self.root)
        self.assertEqual(commands, [[
            planner.PINNED_FLUTTER, 'build', 'apk', '--release',
            '--build-number', '2201', '--target',
            'integration_test/bd9_device_smoke_test.dart',
            '--android-project-arg=ocBd9Smoke=true',
        ], [
            str(self.root / 'android/gradlew'), '-p', str(self.root / 'android'),
            ':app:assembleReleaseAndroidTest', '--no-daemon',
            '-PocBd9Smoke=true',
            f'-Ptarget={self.root / "integration_test/bd9_device_smoke_test.dart"}',
            '-PflutterVersionCode=2201',
        ]])
        for command in commands:
            self.assertNotIn('--no-pub', command)

    def test_toolchain_is_the_pinned_shorebird_cache(self):
        self.assertEqual(planner.PINNED_FLUTTER, str(
            Path.home() / '.shorebird/bin/cache/flutter/'
            '91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter'))

    def test_relative_root_becomes_absolute_for_native_target(self):
        commands = planner.build_commands('qa', repo_root='.')
        self.assertIn(f'-Ptarget={Path.cwd() / planner.QA_TARGET}', commands[1])

    def test_returned_commands_cannot_mutate_future_plans(self):
        commands = planner.build_commands('normal', repo_root=self.root)
        commands[0].append('--no-pub')
        self.assertNotIn('--no-pub',
                         planner.build_commands('normal', repo_root=self.root)[0])

    def test_unknown_mode_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'mode must be normal or qa'):
            planner.build_commands('publish', repo_root=self.root)

    def test_cli_only_prints_quoted_commands_without_touching_worktree(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'missing worktree'
            output = io.StringIO()
            # Even the dry plan must not inspect signing files or run a tool.
            with patch('subprocess.run', side_effect=AssertionError('executed tool')), \
                 patch.object(Path, 'open', side_effect=AssertionError('read file')), \
                 contextlib.redirect_stdout(output):
                self.assertEqual(planner.main(['qa', '--repo-root', str(root)]), 0)
            lines = output.getvalue().splitlines()
            self.assertEqual(lines[0], 'BD9 dry plan only; no commands executed.')
            self.assertEqual([shlex.split(line) for line in lines[1:]],
                             planner.build_commands('qa', repo_root=root))
            self.assertFalse(root.exists())


if __name__ == '__main__':
    unittest.main()
