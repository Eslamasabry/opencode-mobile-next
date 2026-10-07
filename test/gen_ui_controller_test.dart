import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/builtin/agents/gen_ui_install.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_history.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/phone_project_engine.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Installer implements GenUiInstaller {
  final calls = <({String profile, Set<GenUiAgent> agents, bool enabled})>[];
  GenUiSetupStatus on = GenUiSetupOn(agents: [GenUiAgent.openCode1]);
  GenUiSetupStatus off = const GenUiSetupOff();
  Completer<void>? pause;
  bool failRemoval = false;

  @override
  Future<GenUiSetupStatus> setEnabled({
    required String profileId,
    required Set<GenUiAgent> agents,
    required bool enabled,
  }) async {
    calls.add((profile: profileId, agents: Set.of(agents), enabled: enabled));
    await pause?.future;
    if (!enabled && failRemoval) throw StateError('synthetic removal failure');
    return enabled ? on : off;
  }
}

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'https://fixture.invalid');
  List<MessageWithParts> history = [_cardMessage()];

  // Deliberately optimistic underlying capability: controller qualification
  // must still remain authoritative.
  @override
  ServerCapabilities get capabilities => const ServerCapabilities(genUi: true);

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String sessionID, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: history);

  @override
  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  }) async => GenUiHistoryPage(items: history, hasMore: false);

  @override
  Future<bool> genUiSessionIdle(String sessionID) async => true;
}

MessageWithParts _cardMessage() => MessageWithParts(
  info: MessageInfo(id: 'assistant', sessionID: 'session', role: 'assistant'),
  parts: [
    Part(
      id: 'tool',
      type: 'tool',
      messageID: 'assistant',
      callID: 'call',
      toolName: 'oc-ui_show',
      toolState: ToolState(
        status: 'completed',
        input: {
          'v': 1,
          'id': 'continue',
          'title': 'Continue?',
          'body': [],
          'ask': {'kind': 'confirm'},
        },
      ),
    ),
  ],
);

Future<({ConnectionController controller, _Api api, SharedPreferences prefs})>
_harness(_Installer installer, {bool managed = true}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'phone',
        'name': 'Phone',
        'baseUrl': managed ? BuiltinLinux.serverUrl : 'https://fixture.invalid',
      },
      {'id': 'other', 'name': 'Other', 'baseUrl': 'https://other.invalid'},
    ]),
    'oc.activeProfile': 'phone',
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final api = _Api();
  final controller =
      ConnectionController(
          store,
          isIsolated: true,
          genUiInstaller: installer,
          phoneEngineBridge: _EngineBridge(),
        )
        ..api = api
        ..repository = SdkProductRepository(api.sdkClient)
        ..directory = '/work/project'
        ..status = StreamStatus.connected;
  controller.adoptConnectedProfileForTesting(store.profiles.first);
  addTearDown(controller.dispose);
  return (controller: controller, api: api, prefs: prefs);
}

Future<void> _settle() async {
  // Flush the setup chain and best-effort preference writes, without starting
  // timer-driven recovery or native/background services.
  for (var i = 0; i < 5; i++) {
    await Future<void>.delayed(Duration.zero);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, (_) async => null);
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, null);
  });

  test(
    'effective capability defaults false despite gateway capability',
    () async {
      final installer = _Installer();
      final h = await _harness(installer);
      expect(h.api.capabilities.genUi, isTrue);
      expect(h.controller.genUiEnabled, isTrue);
      expect(h.controller.capabilities.genUi, isFalse);
      expect(h.controller.genUiStatus, isA<GenUiSetupUnavailable>());
      expect(h.controller.waitingCardsForSession('session'), isEmpty);
      expect(installer.calls, isEmpty);
    },
  );

  test(
    'OpenCode 2 fallback names the agents whose cards are unchecked',
    () async {
      final h = await _harness(_Installer());
      final profile = ServerProfile(
        id: 'phone',
        name: 'Phone',
        baseUrl: BuiltinLinux.serverUrl,
        flavor: ServerFlavor.v2,
      );
      h.controller.adoptConnectedProfileForTesting(profile);
      final status = h.controller.genUiStatus as GenUiSetupUnavailable;
      expect(status.reason, GenUiSetupProblem.notQualified);
      expect(status.affected, AgentToolAdapters.withTools.toList());
      expect(status.affected, contains(GenUiAgent.openCode2));
      expect(status.agents, isEmpty);
      expect(h.controller.capabilities.genUi, isFalse);
    },
  );

  test('unmanaged profiles reject setup and never call installer', () async {
    final installer = _Installer();
    final h = await _harness(installer, managed: false);
    expect(h.controller.genUiEnabled, isFalse);
    expect(h.controller.capabilities.genUi, isFalse);
    expect(
      (h.controller.genUiStatus as GenUiSetupUnavailable).reason,
      GenUiSetupProblem.unsupportedHost,
    );
    await expectLater(
      h.controller.setGenUiEnabled(true),
      throwsA(isA<ProductException>()),
    );
    expect(installer.calls, isEmpty);
    expect(h.prefs.containsKey('oc.genui.enabled.phone'), isFalse);
  });

  test(
    'explicit setup runs once and enables only the qualified runtime',
    () async {
      final installer = _Installer();
      final h = await _harness(installer);
      await h.controller.setGenUiEnabled(true);
      await _settle();
      expect(installer.calls, hasLength(1));
      expect(installer.calls.single.profile, 'phone');
      expect(installer.calls.single.agents, GenUiAgent.values.toSet());
      expect(h.controller.capabilities.genUi, isTrue);
      expect(h.controller.genUiStatus, isA<GenUiSetupOn>());
      expect(h.prefs.getBool('oc.genui.enabled.phone'), isTrue);

      installer.on = GenUiSetupPartial(
        agents: [GenUiAgent.claude],
        reason: GenUiSetupProblem.notQualified,
      );
      await h.controller.setGenUiEnabled(true);
      expect(h.controller.genUiStatus, isA<GenUiSetupPartial>());
      expect(h.controller.capabilities.genUi, isFalse);
    },
  );

  test(
    'controller stages all managed agents while installer gates readiness',
    () async {
      final installer = _Installer()
        ..on = GenUiSetupOn(agents: [GenUiAgent.claude]);
      final h = await _harness(installer);
      await h.controller.setGenUiEnabled(true);
      expect(installer.calls.single.agents, GenUiAgent.values.toSet());
      expect(h.controller.genUiStatus, isA<GenUiSetupOn>());
      // Existing unqualified registrations can still be cleaned up on disable.
      await h.controller.setGenUiEnabled(false);
      expect(installer.calls.last.agents, GenUiAgent.values.toSet());
    },
  );

  test(
    'review 3 pure deltas do not invalidate cards or notify the app',
    () async {
      final h = await _harness(_Installer());
      await h.controller.setGenUiEnabled(true);
      await h.controller.loadSessionTail('session');
      await _settle();
      final card = h.controller.waitingCardsForSession('session').single;
      var notifications = 0;
      h.controller.addListener(() => notifications++);
      for (var i = 0; i < 5; i++) {
        for (final type in [
          'message.part.delta',
          'message.delta',
          'tool.progress',
        ]) {
          h.controller.handleEventForTesting(
            EventEnvelope(
              type: type,
              properties: {
                'sessionID': 'session',
                'messageID': 'assistant',
                'partID': 'tool',
                'delta': 'x',
              },
            ),
          );
        }
      }
      expect(notifications, 0);
      expect(h.controller.genUiStateForCard(card), GenUiCardState.waiting);
    },
  );

  for (final throwsError in [false, true]) {
    test(
      'review 6 profile deletion continues after registration cleanup ${throwsError ? "throws" : "fails"}',
      () async {
        final installer = _Installer()
          ..failRemoval = throwsError
          ..off = const GenUiSetupFailed(
            reason: GenUiSetupProblem.removalFailed,
          );
        final h = await _harness(installer);
        await h.controller.setGenUiEnabled(true);
        await h.controller.loadSessionTail('session');
        await _settle();
        final result = await h.controller.deleteProfileAndLocalData('phone');
        expect(result.failures, isEmpty);
        expect(result.removedProfile, isTrue);
        expect(h.prefs.containsKey('oc.genui.enabled.phone'), isFalse);
        expect(h.prefs.containsKey('oc.genui.journal.phone'), isFalse);
        expect(installer.calls.last.enabled, isFalse);
      },
    );
  }

  test('automatic setup is coalesced across transcript observations', () async {
    final installer = _Installer();
    final h = await _harness(installer);
    await h.controller.loadSessionTail('session');
    await _settle();
    await h.controller.loadSessionTail('session');
    await _settle();
    expect(installer.calls, hasLength(1));
    expect(h.controller.capabilities.genUi, isTrue);
    expect(h.controller.waitingCardsForSession('session'), hasLength(1));
  });

  test(
    'pending enable cannot reactivate after a newer disable request',
    () async {
      final installer = _Installer()..pause = Completer<void>();
      final h = await _harness(installer);
      final enabling = h.controller.setGenUiEnabled(true);
      await _settle();
      expect(installer.calls.map((call) => call.enabled), [true]);
      final disallow = h.controller.setGenUiEnabled(false);
      expect(h.controller.genUiEnabled, isFalse);
      expect(h.controller.capabilities.genUi, isFalse);
      final observed = <bool>[];
      void changed() => observed.add(h.controller.capabilities.genUi);
      h.controller.addListener(changed);
      installer.pause!.complete();
      await enabling;
      await disallow;
      await _settle();
      h.controller.removeListener(changed);
      expect(observed, isNot(contains(true)));
      expect(installer.calls.map((call) => call.enabled), [true, false]);
      expect(h.controller.genUiEnabled, isFalse);
      expect(h.controller.capabilities.genUi, isFalse);
      expect(h.controller.genUiStatus, isA<GenUiSetupOff>());
      expect(h.prefs.getBool('oc.genui.enabled.phone'), isFalse);
    },
  );

  test('disable blocks card actions before removal completes', () async {
    final installer = _Installer();
    final h = await _harness(installer);
    await h.controller.setGenUiEnabled(true);
    await h.controller.loadSessionTail('session');
    final card = h.controller.waitingCardsForSession('session').single;
    installer.pause = Completer<void>();
    installer.off = GenUiSetupRestartRequired(agents: []);
    final disable = h.controller.setGenUiEnabled(false);
    expect(h.controller.capabilities.genUi, isFalse);
    expect(h.controller.waitingCardsForSession('session'), isEmpty);
    await expectLater(
      h.controller.answerGenUi(card, const GenUiConfirmAnswer(true)),
      throwsA(isA<ProductException>()),
    );
    installer.pause!.complete();
    await disable;
    expect(h.controller.genUiEnabled, isFalse);
    expect(h.controller.genUiStatus, isA<GenUiSetupRestartRequired>());
    expect(h.controller.capabilities.genUi, isFalse);
    expect(h.prefs.getBool('oc.genui.enabled.phone'), isFalse);
  });

  test(
    'waiting cards project into feed and typed reply clears attention',
    () async {
      final installer = _Installer();
      final h = await _harness(installer);
      await h.controller.setGenUiEnabled(true);
      h.controller.sessionsById['session'] = Session(
        id: 'session',
        title: 'Choose next step',
        directory: '/work/project',
      );
      await h.controller.loadSessionTail('session');
      final waiting = h.controller.chatFeed().items.single;
      expect(waiting.status, ChatStatus.needsYou);
      expect(h.controller.waitingCardsForFeedItem(waiting), hasLength(1));
      final card = h.controller.waitingCardsForSession('session').single;
      h.api.history = [
        ...h.api.history,
        MessageWithParts(
          info: MessageInfo(id: 'reply', sessionID: 'session', role: 'user'),
          parts: [Part(type: 'text', text: 'Use a different approach')],
        ),
      ];
      await h.controller.loadSessionTail('session');
      expect(h.controller.genUiStateForCard(card), GenUiCardState.passedOver);
      expect(h.controller.chatFeed().items.single.status, ChatStatus.idle);
      expect(h.controller.waitingCardsForSession('session'), isEmpty);
    },
  );

  test(
    'profile deletion removes owned card keys and retains other owner',
    () async {
      final installer = _Installer();
      final h = await _harness(installer);
      await h.controller.setGenUiEnabled(true);
      await h.controller.loadSessionTail('session');
      await _settle();
      expect(h.prefs.containsKey('oc.genui.journal.phone'), isTrue);
      await h.prefs.setBool('oc.genui.enabled.other', true);
      await h.prefs.setString('oc.genui.journal.other', '{"v":1,"entries":[]}');
      final result = await h.controller.deleteProfileAndLocalData('phone');
      expect(result.failures, isEmpty);
      expect(result.removedProfile, isTrue);
      expect(h.prefs.containsKey('oc.genui.enabled.phone'), isFalse);
      expect(h.prefs.containsKey('oc.genui.journal.phone'), isFalse);
      expect(h.prefs.getBool('oc.genui.enabled.other'), isTrue);
      expect(h.prefs.containsKey('oc.genui.journal.other'), isTrue);
      expect(installer.calls.last.enabled, isFalse);
      expect(installer.calls.last.profile, 'phone');
      expect(h.controller.waitingCardsForSession('session'), isEmpty);
    },
  );

  test(
    'remote main routes card state and Undo to built-in side owner',
    () async {
      final installer = _Installer();
      SharedPreferences.setMockInitialValues({
        'oc.profiles': jsonEncode([
          {
            'id': 'remote',
            'name': 'Remote',
            'baseUrl': 'https://fixture.invalid',
          },
          {'id': 'phone', 'name': 'Phone', 'baseUrl': BuiltinLinux.serverUrl},
        ]),
        'oc.activeProfile': 'remote',
      });
      final prefs = await SharedPreferences.getInstance();
      final store = ProfileStore(prefs: prefs);
      await store.load();
      final mainApi = _SideApi();
      final main =
          ConnectionController(
              store,
              apiFactory: (_) => _SideApi(),
              repositoryFactory: (api) => _SideRepo(api),
              eventStreamFactory:
                  ({
                    required api,
                    required onEvent,
                    required onStatus,
                    onError,
                  }) => _SideStream(
                    api: api,
                    onEvent: onEvent,
                    onStatus: onStatus,
                    onError: onError,
                  ),
              genUiInstaller: installer,
              phoneEngineBridge: _EngineBridge(),
              localWakeLockEnsurer: () async {},
            )
            ..api = mainApi
            ..repository = _SideRepo(mainApi)
            ..directory = '/work/project'
            ..status = StreamStatus.connected;
      main.adoptConnectedProfileForTesting(store.profiles.first);
      addTearDown(main.dispose);
      await main.setChatListSourceShown('phone', true);
      await Future<void>.delayed(const Duration(milliseconds: 50));
      final row = ChatFeedItem(
        sessionID: 'session',
        title: 'Phone card',
        directory: '/work/project',
        projectName: 'Project',
        isGit: false,
        status: ChatStatus.idle,
        lastActivity: DateTime.fromMillisecondsSinceEpoch(1),
        sourceId: 'profile:phone',
      );
      final side = main.connectionForRow(row)!;
      expect(side.isSideBackend, isTrue);
      expect(side.isConnected, isTrue);
      side.directory = '/work/project';
      await side.setGenUiEnabled(true).timeout(const Duration(seconds: 5));
      await side.loadSessionTail('session').timeout(const Duration(seconds: 5));
      expect(main.capabilities.genUi, isFalse);
      expect(side.capabilities.genUi, isTrue);
      final card = main.waitingCardsForFeedItem(row).single;
      expect(card.scope.profileID, 'phone');
      expect(card.scope.sourceId, 'profile:phone');
      expect(main.genUiStateForCard(card), GenUiCardState.waiting);
      expect(main.waitingCardsForSession('session'), isEmpty);
      final answer = main.answerGenUi(card, const GenUiConfirmAnswer(true));
      expect(main.genUiDeliveryFor(card), GenUiDeliveryState.held);
      expect(side.genUiDeliveryFor(card), GenUiDeliveryState.held);
      main.undoGenUiAnswer(card);
      await answer;
      expect(main.genUiDeliveryFor(card), GenUiDeliveryState.idle);
      final deletion = await main
          .deleteProfileAndLocalData('phone')
          .timeout(
            const Duration(seconds: 5),
            onTimeout: () =>
                throw StateError('Side profile deletion did not drain'),
          );
      expect(deletion.failures, isEmpty);
      expect(deletion.removedProfile, isTrue);
      expect(main.waitingCardsForFeedItem(row), isEmpty);
      expect(main.genUiStateForCard(card), GenUiCardState.unknown);
      expect(installer.calls.every((call) => call.profile == 'phone'), isTrue);
      expect(installer.calls.last.enabled, isFalse);
      expect(prefs.containsKey('oc.genui.enabled.phone'), isFalse);
      expect(prefs.containsKey('oc.genui.enabled.remote'), isFalse);
    },
    timeout: const Timeout(Duration(seconds: 20)),
  );

  test(
    'failed setup keeps desired setting separate from effective capability',
    () async {
      final installer = _Installer()
        ..on = const GenUiSetupFailed(
          reason: GenUiSetupProblem.verificationFailed,
        );
      final h = await _harness(installer);
      await h.controller.setGenUiEnabled(true);
      expect(h.controller.genUiEnabled, isTrue);
      expect(h.controller.capabilities.genUi, isFalse);
      expect(
        (h.controller.genUiStatus as GenUiSetupFailed).reason,
        GenUiSetupProblem.verificationFailed,
      );
      expect(h.controller.waitingCardsForSession('session'), isEmpty);
    },
  );
}

class _EngineBridge implements PhoneProjectEngineBridge {
  @override
  Future<void> start(
    String profileId, {
    int port = 4098,
    String? notice,
  }) async {}
  @override
  Future<({String baseUrl, String bearerToken})> credentials(
    String profileId,
  ) async => (baseUrl: '', bearerToken: '');
  @override
  Future<void> delete(String profileId) async {}
  @override
  Future<void> stop(String profileId) async {}
}

class _SideApi extends _Api {
  @override
  Future<Health> health() async => Health(healthy: true, version: '1');
  @override
  Future<List<Session>> sessions() async => const [];
  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<ProvidersResponse> providers() async =>
      ProvidersResponse(providers: const []);
  @override
  Future<ProvidersResponse> configuredProviders() async =>
      ProvidersResponse(providers: const []);
  @override
  Future<List<AgentInfo>> agents() async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
  @override
  Future<List<FileNode>> listFiles(String path) async => const [];
}

class _SideRepo extends SdkProductRepository {
  _SideRepo(OpenCodeApi api) : super(api.sdkClient);
  @override
  Future<ChatDefaults> loadChatDefaults() async => const ChatDefaults();
  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];
  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);
  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [];
  @override
  Future<WorkspaceProject?> loadCurrentProject() async => null;
  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];
  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];
  @override
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    String? cursor,
    int limit = 50,
  }) async => const ServerPage(items: []);
}

class _SideStream extends EventStream {
  _SideStream({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });
  @override
  void start() => onStatus(StreamStatus.connected);
  @override
  Future<void> dispose() async {}
}
