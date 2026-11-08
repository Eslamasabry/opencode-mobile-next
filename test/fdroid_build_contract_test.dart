import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitSkeletonRows;
import 'package:opencode_mobile/ui/screens/about_screen.dart' show AboutScreen;
import 'package:opencode_mobile/update/app_update_service.dart';
import 'package:opencode_mobile/update/shorebird_updater_stub.dart'
    as updater_stub;
import 'package:package_info_plus/package_info_plus.dart';

import '../tool/fdroid/prebuild.dart';

String _read(String path) => File(path).readAsStringSync();

Iterable<File> _dartFiles(String dir) => Directory(dir)
    .listSync(recursive: true)
    .whereType<File>()
    .where((f) => f.path.endsWith('.dart') && !f.path.contains('/l10n/'));

/// The F-Droid build (docs/release/fdroid-2026-10-31.md): what it must not
/// contain, and the switches that keep it out. Not about the shipping build,
/// which has its own tests.
void main() {
  group('capabilities', () {
    test('the F-Droid build has no code push', () {
      const caps = PlatformCapabilities(
        platform: TargetPlatform.android,
        fdroid: true,
      );
      expect(caps.isAndroid, isTrue);
      expect(caps.supportsCodePush, isFalse);
      // The QR scanner is Google-free, so it stays.
      expect(caps.supportsQrPairing, isTrue);
      // Everything else on the phone is still there.
      expect(caps.supportsBackgroundService, isTrue);
      expect(caps.supportsTermux, isTrue);
      expect(caps.supportsVoice, isTrue);
    });

    test('the shipping build keeps code push', () {
      const caps = PlatformCapabilities(platform: TargetPlatform.android);
      expect(caps.fdroid, isFalse);
      expect(caps.supportsCodePush, isTrue);
      expect(caps.supportsQrPairing, isTrue);
    });

    test('an F-Droid answer is not equal to a shipping one', () {
      expect(
        const PlatformCapabilities(
          platform: TargetPlatform.android,
          fdroid: true,
        ),
        isNot(const PlatformCapabilities(platform: TargetPlatform.android)),
      );
    });
  });

  group('stand-ins', () {
    test('the updater stand-in can never find or fetch an update', () async {
      final service = updater_stub.ShorebirdAppUpdateService();
      expect(service.isAvailable, isFalse);
      expect(await service.checkForUpdate(), AppUpdateState.unavailable);
      await service.downloadUpdate();
    });
  });

  group('About', () {
    setUp(() {
      rootBundle
        ..evict('PRIVACY.md')
        ..evict('THIRD_PARTY_NOTICES.md');
      PackageInfo.setMockInitialValues(
        appName: 'OpenCode Mobile',
        packageName: 'io.github.eslamasabry.opencode_mobile',
        version: '2.1.0',
        buildNumber: '57',
        buildSignature: '',
      );
    });
    tearDown(() => debugPlatformCapabilities = null);

    Future<void> pumpAbout(WidgetTester tester) async {
      tester.view
        ..physicalSize = const Size(412, 915)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: AboutScreen(),
        ),
      );
      await tester.pumpAndSettle();
      for (var i = 0; i < 100; i++) {
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 100)),
        );
        await tester.pumpAndSettle();
        if (find.byType(KitSkeletonRows).evaluate().isEmpty) break;
      }
    }

    testWidgets('offers "Check for updates" in the shipping build', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      await pumpAbout(tester);
      expect(find.byKey(const ValueKey('about-check-updates')), findsOneWidget);
    });

    testWidgets('has no update row in the F-Droid build', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities(
        platform: TargetPlatform.android,
        fdroid: true,
      );
      await pumpAbout(tester);
      expect(find.byKey(const ValueKey('about-version')), findsOneWidget);
      expect(find.byKey(const ValueKey('about-check-updates')), findsNothing);
    });
  });

  group('where the proprietary packages may be named', () {
    test('only the "real" updater file imports them', () {
      final importers = <String, List<String>>{
        'shorebird_code_push': [],
        'mobile_scanner': [],
        'google_mlkit_barcode_scanning': [],
      };
      for (final file in _dartFiles('lib')) {
        final text = file.readAsStringSync();
        for (final name in importers.keys) {
          if (text.contains("import 'package:$name/")) {
            importers[name]!.add(file.path);
          }
        }
      }
      expect(importers['shorebird_code_push'], [
        'lib/update/shorebird_updater_real.dart',
      ]);
      // The ML Kit scanner is gone for every build (kit_qr_decoder_test.dart).
      expect(importers['mobile_scanner'], isEmpty);
      expect(importers['google_mlkit_barcode_scanning'], isEmpty);
    });

    test('the switch is a conditional on the OC_FDROID define', () {
      expect(
        _read('lib/update/shorebird_update_notice.dart'),
        contains(
          "export 'shorebird_updater_real.dart'\n"
          "    if (OC_FDROID) 'shorebird_updater_stub.dart';",
        ),
      );
      expect(
        _read('lib/build_flavor.dart'),
        contains("fromEnvironment('OC_FDROID')"),
      );
    });
  });

  group('prebuild', () {
    final pubspec = _read('pubspec.yaml');

    test('takes the code-push package and asset out', () {
      final out = stripPubspecForFdroid(pubspec);
      expect(pubspec, contains('shorebird_code_push:'));
      expect(out, isNot(contains('shorebird_code_push')));
      expect(out, isNot(contains('shorebird.yaml')));
    });

    test('leaves every other dependency and asset alone', () {
      final out = stripPubspecForFdroid(pubspec);
      for (final keep in [
        '  dio:',
        '  flutter_riverpod:',
        '  sherpa_onnx:',
        '  path_provider:',
        '    - PRIVACY.md',
        '    - assets/marks/',
      ]) {
        expect(out, contains(keep));
      }
    });

    test('refuses a pubspec that no longer names a package', () {
      final renamed = pubspec.replaceFirst(
        '  shorebird_code_push:',
        '  shorebird_renamed:',
      );
      expect(() => stripPubspecForFdroid(renamed), throwsStateError);
    });
  });

  group('Android project', () {
    test('the F-Droid build takes the self-install permission out', () {
      final gradle = _read('android/app/build.gradle.kts');
      expect(gradle, contains('"ocFdroid"'));
      expect(
        gradle,
        contains('manifest.srcFile("src/fdroid/AndroidManifest.xml")'),
      );
      final overlay = _read('android/app/src/fdroid/AndroidManifest.xml');
      expect(overlay, contains('REQUEST_INSTALL_PACKAGES'));
      expect(overlay, contains('tools:node="remove"'));
    });

    test('the scanner pulls in no Google SDK at all', () {
      final pubspec = _read('pubspec.yaml');
      expect(pubspec, contains('  camera: '));
      expect(pubspec, contains('  zxing2: '));
      expect(pubspec, isNot(contains('\n  mobile_scanner:')));
    });

    test('the F-Droid build refuses Google SDK dependencies', () {
      final root = _read('android/build.gradle.kts');
      for (final group in [
        'com.google.android.gms',
        'com.google.mlkit',
        'com.google.firebase',
      ]) {
        expect(root, contains('"$group"'));
      }
      expect(root, contains('throw GradleException('));
    });
  });
}
