// Coverage ratchet for the file-access questions before a shared folder opens
// (Android's "All files access", Termux storage, the restart a new folder can
// need): each case is what the phone says, then the real dialogs are driven
// and read (see paseo_coverage_support.dart for the rules).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/platform/storage_access.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/shared_project_roots.dart';
import 'package:opencode_mobile/state/shared_storage_gate.dart';
import 'package:opencode_mobile/ui/screens/shared_storage_access_flow.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';

final _servers = {
  'inApp': ServerProfile(
    id: 'builtin',
    name: 'This phone',
    baseUrl: BuiltinLinux.serverUrl,
    username: BuiltinLinux.serverUsername,
  ),
  'termux': ServerProfile(
    id: 'termux',
    name: 'Termux',
    baseUrl: 'http://127.0.0.1:4096',
    username: 'opencode',
  ),
  'remote': ServerProfile(
    id: 'remote',
    name: 'Server',
    baseUrl: 'https://example.test',
    username: 'u',
  ),
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  final family = CoverageFamily('phone_storage', prefix: '');
  registerLedgerTests(family);

  tearDown(() {
    debugPlatformCapabilities = null;
    StorageAccessBridge.statusOverride = null;
    StorageAccessBridge.openOverride = null;
    SharedStorageGate.termuxStorageOverride = null;
    SharedStorageAccessFlow.confinedRunningOverride = null;
    SharedStorageAccessFlow.restartOverride = null;
    SharedProjectRoots.pushOverride = null;
  });

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('file access · $id', (tester) async {
      SharedPreferences.setMockInitialValues({});
      await SharedPreferences.getInstance();
      final g = Map<String, dynamic>.from(variant['payload'] as Map);
      debugPlatformCapabilities = const PlatformCapabilities(
        platform: TargetPlatform.android,
        isWeb: false,
      );
      var access = g['accessGranted'] == true
          ? StorageAccess.granted
          : StorageAccess.notGranted;
      StorageAccessBridge.statusOverride = () async => access;
      StorageAccessBridge.openOverride = () async {
        if (g['accessAfterAllow'] == true) access = StorageAccess.granted;
      };
      SharedStorageGate.termuxStorageOverride = () async =>
          g['termuxCanRead'] == true;
      SharedProjectRoots.pushOverride = (_) async {};
      SharedStorageAccessFlow.confinedRunningOverride = (_) async =>
          g['confinedRunning'] == true;
      SharedStorageAccessFlow.restartOverride = (_) async =>
          g['restartBack'] == true;
      // Termux's own terminal opens (the person answers there).
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
            const MethodChannel('oc/termux'),
            (call) async => true,
          );
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(const MethodChannel('oc/termux'), null),
      );
      tester.view
        ..devicePixelRatio = 1
        ..physicalSize = const Size(420, 1400);
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          theme: captureTheme(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () => SharedStorageAccessFlow.ensure(
                context,
                _servers[g['server']],
                '/sdcard/CodeAnything',
                workRunning: g['workRunning'] == true,
              ),
              child: const Text('go'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('go'));
      await tester.pumpAndSettle();
      final seen = <String>[screenText(tester).join('\n')];
      for (final key in [
        'storage-access-allow',
        'storage-termux-allow',
        'storage-restart-confirm',
      ]) {
        final button = find.byKey(ValueKey(key));
        if (button.evaluate().isEmpty) continue;
        await tester.tap(button);
        await tester.pumpAndSettle();
        seen.add(screenText(tester).join('\n'));
      }
      final screen = seen.join('\n');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      await tester.pumpWidget(const SizedBox.shrink());
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
