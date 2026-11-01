import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/feedback/bug_report.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));
  tearDown(() => debugPlatformCapabilities = null);

  test('labels every platform the seam can report', () {
    const cases = <TargetPlatform, String>{
      TargetPlatform.android: 'Android',
      TargetPlatform.linux: 'Linux desktop (alpha)',
      TargetPlatform.windows:
          'Windows desktop (experimental — contributor-tested)',
      TargetPlatform.macOS: 'macOS (untested)',
    };
    cases.forEach((platform, expected) {
      debugPlatformCapabilities = PlatformCapabilities(platform: platform);
      expect(bugReportPlatformLabel(), expected, reason: 'platform $platform');
    });
  });

  test('a browser reports as a preview, whatever system is under it', () {
    for (final platform in [TargetPlatform.linux, TargetPlatform.android]) {
      debugPlatformCapabilities = PlatformCapabilities.browser(
        platform: platform,
      );
      expect(bugReportPlatformLabel(), 'Web browser (preview)');
    }
  });
}
