// Synthetic UI evidence: fixture server, no credentials or network actions.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';

import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

class _Api extends CaptureApi {
  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.25');
}

class _Repository extends CaptureRepository {
  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: '', options: []);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  const stage = String.fromEnvironment('CAPTURE_STAGE', defaultValue: 'after');
  for (final light in [true, false]) {
    for (final scale in [1.0, 2.0]) {
      for (final page in ['more', 'settings', 'coding']) {
        testWidgets('$page ${light ? 'light' : 'dark'} ${scale}x', (
          tester,
        ) async {
          tester.view.physicalSize = const Size(390, 844);
          tester.view.devicePixelRatio = 1;
          tester.platformDispatcher.textScaleFactorTestValue = scale;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
          const secure = MethodChannel(
            'plugins.it_nomads.com/flutter_secure_storage',
          );
          tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
            secure,
            (call) async =>
                call.method == 'readAll' ? <String, String>{} : null,
          );
          addTearDown(
            () => tester.binding.defaultBinaryMessenger
                .setMockMethodCallHandler(secure, null),
          );
          final prefs = await setupCapturePreferences();
          final controller = await captureController(
            prefs: prefs,
            api: _Api(),
            repository: _Repository(),
          );
          controller.catalog = sampleCatalog();
          final model = controller.catalog!.models.first;
          controller.selectedModel = ModelRef(
            providerID: model.providerID,
            modelID: model.id,
          );
          controller.appearance.value = light
              ? AppAppearance.light
              : AppAppearance.dark;
          controller.selectedAgent = 'build';
          final boundary = GlobalKey();
          await tester.pumpWidget(
            captureApp(
              home: page == 'more'
                  ? const HomeScreen(initialTab: 3)
                  : page == 'settings'
                  ? SettingsScreen(controller: controller)
                  : CodingSettingsScreen(controller: controller),
              boundaryKey: boundary,
              controller: controller,
              light: light,
            ),
          );
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 500));
          expect(tester.takeException(), isNull);
          await writePng(
            'docs/qa/settings-discovery/$stage-$page-${light ? 'light' : 'dark'}-${scale.toInt()}x.png',
            await capturePng(tester, boundary, pixelRatio: 1),
          );
          if (stage == 'after' && page == 'coding' && scale == 1) {
            await tester.tap(find.text('Selected agent'));
            await tester.pumpAndSettle();
            expect(find.text('Choose an agent'), findsOneWidget);
            expect(controller.selectedAgent, 'build');
          }
          await tester.pumpWidget(const SizedBox.shrink());
          controller.dispose();
          await tester.pump();
        });
      }
    }
  }
}
