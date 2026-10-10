// An agent's own switches (fast mode and the like): a chip in the composer's
// chip row opens "<Agent> settings"; each switch saves at once and shows the
// server's answer; a refusal keeps the old value and says so in plain words.
// Also: the model sheet starts on the conversation's own model.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';
import 'coverage/paseo_chat_harness.dart' show CoverageApi, frames;
import 'goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;

class _FeatureApi extends CoverageApi implements AgentFeatureGateway {
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
  String? agentFeaturesOwner(String sessionID) => 'Claude Code';

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
        if (f.id != featureId)
          f
        else
          AgentFeature(
            id: f.id,
            label: f.label,
            kind: f.kind,
            on: value is bool ? value : f.on,
            selected: value is String ? value : f.selected,
            options: f.options,
          ),
    ];
    return features;
  }
}

final _boundary = GlobalKey();

Future<_FeatureApi> _chat(
  WidgetTester tester, {
  void Function(_FeatureApi api)? setUpApi,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final api = _FeatureApi()..messagesHandler = (_) async => const [];
  setUpApi?.call(api);
  final controller = await captureController(
    prefs: await SharedPreferences.getInstance(),
    api: api,
  );
  await tester.pumpWidget(
    captureApp(
      home: const ChatScreen(sessionID: checkoutSessionID),
      boundaryKey: _boundary,
      controller: controller,
    ),
  );
  addTearDown(() async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
    controller.dispose();
  });
  await frames(tester);
  return api;
}

Future<void> _shot(WidgetTester tester, String name) async => writePng(
  'build/coverage/od-agent-features-$name.png',
  await capturePng(tester, _boundary, pixelRatio: 1),
);

Future<void> _modelSheet(
  WidgetTester tester,
  String shot, {
  required bool withModel,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final controller = ConnectionController(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
  );
  addTearDown(controller.dispose);
  controller
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
  if (withModel) {
    await controller.selectModel(
      ModelRef(providerID: 'claude', modelID: 'opus'),
    );
  }
  await tester.pumpWidget(
    RepaintBoundary(
      key: _boundary,
      child: ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
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
  await _shot(tester, shot);
  if (withModel) {
    expect(find.text('Choose a model first.'), findsNothing);
    expect(find.byKey(const Key('model-picker-apply')), findsOneWidget);
  } else {
    expect(find.text('Choose a model first.'), findsOneWidget);
  }
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

  final chip = find.byKey(const Key('agent-features-chip'));
  final fast = find.byKey(const Key('agent-feature-fast_mode'));

  testWidgets('the chip says Fast mode off; its sheet turns it on', (
    tester,
  ) async {
    final api = await _chat(tester);
    expect(find.text('Fast mode off'), findsOneWidget);
    await _shot(tester, '1-chat-chip-off');
    await tester.tap(chip);
    await tester.pumpAndSettle();
    expect(find.text('Claude Code settings'), findsOneWidget);
    expect(find.text('Fast mode'), findsOneWidget);
    expect(
      find.text('Quicker replies from supported models. It costs more.'),
      findsOneWidget,
    );
    expect(find.text('Agent settings'), findsNothing);
    await _shot(tester, '2-settings-sheet');
    await tester.tap(fast);
    await tester.pumpAndSettle();
    expect(api.calls, [(checkoutSessionID, 'fast_mode', true)]);
    expect(tester.widget<Switch>(find.byType(Switch).first).value, isTrue);
    // Closing the sheet leaves the chip lit and in words.
    await tester.tapAt(const Offset(206, 40));
    await tester.pumpAndSettle();
    expect(find.text('Fast mode off'), findsNothing);
    expect(find.text('Fast mode'), findsOneWidget);
    await _shot(tester, '3-chat-chip-on');
  });

  testWidgets('the model sheet no longer carries the agent settings', (
    tester,
  ) async {
    await _chat(tester);
    expect(find.text('Agent settings'), findsNothing);
  });

  testWidgets('it says Saving while the server decides', (tester) async {
    final api = await _chat(
      tester,
      setUpApi: (api) => api.hold = Completer<void>(),
    );
    await tester.tap(chip);
    await tester.pumpAndSettle();
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
    final api = await _chat(tester, setUpApi: (api) => api.refuse = true);
    await tester.tap(chip);
    await tester.pumpAndSettle();
    await tester.tap(fast);
    await tester.pumpAndSettle();
    expect(api.calls, hasLength(1));
    expect(tester.widget<Switch>(find.byType(Switch).first).value, isFalse);
    expect(
      find.text('Could not change Fast mode. It keeps its old setting.'),
      findsOneWidget,
    );
    expect(find.textContaining('has no fast mode'), findsNothing);
    await _shot(tester, '4-settings-refused');
  });

  testWidgets('an agent with no switches has no chip', (tester) async {
    await _chat(tester, setUpApi: (api) => api.features = const []);
    expect(chip, findsNothing);
  });

  testWidgets('switches that could not be read leave no chip', (tester) async {
    await _chat(tester, setUpApi: (api) => api.failLoad = true);
    expect(chip, findsNothing);
  });

  testWidgets('a switch the app has no words for keeps its label', (
    tester,
  ) async {
    await _chat(
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
    expect(find.text('Plan first off'), findsOneWidget);
    await tester.tap(chip);
    await tester.pumpAndSettle();
    expect(find.text('Draft a plan before editing.'), findsOneWidget);
  });

  testWidgets('several switches: the chip names the sheet; a choice menu', (
    tester,
  ) async {
    final api = await _chat(
      tester,
      setUpApi: (api) => api.features = const [
        AgentFeature(
          id: 'fast_mode',
          label: 'Fast',
          kind: AgentFeatureKind.toggle,
        ),
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
    expect(find.text('Claude Code settings'), findsOneWidget);
    await tester.tap(chip);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('agent-feature-effort')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('agent-feature-effort-high')));
    await tester.pumpAndSettle();
    expect(api.calls, [(checkoutSessionID, 'effort', 'high')]);
  });

  testWidgets('model sheet before: no model is known', (tester) async {
    await _modelSheet(tester, 'm1-model-sheet-no-model', withModel: false);
  });

  testWidgets('model sheet after: it starts on the conversation\'s model', (
    tester,
  ) async {
    await _modelSheet(tester, 'm2-model-sheet-with-model', withModel: true);
  });
}
