import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/provider_presentation.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RefreshCountingController extends ConnectionController {
  _RefreshCountingController(super.store);

  int refreshCalls = 0;
  int ensureCalls = 0;
  int reloadCalls = 0;
  bool failSelection = false;
  Future<void>? waitForModel;
  bool failAgent = false;
  int agentWrites = 0;
  final sessionAgents = <String, String>{};

  @override
  String agentForSession(String id) => sessionAgents[id] ?? selectedAgent;

  @override
  Future<void> selectAgentForSession(String id, String name) async {
    sessionAgents[id] = name;
    notifyListeners();
  }

  @override
  Future<void> selectAgent(String name) async {
    agentWrites++;
    if (failAgent) throw StateError("agent unavailable");
    await super.selectAgent(name);
  }

  @override
  Future<void> selectModel(ModelRef ref, {String? variant}) async {
    await waitForModel;
    if (failSelection) throw StateError('disk unavailable');
    await super.selectModel(ref, variant: variant);
  }

  @override
  Future<void> refreshCatalog() async {
    refreshCalls++;
  }

  @override
  Future<void> ensureCatalog() async {
    ensureCalls++;
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
  double keyboardInset = 0,
  ModelPickerApplyScope applyScope = ModelPickerApplyScope.classic,
  String? sessionID,
  bool focusAgent = false,
}) {
  return ProviderScope(
    overrides: [connProvider.overrideWithValue(controller)],
    child: MaterialApp(
      themeMode: ThemeMode.dark,
      darkTheme: AppTheme.dark(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(textScale),
          viewInsets: EdgeInsets.only(bottom: keyboardInset),
        ),
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
                focusAgent: focusAgent,
              ),
              child: const Text('Choose model'),
            ),
          ),
        ),
      ),
    ),
  );
}

Future<void> _reveal(WidgetTester tester, Finder target) async {
  // Kit search settles its query before updating the catalog; focused fields
  // also finish their caret scroll before the next action is revealed.
  await tester.pump(const Duration(milliseconds: 400));
  await tester.pumpAndSettle();
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
  expect(target.hitTestable(), findsOneWidget);
}

Future<void> _tap(WidgetTester tester, Finder target) async {
  await _reveal(tester, target);
  await tester.tap(target);
  await tester.pump();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  // Provider logos are fetched favicons; tests render the monogram instead.
  setUpAll(() => ProviderLogo.imageProviderOverride = (_) => null);
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
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
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));

    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();

    expect(find.text('Choose a model'), findsOneWidget);
    // Opening asks for a current catalog; only Reload forces a new one.
    expect(controller.ensureCalls, 1);
    expect(controller.refreshCalls, 0);
    expect(find.byKey(const Key('model-picker-refresh')), findsOneWidget);
    expect(find.byKey(const Key('model-picker-search')), findsOneWidget);
    expect(find.textContaining('131K context'), findsOneWidget);

    await tester.enterText(
      find.byKey(const Key('model-picker-search')),
      'ultra',
    );
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();

    expect(find.text('Nemotron Ultra'), findsOneWidget);
    expect(find.text('Big Pickle'), findsNothing);

    await _tap(tester, find.text('Nemotron Ultra'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(const Key('model-picker-apply')));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-apply')));
    await tester.pumpAndSettle();

    expect(controller.selectedModel?.providerID, 'opencode');
    expect(controller.selectedModel?.modelID, 'nemotron-3-ultra-free');
    expect(find.text('Choose a model'), findsNothing);
  });

  testWidgets('"Use for this conversation" leaves every other session alone', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
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

    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('model-picker-search')),
      'ultra',
    );
    await tester.pump();
    await _tap(tester, find.text('Nemotron Ultra'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Use for this conversation'));
    await tester.pumpAndSettle();
    await _tap(tester, find.text('Use for this conversation'));
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

    expect(find.text('Choose a model'), findsNothing);

    // Reopening the picker for that session starts from its own choice.
    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('model-picker-apply')), findsOneWidget);
    expect(
      tester
          .widget<KitRow>(
            find.byKey(
              const ValueKey('model-option-opencode-nemotron-3-ultra-free'),
            ),
          )
          .selected,
      isTrue,
    );
    expect(
      tester
          .widget<KitRow>(
            find.byKey(
              const ValueKey(
                'model-option-opencode-nemotron-3.5-lightning-free',
              ),
            ),
          )
          .selected,
      isFalse,
    );
  });

  testWidgets('a signed-in provider the server has not loaded is called out', (
    tester,
  ) async {
    final controller = await _controller()
      ..unloadedProviderIDs = const {'local'};
    await tester.pumpWidget(_app(controller));

    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('picker-unloaded-providers')), findsOneWidget);
    expect(
      find.textContaining(
        'Signed in to Local models, but the server has not loaded it',
      ),
      findsOneWidget,
    );
    expect(find.byType(Dialog), findsNothing);
    await _tap(tester, find.byKey(const Key('picker-reload-providers')));
    await tester.pump();
    expect(controller.reloadCalls, 1);
  });

  test(
    'unloaded-provider notice reads naturally for one or many providers',
    () {
      expect(
        unloadedProvidersNotice(['OpenAI']),
        'Signed in to OpenAI, but the server has not loaded it yet, so its '
        'models cannot answer.',
      );
      expect(
        unloadedProvidersNotice(['OpenAI', 'Anthropic']),
        contains('Anthropic and OpenAI, but the server has not loaded them'),
      );
      // After a reload that still could not load them: steer to API keys.
      expect(
        unloadedProvidersNotice(['Anthropic'], unusable: true),
        allOf(contains('Add an API key under Providers'), contains('Google')),
      );
    },
  );

  testWidgets('current model and provider family lead the unfiltered catalog', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
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

    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();

    final current = find.byKey(
      const Key('model-option-opencode-nemotron-3.5-lightning-free'),
    );
    expect(current, findsOneWidget);
    expect(tester.getTopLeft(current).dy, lessThan(891));
    expect(find.byKey(const Key('model-picker-apply')), findsOneWidget);
  });

  testWidgets('explicit fast thinking mode is selected and persisted', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));

    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-filters')));
    await tester.pumpAndSettle();
    await _tap(tester, find.text('Fast modes'));
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

    await _tap(
      tester,
      find.byKey(
        const Key('model-option-opencode-nemotron-3.5-lightning-free'),
      ),
    );
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-thinking')));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('fast · low effort'));
    await tester.pumpAndSettle();
    await _tap(tester, find.text('fast · low effort'));

    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(const Key('model-picker-apply')));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-apply')));
    await tester.pumpAndSettle();

    expect(controller.selectedVariant, 'fast');
  });

  testWidgets('the thinking menu keeps edits bound to the displayed model', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-thinking')));
    await tester.pumpAndSettle();
    await controller.selectModel(
      ModelRef(providerID: 'opencode', modelID: 'big-pickle'),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('fast · low effort'));
    await _tap(tester, find.text('fast · low effort'));

    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-apply')));
    await tester.pumpAndSettle();
    expect(controller.selectedModel!.modelID, 'nemotron-3.5-lightning-free');
    expect(controller.selectedVariant, 'fast');
  });

  testWidgets('direct agent intent survives loading and opens only once', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    final catalog = controller.catalog;
    controller.catalog = null;
    await tester.pumpWidget(_app(controller, focusAgent: true));
    await _tap(tester, find.text('Choose model'));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byKey(const ValueKey('model-picker-agent-plan')), findsNothing);
    controller.catalog = catalog;
    controller.notifyListeners();
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('model-picker-agent-plan')),
      findsOneWidget,
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();

    await tester.pumpAndSettle();
    controller.notifyListeners();
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('model-picker-agent-plan')), findsNothing);
    expect(controller.agentWrites, 0);
  });

  testWidgets('direct agent entry opens agent choice without saving', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, focusAgent: true));
    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('model-picker-agent-plan')),
      findsOneWidget,
    );
    expect(find.byKey(const Key('model-picker-agent')), findsOneWidget);
    expect(controller.agentWrites, 0);
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();

    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const ValueKey('kit-sheet-close')));
    await tester.pumpAndSettle();
    expect(controller.selectedAgent, 'build');
  });

  for (final apply in [false, true]) {
    testWidgets('agent and mode are staged until apply: $apply', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(411, 891);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await _tap(tester, find.text('Choose model'));
      await tester.pumpAndSettle();
      await _tap(tester, find.byKey(const Key('model-picker-thinking')));
      await tester.pumpAndSettle();
      await _tap(tester, find.text('fast · low effort'));
      await _tap(tester, find.byKey(const Key('model-picker-agent')));
      await tester.pumpAndSettle();
      await _tap(tester, find.byKey(const ValueKey('model-picker-agent-plan')));
      await tester.pumpAndSettle();
      expect(controller.selectedAgent, 'build');
      expect(controller.selectedVariant, '');
      expect(controller.agentWrites, 0);

      await tester.pumpAndSettle();
      await _tap(
        tester,
        apply
            ? find.byKey(const Key('model-picker-apply'))
            : find.byKey(const ValueKey('kit-sheet-close')),
      );
      await tester.pumpAndSettle();
      expect(controller.selectedAgent, apply ? 'plan' : 'build');
      expect(controller.selectedVariant, apply ? 'fast' : '');
      expect(controller.agentWrites, apply ? 1 : 0);
    });
  }

  testWidgets('session apply changes agent only in its target session', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(
        controller,
        applyScope: ModelPickerApplyScope.session,
        sessionID: 'chat',
      ),
    );
    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-agent')));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const ValueKey('model-picker-agent-plan')));
    await tester.pumpAndSettle();

    await tester.pumpAndSettle();
    await _tap(tester, find.text('Use for this conversation'));
    await tester.pumpAndSettle();
    expect(controller.agentForSession('chat'), 'plan');
    expect(controller.agentForSession('other'), 'build');
    expect(controller.selectedAgent, 'build');
    expect(controller.agentWrites, 0);
  });

  testWidgets(
    'dismissing after Apply still completes the authorized agent choice',
    (tester) async {
      tester.view.physicalSize = const Size(411, 891);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = await _controller();
      final pending = Completer<void>();
      controller.waitForModel = pending.future;
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await _tap(tester, find.text('Choose model'));
      await tester.pumpAndSettle();
      await _tap(tester, find.byKey(const Key('model-picker-agent')));
      await tester.pumpAndSettle();
      await _tap(tester, find.byKey(const ValueKey('model-picker-agent-plan')));
      await tester.pumpAndSettle();

      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('model-picker-apply')));
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('kit-sheet-close')));
      await tester.pump(const Duration(seconds: 1));
      pending.complete();
      await tester.pumpAndSettle();
      expect(controller.selectedAgent, 'plan');
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('partial apply names saved model and allows agent retry', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller()
      ..failAgent = true;
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-thinking')));
    await tester.pumpAndSettle();
    await _tap(tester, find.text('fast · low effort'));
    await _tap(tester, find.byKey(const Key('model-picker-agent')));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const ValueKey('model-picker-agent-plan')));
    await tester.pumpAndSettle();

    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-apply')));
    await tester.pumpAndSettle();
    expect(controller.selectedVariant, 'fast');
    expect(controller.selectedAgent, 'build');
    expect(
      find.text('Model saved. Agent choice was not confirmed. Try again.'),
      findsOneWidget,
    );
    controller.failAgent = false;
    await _tap(tester, find.byKey(const Key('model-picker-apply')));
    await tester.pumpAndSettle();
    expect(controller.selectedAgent, 'plan');
    expect(find.byType(ModelCatalogView), findsNothing);
  });

  testWidgets('primary agent picker excludes subagents', (tester) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));

    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-agent')));
    await tester.pumpAndSettle();

    expect(find.text('Build'), findsOneWidget);
    expect(find.text('Edits files and runs commands'), findsOneWidget);
    expect(find.text('Plan'), findsOneWidget);
    expect(find.text('Reads and plans; does not change files'), findsOneWidget);
    expect(find.textContaining('explore'), findsNothing);
  });

  testWidgets(
    'session picker labels and restores its own current model and mode',
    (tester) async {
      tester.view.physicalSize = const Size(411, 891);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await controller.selectModelForSession(
        'chat',
        ModelRef(
          providerID: 'opencode',
          modelID: 'nemotron-3.5-lightning-free',
        ),
        variant: 'deep',
      );
      await controller.selectModel(
        ModelRef(providerID: 'opencode', modelID: 'big-pickle'),
      );
      await tester.pumpWidget(
        _app(
          controller,
          applyScope: ModelPickerApplyScope.session,
          sessionID: 'chat',
        ),
      );
      await _tap(tester, find.text('Choose model'));
      await tester.pumpAndSettle();
      expect(find.text('Thinking: deep · high effort'), findsOneWidget);
      expect(find.text('Agent: Build'), findsOneWidget);
      await tester.enterText(
        find.byKey(const Key('model-picker-search')),
        'lightning',
      );
      await tester.pumpAndSettle();
      final row = find.byKey(
        const ValueKey('model-option-opencode-nemotron-3.5-lightning-free'),
      );
      await tester.ensureVisible(row);
      await _tap(tester, row);
      await tester.pump();
      await _tap(tester, find.byKey(const Key('model-picker-thinking')));
      await tester.pumpAndSettle();
      final mode = tester.widget<Semantics>(
        find
            .ancestor(
              of: find.byKey(
                const ValueKey(
                  'model-variant-nemotron-3.5-lightning-free-deep',
                ),
              ),
              matching: find.byType(Semantics),
            )
            .first,
      );
      expect(mode.properties.checked, isTrue);
      expect(controller.selectedModel?.modelID, 'big-pickle');
    },
  );

  testWidgets('favorites have a dedicated list and stage a session choice', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await controller.toggleModelFavorite(
      ModelRef(providerID: 'opencode', modelID: 'nemotron-3-ultra-free'),
    );
    await tester.pumpWidget(
      _app(
        controller,
        applyScope: ModelPickerApplyScope.session,
        sessionID: 'chat',
      ),
    );
    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-collection-favorites')));
    await tester.pumpAndSettle();
    await _tap(
      tester,
      find.byKey(const ValueKey('model-option-opencode-nemotron-3-ultra-free')),
    );
    await tester.pumpAndSettle();
    expect(
      controller.modelForSession('chat')?.modelID,
      'nemotron-3.5-lightning-free',
    );
    await tester.ensureVisible(find.text('Use for this conversation'));
    await _tap(tester, find.text('Use for this conversation'));
    await tester.pumpAndSettle();
    expect(
      controller.modelForSession('chat')?.modelID,
      'nemotron-3-ultra-free',
    );
    expect(
      controller.modelLibrary.recent.first.modelID,
      'nemotron-3-ultra-free',
    );
    expect(controller.selectedModel?.modelID, 'nemotron-3.5-lightning-free');
  });

  testWidgets('an empty search offers a working clear-search action', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('model-picker-search')),
      'nothing-matches-this',
    );
    await tester.pumpAndSettle();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
    expect(find.byType(KitSearchNoMatch), findsOneWidget);
    await _tap(tester, find.text('Clear search'));
    await tester.pumpAndSettle();
    expect(find.byType(KitSearchNoMatch), findsNothing);
    expect(
      tester
          .widget<TextField>(find.byKey(const Key('model-picker-search')))
          .controller!
          .text,
      isEmpty,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('Z.AI aliases share a filter but retain exact backend routes', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));

    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-filters')));
    await tester.pumpAndSettle();

    expect(find.text('Z.AI Coding Plan'), findsOneWidget);
    expect(find.text('Zhipu AI Coding Plan'), findsNothing);
    await _tap(tester, find.text('Z.AI Coding Plan'));
    await tester.pumpAndSettle();

    expect(find.text('GLM-5.2'), findsNWidgets(2));
    expect(
      find.textContaining('Z.AI Coding Plan · Global · 1M context'),
      findsOneWidget,
    );
    expect(
      find.textContaining('Z.AI Coding Plan · China · 1M context'),
      findsOneWidget,
    );

    await _tap(
      tester,
      find.byKey(const Key('model-option-zhipuai-coding-plan-glm-5.2')),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(const Key('model-picker-apply')));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-apply')));
    await tester.pumpAndSettle();

    expect(controller.selectedModel?.providerID, 'zhipuai-coding-plan');
    expect(controller.selectedModel?.modelID, 'glm-5.2');
  });

  for (final screen in [
    (const Size(320, 640), 2.0),
    (const Size(360, 740), 2.5),
  ]) {
    testWidgets(
      'search and apply are usable at ${screen.$1.width}dp with ${screen.$2}x text',
      (tester) async {
        tester.view.physicalSize = screen.$1;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final controller = await _controller();
        addTearDown(controller.dispose);
        await tester.pumpWidget(_app(controller, textScale: screen.$2));
        await _tap(tester, find.text('Choose model'));
        await tester.pumpAndSettle();
        expect(
          find.byKey(const ValueKey('kit-sheet-close')).hitTestable(),
          findsOneWidget,
        );
        // At enlarged text the sheet's sliver header scrolls with its
        // body, which may not build the search until it enters the cache.
        // Drive that scroll as a user would; Apply must stay pinned.
        await tester.scrollUntilVisible(
          find.byKey(const Key('model-picker-search')),
          100,
          scrollable: find
              .descendant(
                of: find.byType(KitSheet),
                matching: find.byType(Scrollable),
              )
              .first,
        );
        await tester.pumpAndSettle();
        final search = find.byKey(const Key('model-picker-search'));
        final sheetScroll = find
            .descendant(
              of: find.byType(KitSheet),
              matching: find.byType(Scrollable),
            )
            .first;
        final position = tester.state<ScrollableState>(sheetScroll).position;
        expect(
          search.hitTestable(),
          findsOneWidget,
          reason:
              'Search: ${tester.getRect(search)}; '
              'scroll: ${tester.getRect(sheetScroll)}; '
              'offset: ${position.pixels}/${position.maxScrollExtent}; '
              'actions: ${tester.getRect(find.byKey(const ValueKey('kit-sheet-actions')))}; '
              'hit: ${tester.hitTestOnBinding(tester.getCenter(search)).path.map((entry) => entry.target.runtimeType).toList()}',
        );
        final apply = find.byKey(const Key('model-picker-apply'));
        expect(apply.hitTestable(), findsOneWidget);
        expect(
          tester.getBottomRight(apply).dy,
          lessThanOrEqualTo(screen.$1.height),
        );
        await tester.enterText(
          find.byKey(const Key('model-picker-search')),
          'ultra',
        );
        await tester.pumpAndSettle();
        final row = find.byKey(
          const Key('model-option-opencode-nemotron-3-ultra-free'),
        );
        await tester.scrollUntilVisible(
          row,
          150,
          scrollable: find
              .descendant(
                of: find.byType(KitSheet),
                matching: find.byType(Scrollable),
              )
              .first,
        );
        await tester.pumpAndSettle();
        await _tap(tester, row);
        await tester.pumpAndSettle();
        final nextApply = find.byKey(const Key('model-picker-apply'));
        expect(nextApply.hitTestable(), findsOneWidget);
        await _tap(tester, nextApply);
        await tester.pumpAndSettle();
        expect(controller.selectedModel?.modelID, 'nemotron-3-ultra-free');
        expect(tester.takeException(), isNull);
      },
    );
  }
  testWidgets(
    'opening the keyboard preserves search focus and continued input',
    (tester) async {
      tester.view.physicalSize = const Size(411, 891);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = await _controller();
      addTearDown(controller.dispose);
      final inset = ValueNotifier(0.0);
      addTearDown(inset.dispose);
      await tester.pumpWidget(
        ValueListenableBuilder<double>(
          valueListenable: inset,
          builder: (_, value, _) => _app(controller, keyboardInset: value),
        ),
      );
      await _tap(tester, find.text('Choose model'));
      await tester.pumpAndSettle();
      final search = find.byKey(const Key('model-picker-search'));
      await tester.enterText(search, 'ul');
      final editable = tester.state<EditableTextState>(
        find.descendant(of: search, matching: find.byType(EditableText)),
      );
      expect(editable.widget.focusNode.hasFocus, isTrue);
      inset.value = 320;
      await tester.pumpAndSettle();
      expect(
        tester.state<EditableTextState>(
          find.descendant(of: search, matching: find.byType(EditableText)),
        ),
        same(editable),
      );
      expect(editable.widget.focusNode.hasFocus, isTrue);
      tester.testTextInput.enterText('ultra');
      await tester.pumpAndSettle();
      expect(find.text('Nemotron Ultra'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets('keyboard leaves the apply action above its inset', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, keyboardInset: 320));
    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    final apply = find.byKey(const Key('model-picker-apply'));
    expect(apply.hitTestable(), findsOneWidget);
    expect(tester.getBottomRight(apply).dy, lessThanOrEqualTo(891 - 320));
    expect(
      find.byKey(const Key('model-picker-search')).hitTestable(),
      findsOneWidget,
    );
    await tester.enterText(
      find.byKey(const Key('model-picker-search')),
      'ultra',
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
  testWidgets('apply bar stays pinned while browsing models', (tester) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));

    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();

    // The current model is drafted on open, so its apply action is already
    // visible without scrolling.
    expect(find.byKey(const ValueKey('kit-sheet-actions')), findsOneWidget);
    expect(
      find.text('Use Nemotron Lightning · Build').hitTestable(),
      findsOneWidget,
    );

    // Tapping another row re-targets the same pinned bar immediately.
    await _tap(tester, find.text('Nemotron Ultra'));
    await tester.pump();
    expect(
      find.text('Use Nemotron Ultra · Build').hitTestable(),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('kit-sheet-actions')), findsOneWidget);
  });

  testWidgets('a failed save keeps the picker open and allows retry', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller()
      ..failSelection = true;
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await _tap(tester, find.text('Choose model'));
    await tester.pumpAndSettle();
    await _tap(tester, find.byKey(const Key('model-picker-apply')));
    await tester.pumpAndSettle();
    expect(
      find.text(
        'Could not confirm the model choice. Check your selection and try again.',
      ),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('kit-sheet-close')), findsOneWidget);
    controller.failSelection = false;
    await _tap(tester, find.byKey(const Key('model-picker-apply')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('kit-sheet-close')), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
