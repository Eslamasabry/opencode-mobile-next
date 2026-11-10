import 'support/fake_server_capabilities.dart';
// The chat's context ring: in the top bar left of the menu when the use is
// known, gone when it is not, compacting from the session's own signal, the
// details sheet with Compact now only where the server can compact, and the
// value left behind for the chats list.
import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/nudges.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart'
    show KitButton, KitContextBar, KitContextChartPainter;
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api({this.transcript = const [], ServerCapabilities? capabilities})
    : _capabilities = capabilities ?? testServerCapabilities,
      super(baseUrl: 'http://localhost');

  final List<MessageWithParts> transcript;
  final ServerCapabilities _capabilities;

  @override
  ServerCapabilities get capabilities => _capabilities;

  @override
  Future<List<MessageWithParts>> messages(String id) async => transcript;

  @override
  Future<List<FileDiff>> diff(String id, {String? messageID}) async => [];

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

class _Repository extends ProductRepository {
  final compacted = <String>[];

  @override
  Future<void> compactSession(
    String id, {
    required String providerID,
    required String modelID,
  }) async => compacted.add(id);

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  // Pins and approvals are scoped to a server profile.
  @override
  ServerProfile get profile =>
      ServerProfile(id: 'server-a', name: 'A', baseUrl: 'http://localhost');

  @override
  Future<FakeServerGateway?> prepareActionTransport() async =>
      api as FakeServerGateway?;

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;

  @override
  Future<void> refreshPendingPermissions() async {}

  @override
  Future<void> refreshPendingQuestions() async {}
}

Future<_Controller> _controller(
  _Api api, {
  bool firstReplySeen = true,
  _Repository? repository,
}) async {
  SharedPreferences.setMockInitialValues({
    if (firstReplySeen) NudgeRegistry.firstReplySeenKey: true,
  });
  final prefs = await SharedPreferences.getInstance();
  return _Controller(ProfileStore(prefs: prefs))
    ..api = api
    ..repository = repository ?? _Repository()
    ..status = StreamStatus.connected;
}

Future<void> _pumpChat(WidgetTester tester, ConnectionController controller) =>
    tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          theme: AppTheme.light(),
          home: const ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );

MessageWithParts _message(
  String id,
  String role,
  List<Part> parts, {
  Tokens? tokens,
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: role,
    providerID: 'p',
    modelID: 'm',
    tokens: tokens,
    time: MsgTime(created: 1, completed: 2),
  ),
  parts: parts,
);

const _catalog = CatalogSnapshot(
  providers: [CatalogProvider(id: 'p', name: 'Provider', enabled: true)],
  models: [
    CatalogModel(
      id: 'm',
      providerID: 'p',
      name: 'Model',
      enabled: true,
      status: 'active',
      contextLimit: 100000,
      outputLimit: 8192,
      reasoning: false,
      attachments: false,
      tools: false,
      variants: [],
    ),
  ],
  agents: [],
);

/// Replies that used [thousands] each, with a compaction between the
/// second and third.
List<MessageWithParts> _transcript(List<int> thousands) => [
  _message('user-0', 'user', [Part(type: 'text', text: 'go')]),
  for (var i = 0; i < thousands.length; i++)
    _message('assistant-$i', 'assistant', [
      Part(type: 'text', text: 'done $i'),
    ], tokens: Tokens(input: thousands[i] * 1000, output: 0)),
];

const _ring = Key('chat-context-ring');

Future<_Controller> _open(
  WidgetTester tester,
  List<int> thousands, {
  bool catalog = true,
  ServerCapabilities? capabilities,
  Session? session,
}) async {
  final controller = await _controller(
    _Api(transcript: _transcript(thousands), capabilities: capabilities),
  );
  addTearDown(controller.dispose);
  if (catalog) controller.catalog = _catalog;
  controller.selectedModel = ModelRef(providerID: 'p', modelID: 'm');
  if (session != null) controller.sessionsById[session.id] = session;
  await _pumpChat(tester, controller);
  await tester.pumpAndSettle();
  return controller;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => debugPlatformCapabilities = const PlatformCapabilities.android());
  tearDown(() => debugPlatformCapabilities = null);

  testWidgets('known use: the ring sits in the bar, left of the menu', (
    tester,
  ) async {
    await _open(tester, [20, 85]);
    expect(find.byKey(_ring), findsOneWidget);
    expect(find.text('85 %'), findsOneWidget);
    final ring = tester.getCenter(find.byKey(_ring));
    final menu = tester.getCenter(
      find.byKey(const ValueKey('session-actions-button')),
    );
    expect(ring.dx, lessThan(menu.dx));
    expect((ring.dy - menu.dy).abs(), lessThan(8));
  });

  testWidgets('the model chip is back to just the model', (tester) async {
    await _open(tester, [20, 96]);
    expect(find.byKey(const Key('composer-model-context')), findsOneWidget);
    expect(find.text('· 96 %'), findsNothing);
    expect(find.text('Context almost full'), findsNothing);
    // The percent shows once: in the ring.
    expect(find.text('96 %'), findsOneWidget);
  });

  testWidgets('under half used the bar stays quiet: no ring at 4 %', (
    tester,
  ) async {
    await _open(tester, [4]);
    expect(find.byKey(_ring), findsNothing);
    expect(find.text('4 %'), findsNothing);
  });

  testWidgets('from half used the ring and its percent show: 60 %', (
    tester,
  ) async {
    await _open(tester, [60]);
    expect(find.byKey(_ring), findsOneWidget);
  });

  testWidgets('unknown use: no ring', (tester) async {
    await _open(tester, [20, 85], catalog: false);
    expect(find.byKey(_ring), findsNothing);
  });

  testWidgets('a conversation with no reply yet has no ring', (tester) async {
    await _open(tester, const []);
    expect(find.byKey(_ring), findsNothing);
  });

  testWidgets('a compaction the server is running turns the ring', (
    tester,
  ) async {
    await _open(tester, [
      20,
      85,
    ], session: Session(id: 'session-1', compactingSince: DateTime(2026)));
    expect(find.text('Compacting'), findsOneWidget);
    expect(find.text('85 %'), findsNothing);
  });

  testWidgets('leaves the value behind for the chats list', (tester) async {
    final controller = await _open(tester, [20, 85]);
    expect(controller.contextUsageMemory!.usageOf('session-1'), 0.85);
  });

  group('the details sheet', () {
    testWidgets('numbers, rows and one chart bar per reply', (tester) async {
      await _open(tester, [20, 60, 85]);
      await tester.tap(find.byKey(_ring));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('context-details-sheet')), findsOneWidget);
      expect(find.text('Context window'), findsWidgets);
      expect(find.text('85,000 of 100,000 tokens'), findsOneWidget);
      expect(find.text('Model'), findsWidgets);
      expect(find.text('After the last reply'), findsOneWidget);
      final chart = tester
          .widgetList<CustomPaint>(find.byType(CustomPaint))
          .map((paint) => paint.painter)
          .whereType<KitContextChartPainter>()
          .single;
      expect(chart.bars, hasLength(3));
      expect(chart.bars.map((KitContextBar bar) => bar.fraction), [
        0.2,
        0.6,
        0.85,
      ]);
    });

    testWidgets('Compact now asks first, then compacts', (tester) async {
      final controller = await _open(tester, [20, 85]);
      final repository = controller.repository as _Repository;
      await tester.tap(find.byKey(_ring));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('context-compact-now')));
      await tester.pumpAndSettle();
      expect(find.text('Compact this conversation?'), findsOneWidget);
      expect(repository.compacted, isEmpty);
      await tester.tap(
        find.widgetWithText(KitButton, 'Compact conversation').last,
      );
      await tester.pumpAndSettle();
      expect(repository.compacted, ['session-1']);
    });

    testWidgets('no Compact now where the server cannot compact', (
      tester,
    ) async {
      await _open(tester, [
        20,
        85,
      ], capabilities: const ServerCapabilities(sessionCompact: false));
      await tester.tap(find.byKey(_ring));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('context-details-sheet')), findsOneWidget);
      expect(find.byKey(const Key('context-compact-now')), findsNothing);
    });
  });
}
