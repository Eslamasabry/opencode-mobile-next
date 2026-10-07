import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/model_display_name.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart'
    show defaultModelLabel;

import '../tool/capture/fixtures.dart';
import 'support/setup_capture_preferences.dart';

class _PagedApi extends CaptureApi {
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => const ServerPage(items: []);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('modelNameFromId', () {
    for (final (id, name) in const [
      ('claude-sonnet-5', 'Sonnet 5'),
      ('anthropic/claude-sonnet-5', 'Sonnet 5'),
      ('claude-opus-4-1-20250805', 'Opus 4.1'),
      ('claude-haiku-3-5', 'Haiku 3.5'),
      ('claude-sonnet-4-5@20250929', 'Sonnet 4.5'),
      ('gpt-6-sol', 'GPT-6 Sol'),
      ('gpt-4-1-mini', 'GPT-4.1 Mini'),
      ('gpt-4o', 'GPT-4o'),
      ('gpt-5-2025-08-07', 'GPT-5'),
      ('glm-5.3', 'GLM-5.3'),
      ('o3', 'o3'),
      ('gemini-2.5-pro', 'Gemini 2.5 Pro'),
      ('kimi-k2', 'Kimi K2'),
      ('sonnet', 'Sonnet'),
      ('claude', 'Claude'),
    ]) {
      test('$id reads "$name"', () => expect(modelNameFromId(id), name));
    }
  });

  group('a new conversation\'s model chip label', () {
    late AppLocalizations l10n;
    setUp(() async {
      l10n = await AppLocalizations.delegate.load(const Locale('en'));
    });

    test('names the model from its id while the catalog loads', () async {
      final prefs = await setupCapturePreferences();
      final controller = await captureController(prefs: prefs);
      addTearDown(controller.dispose);
      controller
        ..catalog = null
        ..selectedModel = ModelRef(
          providerID: 'anthropic',
          modelID: 'claude-sonnet-5',
        );
      final label = defaultModelLabel(controller, l10n);
      expect(label, 'Sonnet 5');
      expect(label, isNot(contains('claude-sonnet-5')));

      controller
        ..catalog = sampleCatalog()
        ..catalogLoading = true;
      expect(defaultModelLabel(controller, l10n), 'Sonnet 5');
    });

    test('a name seen before wins over the derived one', () async {
      final prefs = await setupCapturePreferences();
      final controller = await captureController(prefs: prefs);
      addTearDown(controller.dispose);
      controller
        ..catalog = null
        ..selectedModel = ModelRef(
          providerID: 'anthropic',
          modelID: 'claude-sonnet-5',
        )
        ..rememberModelName('anthropic', 'claude-sonnet-5', 'Claude Sonnet 5');
      expect(defaultModelLabel(controller, l10n), 'Claude Sonnet 5');
    });

    test('the catalog name replaces it once the models are read', () async {
      final prefs = await setupCapturePreferences();
      final controller = await captureController(prefs: prefs);
      addTearDown(controller.dispose);
      controller
        ..catalog = sampleCatalog()
        ..catalogLoading = false
        ..selectedModel = ModelRef(
          providerID: 'anthropic',
          modelID: 'claude-sonnet-4',
        );
      expect(defaultModelLabel(controller, l10n), 'Claude Sonnet 4');
    });
  });

  testWidgets('the chat composer chip never shows the raw id while loading', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final prefs = await setupCapturePreferences();
    final api = _PagedApi()..busy = {};
    final controller = await captureController(prefs: prefs, api: api);
    addTearDown(controller.dispose);
    controller
      ..catalog = null
      ..selectedModel = ModelRef(
        providerID: 'anthropic',
        modelID: 'claude-sonnet-5',
      );
    await tester.pumpWidget(
      captureApp(
        home: const ChatScreen(sessionID: checkoutSessionID),
        boundaryKey: GlobalKey(),
        controller: controller,
      ),
    );
    for (var frame = 0; frame < 8; frame++) {
      await tester.pump(const Duration(milliseconds: 120));
    }
    final chip = find.byKey(const Key('composer-model-context'));
    expect(chip, findsOneWidget);
    expect(
      find.descendant(of: chip, matching: find.textContaining('Sonnet 5')),
      findsOneWidget,
    );
    expect(find.textContaining('claude-sonnet-5'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
