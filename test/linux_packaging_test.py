"""Executable package checks with inert ELF fixtures, never a desktop launch.

Run: python3 -m unittest discover -s test -p linux_packaging_test.py -v
Requires GNU packaging tools and readelf; .deb checks additionally need dpkg-deb.
"""
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tarfile
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[1]
VERSION = '1.2.3+4'


def elf(machine):
    # A complete ELF64 header with no code, sections, or program headers.
    # readelf can inspect it; the fixture is never executed.
    ident = b'\x7fELF' + bytes([2, 1, 1, 0]) + bytes(8)
    return ident + struct.pack('<HHIQQQIHHHHHH', 3, machine, 1, 0, 0, 0,
                               0, 64, 0, 0, 0, 0, 0)


@unittest.skipUnless(os.name == 'posix' and shutil.which('readelf'),
                     'requires a Linux packaging host with readelf')
class LinuxPackagingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='oc-linux-package-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'scripts').mkdir()
        shutil.copy2(REPO / 'scripts/package-linux.sh', self.root / 'scripts')
        shutil.copytree(REPO / 'linux/packaging', self.root / 'linux/packaging')
        shutil.copy2(REPO / 'LICENSE', self.root)
        (self.root / 'pubspec.yaml').write_text(f'version: {VERSION}\n')
        self.bundle = self.root / 'bundle'
        (self.bundle / 'lib').mkdir(parents=True)
        (self.bundle / 'data/flutter_assets').mkdir(parents=True)
        (self.bundle / 'data/flutter_assets/version.json').write_text(
            json.dumps({'version': '1.2.3', 'build_number': '4'},
                       separators=(',', ':')))

    def runner(self, machine):
        path = self.bundle / 'opencode_mobile'
        path.write_bytes(elf(machine))
        path.chmod(0o755)
        (self.bundle / 'lib/libflutter_linux_gtk.so').write_bytes(elf(machine))

    def package(self, *extra, output='out', deb=False):
        command = ['bash', str(self.root / 'scripts/package-linux.sh'),
                   '--bundle', str(self.bundle), '--out', str(self.root / output)]
        if not deb:
            command.append('--skip-deb')
        return subprocess.run(command + list(extra), cwd=self.root,
                              env={**os.environ, 'SOURCE_DATE_EPOCH': '1700000000'},
                              text=True, capture_output=True, timeout=60)

    def assert_ok(self, result):
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_each_runner_architecture_labels_tarball_and_preserves_payload(self):
        for machine, target in [(62, 'x64'), (183, 'arm64')]:
            with self.subTest(target=target):
                self.runner(machine)
                self.assert_ok(self.package(output=target))
                stem = f'opencode-mobile-linux-{target}-{VERSION}'
                archive = self.root / target / f'{stem}.tar.gz'
                with tarfile.open(archive) as tar:
                    payload = tar.extractfile(f'{stem}/lib/opencode-mobile/opencode_mobile')
                    self.assertEqual(payload.read(), elf(machine))
                    self.assertIn(f'{stem}/install.sh', tar.getnames())
                checksums = (self.root / target / 'SHA256SUMS').read_text()
                self.assertIn(archive.name, checksums)

    @unittest.skipUnless(shutil.which('dpkg-deb'), 'requires dpkg-deb')
    def test_deb_architecture_comes_from_bundle_even_on_another_host(self):
        for machine, arch in [(62, 'amd64'), (183, 'arm64')]:
            with self.subTest(arch=arch):
                self.runner(machine)
                self.assert_ok(self.package(output=arch, deb=True))
                package = self.root / arch / f'opencode-mobile_{VERSION}_{arch}.deb'
                info = subprocess.run(['dpkg-deb', '-f', str(package), 'Architecture'],
                                      text=True, capture_output=True, check=True)
                self.assertEqual(info.stdout.strip(), arch)

    def test_repackaging_identical_bundle_is_reproducible(self):
        self.runner(183)
        self.assert_ok(self.package(output='first'))
        self.assert_ok(self.package(output='second'))
        filename = f'opencode-mobile-linux-arm64-{VERSION}.tar.gz'
        self.assertEqual((self.root / 'first' / filename).read_bytes(),
                         (self.root / 'second' / filename).read_bytes())

    def test_mixed_native_libraries_are_rejected_before_artifacts(self):
        self.runner(183)
        (self.bundle / 'lib/libplugin.so.1').write_bytes(elf(62))
        result = self.package()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('mixed bundle architectures', result.stderr)
        self.assertFalse((self.root / 'out').exists())

    def test_invalid_or_unsupported_runner_is_rejected(self):
        for payload in [b'#!/bin/sh\nexit 0\n', elf(40)]:
            self.runner(183)
            (self.bundle / 'opencode_mobile').write_bytes(payload)
            result = self.package()
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse((self.root / 'out').exists())

    def test_wrong_version_is_rejected(self):
        self.runner(183)
        result = self.package('--version', '1.2.3+5')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('rebuild the bundle', result.stderr)
        self.assertFalse((self.root / 'out').exists())


if __name__ == '__main__':
    unittest.main()
