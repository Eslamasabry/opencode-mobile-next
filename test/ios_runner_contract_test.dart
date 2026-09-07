import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';

// Source/asset contracts only: these do not establish Xcode or device success.
void main() {
  test(
    'iOS runner has the generated lifecycle and explicit unsigned-build path',
    () {
      final app = File('ios/Runner/AppDelegate.swift').readAsStringSync();
      final scene = File('ios/Runner/SceneDelegate.swift').readAsStringSync();
      final project = File(
        'ios/Runner.xcodeproj/project.pbxproj',
      ).readAsStringSync();
      expect(app, contains('FlutterImplicitEngineDelegate'));
      expect(app, contains('GeneratedPluginRegistrant.register'));
      expect(scene, contains('FlutterSceneDelegate'));
      expect(project, contains('io.github.eslamasabry.opencodeMobile'));
      expect(project, contains('IPHONEOS_DEPLOYMENT_TARGET = 15.0;'));
      expect(project, isNot(contains('DEVELOPMENT_TEAM =')));
      expect(project, contains('Runner/DebugProfile.entitlements'));
      expect(project, contains('Runner/Release.entitlements'));
      final workflow = File(
        '.github/workflows/ios-quality.yml',
      ).readAsStringSync();
      expect(
        workflow,
        contains('flutter build ios --simulator --debug --no-codesign'),
      );
      expect(workflow, isNot(contains('secrets.')));
    },
  );

  test('iOS configuration does not claim unsupported native capabilities', () {
    final info = File('ios/Runner/Info.plist').readAsStringSync();
    expect(info, contains('<string>OpenCode Mobile</string>'));
    expect(info, contains('NSLocalNetworkUsageDescription'));
    for (final key in [
      'NSAllowsArbitraryLoads',
      'UIBackgroundModes',
      'NSCameraUsageDescription',
      'NSMicrophoneUsageDescription',
    ]) {
      expect(info, isNot(contains(key)));
    }
    for (final file in ['DebugProfile', 'Release']) {
      final entitlements = File(
        'ios/Runner/$file.entitlements',
      ).readAsStringSync();
      expect(entitlements, contains('<key>keychain-access-groups</key>'));
      expect(entitlements, contains('<array/>'));
      expect(entitlements, isNot(contains('aps-environment')));
    }
  });

  test(
    'iOS CI watches path dependencies and bundled policy/license assets',
    () {
      final workflow = File(
        '.github/workflows/ios-quality.yml',
      ).readAsStringSync();
      for (final input in [
        'packages/opencode_sdk/**',
        'LICENSES/**',
        'shorebird.yaml',
        'PRIVACY.md',
        'THIRD_PARTY_NOTICES.md',
      ]) {
        expect(
          '- "$input"'.allMatches(workflow),
          hasLength(2),
          reason: '$input must be covered in both PR and push path filters',
        );
      }
    },
  );

  test(
    'all declared iOS app icons exist at exact size without an alpha channel',
    () {
      const directory = 'ios/Runner/Assets.xcassets/AppIcon.appiconset';
      final catalog =
          jsonDecode(File('$directory/Contents.json').readAsStringSync())
              as Map;
      for (final entry in catalog['images'] as List) {
        final bytes = File('$directory/${entry['filename']}').readAsBytesSync();
        expect(bytes.take(8), [137, 80, 78, 71, 13, 10, 26, 10]);
        final view = ByteData.sublistView(bytes);
        final points = (entry['size'] as String)
            .split('x')
            .map(double.parse)
            .toList();
        final scale = double.parse(
          (entry['scale'] as String).replaceAll('x', ''),
        );
        expect(view.getUint32(16), (points[0] * scale).round());
        expect(view.getUint32(20), (points[1] * scale).round());
        expect(bytes[25], 2, reason: 'App icons must be opaque RGB PNGs');
      }
    },
  );
}
