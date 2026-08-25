import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
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
      ],
      agents: [
        CatalogAgent(id: 'build', mode: 'primary', hidden: false),
        CatalogAgent(id: 'plan', mode: 'primary', hidden: false),
      ],
    )
    ..selectedAgent = 'build'
    ..selectedModel = ModelRef(
      providerID: 'opencode',
      modelID: 'nemotron-3.5-lightning-free',
    );
}

Widget _app(ConnectionController controller, {double textScale = 1}) {
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
              onPressed: () => showModelPicker(context),
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
    expect(find.text('Nemotron Lightning'), findsOneWidget);
    expect(find.text('Nemotron Ultra'), findsNothing);

    await tester.tap(find.text('Nemotron Lightning'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('fast · low effort'));
    await tester.ensureVisible(find.text('Use model and mode'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Use model and mode'));
    await tester.pumpAndSettle();

    expect(controller.selectedVariant, 'fast');
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
}
