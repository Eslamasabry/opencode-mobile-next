import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
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

    expect(find.text('Model & agent'), findsOneWidget);
    expect(find.byKey(const Key('model-picker-search')), findsOneWidget);
    expect(find.text('Active for new prompts'), findsOneWidget);
    expect(find.text('big-pickle'), findsOneWidget);

    await tester.enterText(
      find.byKey(const Key('model-picker-search')),
      'ultra',
    );
    await tester.pump();

    expect(find.text('nemotron-3-ultra-free'), findsOneWidget);
    expect(find.text('big-pickle'), findsNothing);

    await tester.tap(find.text('nemotron-3-ultra-free'));
    await tester.pumpAndSettle();

    expect(controller.selectedModel?.providerID, 'opencode');
    expect(controller.selectedModel?.modelID, 'nemotron-3-ultra-free');
    expect(find.text('Model & agent'), findsNothing);
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
    expect(find.text('Provider'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
