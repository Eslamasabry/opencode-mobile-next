// P3.3 One model sheet: Settings › Model and the composer's model chip open
// the same sheet (Settings sets the default for new conversations, the chip
// this conversation). The thinking level and the agent sit in its pinned
// footer and open as menus, providers the server has not loaded are a
// Reload row, and nothing opens a dialog over the sheet.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';
import '../support/setup_capture_preferences.dart';
import '../support/composer_tools.dart';

CatalogSnapshot _catalog() => const CatalogSnapshot(
  providers: [
    CatalogProvider(id: 'anthropic', name: 'Anthropic', enabled: true),
    CatalogProvider(id: 'openai', name: 'OpenAI', enabled: true),
  ],
  models: [
    CatalogModel(
      id: 'claude-opus-5-5',
      providerID: 'anthropic',
      name: 'Claude Opus 5.5',
      enabled: true,
      status: 'active',
      contextLimit: 1000000,
      outputLimit: 128000,
      reasoning: true,
      attachments: true,
      tools: true,
      variants: [
        CatalogVariant(id: 'high', disabled: false),
        CatalogVariant(id: 'max', disabled: false),
      ],
    ),
    CatalogModel(
      id: 'gpt-6-sol',
      providerID: 'openai',
      name: 'GPT-6 Sol',
      enabled: true,
      status: 'active',
      contextLimit: 400000,
      outputLimit: 128000,
      reasoning: true,
      attachments: true,
      tools: true,
      variants: [],
    ),
  ],
  agents: [
    CatalogAgent(id: 'build', mode: 'primary', hidden: false),
    CatalogAgent(id: 'plan', mode: 'primary', hidden: false),
    CatalogAgent(id: 'explore', mode: 'subagent', hidden: false),
  ],
);

/// Opens a conversation with no history (the capture API has no pages).
class _Api extends CaptureApi {
  @override
  Future<List<AgentInfo>> agents() async => [
    for (final agent in _catalog().agents)
      AgentInfo(name: agent.id, mode: agent.mode),
  ];

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => const ServerPage(items: []);
}

// Opening the picker refreshes through ensureCatalog. Return the same models
// and agents as the seeded scene, including its thinking variants.
class _Repository extends CaptureRepository {
  @override
  Future<CatalogSnapshot> loadCatalog() async => _catalog();
}

final _opus = ModelRef(providerID: 'anthropic', modelID: 'claude-opus-5-5');

Future<CaptureController> _captured() async {
  final prefs = await setupCapturePreferences();
  final controller = await captureController(
    prefs: prefs,
    api: _Api()..busy = {},
    repository: _Repository(),
  );
  addTearDown(controller.dispose);
  return controller
    ..catalog = _catalog()
    ..selectedModel = _opus
    ..selectedAgent = 'build';
}

void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

/// Everything a person sees of the one sheet, whichever door opened it.
void _expectOneSheet({
  required String apply,
  String? subtitle,
  String? title = 'Choose a model',
}) {
  if (title != null) expect(find.text(title), findsOneWidget);
  final footer = find.byKey(const Key('model-picker-footer'));
  expect(footer, findsOneWidget);
  expect(
    find.descendant(of: footer, matching: find.text('Thinking: Default')),
    findsOneWidget,
  );
  expect(
    find.descendant(of: footer, matching: find.text('Agent: Build')),
    findsOneWidget,
  );
  // The footer is pinned with the apply action, not in the scrolling list.
  expect(
    find.byKey(const Key('model-picker-agent')).hitTestable(),
    findsOneWidget,
  );
  expect(find.text(apply).hitTestable(), findsOneWidget);
  expect(
    find.text('For this conversation'),
    subtitle == null ? findsNothing : findsOneWidget,
  );
  expect(find.byType(Dialog), findsNothing);
  expect(find.byType(AlertDialog), findsNothing);
}

class _ReloadCounting extends ConnectionController {
  _ReloadCounting(super.store);

  int reloads = 0;

  @override
  Future<void> reloadProviderRuntime() async => reloads++;

  @override
  Future<void> refreshCatalog() async {}
}

Future<_ReloadCounting> _plain({Set<String> unloaded = const {}}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return _ReloadCounting(ProfileStore(prefs: prefs))
    ..unloadedProviderIDs = unloaded
    ..catalog = _catalog()
    ..selectedAgent = 'build'
    ..selectedModel = _opus;
}

Future<void> _openPlain(
  WidgetTester tester,
  ConnectionController controller, {
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: TextButton(
                onPressed: () => showModelPicker(context),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('open'));
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  group('two doors, one sheet', () {
    testWidgets('Settings › Model opens the sheet for new conversations', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _captured();
      await tester.pumpWidget(
        captureApp(
          home: SettingsScreen(
            controller: controller,
            initialGroup: SettingsGroup.ai,
          ),
          boundaryKey: GlobalKey(),
          controller: controller,
        ),
      );
      await tester.pumpAndSettle();
      final row = find.byKey(const ValueKey('settings-model-and-mode'));
      await tester.ensureVisible(row);
      await tester.pumpAndSettle();
      await tester.tap(row);
      await tester.pumpAndSettle();
      // Hub → sheet: no catalog page in between, and the sheet names whose
      // chats it is for.
      _expectOneSheet(apply: 'Use Claude Opus 5.5 · Build', title: null);
      expect(find.textContaining('New conversations on '), findsOneWidget);
    });

    testWidgets('the composer plus opens it for this conversation', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _captured();
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
      await openComposerTools(tester);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('composer-setting-model')));
      await tester.pumpAndSettle();
      _expectOneSheet(
        apply: 'Use for this conversation',
        subtitle: 'For this conversation',
      );
    });
  });

  group('footer', () {
    testWidgets('thinking opens as a menu and is staged until applied', (
      tester,
    ) async {
      final controller = await _plain();
      await _openPlain(tester, controller);
      await tester.tap(find.byKey(const Key('model-picker-thinking')));
      await tester.pumpAndSettle();
      expect(find.byType(Dialog), findsNothing);
      // The level in use is checked; picking one closes the menu.
      expect(find.text('High'), findsOneWidget);
      await tester.tap(find.text('Max'));
      await tester.pumpAndSettle();
      expect(find.text('Thinking: Max'), findsOneWidget);
      expect(controller.selectedVariant, '');
      await tester.tap(find.byKey(const Key('model-picker-apply')));
      await tester.pumpAndSettle();
      expect(controller.selectedVariant, 'max');
    });

    testWidgets('each agent says what it does; subagents are not offered', (
      tester,
    ) async {
      final controller = await _plain();
      await _openPlain(tester, controller);
      await tester.tap(find.byKey(const Key('model-picker-agent')));
      await tester.pumpAndSettle();
      expect(find.text('Edits files and runs commands'), findsOneWidget);
      expect(
        find.text('Reads and plans; does not change files'),
        findsOneWidget,
      );
      expect(find.text('Explore'), findsNothing);
      expect(find.text('explore'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('model-picker-agent-plan')));
      await tester.pumpAndSettle();
      expect(find.text('Agent: Plan'), findsOneWidget);
      // The primary names what it applies.
      expect(find.text('Use Claude Opus 5.5 · Plan'), findsOneWidget);
      expect(controller.selectedAgent, 'build');
    });

    testWidgets('a model with one level drops the thinking chip only', (
      tester,
    ) async {
      final controller = await _plain()
        ..selectedModel = ModelRef(providerID: 'openai', modelID: 'gpt-6-sol');
      await _openPlain(tester, controller);
      expect(find.byKey(const Key('model-picker-thinking')), findsNothing);
      expect(find.byKey(const Key('model-picker-agent')), findsOneWidget);
    });

    testWidgets('with no model chosen the primary says why it waits', (
      tester,
    ) async {
      final controller = await _plain()
        ..selectedModel = null;
      await _openPlain(tester, controller);
      expect(find.text('Choose a model first.'), findsOneWidget);
      await tester.tap(find.text('GPT-6 Sol'));
      await tester.pumpAndSettle();
      expect(find.text('Choose a model first.'), findsNothing);
      expect(find.text('Use GPT-6 Sol · Build'), findsOneWidget);
    });

    testWidgets('a wide window pins the same footer in the side sheet', (
      tester,
    ) async {
      final controller = await _plain();
      await _openPlain(tester, controller, size: const Size(1280, 800));
      expect(
        find.byKey(const Key('model-picker-agent')).hitTestable(),
        findsOneWidget,
      );
      expect(
        find.text('Use Claude Opus 5.5 · Build').hitTestable(),
        findsOneWidget,
      );
    });
  });

  testWidgets('providers not loaded are a row that reloads them', (
    tester,
  ) async {
    final controller = await _plain(unloaded: {'openai'});
    await _openPlain(tester, controller);
    final row = find.byKey(const ValueKey('picker-reload-providers'));
    expect(row, findsOneWidget);
    expect(
      find.textContaining(
        'Signed in to OpenAI, but the server has not loaded it yet',
        findRichText: true,
      ),
      findsOneWidget,
    );
    // Words, not the server's error.
    expect(
      find.textContaining('Model not found', findRichText: true),
      findsNothing,
    );
    await tester.tap(row);
    await tester.pumpAndSettle();
    expect(controller.reloads, 1);
    expect(find.byType(Dialog), findsNothing);
    expect(find.text('Choose a model'), findsOneWidget);
  });
}
