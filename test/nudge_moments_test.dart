// The five one-time nudges at their real moments (UX plan 5.8 item 4): the
// card appears at the trigger, its action opens the door that already
// exists, closing removes it, and it never comes back.
import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/first_run.dart';
import 'package:opencode_mobile/state/nudges.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart'
    show KitAction, KitButton, KitNotice;
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api({this.transcript = const [], ServerCapabilities? capabilities})
    : _capabilities = capabilities ?? ServerCapabilities.allV1,
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
  Future<OpenCodeApi?> prepareActionTransport() async => api as OpenCodeApi?;

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

/// A busy conversation animates forever, so settle by hand.
Future<void> _frames(WidgetTester tester) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

EventEnvelope _asked(String id, String permission) => EventEnvelope(
  type: 'permission.asked',
  properties: {
    'id': id,
    'sessionID': 'session-1',
    'permission': permission,
    'patterns': ['lib/main.dart'],
    'metadata': <String, Object?>{},
    'always': <String>[],
  },
);

EventEnvelope _replied(String id) => EventEnvelope(
  type: 'permission.replied',
  properties: {'sessionID': 'session-1', 'requestID': id},
);

EventEnvelope _status(String status) => EventEnvelope(
  type: 'session.status',
  properties: {
    'sessionID': 'session-1',
    'status': {'type': status},
  },
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

final _editedRun = [
  _message('user-1', 'user', [Part(type: 'text', text: 'fix it')]),
  _message('assistant-1', 'assistant', [
    Part(
      type: 'tool',
      toolName: 'edit',
      callID: 'call-1',
      toolState: ToolState(status: 'completed'),
    ),
    Part(type: 'text', text: 'done'),
  ]),
];

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

ValueKey<String> _nudgeKey(NudgeId id) =>
    ValueKey('nudge-${id == NudgeId.pinConversations ? id.name : id.wire}');
Finder _nudge(NudgeId id) => find.byKey(_nudgeKey(id));
Finder _action(NudgeId id) => id == NudgeId.pinConversations
    ? find.descendant(of: _nudge(id), matching: find.text('Got it'))
    : find.byKey(ValueKey('nudge-${id.wire}-action'));
Finder _dismiss(NudgeId id) => id == NudgeId.pinConversations
    ? find.descendant(of: _nudge(id), matching: find.byTooltip('Hide tip'))
    : find.byKey(ValueKey('nudge-${id.wire}-dismiss'));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => debugPlatformCapabilities = const PlatformCapabilities.android());
  tearDown(() => debugPlatformCapabilities = null);

  group('approvals', () {
    Future<void> askAndAnswer(
      WidgetTester tester,
      ConnectionController controller,
      String id,
      String kind,
    ) async {
      controller.handleEventForTesting(_asked(id, kind));
      await tester.pumpAndSettle();
      controller.handleEventForTesting(_replied(id));
      await tester.pumpAndSettle();
    }

    testWidgets('the third request of one kind points at Approvals', (
      tester,
    ) async {
      final controller = await _controller(_Api());
      addTearDown(controller.dispose);
      await _pumpChat(tester, controller);
      await tester.pumpAndSettle();

      await askAndAnswer(tester, controller, 'r1', 'edit');
      await askAndAnswer(tester, controller, 'r2', 'bash');
      await askAndAnswer(tester, controller, 'r3', 'edit');
      expect(_nudge(NudgeId.approvals), findsNothing);

      controller.handleEventForTesting(_asked('r4', 'edit'));
      await tester.pumpAndSettle();
      // The request card has the space to itself.
      expect(find.byKey(const Key('permission-card-review')), findsOneWidget);
      expect(_nudge(NudgeId.approvals), findsNothing);
      controller.handleEventForTesting(_replied('r4'));
      await tester.pumpAndSettle();

      expect(_nudge(NudgeId.approvals), findsOneWidget);
      expect(
        find.text(
          'Asked for “Edit a file” 3 times: this conversation can approve '
          'requests for you.',
        ),
        findsOneWidget,
      );

      await tester.tap(_action(NudgeId.approvals));
      await tester.pumpAndSettle();
      expect(find.byType(SessionApprovalsSheet), findsOneWidget);
      expect(find.byKey(const Key('approvals-mode-ask')), findsOneWidget);
      expect(_nudge(NudgeId.approvals), findsNothing);

      // Closing the sheet and meeting a fourth request does not bring it back.
      Navigator.of(tester.element(find.byType(SessionApprovalsSheet))).pop();
      await tester.pumpAndSettle();
      await askAndAnswer(tester, controller, 'r5', 'edit');
      expect(_nudge(NudgeId.approvals), findsNothing);
      expect(controller.nudges.record(NudgeId.approvals)?.dismissed, isTrue);
    });

    testWidgets('closing it removes it for good', (tester) async {
      final controller = await _controller(_Api());
      addTearDown(controller.dispose);
      await _pumpChat(tester, controller);
      await tester.pumpAndSettle();
      for (final id in ['r1', 'r2', 'r3']) {
        await askAndAnswer(tester, controller, id, 'bash');
      }
      expect(_nudge(NudgeId.approvals), findsOneWidget);
      expect(find.byTooltip('Hide tip'), findsOneWidget);

      await tester.tap(_dismiss(NudgeId.approvals));
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.approvals), findsNothing);
      expect(find.byType(SessionApprovalsSheet), findsNothing);

      await askAndAnswer(tester, controller, 'r4', 'bash');
      expect(_nudge(NudgeId.approvals), findsNothing);
    });

    testWidgets('never during first run before the first reply', (
      tester,
    ) async {
      final controller = await _controller(_Api(), firstReplySeen: false);
      addTearDown(controller.dispose);
      await _pumpChat(tester, controller);
      await tester.pumpAndSettle();
      for (final id in ['r1', 'r2', 'r3']) {
        await askAndAnswer(tester, controller, id, 'bash');
      }
      expect(_nudge(NudgeId.approvals), findsNothing);
      expect(controller.nudges.wasShown(NudgeId.approvals), isFalse);
    });
  });

  group('review what changed', () {
    testWidgets(
      'tips wait while first run still owes its notification question',
      (tester) async {
        final controller = await _controller(_Api(transcript: _editedRun));
        addTearDown(controller.dispose);
        await controller.store.prefs.setString(
          FirstRun.notifyAskKey,
          'pending',
        );
        await _pumpChat(tester, controller);
        await tester.pumpAndSettle();
        controller.handleEventForTesting(_status('busy'));
        await _frames(tester);
        controller.handleEventForTesting(_status('idle'));
        await tester.pumpAndSettle();
        // One question at a time: the tip is neither shown nor used up.
        expect(_nudge(NudgeId.reviewChanges), findsNothing);

        await FirstRun(controller.store.prefs).answerNotifyAsk();
        controller.handleEventForTesting(_status('busy'));
        await _frames(tester);
        controller.handleEventForTesting(_status('idle'));
        await tester.pumpAndSettle();
        expect(_nudge(NudgeId.reviewChanges), findsOneWidget);
      },
    );

    testWidgets('a finished run that edited files opens the review', (
      tester,
    ) async {
      final controller = await _controller(_Api(transcript: _editedRun));
      addTearDown(controller.dispose);
      await _pumpChat(tester, controller);
      await tester.pumpAndSettle();
      // Opening a finished conversation is not the moment.
      expect(_nudge(NudgeId.reviewChanges), findsNothing);

      controller.handleEventForTesting(_status('busy'));
      await _frames(tester);
      controller.handleEventForTesting(_status('idle'));
      await tester.pumpAndSettle();

      expect(_nudge(NudgeId.reviewChanges), findsOneWidget);
      expect(find.widgetWithText(TextButton, 'Review changes'), findsOneWidget);
      await tester.tap(_action(NudgeId.reviewChanges));
      await tester.pumpAndSettle();
      expect(find.byType(ReviewWorkspace), findsOneWidget);

      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.reviewChanges), findsNothing);

      // The next run with changes stays quiet: once only.
      controller.handleEventForTesting(_status('busy'));
      await _frames(tester);
      controller.handleEventForTesting(_status('idle'));
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.reviewChanges), findsNothing);
    });

    testWidgets('closing it removes it', (tester) async {
      final controller = await _controller(_Api(transcript: _editedRun));
      addTearDown(controller.dispose);
      await _pumpChat(tester, controller);
      await tester.pumpAndSettle();
      controller.handleEventForTesting(_status('busy'));
      await _frames(tester);
      controller.handleEventForTesting(_status('idle'));
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.reviewChanges), findsOneWidget);

      await tester.tap(_dismiss(NudgeId.reviewChanges));
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.reviewChanges), findsNothing);
      expect(find.byType(ReviewWorkspace), findsNothing);
    });

    testWidgets('absent where the server has no review', (tester) async {
      final controller = await _controller(
        _Api(
          transcript: _editedRun,
          capabilities: const ServerCapabilities(sessionDiff: false),
        ),
      );
      addTearDown(controller.dispose);
      await _pumpChat(tester, controller);
      await tester.pumpAndSettle();
      controller.handleEventForTesting(_status('busy'));
      await _frames(tester);
      controller.handleEventForTesting(_status('idle'));
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.reviewChanges), findsNothing);
      expect(controller.nudges.wasShown(NudgeId.reviewChanges), isFalse);
    });
  });

  group('leave and be told', () {
    testWidgets('a minute of waiting with notifications on', (tester) async {
      final controller = await _controller(_Api());
      addTearDown(controller.dispose);
      controller.backgroundLive
        ..enabled = true
        ..notificationGranted = true;
      expect(controller.finishedRunNotificationsReady, isTrue);
      await _pumpChat(tester, controller);
      await tester.pumpAndSettle();

      controller.handleEventForTesting(_status('busy'));
      await _frames(tester);
      await tester.pump(const Duration(seconds: 50));
      expect(_nudge(NudgeId.leaveAndBeTold), findsNothing);

      await tester.pump(const Duration(seconds: 10));
      await _frames(tester);
      expect(_nudge(NudgeId.leaveAndBeTold), findsOneWidget);
      expect(
        find.text('You can leave: this phone tells you when the run is done.'),
        findsOneWidget,
      );

      await tester.tap(_action(NudgeId.leaveAndBeTold));
      await _frames(tester);
      expect(_nudge(NudgeId.leaveAndBeTold), findsNothing);
      expect(
        controller.nudges.record(NudgeId.leaveAndBeTold)?.dismissed,
        isTrue,
      );

      // Another long wait: once only.
      await tester.pump(const Duration(seconds: 120));
      await _frames(tester);
      expect(_nudge(NudgeId.leaveAndBeTold), findsNothing);
      controller.handleEventForTesting(_status('idle'));
      await tester.pumpAndSettle();
    });

    testWidgets('closing it removes it', (tester) async {
      final controller = await _controller(_Api());
      addTearDown(controller.dispose);
      controller.backgroundLive
        ..enabled = true
        ..notificationGranted = true;
      await _pumpChat(tester, controller);
      await tester.pumpAndSettle();
      controller.handleEventForTesting(_status('busy'));
      await _frames(tester);
      await tester.pump(const Duration(seconds: 61));
      await _frames(tester);
      expect(_nudge(NudgeId.leaveAndBeTold), findsOneWidget);

      await tester.tap(_dismiss(NudgeId.leaveAndBeTold));
      await _frames(tester);
      expect(_nudge(NudgeId.leaveAndBeTold), findsNothing);
      controller.handleEventForTesting(_status('idle'));
      await tester.pumpAndSettle();
    });

    testWidgets('never when Android has not granted notifications', (
      tester,
    ) async {
      final controller = await _controller(_Api());
      addTearDown(controller.dispose);
      controller.backgroundLive.enabled = true;
      expect(controller.finishedRunNotificationsReady, isFalse);
      await _pumpChat(tester, controller);
      await tester.pumpAndSettle();
      controller.handleEventForTesting(_status('busy'));
      await _frames(tester);
      await tester.pump(const Duration(seconds: 90));
      await _frames(tester);
      expect(_nudge(NudgeId.leaveAndBeTold), findsNothing);
      expect(controller.nudges.wasShown(NudgeId.leaveAndBeTold), isFalse);
      controller.handleEventForTesting(_status('idle'));
      await tester.pumpAndSettle();
    });
  });

  group('compact', () {
    const catalog = _catalog;
    List<MessageWithParts> transcript(int thousands) => [
      _message('user-1', 'user', [Part(type: 'text', text: 'go')]),
      _message('assistant-1', 'assistant', [
        Part(type: 'text', text: 'done'),
      ], tokens: Tokens(input: thousands * 1000, output: 0)),
    ];

    testWidgets('above 80 percent the card compacts the conversation', (
      tester,
    ) async {
      final repository = _Repository();
      final controller = await _controller(
        _Api(transcript: transcript(85)),
        repository: repository,
      );
      addTearDown(controller.dispose);
      controller
        ..catalog = catalog
        ..selectedModel = ModelRef(providerID: 'p', modelID: 'm');
      await _pumpChat(tester, controller);
      await tester.pumpAndSettle();

      expect(_nudge(NudgeId.compact), findsOneWidget);
      expect(
        find.text('The context is 85% full: compact to keep going.'),
        findsOneWidget,
      );
      await tester.tap(_action(NudgeId.compact));
      await tester.pumpAndSettle();
      // Asked first: nothing is compacted until the person confirms.
      expect(find.text('Compact this conversation?'), findsOneWidget);
      expect(repository.compacted, isEmpty);
      await tester.tap(find.widgetWithText(KitButton, 'Compact conversation'));
      await tester.pumpAndSettle();
      expect(repository.compacted, ['session-1']);
      expect(_nudge(NudgeId.compact), findsNothing);

      // Reopening the still-full conversation does not repeat the tip.
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
      await _pumpChat(tester, controller);
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.compact), findsNothing);
    });

    testWidgets('closing it removes it without compacting', (tester) async {
      final repository = _Repository();
      final controller = await _controller(
        _Api(transcript: transcript(90)),
        repository: repository,
      );
      addTearDown(controller.dispose);
      controller.catalog = catalog;
      await _pumpChat(tester, controller);
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.compact), findsOneWidget);
      await tester.tap(_dismiss(NudgeId.compact));
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.compact), findsNothing);
      expect(repository.compacted, isEmpty);
    });

    testWidgets('quiet below the threshold and where compacting is absent', (
      tester,
    ) async {
      final below = await _controller(_Api(transcript: transcript(75)));
      addTearDown(below.dispose);
      below.catalog = catalog;
      await _pumpChat(tester, below);
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.compact), findsNothing);

      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
      final unsupported = await _controller(
        _Api(
          transcript: transcript(95),
          capabilities: const ServerCapabilities(sessionCompact: false),
        ),
      );
      addTearDown(unsupported.dispose);
      unsupported.catalog = catalog;
      await _pumpChat(tester, unsupported);
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.compact), findsNothing);
      expect(unsupported.nudges.wasShown(NudgeId.compact), isFalse);
    });
  });

  group('pin conversations', () {
    Session session(String id, String directory) => Session(
      id: id,
      title: id,
      directory: directory,
      time: SessionTime(created: 1, updated: 2),
    );

    Widget work(ConnectionController controller) => MaterialApp(
      theme: AppTheme.light(),
      home: Scaffold(body: WorkspaceScreen(controller: controller)),
    );

    testWidgets('the second project used brings the tip to Work', (
      tester,
    ) async {
      final controller = await _controller(_Api());
      addTearDown(controller.dispose);
      controller
        ..directory = '/work/app'
        ..sessionsById = {
          'one': session('one', '/work/app'),
          'two': session('two', '/work/site'),
        };
      await tester.pumpWidget(work(controller));
      await tester.pumpAndSettle();
      expect(controller.nudges.projectsUsed, 1);
      expect(_nudge(NudgeId.pinConversations), findsNothing);

      controller.directory = '/work/site';
      await tester.pumpWidget(work(controller));
      await tester.pumpAndSettle();
      expect(controller.nudges.projectsUsed, 2);
      expect(_nudge(NudgeId.pinConversations), findsOneWidget);
      expect(
        find.text(
          'Pin conversations you return to from their menu; they stay at the '
          'top of Work.',
        ),
        findsOneWidget,
      );

      await tester.tap(_dismiss(NudgeId.pinConversations));
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.pinConversations), findsNothing);

      controller.directory = '/work/app';
      await tester.pumpWidget(work(controller));
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.pinConversations), findsNothing);
    });

    testWidgets('Got it closes it too', (tester) async {
      final controller = await _controller(_Api());
      addTearDown(controller.dispose);
      await controller.nudges.noteProjectUsed(
        profileID: 'server-a',
        directory: '/work/earlier',
      );
      controller
        ..directory = '/work/app'
        ..sessionsById = {'one': session('one', '/work/app')};
      await tester.pumpWidget(work(controller));
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.pinConversations), findsOneWidget);
      await tester.tap(_action(NudgeId.pinConversations));
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.pinConversations), findsNothing);
      expect(
        controller.nudges.record(NudgeId.pinConversations)?.dismissed,
        isTrue,
      );
    });

    testWidgets('someone who already pins here is not told', (tester) async {
      final controller = await _controller(_Api());
      addTearDown(controller.dispose);
      await controller.nudges.noteProjectUsed(
        profileID: 'server-a',
        directory: '/work/earlier',
      );
      controller
        ..directory = '/work/app'
        ..sessionsById = {
          'one': session('one', '/work/app'),
          'two': session('two', '/work/app'),
        };
      await controller.setSessionPinned(
        'one',
        true,
        locationRevision: controller.locationRevision,
      );
      await tester.pumpWidget(work(controller));
      await tester.pumpAndSettle();
      expect(_nudge(NudgeId.pinConversations), findsNothing);
      expect(controller.nudges.wasShown(NudgeId.pinConversations), isFalse);
    });
  });

  group('card layout', () {
    for (final locale in const [Locale('en'), Locale('ar')]) {
      testWidgets('320 dp, text scale 2.5, ${locale.languageCode}', (
        tester,
      ) async {
        tester.view.physicalSize = const Size(320, 760);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final strings = lookupAppLocalizations(locale);
        var actions = 0;
        var dismissals = 0;
        await tester.pumpWidget(
          MaterialApp(
            theme: AppTheme.light(),
            locale: locale,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                textScaler: const TextScaler.linear(AppTheme.maxTextScale),
              ),
              child: child!,
            ),
            home: Scaffold(
              body: SingleChildScrollView(
                // The offer as the chat's nudge slot draws it.
                child: KitNotice.offer(
                  key: _nudgeKey(NudgeId.approvals),
                  // The longest sentence and the longest action label.
                  message: strings.nudgeApprovals(strings.e7PermissionAction4),
                  icon: AppIconography.idea,
                  action: KitAction(
                    key: ValueKey('nudge-${NudgeId.approvals.wire}-action'),
                    label: strings.chatUiCompactSession,
                    onPressed: () => actions += 1,
                  ),
                  onDismiss: () => dismissals += 1,
                  dismissKey: ValueKey(
                    'nudge-${NudgeId.approvals.wire}-dismiss',
                  ),
                  dismissLabel: strings.nudgeDismiss,
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);

        final card = tester.getRect(_nudge(NudgeId.approvals));
        expect(card.left, greaterThanOrEqualTo(0));
        expect(card.right, lessThanOrEqualTo(320));
        for (final control in [
          _action(NudgeId.approvals),
          _dismiss(NudgeId.approvals),
        ]) {
          final rect = tester.getRect(control);
          expect(rect.left, greaterThanOrEqualTo(0));
          expect(rect.right, lessThanOrEqualTo(320));
          expect(rect.height, greaterThanOrEqualTo(48));
          expect(rect.width, greaterThanOrEqualTo(48));
        }
        // The close button sits at the trailing edge in both directions.
        final close = tester.getCenter(_dismiss(NudgeId.approvals));
        expect(
          locale.languageCode == 'ar' ? close.dx < 160 : close.dx > 160,
          isTrue,
        );
        // The whole sentence is laid out, not truncated.
        final sentence = tester.widget<Text>(
          find.text(strings.nudgeApprovals(strings.e7PermissionAction4)),
        );
        expect(sentence.maxLines, isNull);
        expect(sentence.overflow, isNull);

        // R6 places the close beside the first line and the action below
        // a wrapped sentence. Each remains reachable in the scrollable slot.
        expect(
          tester.getCenter(_action(NudgeId.approvals)).dy,
          greaterThan(tester.getCenter(_dismiss(NudgeId.approvals)).dy),
        );
        await tester.ensureVisible(_action(NudgeId.approvals));
        await tester.pumpAndSettle();
        await tester.tap(_action(NudgeId.approvals));
        await tester.ensureVisible(_dismiss(NudgeId.approvals));
        await tester.pumpAndSettle();
        await tester.tap(_dismiss(NudgeId.approvals));
        expect((actions, dismissals), (1, 1));
      });

      testWidgets('above the composer at 320 dp, 2.5, ${locale.languageCode}', (
        tester,
      ) async {
        tester.view.physicalSize = const Size(320, 760);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final repository = _Repository();
        final controller = await _controller(
          _Api(
            transcript: [
              _message('user-1', 'user', [Part(type: 'text', text: 'go')]),
              _message('assistant-1', 'assistant', [
                Part(type: 'text', text: 'done'),
              ], tokens: Tokens(input: 85000, output: 0)),
            ],
          ),
          repository: repository,
        );
        addTearDown(controller.dispose);
        controller
          ..catalog = _catalog
          ..selectedModel = ModelRef(providerID: 'p', modelID: 'm');
        await tester.pumpWidget(
          ProviderScope(
            overrides: [connProvider.overrideWithValue(controller)],
            child: MaterialApp(
              theme: AppTheme.light(),
              locale: locale,
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(context).copyWith(
                  textScaler: const TextScaler.linear(AppTheme.maxTextScale),
                ),
                child: child!,
              ),
              home: const ChatScreen(sessionID: 'session-1'),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        expect(_nudge(NudgeId.compact), findsOneWidget);
        // The slot ends on the action. R6 keeps the close beside the first
        // line, so large text may need scrolling to reach it.
        expect(_action(NudgeId.compact).hitTestable(), findsOneWidget);
        await tester.ensureVisible(_dismiss(NudgeId.compact));
        await tester.pumpAndSettle();
        expect(_dismiss(NudgeId.compact).hitTestable(), findsOneWidget);
        await tester.ensureVisible(_action(NudgeId.compact));
        await tester.pumpAndSettle();
        expect(_action(NudgeId.compact).hitTestable(), findsOneWidget);
        await tester.tap(_action(NudgeId.compact));
        await tester.pumpAndSettle();
        // Compact asks first: nothing happens until the person confirms.
        expect(repository.compacted, isEmpty);
        // The nudge steps aside for the sheet, which scrolls at large text.
        final confirm = find.widgetWithText(
          KitButton,
          lookupAppLocalizations(locale).chatUiCompactConfirmAction,
        );
        await tester.ensureVisible(confirm);
        await tester.pumpAndSettle();
        await tester.tap(confirm);
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        expect(repository.compacted, ['session-1']);
        expect(_nudge(NudgeId.compact), findsNothing);
      });
    }
  });
}
