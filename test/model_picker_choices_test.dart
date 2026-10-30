// The model sheet's first view (vision slice 2026-10-20,
// docs/qa/vision-model-picker-2026-10-20): a few plain choices that lead
// with the reason, a tap chooses and closes, and "All models (N)" opens
// today's full list in the same sheet.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Controller extends ConnectionController {
  _Controller(super.store);

  int modelWrites = 0;

  @override
  Future<void> ensureCatalog() async {}

  @override
  Future<void> refreshCatalog() async {}

  @override
  Future<void> selectModelForSession(
    String sessionID,
    ModelRef ref, {
    String? variant,
    bool recordRecent = true,
  }) async {
    modelWrites++;
    await super.selectModelForSession(
      sessionID,
      ref,
      variant: variant,
      recordRecent: recordRecent,
    );
  }
}

CatalogModel _model(
  String provider,
  String id,
  String name, {
  ModelCost? cost,
}) => CatalogModel(
  id: id,
  providerID: provider,
  name: name,
  enabled: true,
  status: 'active',
  contextLimit: 200000,
  outputLimit: 32000,
  reasoning: true,
  attachments: false,
  tools: true,
  variants: const [
    CatalogVariant(id: 'high', options: {'reasoningEffort': 'high'}),
  ],
  cost: cost,
);

ModelCost _price(double input, double output) =>
    ModelCost(inputPerMillion: input, outputPerMillion: output);

const _session = 'ses_a';

Future<_Controller> _controller({
  List<CatalogModel>? models,
  ProvidersResponse? providers,
  List<ModelRef> history = const [],
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final catalogModels =
      models ??
      [
        _model('anthropic', 'sonnet', 'Claude Sonnet 5.5', cost: _price(3, 15)),
        _model('anthropic', 'opus', 'Claude Opus 5.5', cost: _price(5, 25)),
        _model('openai', 'sol', 'GPT-6 Sol', cost: _price(2.5, 15)),
        _model('opencode', 'pickle', 'Big Pickle', cost: _price(0, 0)),
      ];
  final controller = _Controller(ProfileStore(prefs: prefs))
    ..providers =
        providers ??
        ProvidersResponse(
          providers: const [],
          defaultProviderID: 'anthropic',
          defaultModelID: 'sonnet',
        )
    ..agents = [AgentInfo(name: 'build'), AgentInfo(name: 'plan')]
    ..catalogDetailed = true
    ..catalog = CatalogSnapshot(
      providers: const [
        CatalogProvider(id: 'anthropic', name: 'Anthropic', enabled: true),
        CatalogProvider(id: 'openai', name: 'OpenAI', enabled: true),
        CatalogProvider(id: 'opencode', name: 'OpenCode Zen', enabled: true),
      ],
      models: catalogModels,
      agents: const [
        CatalogAgent(id: 'build', mode: 'primary', hidden: false),
        CatalogAgent(id: 'plan', mode: 'primary', hidden: false),
      ],
    )
    ..selectedAgent = 'build'
    ..selectedModel = ModelRef(providerID: 'anthropic', modelID: 'sonnet');
  for (final ref in history) {
    await controller.selectModelForSession(_session, ref);
  }
  controller.modelWrites = 0;
  return controller;
}

Widget _app(
  ConnectionController controller, {
  bool focusAgent = false,
  double textScale = 1,
}) => ProviderScope(
  overrides: [connProvider.overrideWithValue(controller)],
  child: MaterialApp(
    theme: AppTheme.dark(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: child!,
    ),
    home: Builder(
      builder: (context) => Scaffold(
        body: Center(
          child: FilledButton(
            onPressed: () => showModelPicker(
              context,
              applyScope: ModelPickerApplyScope.session,
              sessionID: _session,
              focusAgent: focusAgent,
            ),
            child: const Text('Choose model'),
          ),
        ),
      ),
    ),
  ),
);

Future<void> _open(WidgetTester tester, ConnectionController controller) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(_app(controller));
  await tester.tap(find.text('Choose model'));
  await tester.pumpAndSettle();
}

Finder _choice(String reason) => find.byKey(ValueKey('model-choice-$reason'));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() => ProviderLogo.logoLoaderOverride = (_) => null);
  tearDownAll(() => ProviderLogo.logoLoaderOverride = null);
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('opens on plain choices that lead with the reason', (
    tester,
  ) async {
    final controller = await _controller(
      history: [
        ModelRef(providerID: 'openai', modelID: 'sol'),
        ModelRef(providerID: 'anthropic', modelID: 'sonnet'),
      ],
    );
    addTearDown(controller.dispose);
    await _open(tester, controller);

    expect(find.text('Model'), findsOneWidget);
    expect(find.text('For this conversation'), findsOneWidget);
    expect(
      find.descendant(
        of: _choice('serverDefault'),
        matching: find.text('Default'),
      ),
      findsOneWidget,
    );
    expect(find.text('Claude Sonnet 5.5'), findsOneWidget);
    expect(
      find.descendant(of: _choice('recent'), matching: find.text('GPT-6 Sol')),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: _choice('cheapest'),
        matching: find.text('Big Pickle · Free'),
      ),
      findsOneWidget,
    );
    // Nothing invented: no speed claim, no apply button, no search here.
    expect(find.text('Fastest'), findsNothing);
    expect(find.byKey(const Key('model-picker-apply')), findsNothing);
    expect(find.byKey(const Key('model-picker-search')), findsNothing);
    expect(find.text('All models (4)'), findsOneWidget);
    // No raw id on the surface.
    expect(find.textContaining('anthropic/'), findsNothing);
  });

  testWidgets('a model whose name says Free never says it twice', (
    tester,
  ) async {
    final controller = await _controller(
      models: [
        _model('anthropic', 'sonnet', 'Claude Sonnet 5.5', cost: _price(3, 15)),
        _model('anthropic', 'opus', 'Claude Opus 5.5', cost: _price(5, 25)),
        _model('openai', 'sol', 'GPT-6 Sol', cost: _price(2.5, 15)),
        _model('opencode', 'glm', 'GLM-5.3-Flash Free', cost: _price(0, 0)),
      ],
    );
    addTearDown(controller.dispose);
    await _open(tester, controller);
    expect(find.textContaining('Free · Free'), findsNothing);
    expect(
      find.descendant(
        of: _choice('cheapest'),
        matching: find.text('GLM-5.3-Flash Free'),
      ),
      findsOneWidget,
    );
  });

  testWidgets('a tap chooses for this conversation and closes the sheet', (
    tester,
  ) async {
    final controller = await _controller(
      history: [ModelRef(providerID: 'anthropic', modelID: 'sonnet')],
    );
    addTearDown(controller.dispose);
    await _open(tester, controller);

    await tester.tap(find.text('Big Pickle · Free'));
    await tester.pumpAndSettle();

    expect(controller.modelForSession(_session)?.modelID, 'pickle');
    expect(controller.variantForSession(_session), '');
    // The default for new conversations and the agent stay as they were.
    expect(controller.selectedModel?.modelID, 'sonnet');
    expect(controller.agentForSession(_session), 'build');
    expect(find.text('Model'), findsNothing);
  });

  testWidgets('the model in use only closes the sheet', (tester) async {
    final controller = await _controller(
      history: [ModelRef(providerID: 'openai', modelID: 'sol')],
    );
    addTearDown(controller.dispose);
    await _open(tester, controller);

    expect(
      find.descendant(of: _choice('inUse'), matching: find.text('In use')),
      findsOneWidget,
    );
    await tester.tap(find.text('GPT-6 Sol'));
    await tester.pumpAndSettle();
    expect(controller.modelWrites, 0);
    expect(find.text('Model'), findsNothing);
  });

  testWidgets('All models opens the full list in place, and back returns', (
    tester,
  ) async {
    final controller = await _controller(
      history: [ModelRef(providerID: 'openai', modelID: 'sol')],
    );
    addTearDown(controller.dispose);
    await _open(tester, controller);

    await tester.tap(find.byKey(const Key('model-picker-all')));
    await tester.pumpAndSettle();
    expect(find.text('Choose a model'), findsOneWidget);
    expect(find.byKey(const Key('model-picker-search')), findsOneWidget);
    expect(find.byKey(const Key('model-collection-favorites')), findsOneWidget);
    expect(find.byKey(const Key('model-picker-apply')), findsOneWidget);
    expect(find.byKey(const Key('model-picker-footer')), findsOneWidget);
    expect(find.byType(Dialog), findsNothing);

    // A typed search clears first; the next back returns to the choices.
    await tester.enterText(
      find.byKey(const Key('model-picker-search')),
      'opus',
    );
    await tester.pumpAndSettle();
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('model-picker-search')), findsOneWidget);
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.text('Model'), findsOneWidget);
    expect(_choice('inUse'), findsOneWidget);

    // And the header's back chevron does the same.
    await tester.tap(find.byKey(const Key('model-picker-all')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('model-picker-back')));
    await tester.pumpAndSettle();
    expect(_choice('inUse'), findsOneWidget);

    // From the full list a model is still chosen with the pinned apply.
    await tester.tap(find.byKey(const Key('model-picker-all')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('model-option-anthropic-opus')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('model-picker-apply')));
    await tester.pumpAndSettle();
    expect(controller.modelForSession(_session)?.modelID, 'opus');
  });

  testWidgets('the agent door opens straight on the full list', (tester) async {
    final controller = await _controller(
      history: [ModelRef(providerID: 'openai', modelID: 'sol')],
    );
    addTearDown(controller.dispose);
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_app(controller, focusAgent: true));
    await tester.tap(find.text('Choose model'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('model-choices')), findsNothing);
    expect(find.byKey(const Key('model-picker-back')), findsNothing);
    expect(find.text('Edits files and runs commands'), findsOneWidget);
  });

  testWidgets('nothing but the model in use to offer: the full list', (
    tester,
  ) async {
    // No prices, no recent choice, the default is the one in use.
    final controller = await _controller(
      models: [
        _model('anthropic', 'sonnet', 'Claude Sonnet 5.5'),
        _model('anthropic', 'opus', 'Claude Opus 5.5'),
      ],
    );
    addTearDown(controller.dispose);
    await _open(tester, controller);
    expect(find.byKey(const Key('model-choices')), findsNothing);
    expect(find.byKey(const Key('model-picker-search')), findsOneWidget);
  });

  testWidgets('a phone agent offers its own marked default, no price row', (
    tester,
  ) async {
    final controller = await _controller(
      models: [
        _model('claude', 'opus', 'Opus 5.5'),
        _model('claude', 'sonnet', 'Sonnet 5.5'),
        _model('claude', 'haiku', 'Haiku 5'),
      ],
      providers: ProvidersResponse(
        providers: [
          ProviderInfo(
            id: 'claude',
            name: 'Claude Code',
            modelIDs: const ['opus', 'sonnet', 'haiku'],
            modelData: const {
              'opus': {'name': 'Opus 5.5'},
              'sonnet': {'name': 'Sonnet 5.5', 'isDefault': true},
              'haiku': {'name': 'Haiku 5'},
            },
          ),
        ],
        // The list's fallback default is only its first model.
        defaultProviderID: 'claude',
        defaultModelID: 'opus',
      ),
      history: [ModelRef(providerID: 'claude', modelID: 'opus')],
    );
    addTearDown(controller.dispose);
    await _open(tester, controller);
    expect(
      find.descendant(
        of: _choice('serverDefault'),
        matching: find.text('Sonnet 5.5'),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(of: _choice('inUse'), matching: find.text('Opus 5.5')),
      findsOneWidget,
    );
    expect(_choice('cheapest'), findsNothing);
    expect(find.text('All models (3)'), findsOneWidget);
  });

  testWidgets('320 dp at 200 % text: the choices wrap, nothing overflows', (
    tester,
  ) async {
    final controller = await _controller(
      history: [ModelRef(providerID: 'openai', modelID: 'sol')],
    );
    addTearDown(controller.dispose);
    tester.view.physicalSize = const Size(320, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_app(controller, textScale: 2));
    await tester.tap(find.text('Choose model'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('Big Pickle\nFree'), findsOneWidget);
  });
}
