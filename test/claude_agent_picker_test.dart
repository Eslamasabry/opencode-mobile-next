// The Claude Agent trial in the model picker: the "Try Claude Agent" row on
// this phone's OpenCode, the Trial tag on its models, the plain notice when
// Claude Code is not signed in, and nothing at all elsewhere.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/claude_agent_link.dart';
import 'package:opencode_mobile/domain/claude_agent_trial.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/claude_agent_trial.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_status_tag.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Repository extends ProductRepository
    implements ExternalIntegrationGateway, IntegrationCredentialGateway {
  bool offered = true;
  String? statusAfterConnect;
  List<IntegrationConnectionInfo> connections = [];
  int connects = 0;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<IntegrationInfo>> listIntegrations() async => [
    if (offered)
      IntegrationInfo(
        id: claudeAgentProviderId,
        name: 'Claude Agent',
        methods: const [
          IntegrationMethodInfo(
            type: 'external',
            id: claudeAgentLoginMethodId,
            label: 'Claude Code login (claude auth login)',
          ),
        ],
        connections: connections,
        connectionCount: connections.length,
      ),
  ];

  @override
  Future<void> connectIntegrationExternal(
    String integrationID,
    String methodID,
  ) async {
    connects++;
    connections = [
      IntegrationConnectionInfo(
        type: 'credential',
        id: 'cred-$connects',
        label: 'Claude Agent',
        status: statusAfterConnect,
      ),
      ...connections,
    ];
  }

  @override
  Future<void> removeCredential(String id) async {
    connections = [
      for (final c in connections)
        if (c.id != id) c,
    ];
  }

  @override
  Future<void> activateCredential(String id) async {}

  @override
  Future<void> renameCredential(String id, String label) async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Link implements ClaudeAgentLinkRunner {
  int calls = 0;
  @override
  Future<ClaudeAgentLinkResult> link(String ownerId) async {
    calls++;
    return ClaudeAgentLinkResult.linked;
  }
}

class _Host implements ClaudeAgentTrialHost {
  _Host(this.controller, {required this.onPhoneServer});
  final _Controller controller;
  @override
  final bool onPhoneServer;
  @override
  String? get serverVersion => '2.2.5';
  @override
  String? get agentOwnerId => 'picker-test';
  @override
  String? get scope => 'picker-test@2.2.5';
  @override
  ServerOperationsGateway? get operations => controller.repository;
  @override
  bool? get hasClaudeAgentModels => controller.catalog?.models.any(
    (m) => m.providerID == claudeAgentProviderId,
  );
  @override
  Future<void> refreshCatalog() => controller.refreshCatalog();
}

class _Controller extends ConnectionController {
  _Controller(super.store, {bool onPhoneServer = true}) {
    _trial = ClaudeAgentTrial(
      _Host(this, onPhoneServer: onPhoneServer),
      link: link,
      pollInterval: Duration.zero,
      pollAttempts: 3,
      lateLoadDelay: Duration.zero,
    )..addListener(notifyListeners);
  }

  final link = _Link();
  late final ClaudeAgentTrial _trial;
  CatalogSnapshot? afterRefresh;

  @override
  ClaudeAgentTrial get claudeAgentTrial => _trial;

  @override
  Future<void> refreshCatalog() async {
    final next = afterRefresh;
    if (next != null) {
      catalog = next;
      notifyListeners();
    }
  }
}

CatalogModel _model(String provider, String id, String name) => CatalogModel(
  id: id,
  providerID: provider,
  name: name,
  enabled: true,
  status: 'active',
  contextLimit: 200000,
  outputLimit: 0,
  reasoning: false,
  attachments: true,
  tools: true,
  variants: const [],
);

const _free = CatalogProvider(
  id: 'opencode',
  name: 'OpenCode Zen',
  enabled: true,
);

CatalogSnapshot _before() => CatalogSnapshot(
  providers: const [_free],
  models: [_model('opencode', 'big-pickle', 'Big Pickle')],
  agents: const [],
);

CatalogSnapshot _after() => CatalogSnapshot(
  providers: const [
    _free,
    CatalogProvider(
      id: claudeAgentProviderId,
      name: 'Claude Agent',
      enabled: true,
    ),
  ],
  models: [
    _model('opencode', 'big-pickle', 'Big Pickle'),
    _model(claudeAgentProviderId, 'opus', 'Opus 5.5'),
  ],
  agents: const [],
);

Future<_Controller> _controller(
  _Repository repository, {
  bool onPhoneServer = true,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.upsert(
    ServerProfile(
      id: 'picker-test',
      name: 'This phone',
      baseUrl: 'https://picker.example',
    ),
  );
  await store.setActiveId('picker-test');
  return _Controller(store, onPhoneServer: onPhoneServer)
    ..repository = repository
    ..status = StreamStatus.connected
    ..catalogDetailed = true
    ..catalog = _before();
}

Widget _app(_Controller controller) => ProviderScope(
  overrides: [connProvider.overrideWithValue(controller)],
  child: MaterialApp(
    theme: AppTheme.light(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
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

Future<void> _open(WidgetTester tester, _Controller controller) async {
  await tester.pumpWidget(_app(controller));
  await tester.tap(find.text('Choose model'));
  await tester.pumpAndSettle();
}

Future<void> _reveal(WidgetTester tester, Finder target) async {
  await tester.pumpAndSettle();
  await Scrollable.ensureVisible(tester.element(target), alignment: 0.3);
  await tester.pumpAndSettle();
}

Finder _key(String k) => find.byKey(ValueKey(k));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const secureStorage = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorage, (_) async => null);
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorage, null);
  });
  setUpAll(() => ProviderLogo.logoLoaderOverride = (_) => null);
  tearDownAll(() => ProviderLogo.logoLoaderOverride = null);

  testWidgets('Try Claude Agent sets it up and its models carry Trial', (
    tester,
  ) async {
    final repository = _Repository();
    final controller = await _controller(repository)
      ..afterRefresh = _after();
    await _open(tester, controller);

    expect(controller.capabilities.claudeAgent, isTrue);
    final row = _key('model-picker-claude-agent');
    await _reveal(tester, row);
    expect(find.text('Try Claude Agent'), findsOneWidget);
    expect(
      find.descendant(of: row, matching: find.byType(KitStatusTag)),
      findsOneWidget,
    );

    await tester.tap(row);
    await tester.pumpAndSettle();

    expect(controller.link.calls, 1);
    expect(repository.connects, 1);
    expect(find.text('Try Claude Agent'), findsNothing);
    expect(
      find.text('Claude Agent is ready. Its models are marked Trial.'),
      findsOneWidget,
    );
    final model = _key('model-option-$claudeAgentProviderId-opus');
    expect(model, findsOneWidget);
    expect(
      find.descendant(of: model, matching: find.text('Trial')),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: _key('model-option-opencode-big-pickle'),
        matching: find.byType(KitStatusTag),
      ),
      findsNothing,
    );
  });

  testWidgets('without a Claude sign-in it says so and points to Agents', (
    tester,
  ) async {
    final repository = _Repository()..statusAfterConnect = 'needs_auth';
    final controller = await _controller(repository);
    await _open(tester, controller);
    final row = _key('model-picker-claude-agent');
    await _reveal(tester, row);
    await tester.tap(row);
    await tester.pumpAndSettle();

    expect(find.text("Claude isn't signed in on this phone"), findsOneWidget);
    expect(_key('model-picker-claude-agent-open-agents'), findsOneWidget);
    expect(_key('model-picker-claude-agent-check'), findsOneWidget);
    expect(find.text('Try Claude Agent'), findsNothing);
  });

  testWidgets('nothing shows on a server that is not this phone', (
    tester,
  ) async {
    final controller = await _controller(_Repository(), onPhoneServer: false);
    await _open(tester, controller);
    await tester.pumpAndSettle();
    expect(controller.capabilities.claudeAgent, isFalse);
    expect(_key('model-picker-claude-agent'), findsNothing);
    expect(_key('model-picker-claude-agent-notice'), findsNothing);
  });

  testWidgets('nothing shows when the server does not offer it', (
    tester,
  ) async {
    final controller = await _controller(_Repository()..offered = false);
    await _open(tester, controller);
    await tester.pumpAndSettle();
    expect(controller.capabilities.claudeAgent, isFalse);
    expect(_key('model-picker-claude-agent'), findsNothing);
  });
}
