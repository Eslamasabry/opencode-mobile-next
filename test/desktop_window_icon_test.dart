import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/desktop/window_icon.dart';

void main() {
  // window_manager.setIcon fails silently by design — gtk_window_set_icon_from
  // _file just returns false and the window keeps the GTK default. That makes
  // a mistyped path or an undeclared asset invisible at runtime, so the
  // contract between main.dart, pubspec.yaml and the file on disk is pinned
  // here instead.

  test('the declared window icon exists on disk', () {
    expect(File(desktopWindowIconAsset).existsSync(), isTrue);
  });

  test('the window icon is bundled, and the master is not', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    expect(
      pubspec,
      contains('    - $desktopWindowIconAsset'),
      reason: 'window_manager resolves the icon out of flutter_assets, so an '
          'undeclared asset means no icon',
    );
    // 795 KB of 1024 px master in every Android build would be a real size
    // regression for an asset only the desktop launcher pipelines consume.
    expect(
      pubspec,
      isNot(contains('opencode-mobile-app-icon-v2.png')),
      reason: 'the master is a source artifact, not a bundled asset',
    );
  });

  test('the icon master is kept beside the bundled variant', () {
    final master = File('assets/branding/opencode-mobile-app-icon-v2.png');
    expect(
      master.existsSync(),
      isTrue,
      reason: 'the platform launcher-icon pipelines regenerate from the master',
    );
    expect(master.lengthSync(), greaterThan(File(desktopWindowIconAsset)
        .lengthSync()));
  });
}
