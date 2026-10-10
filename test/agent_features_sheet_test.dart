// An agent's own switches in the model sheet (fast mode and the like):
// each toggle asks the server and shows its answer; a refusal says so in
// plain words and leaves the old value.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';
import 'goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;

class _FeatureApi extends OpenCodeApi implements AgentFeatureGateway {
  _FeatureApi() : super(baseUrl: 'ws://build.example.net:6767');

  final calls = <(String, String, Object)>[];
  var features = <AgentFeature>[
    const AgentFeature(
      id: 'fast_mode',
      label: 'Fast',
      kind: AgentFeatureKind.toggle,
    ),
  ];
  bool failLoad = false;
  bool refuse = false;
  Completer<void>? hold;

  @override
  bool get agentFeaturesSupported => true;

  @override
  Future<List<AgentFeature>> agentFeatures(String sessionID) async {
    if (failLoad) throw StateError('daemon unreachable');
    return features;
  }

  @override
  Future<List<AgentFeature>> setAgentFeature(
    String sessionID,
    String featureId,
    Object value,
  ) async {
    calls.add((sessionID, featureId, value));
    await hold?.future;
    if (refuse) throw StateError('model has no fast mode');
    features = [
      for (final f in features)
        if (f.id == featureId && value is bool)
          AgentFeature(id: f.id, label: f.label, kind: f.kind, on: value)
        else if (f.id == featureId && value is String)
          AgentFeature(
            id: f.id,
            label: f.label,
            kind: f.kind,
            selected: value,
            options: f.options,
          )
        else
          f,
    ];
    return features;
  }
}

final _boundary = GlobalKey();

Future<(ConnectionController, _FeatureApi)> _open(
  WidgetTester tester, {
  void Function(_FeatureApi api)? setUpApi,
}) async {
  SharedPreferences.setMockInitialValues({});
  final controller = ConnectionController(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
  );
  addTearDown(controller.dispose);
  final api = _FeatureApi();
  setUpApi?.call(api);
  controller
    ..api = api
    ..providers = ProvidersResponse(
      providers: [
        ProviderInfo(id: 'claude', name: 'Claude Code', modelIDs: ['opus']),
      ],
      defaultProviderID: 'claude',
      defaultModelID: 'opus',
    )
    ..catalogDetailed = true
    ..catalog = const CatalogSnapshot(
      providers: [
        CatalogProvider(id: 'claude', name: 'Claude Code', enabled: true),
      ],
      models: [
        CatalogModel(
          id: 'opus',
          providerID: 'claude',
          name: 'Opus',
          enabled: true,
          status: 'active',
          contextLimit: 0,
          outputLimit: 0,
          reasoning: false,
          attachments: false,
          tools: false,
          variants: [],
        ),
      ],
      agents: [CatalogAgent(id: 'default', mode: 'primary', hidden: false)],
    );
  await tester.pumpWidget(
    RepaintBoundary(
      key: _boundary,
      child: ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          themeMode: ThemeMode.dark,
          darkTheme: AppTheme.dark(),
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: FilledButton(
                  onPressed: () => showModelPicker(
                    context,
                    applyScope: ModelPickerApplyScope.session,
                    sessionID: 'a1',
                  ),
                  child: const Text('Choose model'),
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('Choose model'));
  await tester.pumpAndSettle();
  return (controller, api);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    ProviderLogo.imageProviderOverride = (_) => null;
  });
  tearDownAll(() => ProviderLogo.imageProviderOverride = null);
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  final fast = find.byKey(const Key('agent-feature-fast_mode'));

  testWidgets('fast mode shows in plain words and turns on', (tester) async {
    final (_, api) = await _open(tester);
    expect(find.text('Agent settings'), findsOneWidget);
    expect(find.text('Fast mode'), findsOneWidget);
    expect(find.text('Fast'), findsNothing, reason: 'the app has its words');
    expect(tester.widget<Switch>(find.byType(Switch).first).value, isFalse);
    await tester.tap(fast);
    await tester.pumpAndSettle();
    expect(api.calls, [('a1', 'fast_mode', true)]);
    expect(tester.widget<Switch>(find.byType(Switch).first).value, isTrue);
  });

  testWidgets('it says Saving while the server decides', (tester) async {
    final (_, api) = await _open(
      tester,
      setUpApi: (api) => api.hold = Completer<void>(),
    );
    await tester.tap(fast);
    await tester.pump();
    expect(find.text('Saving…'), findsOneWidget);
    api.hold!.complete();
    await tester.pumpAndSettle();
    expect(find.text('Saving…'), findsNothing);
  });

  testWidgets('a refusal keeps the old value and says so in plain words', (
    tester,
  ) async {
    final (_, api) = await _open(tester, setUpApi: (api) => api.refuse = true);
    await tester.tap(fast);
    await tester.pumpAndSettle();
    expect(api.calls, hasLength(1));
    expect(tester.widget<Switch>(find.byType(Switch).first).value, isFalse);
    expect(
      find.text('Could not change Fast mode. It keeps its old setting.'),
      findsOneWidget,
    );
    expect(find.textContaining('has no fast mode'), findsNothing);
  });

  testWidgets('an agent with no switches shows nothing', (tester) async {
    await _open(tester, setUpApi: (api) => api.features = const []);
    expect(find.text('Agent settings'), findsNothing);
    expect(find.byKey(const Key('agent-features')), findsNothing);
  });

  testWidgets('a failed read says so', (tester) async {
    await _open(tester, setUpApi: (api) => api.failLoad = true);
    expect(find.text("Could not load this agent's settings."), findsOneWidget);
  });

  testWidgets('an agent\'s own switch it has no words for keeps its label', (
    tester,
  ) async {
    await _open(
      tester,
      setUpApi: (api) => api.features = const [
        AgentFeature(
          id: 'plan_first',
          label: 'Plan first',
          description: 'Draft a plan before editing.',
          kind: AgentFeatureKind.toggle,
        ),
      ],
    );
    expect(find.text('Plan first'), findsOneWidget);
    expect(find.text('Draft a plan before editing.'), findsOneWidget);
  });

  testWidgets('a choice switch offers its options', (tester) async {
    final (_, api) = await _open(
      tester,
      setUpApi: (api) => api.features = const [
        AgentFeature(
          id: 'effort',
          label: 'Effort',
          kind: AgentFeatureKind.choice,
          selected: 'low',
          options: [
            AgentFeatureOption(id: 'low', label: 'Low'),
            AgentFeatureOption(id: 'high', label: 'High'),
          ],
        ),
      ],
    );
    expect(find.text('Low'), findsOneWidget);
    await tester.tap(find.byKey(const Key('agent-feature-effort')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('agent-feature-effort-high')));
    await tester.pumpAndSettle();
    expect(api.calls, [('a1', 'effort', 'high')]);
    expect(find.text('High'), findsOneWidget);
  });

  // The look gate: build/coverage/od-agent-features-*.png.
  testWidgets('look: on, saving, refused', (tester) async {
    Future<void> shot(String name) async => writePng(
      'build/coverage/od-agent-features-$name.png',
      await capturePng(tester, _boundary, pixelRatio: 1),
    );
    final (_, api) = await _open(
      tester,
      setUpApi: (api) => api.hold = Completer<void>(),
    );
    await shot('1-off');
    await tester.tap(fast);
    await tester.pump();
    await shot('2-saving');
    api.hold!.complete();
    await tester.pumpAndSettle();
    await shot('3-on');
    api.refuse = true;
    await tester.tap(fast);
    await tester.pumpAndSettle();
    await shot('4-refused');
  });
}
