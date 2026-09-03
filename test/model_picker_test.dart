import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/provider_presentation.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RefreshCountingController extends ConnectionController {
  _RefreshCountingController(super.store);

  int refreshCalls = 0;
  int reloadCalls = 0;

  @override
  Future<void> refreshCatalog() async {
    refreshCalls++;
  }

  @override
  Future<void> reloadProviderRuntime() async {
    reloadCalls++;
  }
}

Future<_RefreshCountingController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return _RefreshCountingController(ProfileStore(prefs: prefs))
    ..providers = ProvidersResponse(
      providers: [
        ProviderInfo(
          id: 'opencode',
          name: 'OpenCode Zen',
          modelIDs: const [
            'nemotron-3.5-lightning-free',
            'nemotron-3-ultra-free',
            'big-pickle',
          ],
        ),
        ProviderInfo(
          id: 'local',
          name: 'Local models',
          modelIDs: const ['small-local'],
        ),
      ],
      defaultProviderID: 'opencode',
      defaultModelID: 'nemotron-3.5-lightning-free',
    )
    ..agents = [AgentInfo(name: 'build'), AgentInfo(name: 'plan')]
    ..catalogDetailed = true
    ..catalog = const CatalogSnapshot(
      providers: [
        CatalogProvider(id: 'opencode', name: 'OpenCode Zen', enabled: true),
        CatalogProvider(id: 'local', name: 'Local models', enabled: true),
        CatalogProvider(
          id: 'zai-coding-plan',
          name: 'Z.AI Coding Plan',
          enabled: true,
        ),
        CatalogProvider(
          id: 'zhipuai-coding-plan',
          name: 'Zhipu AI Coding Plan',
          enabled: true,
        ),
      ],
      models: [
        CatalogModel(
          id: 'nemotron-3.5-lightning-free',
          providerID: 'opencode',
          name: 'Nemotron Lightning',
          enabled: true,
          status: 'active',
          contextLimit: 131072,
          outputLimit: 16384,
          reasoning: true,
          attachments: true,
          tools: true,
          variants: [
            CatalogVariant(id: 'fast', options: {'reasoningEffort': 'low'}),
            CatalogVariant(id: 'deep', options: {'reasoningEffort': 'high'}),
          ],
        ),
        CatalogModel(
          id: 'nemotron-3-ultra-free',
          providerID: 'opencode',
          name: 'Nemotron Ultra',
          enabled: true,
          status: 'active',
          contextLimit: 262144,
          outputLimit: 32768,
          reasoning: true,
          attachments: false,
          tools: true,
          variants: [],
        ),
        CatalogModel(
          id: 'big-pickle',
          providerID: 'opencode',
          name: 'Big Pickle',
          enabled: true,
          status: 'active',
          contextLimit: 65536,
          outputLimit: 8192,
          reasoning: false,
          attachments: false,
          tools: true,
          variants: [],
        ),
        CatalogModel(
          id: 'small-local',
          providerID: 'local',
          name: 'Small local',
          enabled: true,
          status: 'active',
          contextLimit: 32768,
          outputLimit: 4096,
          reasoning: false,
          attachments: false,
          tools: false,
          variants: [],
        ),
        CatalogModel(
          id: 'glm-5.2',
          providerID: 'zai-coding-plan',
          name: 'GLM-5.2',
          enabled: true,
          status: 'active',
          contextLimit: 1000000,
          outputLimit: 131072,
          reasoning: true,
          attachments: false,
          tools: true,
          variants: [],
        ),
        CatalogModel(
          id: 'glm-5.2',
          providerID: 'zhipuai-coding-plan',
          name: 'GLM-5.2',
          enabled: true,
          status: 'active',
          contextLimit: 1000000,
          outputLimit: 131072,
          reasoning: true,
          attachments: false,
          tools: true,
          variants: [],
        ),
      ],
      agents: [
        CatalogAgent(id: 'build', mode: 'primary', hidden: false),
        CatalogAgent(id: 'plan', mode: 'primary', hidden: false),
        CatalogAgent(id: 'explore', mode: 'subagent', hidden: false),
      ],
    )
    ..selectedAgent = 'build'
    ..selectedModel = ModelRef(
      providerID: 'opencode',
      modelID: 'nemotron-3.5-lightning-free',
    );
}

Widget _app(
  ConnectionController controller, {
  double textScale = 1,
  ModelPickerApplyScope applyScope = ModelPickerApplyScope.classic,
  String? sessionID,
}) {
  return ProviderScope(
    overrides: [connProvider.overrideWithValue(controller)],
    child: MaterialApp(
      themeMode: ThemeMode.dark,
      darkTheme: AppTheme.dark(),
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
                applyScope: applyScope,
                sessionID: sessionID,
              ),
              child: const Text('Choose model'),
            ),
          ),
        ),
      ),
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  // Provider logos are fetched favicons; tests render the monogram instead.
  setUpAll(() => ProviderLogo.imageProviderOverride = (_) => null);
  tearDownAll(() => ProviderLogo.imageProviderOverride = null);

  test('model presentation leads with current model and provider family', () {
    const models = [
      CatalogModel(
        id: 'gemini-wire-first',
        providerID: 'google',
        name: 'Gemini wire first',
        enabled: true,
        status: 'active',
        contextLimit: 1000000,
        outputLimit: 64000,
        reasoning: true,
        attachments: true,
        tools: true,
        variants: [],
      ),
      CatalogModel(
        id: 'gpt-current',
        providerID: 'openai',
        name: 'GPT current',
        enabled: true,
        status: 'active',
        contextLimit: 400000,
        outputLimit: 128000,
        reasoning: true,
        attachments: true,
        tools: true,
        variants: [],
      ),
      CatalogModel(
        id: 'gpt-sibling',
        providerID: 'openai',
        name: 'GPT sibling',
        enabled: true,
        status: 'active',
        contextLimit: 400000,
        outputLimit: 128000,
        reasoning: true,
        attachments: true,
        tools: true,
        variants: [],
      ),
      CatalogModel(
        id: 'local-last',
        providerID: 'local',
        name: 'Local last',
        enabled: true,
        status: 'active',
        contextLimit: 32000,
        outputLimit: 4000,
        reasoning: false,
        attachments: false,
        tools: false,
        variants: [],
      ),
    ];

    final ordered = presentModels(
      models,
      selected: ModelRef(providerID: 'openai', modelID: 'gpt-current'),
    );

    expect(ordered.map((model) => '${model.providerID}/${model.id}').toList(), [
      'openai/gpt-current',
      'openai/gpt-sibling',
      'google/gemini-wire-first',
      'local/local-last',
    ]);
  });

  testWidgets('model selector searches and persists a new selection', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(411, 891));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));

    await tester.tap(find.text('Choose model'));
    await tester.pumpAndSettle();

    expect(find.text('Model, mode & agent'), findsOneWidget);
    expect(controller.refreshCalls, 1);
    expect(find.byKey(const Key('model-picker-refresh')), findsOneWidget);
    expect(find.byKey(const Key('model-picker-search')), findsOneWidget);
    expect(find.textContaining('131,072 context'), findsOneWidget);

    await tester.enterText(
      find.byKey(const Key('model-picker-search')),
      'ultra',
    );
    await tester.pump();

    expect(find.text('Nemotron Ultra'), findsOneWidget);
    expect(find.text('Big Pickle'), findsNothing);

    await tester.tap(find.text('Nemotron Ultra'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Use model and mode'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Use model and mode'));
    await tester.pumpAndSettle();

    expect(controller.selectedModel?.providerID, 'opencode');
    expect(controller.selectedModel?.modelID, 'nemotron-3-ultra-free');
    expect(find.text('Model, mode & agent'), findsNothing);
  });

  testWidgets('"Use for this session" leaves every other session alone', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(411, 891));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controller();
    addTearDown(controller.dispose);
    controller.selectedModel = ModelRef(
      providerID: 'opencode',
      modelID: 'nemotron-3.5-lightning-free',
    );
    await tester.pumpWidget(
      _app(
        controller,
        applyScope: ModelPickerApplyScope.session,
        sessionID: 'ses_a',
      ),
    );

    await tester.tap(find.text('Choose model'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('model-picker-search')),
      'ultra',
    );
    await tester.pump();
    await tester.tap(find.text('Nemotron Ultra'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Use for this session'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Use for this session'));
    await tester.pumpAndSettle();

    expect(
      controller.modelForSession('ses_a')?.modelID,
      'nemotron-3-ultra-free',
    );
    // The profile default and any other session are untouched.
    expect(controller.selectedModel?.modelID, 'nemotron-3.5-lightning-free');
    expect(
      controller.modelForSession('ses_b')?.modelID,
      'nemotron-3.5-lightning-free',
    );

    // Reopening the picker for that session starts from its own choice.
    await tester.tap(find.text('Choose model'));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('use-model-opencode-nemotron-3-ultra-free')),
      findsOneWidget,
    );
  });

  testWidgets('a signed-in provider the server has not loaded is called out', (
    tester,
  ) async {
    final controller = await _controller()
      ..unloadedProviderIDs = const {'local'};
    await tester.pumpWidget(_app(controller));

    await tester.tap(find.text('Choose model'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('picker-unloaded-providers')), findsOneWidget);
    expect(
      find.textContaining('signed in to Local models but has not loaded it'),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const Key('picker-reload-providers')));
    await tester.pump();
    expect(controller.reloadCalls, 1);
  });

  test(
    'unloaded-provider notice reads naturally for one or many providers',
    () {
      expect(
        unloadedProvidersNotice(['OpenAI']),
        contains(
          'signed in to OpenAI but has not loaded it yet, so its models',
        ),
      );
      expect(
        unloadedProvidersNotice(['OpenAI', 'Anthropic']),
        contains('Anthropic and OpenAI but has not loaded them yet, so their'),
      );
    },
  );

  testWidgets('current model and provider family lead the unfiltered catalog', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(411, 891));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controller();
    addTearDown(controller.dispose);
    final existing = controller.catalog!;
    controller.catalog = CatalogSnapshot(
      providers: const [
        CatalogProvider(id: 'google', name: 'Google', enabled: true),
        CatalogProvider(id: 'opencode', name: 'OpenCode Zen', enabled: true),
        CatalogProvider(id: 'local', name: 'Local models', enabled: true),
      ],
      models: [
        const CatalogModel(
          id: 'gemini-first-on-wire',
          providerID: 'google',
          name: 'Gemini first on wire',
          enabled: true,
          status: 'active',
          contextLimit: 1000000,
          outputLimit: 64000,
          reasoning: true,
          attachments: true,
          tools: true,
          variants: [],
        ),
        ...existing.models,
      ],
      agents: existing.agents,
    );
    await tester.pumpWidget(_app(controller));

    await tester.tap(find.text('Choose model'));
    await tester.pumpAndSettle();

    final current = find.byKey(
      const Key('model-option-opencode-nemotron-3.5-lightning-free'),
    );
    expect(current, findsOneWidget);
    expect(tester.getTopLeft(current).dy, lessThan(891));
    expect(find.text('Use model and mode'), findsOneWidget);
  });

  testWidgets('explicit fast thinking mode is selected and persisted', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(411, 891));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));

    await tester.tap(find.text('Choose model'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Fast modes'));
    await tester.pumpAndSettle();
    // The pinned apply bar also names the drafted (current) model, so the
    // name can appear twice while its row stays unique.
    expect(
      find.byKey(
        const Key('model-option-opencode-nemotron-3.5-lightning-free'),
      ),
      findsOneWidget,
    );
    expect(find.text('Nemotron Ultra'), findsNothing);

    await tester.tap(
      find.byKey(
        const Key('model-option-opencode-nemotron-3.5-lightning-free'),
      ),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('fast · low effort'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('fast · low effort'));
    await tester.ensureVisible(find.text('Use model and mode'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Use model and mode'));
    await tester.pumpAndSettle();

    expect(controller.selectedVariant, 'fast');
  });

  testWidgets('primary agent picker excludes subagents', (tester) async {
    await tester.binding.setSurfaceSize(const Size(411, 891));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));

    await tester.tap(find.text('Choose model'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('model-picker-agent')));
    await tester.pumpAndSettle();

    expect(find.textContaining('build · primary'), findsWidgets);
    expect(find.textContaining('plan · primary'), findsOneWidget);
    expect(find.textContaining('explore'), findsNothing);
  });

  testWidgets('Z.AI aliases share a filter but retain exact backend routes', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(411, 891));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));

    await tester.tap(find.text('Choose model'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('model-picker-provider')));
    await tester.pumpAndSettle();

    expect(find.text('Z.AI Coding Plan'), findsOneWidget);
    expect(find.text('Zhipu AI Coding Plan'), findsNothing);
    await tester.tap(find.text('Z.AI Coding Plan'));
    await tester.pumpAndSettle();

    expect(find.text('GLM-5.2'), findsNWidgets(2));
    expect(
      find.textContaining('Z.AI Coding Plan · Global · glm-5.2'),
      findsOneWidget,
    );
    expect(
      find.textContaining('Z.AI Coding Plan · China · glm-5.2'),
      findsOneWidget,
    );

    await tester.tap(
      find.byKey(const Key('model-option-zhipuai-coding-plan-glm-5.2')),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Use model and mode'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Use model and mode'));
    await tester.pumpAndSettle();

    expect(controller.selectedModel?.providerID, 'zhipuai-coding-plan');
    expect(controller.selectedModel?.modelID, 'glm-5.2');
  });

  testWidgets('model selector remains usable at 320dp with 2x text', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, textScale: 2));

    await tester.tap(find.text('Choose model'));
    await tester.pumpAndSettle();

    expect(find.byTooltip('Close model selector'), findsOneWidget);
    expect(find.byKey(const Key('model-picker-search')), findsOneWidget);
    expect(find.text('Agent'), findsOneWidget);
    await tester.drag(find.byType(ListView).first, const Offset(0, -260));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('model-picker-provider')), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets('apply bar stays pinned while browsing models', (tester) async {
    await tester.binding.setSurfaceSize(const Size(411, 891));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));

    await tester.tap(find.text('Choose model'));
    await tester.pumpAndSettle();

    // The current model is drafted on open, so its apply action is already
    // visible without scrolling.
    expect(find.byKey(const Key('model-picker-apply-bar')), findsOneWidget);
    expect(
      find.byKey(const Key('use-model-opencode-nemotron-3.5-lightning-free')),
      findsOneWidget,
    );

    // Tapping another row re-targets the same pinned bar immediately.
    await tester.tap(find.text('Nemotron Ultra'));
    await tester.pump();
    expect(
      find.byKey(const Key('use-model-opencode-nemotron-3-ultra-free')),
      findsOneWidget,
    );
    expect(find.byKey(const Key('model-picker-apply-bar')), findsOneWidget);
  });
}
