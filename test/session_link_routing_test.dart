import 'dart:async';
import 'dart:convert';

import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/session_handoff.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/platform/session_link.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_address_controller.dart';
import 'package:opencode_mobile/state/session_link_bindings.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/update/shorebird_update_notice.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs, required this.saved});

  final List<ServerProfile> saved;
  String? selectedID;

  @override
  List<ServerProfile> get profiles => saved;

  @override
  String? get activeId => selectedID;

  @override
  Future<void> setActiveId(String? id) async {
    selectedID = id;
  }
}

/// A connected v1 transport that counts everything a link must never do:
/// creating a session or sending a prompt.
class _LinkApi extends OpenCodeApi with CompleteMessageHistory {
  _LinkApi({super.baseUrl = 'http://localhost:4096'});

  int created = 0;
  int prompted = 0;
  final healthResult = Completer<Health>();

  @override
  Future<Health> health() => healthResult.future;

  @override
  Future<Session> createSession() async {
    created += 1;
    return Session(id: 'created-$created');
  }

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async {
    prompted += 1;
  }

  @override
  Future<List<MessageWithParts>> messages(String id) async => const [];

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
  Future<List<PermissionRequest>> pendingPermissionsV2() async => const [];

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => const [];
}

class _LinkRepository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];

  @override
  Future<ChatDefaults> loadChatDefaults() async => const ChatDefaults();

  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [];

  @override
  Future<WorkspaceProject?> loadCurrentProject() async => null;

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FakeEventStream extends EventStream {
  _FakeEventStream({
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

class _NoUpdateService implements AppUpdateService {
  @override
  bool get isAvailable => false;

  @override
  Future<AppUpdateState> checkForUpdate() async => AppUpdateState.unavailable;

  @override
  Future<void> downloadUpdate() async {}
}

const _addressOrigin = 'https://device.tailnet.ts.net';
const _addressInstance = '9e30af6d-422d-4d89-baad-006ac07cb9d1';
const _verifiedDeployment = SessionAddressDeployment(
  privateIngress: true,
  privateTransportEnforced: true,
  requesterIdentityOnEveryRequest: true,
  taggedPeerPolicyVerified: true,
  noPublicAlternateIngress: true,
  scopedSessionAuthorization: true,
  sessionIdsAreBearerCredentials: false,
);

SessionAddressLink _addressLink() => SessionAddressLink.require(
  'opencode-mobile://session/v2?server=${Uri.encodeQueryComponent(_addressOrigin)}'
  '&instance=$_addressInstance&session=ses_link',
);

class _AddressReader implements SessionAddressDescriptorReader {
  int calls = 0;
  @override
  Future<SessionAddressDescriptor> discover(String origin) async {
    calls++;
    return SessionAddressDescriptor.parse({
      'schemaVersion': 1,
      'canonicalOrigin': origin,
      'instanceId': _addressInstance,
      'linkVersions': [2],
      'capabilities': {'sessionLookupById': true},
    });
  }
}

class _AddressLookup implements SessionAddressLookupGateway {
  @override
  SessionAddressDeployment get deployment => _verifiedDeployment;
  @override
  String get origin => _addressOrigin;
  @override
  String get instanceId => _addressInstance;
  @override
  Future<Session> lookupAuthorizedSession(String id) async =>
      Session(id: id, directory: '/private/project');
}

final _active = ServerProfile(
  id: 'server-1',
  name: 'Local',
  baseUrl: 'http://localhost:4096',
);

/// Builds a controller with `server-1` active and connected through fakes.
/// [others] are additional saved servers; [apis] collects every transport a
/// later `connect` creates so a test can complete its health probe.
Future<ConnectionController> _controller({
  bool connected = true,
  List<ServerProfile> others = const [],
  List<_LinkApi>? apis,
}) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  final store = _MemoryProfileStore(
    prefs: preferences,
    saved: [_active, ...others],
  );
  await store.setActiveId(_active.id);
  final controller = ConnectionController(
    store,
    apiFactory: (profile) {
      final api = _LinkApi(baseUrl: profile.baseUrl);
      apis?.add(api);
      return api;
    },
    repositoryFactory: (_) => _LinkRepository(),
    eventStreamFactory:
        ({required api, required onEvent, required onStatus, onError}) =>
            _FakeEventStream(
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
            ),
  );
  if (connected) {
    controller
      ..api = _LinkApi()
      ..repository = _LinkRepository()
      ..version = '1.18.25'
      ..status = StreamStatus.connected;
  }
  return controller;
}

SessionLinkIntent _intent() {
  final intent = SessionLinkIntent(
    channel: const MethodChannel('oc/link-test'),
  );
  addTearDown(intent.dispose);
  return intent;
}

Widget _app(
  ConnectionController controller,
  SessionLinkIntent intent, {
  SessionAddressController? addresses,
}) => ProviderScope(
  overrides: [
    bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
    connProvider.overrideWithValue(controller),
    if (addresses != null) sessionAddressProvider.overrideWithValue(addresses),
  ],
  child: OcApp(updateService: _NoUpdateService(), sessionLinkIntent: intent),
);

Future<void> _drainNotices(WidgetTester tester) =>
    tester.pump(const Duration(seconds: 9));

/// The app's own status line (main.dart's notice, KitStatusLine keys
/// `kit-status-app:*`), which replaced the snack bars and the banner.
final _appNotice = find.byWidgetPredicate((widget) {
  final key = widget.key;
  return key is ValueKey && '${key.value}'.startsWith('kit-status-app:');
});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('a link for the active server opens that exact session', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    final api = controller.api! as _LinkApi;
    final intent = _intent();

    await tester.pumpWidget(_app(controller, intent));
    await tester.pumpAndSettle();
    expect(find.byType(ChatScreen), findsNothing);

    intent.pending.value = const SessionLink(
      profileID: 'server-1',
      sessionID: 'ses_link',
    );
    await tester.pumpAndSettle();

    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'ses_link');
    expect(chat.initialText, isEmpty);
    expect(chat.initialAttachments, isEmpty);
    expect(chat.discardIfUntouched, isFalse);
    expect(api.created, 0);
    expect(api.prompted, 0);
    expect(intent.pending.value, isNull);
    expect(find.byType(ServersScreen), findsNothing);
    expect(_appNotice, findsNothing);
    expect(controller.profile?.id, 'server-1');

    // Nothing re-fires the same link on later controller changes.
    await controller.refreshSessions();
    await tester.pumpAndSettle();
    expect(find.byType(ChatScreen), findsOneWidget);
    expect(api.created, 0);
    expect(api.prompted, 0);
  });

  testWidgets(
    'an unknown server asks to add it, and Add server opens the flow',
    (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final api = controller.api! as _LinkApi;
      final intent = _intent();

      await tester.pumpWidget(_app(controller, intent));
      await tester.pumpAndSettle();

      intent.pending.value = const SessionLink(
        profileID: 'someone-elses-phone',
        sessionID: 'ses_link',
      );
      await tester.pumpAndSettle();

      expect(find.byType(ChatScreen), findsNothing);
      expect(
        find.byKey(const Key('session-link-server-missing')),
        findsOneWidget,
      );
      expect(find.text('Add this server?'), findsOneWidget);
      expect(
        find.text(
          'The conversation is on a server this phone has not saved. Add it '
          'here, then scan the code again.',
        ),
        findsOneWidget,
      );
      expect(intent.pending.value, isNull);
      expect(api.created, 0);
      expect(api.prompted, 0);
      // The connection is untouched.
      expect(controller.status, StreamStatus.connected);
      expect(identical(controller.api, api), isTrue);

      await tester.tap(find.byKey(const Key('session-link-add-server')));
      await tester.pumpAndSettle();
      // Add server itself (P3.9), over Servers, not the list to find it on.
      expect(find.byType(ServersScreen, skipOffstage: false), findsOneWidget);
      expect(find.byKey(const ValueKey('server-kind-step')), findsOneWidget);
      expect(
        find.byKey(const Key('session-link-server-missing')),
        findsNothing,
      );
      expect(find.byType(ChatScreen), findsNothing);
    },
  );

  testWidgets('dismissing the unknown-server state leaves everything alone', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    final intent = _intent();

    await tester.pumpWidget(_app(controller, intent));
    await tester.pumpAndSettle();
    intent.pending.value = const SessionLink(
      profileID: 'unknown',
      sessionID: 'ses_link',
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const Key('session-link-server-missing')),
      findsOneWidget,
    );

    await tester.tap(find.text('Dismiss'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('session-link-server-missing')), findsNothing);
    expect(find.byType(ServersScreen), findsNothing);
    expect(find.byType(ChatScreen), findsNothing);
  });

  /// Cold-starts the shell the way production does: `_Root` connects the
  /// saved server once, so a later profile switch is not raced by it.
  Future<void> coldStart(
    WidgetTester tester,
    ConnectionController controller,
    SessionLinkIntent intent,
    List<_LinkApi> apis,
  ) async {
    await tester.pumpWidget(_app(controller, intent));
    await tester.pump();
    await tester.pump();
    expect(apis, hasLength(1));
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await tester.pumpAndSettle();
    expect(controller.status, StreamStatus.connected);
    expect(controller.profile?.id, 'server-1');
    apis.clear();
  }

  testWidgets('a link for another saved server switches to it, then opens', (
    tester,
  ) async {
    final other = ServerProfile(
      id: 'server-2',
      name: 'Desk',
      baseUrl: 'https://desk.example.test:4096',
    );
    final apis = <_LinkApi>[];
    final controller = await _controller(
      connected: false,
      others: [other],
      apis: apis,
    );
    addTearDown(controller.dispose);
    final intent = _intent();
    await coldStart(tester, controller, intent, apis);
    final original = controller.api! as _LinkApi;

    intent.pending.value = const SessionLink(
      profileID: 'server-2',
      sessionID: 'ses_desk',
    );
    await tester.pump();
    await tester.pump();
    // The server left behind stays in the list beside it (it runs on this
    // phone): only Desk's connection is the switch.
    final desk = apis
        .where((api) => api.baseUrl == 'https://desk.example.test:4096')
        .single;
    desk.healthResult.complete(Health(healthy: true, version: '1'));
    for (final api in apis) {
      if (!api.healthResult.isCompleted) {
        api.healthResult.complete(Health(healthy: true, version: '1'));
      }
    }
    await tester.pumpAndSettle();

    expect(controller.profile?.id, 'server-2');
    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'ses_desk');
    expect(chat.initialText, isEmpty);
    expect(original.created, 0);
    expect(original.prompted, 0);
    expect(desk.created, 0);
    expect(desk.prompted, 0);
    expect(intent.pending.value, isNull);
    expect(_appNotice, findsNothing);
    expect(find.byType(ServersScreen), findsNothing);
    // The live connection's polling fallback is a periodic timer; retire it
    // before the tree is torn down.
    controller.dispose();
  });

  testWidgets('a saved server whose connection fails routes to Servers', (
    tester,
  ) async {
    final broken = ServerProfile(
      id: 'server-2',
      name: 'Broken',
      baseUrl: 'desk:4096', // no scheme → synchronous validation error
    );
    final apis = <_LinkApi>[];
    final controller = await _controller(
      connected: false,
      others: [broken],
      apis: apis,
    );
    addTearDown(controller.dispose);
    final intent = _intent();
    await coldStart(tester, controller, intent, apis);

    intent.pending.value = const SessionLink(
      profileID: 'server-2',
      sessionID: 'ses_desk',
    );
    await tester.pumpAndSettle();

    expect(find.byType(ChatScreen), findsNothing);
    expect(find.byType(ServersScreen), findsOneWidget);
    // The connection line outranks the link's notice in the one status
    // slot (P4.4, 3d64653c), and says why with its way forward.
    expect(
      find.byKey(const ValueKey('connection-status-banner')),
      findsOneWidget,
    );
    expect(intent.pending.value, isNull);
    expect(apis, isEmpty);
    await _drainNotices(tester);
  });

  testWidgets('a saved server needing its password again routes to Servers', (
    tester,
  ) async {
    final locked = ServerProfile(
      id: 'server-2',
      name: 'Locked',
      baseUrl: 'https://desk.example.test:4096',
      requiresPasswordReentry: true,
    );
    final controller = await _controller(others: [locked]);
    addTearDown(controller.dispose);
    final api = controller.api! as _LinkApi;
    final intent = _intent();

    await tester.pumpWidget(_app(controller, intent));
    await tester.pumpAndSettle();

    intent.pending.value = const SessionLink(
      profileID: 'server-2',
      sessionID: 'ses_desk',
    );
    await tester.pumpAndSettle();

    expect(find.byType(ChatScreen), findsNothing);
    expect(find.byType(ServersScreen), findsOneWidget);
    expect(_appNotice, findsOneWidget);
    expect(intent.pending.value, isNull);
    expect(api.created, 0);
    expect(api.prompted, 0);
    // The active connection was not switched away.
    expect(controller.profile?.id, 'server-1');
    expect(identical(controller.api, api), isTrue);
    await _drainNotices(tester);
  });

  testWidgets('a malformed link never reaches routing', (tester) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    final api = controller.api! as _LinkApi;
    final intent = _intent();
    await tester.pumpWidget(_app(controller, intent));
    await tester.pumpAndSettle();

    // The bridge parses strictly; a bad payload leaves nothing pending.
    expect(
      SessionLink.parse('opencode-mobile://session?profile=server-1'),
      isNull,
    );
    expect(intent.pending.value, isNull);
    await tester.pumpAndSettle();

    expect(find.byType(ChatScreen), findsNothing);
    expect(find.byType(ServersScreen), findsNothing);
    expect(_appNotice, findsNothing);

    expect(api.created, 0);
    expect(api.prompted, 0);
  });

  testWidgets('an address link says plainly it is not available yet', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    final api = controller.api! as _LinkApi;
    final intent = _intent();
    await tester.pumpWidget(_app(controller, intent));
    await tester.pumpAndSettle();

    intent.pendingAddress.value = _addressLink();
    await tester.pumpAndSettle();

    expect(intent.pendingAddress.value, isNull);
    expect(find.byKey(const Key('session-address-sheet')), findsOneWidget);
    expect(
      find.text(
        'Conversation links with a server address are not available yet.',
      ),
      findsOneWidget,
    );
    expect(find.byKey(const Key('session-address-check')), findsNothing);
    expect(find.byType(ChatScreen), findsNothing);
    expect(find.byType(ServersScreen), findsNothing);
    expect(api.created, 0);
    expect(api.prompted, 0);
  });

  testWidgets('a link that failed to parse shows only its category', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    final intent = _intent();
    await tester.pumpWidget(_app(controller, intent));
    await tester.pumpAndSettle();

    intent.pendingAddressFailure.value = SessionAddressFailureCode.credentials;
    await tester.pumpAndSettle();

    expect(intent.pendingAddressFailure.value, isNull);
    expect(
      find.text(
        'This link contains private sign-in information and cannot be used.',
      ),
      findsOneWidget,
    );
    expect(find.byType(ChatScreen), findsNothing);
  });

  testWidgets('a verified address link opens the existing conversation', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    final api = controller.api! as _LinkApi;
    final intent = _intent();
    // The harness sees `server-1` at the link's address with a verified
    // binding; the app then navigates exactly as for a local link.
    final store = _MemoryProfileStore(
      prefs: controller.store.prefs,
      saved: [
        ServerProfile(id: 'server-1', name: 'Local', baseUrl: _addressOrigin),
      ],
    );
    await store.prefs.setString(
      'oc.profiles',
      jsonEncode([
        {'id': 'server-1', 'name': 'Local', 'baseUrl': _addressOrigin},
      ]),
    );
    await SessionLinkBindings.forProfile(store.prefs, 'server-1').save(
      SessionLinkBinding(
        origin: _addressOrigin,
        instanceId: _addressInstance,
        verifiedAt: DateTime.utc(2026, 9, 28),
      ),
    );
    final reader = _AddressReader();
    final addresses = SessionAddressController.verifiedTestHarness(
      store: store,
      descriptors: reader,
      deploymentForOrigin: (_) => _verifiedDeployment,
      lookupForProfile: (_) async => _AddressLookup(),
    );
    addTearDown(addresses.dispose);
    await tester.pumpWidget(_app(controller, intent, addresses: addresses));
    await tester.pumpAndSettle();

    intent.pendingAddress.value = _addressLink();
    await tester.pumpAndSettle();
    expect(find.text('Open on this saved server?'), findsOneWidget);
    expect(reader.calls, 0);

    await tester.tap(find.byKey(const Key('session-address-check')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('session-address-open')));
    await tester.pumpAndSettle();

    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'ses_link');
    expect(api.created, 0);
    expect(api.prompted, 0);
    expect(addresses.pending, isNull);
  });
}
