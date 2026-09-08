import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';

import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  setUpAll(() => ProviderLogo.imageProviderOverride = (_) => null);
  tearDownAll(() => ProviderLogo.imageProviderOverride = null);
  for (final light in [false, true]) {
    for (final scale in [1.0, 2.0]) {
      testWidgets('simple model $light $scale', (tester) async {
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final prefs = await setupCapturePreferences();
        final controller = ConnectionController(ProfileStore(prefs: prefs))
          ..catalogDetailed = true
          ..unloadedProviderIDs = {'anthropic', 'google'}
          ..selectedAgent = 'build'
          ..selectedModel = ModelRef(providerID: 'openai', modelID: 'balanced')
          ..catalog = CatalogSnapshot(
            providers: const [
              CatalogProvider(id: 'openai', name: 'OpenAI', enabled: true),
              CatalogProvider(
                id: 'anthropic',
                name: 'Anthropic',
                enabled: true,
              ),
              CatalogProvider(id: 'google', name: 'Google', enabled: true),
            ],
            models: [
              for (final entry in const {
                'balanced': 'Balanced model',
                'fast': 'Fast model',
                'reasoning': 'Reasoning model',
                'compact': 'Compact model',
              }.entries)
                CatalogModel(
                  id: entry.key,
                  providerID: 'openai',
                  name: entry.value,
                  enabled: true,
                  status: 'active',
                  contextLimit: 200000,
                  outputLimit: 32000,
                  reasoning: true,
                  attachments: true,
                  tools: true,
                  variants: const [],
                ),
            ],
            agents: const [
              CatalogAgent(id: 'build', mode: 'primary', hidden: false),
              CatalogAgent(id: 'plan', mode: 'primary', hidden: false),
            ],
          );
        addTearDown(controller.dispose);
        final boundary = GlobalKey();
        await tester.pumpWidget(
          MaterialApp(
            theme: light ? AppTheme.light() : AppTheme.dark(),
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: TextScaler.linear(scale)),
              child: child!,
            ),
            home: RepaintBoundary(
              key: boundary,
              child: Scaffold(
                body: SafeArea(
                  child: ModelCatalogView(
                    controller: controller,
                    applyScope: ModelPickerApplyScope.session,
                    onClose: () {},
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/simple-model/${const String.fromEnvironment('CAPTURE_LABEL', defaultValue: 'after')}-${light ? 'light' : 'dark'}-${scale.toInt()}x.png',
          await capturePng(tester, boundary),
        );
        await tester.pumpWidget(const SizedBox());
      });
    }
  }
}
