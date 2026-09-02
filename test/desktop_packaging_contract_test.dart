import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/update/desktop_release_check.dart';

/// Locks the couplings between the desktop packaging pipeline and the code
/// that consumes its output. Each of these can be broken by an edit in a
/// file that looks unrelated, and none of them fails loudly at runtime — a
/// mismatched application id just quietly gives the window a generic icon,
/// and a mismatched version convention just quietly stops offering updates.
String _read(String path) {
  final file = File(path);
  expect(file.existsSync(), isTrue, reason: 'missing $path');
  return file.readAsStringSync();
}

/// `version: 1.0.29+30` -> `1.0.29+30`.
String _pubspecVersion() {
  final match = RegExp(
    r'^version:\s*(\S+)',
    multiLine: true,
  ).firstMatch(_read('pubspec.yaml'));
  expect(match, isNotNull, reason: 'pubspec.yaml has no version');
  return match!.group(1)!;
}

void main() {
  group('release tag and version conventions', () {
    test('the pubspec version carries a build number the checker can read', () {
      final version = _pubspecVersion();
      expect(
        version,
        matches(RegExp(r'^\d+\.\d+\.\d+\+\d+$')),
        reason: 'desktop releases are identified by the +buildNumber',
      );
    });

    test('a release tag for this version parses back to this build', () {
      final version = _pubspecVersion();
      final build = int.parse(version.split('+').last);

      // The two tag shapes this project actually publishes; see `git tag`.
      expect(buildNumberFromTag('v$version'), build);
      expect(buildNumberFromTag('v$version-preview.9'), build);

      // The running build is never newer than itself, and the next one is.
      expect(
        isNewerRelease(currentBuildNumber: build, tag: 'v$version-preview.9'),
        isFalse,
      );
      expect(
        isNewerRelease(currentBuildNumber: build - 1, tag: 'v$version'),
        isTrue,
      );
    });

    test('the packaging script labels artifacts with the pubspec version', () {
      final script = _read('scripts/package-linux.sh');
      // The version is read from pubspec.yaml, not hardcoded...
      expect(script, contains(r'''sed -n 's/^version:'''));
      // ...and it reaches both artifact names.
      expect(script, contains(r'readonly TAR_NAME="opencode-linux-x64-$version"'));
      expect(script, contains(r'${DEB_PACKAGE}_${version}_${arch}.deb'));
    });

    test('the packaging script refuses a bundle from another version', () {
      final script = _read('scripts/package-linux.sh');
      // package_info_plus reads build_number out of this file on Linux, so
      // it is what the in-app update check compares against a release tag.
      // Packaging a stale bundle would make the app misreport its version.
      expect(script, contains('data/flutter_assets/version.json'));
      expect(script, contains('rebuild the bundle'));
    });

    test('the release API target matches the repository CI publishes to', () {
      final workflow = _read('.github/workflows/desktop-linux.yml');
      expect(desktopReleasesApiUrl, contains('Eslamasabry/opencode-mobile-next'));
      expect(desktopReleasesPageUrl, contains('Eslamasabry/opencode-mobile-next'));
      // The release job attaches assets to the tag's existing release rather
      // than creating one, so the tag the checker parses is always the tag
      // scripts/release.sh made.
      expect(workflow, contains('gh release upload'));
      expect(workflow, contains(r'startsWith(github.ref, ' "'" r'refs/tags/v' "'" r')'));
    });
  });

  group('Linux desktop integration', () {
    test('the desktop entry, icons and WM_CLASS all use the app id', () {
      final appId = RegExp(
        r'set\(APPLICATION_ID "([^"]+)"\)',
      ).firstMatch(_read('linux/CMakeLists.txt'))?.group(1);
      expect(appId, isNotNull, reason: 'linux/CMakeLists.txt has no APPLICATION_ID');

      // The desktop file is named after the app id, which is how a Wayland
      // compositor pairs the window (whose app_id is the program name) with
      // this entry.
      final desktop = _read('linux/packaging/$appId.desktop');
      expect(desktop, contains('\nIcon=$appId\n'));
      expect(desktop, contains('\nStartupWMClass=$appId\n'));

      // On X11 the match is against WM_CLASS instead, so both halves of it
      // are pinned to the same string natively.
      final runner = _read('linux/runner/my_application.cc');
      expect(runner, contains('g_set_prgname(APPLICATION_ID)'));
      expect(runner, contains('gdk_set_program_class(APPLICATION_ID)'));

      // AppStream ties itself back to the desktop entry by the same id.
      final metainfo = _read('linux/packaging/$appId.metainfo.xml');
      expect(metainfo, contains('<id>$appId</id>'));
      expect(metainfo, contains('$appId.desktop'));
    });

    test('every icon size the packaging script requires is committed', () {
      final script = _read('scripts/package-linux.sh');
      final sizes = RegExp(r'ICON_SIZES=\(([^)]*)\)')
          .firstMatch(script)!
          .group(1)!
          .trim()
          .split(RegExp(r'\s+'));
      expect(sizes, isNotEmpty);
      for (final size in sizes) {
        final icon = File(
          'linux/packaging/icons/hicolor/${size}x$size/apps/'
          'io.github.eslamasabry.opencode_mobile.png',
        );
        expect(icon.existsSync(), isTrue, reason: 'missing ${size}px icon');
      }
      expect(
        File(
          'linux/packaging/icons/hicolor/scalable/apps/'
          'io.github.eslamasabry.opencode_mobile.svg',
        ).existsSync(),
        isTrue,
      );
    });
  });

  test('CI pins the same Flutter version the release script does', () {
    final pinned = RegExp(
      r'SHOREBIRD_FLUTTER_VERSION="([^"]+)"',
    ).firstMatch(_read('scripts/release.sh'))?.group(1);
    expect(pinned, isNotNull);
    // Shorebird's fork cannot be installed on a runner, so CI uses upstream
    // stable at the identical version rather than the identical toolchain.
    expect(
      _read('.github/workflows/desktop-linux.yml'),
      contains('FLUTTER_VERSION: "$pinned"'),
    );
    expect(
      _read('.github/workflows/android-quality.yml'),
      contains('flutter-version: $pinned'),
    );
  });
}
