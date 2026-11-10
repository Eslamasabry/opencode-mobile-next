#!/usr/bin/env python3
"""Print BD9 release build plans without running commands or reading signing files.

Flutter must run its pub/registrant step when switching between the QA target and
the normal app. --no-pub can retain IntegrationTestPlugin in the generated release
registrant even when its development dependency is excluded from the normal APK.
The caller owns the machine lock, signing authorization and build cleanup.
"""
import argparse
from pathlib import Path
import shlex

PINNED_FLUTTER = str(
    Path.home() / '.shorebird/bin/cache/flutter/'
    '91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter'
)
BUILD_NUMBER = '2201'
NORMAL_TARGET = 'lib/main.dart'
QA_TARGET = 'integration_test/bd9_device_smoke_test.dart'


def build_commands(mode, *, repo_root):
    """Return fresh argument lists for the pinned normal or QA release build."""
    if mode not in ('normal', 'qa'):
        raise ValueError('mode must be normal or qa')
    root = Path(repo_root).resolve()
    target = NORMAL_TARGET if mode == 'normal' else QA_TARGET
    flutter = [PINNED_FLUTTER, 'build', 'apk', '--release',
               '--build-number', BUILD_NUMBER, '--target', target]
    if mode == 'normal':
        return [flutter]
    flutter.append('--android-project-arg=ocBd9Smoke=true')
    gradle = [str(root / 'android/gradlew'), '-p', str(root / 'android'),
              ':app:assembleReleaseAndroidTest', '--no-daemon',
              '-PocBd9Smoke=true', f'-Ptarget={root / QA_TARGET}',
              f'-PflutterVersionCode={BUILD_NUMBER}']
    return [flutter, gradle]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('normal', 'qa'))
    parser.add_argument('--repo-root', type=Path,
                        default=Path(__file__).resolve().parents[2])
    args = parser.parse_args(argv)
    print('BD9 dry plan only; no commands executed.')
    for command in build_commands(args.mode, repo_root=args.repo_root):
        print(shlex.join(command))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
