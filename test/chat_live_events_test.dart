import 'support/complete_message_history.dart';

import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart'
    show
        KitBidi,
        KitCodeBlock,
        KitDiffView,
        KitMarkdown,
        KitMessage,
        KitMotion,
        KitSkeletonTranscript,
        KitStateView,
        KitTurn,
        KitZoom,
        KitUndo;
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/global_sessions_screen.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/screens/project_health_screen.dart';
import 'package:opencode_mobile/ui/screens/session_context_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/tools_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/state/prompt_photos.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

class _FakeOpenCodeApi extends OpenCodeApi with CompleteMessageHistory {
  _FakeOpenCodeApi() : super(baseUrl: 'http://localhost');

  Future<List<MessageWithParts>> Function(String id)? messagesHandler;
  Future<ServerPage<MessageWithParts>> Function(String? cursor)? pageHandler;
  final pageCursors = <String?>[];

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) {
    if (pageHandler == null) {
      return super.messagePage(id, cursor: cursor, limit: limit);
    }
    pageCursors.add(cursor);
    return pageHandler!(cursor);
  }

  Completer<void>? promptCompleter;
  int promptCalls = 0;
  final List<
    ({
      String text,
      ModelRef? model,
      String? variant,
      List<PromptAttachment> attachments,
      List<PromptAgentMention> agentMentions,
    })
  >
  prompts = [];
  String? slashCommandName;
  String? slashArguments;
  ModelRef? slashModel;
  String? slashVariant;
  int abortCalls = 0;
  Object? abortError;
  int createCalls = 0;
  final List<({String id, String title})> renameCalls = [];
  final List<String> deleteCalls = [];
  List<FileDiff> diffs = const [];
  List<Todo> todoItems = const [];
  Object? todosError;
  final Map<String, FileContent> fileContents = {};
  final List<String> fileContentRequests = [];
  List<FileNode> projectFiles = const [];
  Session? sessionResult;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) =>
      messagesHandler?.call(id) ?? Future.value([]);

  @override
  Future<Session> session(String id) async => sessionResult ?? Session(id: id);

  @override
  Future<Session> createSession() async {
    createCalls += 1;
    return Session(id: 'session-created');
  }

  @override
  Future<List<FileDiff>> diff(String id) async => diffs;

  @override
  Future<List<Todo>> todos(String id) async {
    final error = todosError;
    if (error != null) throw error;
    return todoItems;
  }

  @override
  Future<List<FileNode>> listFiles([String path = '']) async =>
      path.isEmpty ? projectFiles : const [];

  @override
  Future<FileContent> fileContent(String path) async {
    fileContentRequests.add(path);
    final content = fileContents[path];
    if (content == null) throw StateError('missing fixture: $path');
    return content;
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
  }) {
    promptCalls += 1;
    prompts.add((
      text: text,
      model: model,
      variant: variant,
      attachments: attachments,
      agentMentions: agentMentions,
    ));
    return promptCompleter?.future ?? Future.value();
  }

  @override
  Future<void> slashCommand(
    String sessionID,
    String command,
    String args, {
    ModelRef? model,
    String? variant,
  }) async {
    slashCommandName = command;
    slashArguments = args;
    slashModel = model;
    slashVariant = variant;
  }

  @override
  Future<void> abort(String sessionID) async {
    abortCalls += 1;
    final error = abortError;
    if (error != null) throw error;
  }

  @override
  Future<void> renameSession(String id, String title) async {
    renameCalls.add((id: id, title: title));
  }

  @override
  Future<void> deleteSession(String id) async {
    deleteCalls.add(id);
  }
}

class _FakeProductRepository implements ProductRepository {
  _FakeProductRepository(this.commands, {this.references = const []});

  final List<CommandInfo> commands;
  final List<ReferenceInfo> references;
  String? forkMessageID;
  int forkCalls = 0;

  @override
  Future<List<CommandInfo>> listCommands() async => commands;

  @override
  Future<List<ReferenceInfo>> listReferences() async => references;

  @override
  Future<String> forkSession(String id, {String? messageID}) async {
    forkCalls += 1;
    forkMessageID = messageID;
    return 'forked-session';
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _DestinationRepository extends _FakeProductRepository {
  _DestinationRepository({this.healthError = false}) : super(const []);

  final bool healthError;

  String? movedDirectory;
  bool? movedChanges;
  String? warpedWorkspaceID;
  bool? copiedChanges;
  ConsoleOrganization? switchedOrganization;
  String? reminderDirectory;

  @override
  Future<Session> getSessionDetails(String id) async => Session(
    id: id,
    title: 'Mobile work',
    projectID: 'project-1',
    directory: movedDirectory ?? '/work/acme',
    workspaceID: warpedWorkspaceID ?? 'workspace-1',
  );

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [
    WorkspaceProject(
      id: 'project-1',
      name: 'Acme',
      directory: '/work/acme',
      worktrees: ['/work/acme-copy'],
      updatedAt: 1,
    ),
  ];

  @override
  Future<List<ProjectDirectoryInfo>> listProjectDirectories(
    String projectID,
  ) async => const [
    ProjectDirectoryInfo(directory: '/work/acme'),
    ProjectDirectoryInfo(
      directory: '/work/acme-copy',
      strategy: 'git_worktree',
    ),
  ];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [
    WorkspaceInfo(
      id: 'workspace-1',
      projectID: 'project-1',
      name: 'Current cloud',
      type: 'cloud',
      directory: '/remote/current',
      status: 'connected',
    ),
    WorkspaceInfo(
      id: 'workspace-2',
      projectID: 'project-1',
      name: 'Review cloud',
      type: 'cloud',
      directory: '/remote/review',
      status: 'connected',
    ),
    WorkspaceInfo(
      id: 'workspace-offline',
      projectID: 'project-1',
      name: 'Offline cloud',
      type: 'cloud',
      status: 'disconnected',
    ),
  ];

  @override
  Future<VersionControlHealth> loadVersionControlHealth() async {
    if (healthError) throw StateError('VCS status unavailable');
    return const VersionControlHealth(
      branch: 'feature/mobile',
      changes: [
        VersionControlFile(
          path: 'lib/main.dart',
          status: 'modified',
          additions: 1,
          deletions: 1,
        ),
      ],
    );
  }

  @override
  Future<List<LanguageServiceHealth>> listLanguageServices() async => const [];

  @override
  Future<List<FormatterHealth>> listFormatters() async => const [];

  @override
  Future<void> moveSession(
    String sessionID, {
    required String directory,
    required bool moveChanges,
  }) async {
    movedDirectory = directory;
    movedChanges = moveChanges;
  }

  @override
  Future<void> warpSession(
    String sessionID, {
    required String? workspaceID,
    required bool copyChanges,
  }) async {
    warpedWorkspaceID = workspaceID;
    copiedChanges = copyChanges;
  }

  @override
  Future<List<ConsoleOrganization>> listConsoleOrganizations() async => const [
    ConsoleOrganization(
      accountID: 'account-1',
      accountEmail: 'dev@example.com',
      accountUrl: 'https://console.example.com',
      orgID: 'org-current',
      orgName: 'Current org',
      active: true,
    ),
    ConsoleOrganization(
      accountID: 'account-1',
      accountEmail: 'dev@example.com',
      accountUrl: 'https://console.example.com',
      orgID: 'org-next',
      orgName: 'Next org',
      active: false,
    ),
  ];

  @override
  Future<void> switchConsoleOrganization(
    ConsoleOrganization organization,
  ) async {
    switchedOrganization = organization;
  }

  @override
  Future<void> addSessionLocationReminder(
    String sessionID,
    String directory,
  ) async {
    reminderDirectory = directory;
  }

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);
}

class _RevertProductRepository extends _FakeProductRepository
    implements StagedRevertGateway {
  _RevertProductRepository() : super(const []);
}

class _MessageDeleteRepository extends _FakeProductRepository {
  _MessageDeleteRepository(this.serverMessages) : super(const []);

  final List<MessageWithParts> serverMessages;
  final List<(String, String)> deleted = [];
  Object? failure;

  @override
  Future<void> deleteMessage({
    required String sessionID,
    required String messageID,
  }) async {
    if (failure case final error?) throw error;
    deleted.add((sessionID, messageID));
    serverMessages.removeWhere((message) => message.info.id == messageID);
  }
}

class _RelationsProductRepository extends _FakeProductRepository {
  _RelationsProductRepository(this.parent, this.children) : super(const []);

  final Session parent;
  final List<Session> children;
  Future<Session> Function(String)? detailsHandler;

  @override
  Future<Session> getSessionDetails(String id) async => detailsHandler != null
      ? await detailsHandler!(id)
      : id == parent.id
      ? parent
      : children.singleWhere((item) => item.id == id);

  @override
  Future<List<Session>> listSessionChildren(String id) async => children;
}

Finder _textField(Finder field) => find.descendant(
  of: field,
  matching: find.byType(TextField),
  matchRoot: true,
);

Future<ConnectionController> _controller(
  _FakeOpenCodeApi api, {
  bool savedProfile = false,
}) async {
  SharedPreferences.setMockInitialValues({
    if (savedProfile) ...{
      'oc.profiles': jsonEncode([
        {'id': 'profile', 'name': 'Synthetic', 'baseUrl': 'http://localhost'},
      ]),
      'oc.activeProfile': 'profile',
    },
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  if (savedProfile) {
    // Navigation guards require the saved profile whose location they protect.
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(secure, (_) async => null);
    addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
    await store.load();
  }
  return ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected;
}

class _DelayedActionController extends ConnectionController {
  _DelayedActionController(super.store, this.readyApi);

  final Completer<OpenCodeApi?> readyApi;

  @override
  Future<OpenCodeApi?> prepareActionTransport() async {
    final replacement = await readyApi.future;
    // Real wake recovery publishes the replacement before returning it.
    api = replacement;
    return replacement;
  }
}

class _DelayedRepositoryController extends ConnectionController {
  _DelayedRepositoryController(super.store, this.readyRepository);

  final Completer<ProductRepository?> readyRepository;

  @override
  Future<ProductRepository?> prepareActionRepository() =>
      readyRepository.future;
}

class _DelayedLocationController extends ConnectionController {
  _DelayedLocationController(super.store);

  final selection = Completer<void>();
  bool selectionStarted = false;

  @override
  Future<void> selectLocationForExistingSession({
    String? directory,
    String? workspace,
  }) async {
    selectionStarted = true;
    await selection.future;
  }
}

class _StaticCatalogController extends ConnectionController {
  _StaticCatalogController(super.store);

  @override
  Future<void> refreshCatalog() async {}
}

MessageWithParts _message(
  String id,
  String role,
  List<Part> parts, {
  int created = 1,
  String? providerID,
  String? modelID,
  Tokens? tokens,
  double cost = 0,
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: role,
    providerID: providerID,
    modelID: modelID,
    tokens: tokens,
    cost: cost,
    time: MsgTime(created: created, completed: created + 1),
  ),
  parts: parts,
);

/// History of one prompt: a running turn's live line (and its Stop) sits
/// under it.
Future<List<MessageWithParts>> _onePrompt(String _) async => [
  _message('u1', 'user', [
    Part(id: 'u1-text', messageID: 'u1', type: 'text', text: 'Hi'),
  ]),
];

/// The transcript toggles may apply in place and leave the session sheet
/// open; a reader would then swipe it away before reaching the app bar.
Future<void> _dismissSheetIfOpen(WidgetTester tester) async {
  if (find.byKey(const Key('session-view-timestamps')).evaluate().isEmpty) {
    return;
  }
  await tester.sendKeyEvent(LogicalKeyboardKey.escape);
  await tester.pumpAndSettle();
  expect(find.byKey(const Key('session-menu-sheet')), findsNothing);
}

EventEnvelope _event(String type, Map<String, dynamic> properties) =>
    EventEnvelope(type: type, properties: properties);

Map<String, dynamic> _partJson({
  required String id,
  required String messageID,
  required String type,
  String text = '',
  String? filename,
  String? tool,
  Map<String, dynamic>? state,
}) => {
  'id': id,
  'sessionID': 'session-1',
  'messageID': messageID,
  'type': type,
  'text': text,
  'filename': ?filename,
  'tool': ?tool,
  'state': ?state,
};

Future<ConnectionController> _pumpChat(
  WidgetTester tester,
  _FakeOpenCodeApi api, {
  ProductRepository? repository,
  ConnectionController? controller,
  ReviewHandoffStore? handoffStore,
  bool reduceMotion = false,
}) async {
  final activeController = controller ?? await _controller(api);
  final navigatorKey = GlobalKey<NavigatorState>();
  activeController.repository = repository;
  addTearDown(activeController.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(activeController)],
      child: MaterialApp(
        navigatorKey: navigatorKey,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,

        builder: (context, child) => ListenableBuilder(
          listenable: activeController,
          builder: (context, _) => AppConditionsScope(
            conditions: [
              connectionKitStatus(
                context,
                activeController,
                actionContext: () =>
                    navigatorKey.currentState?.overlay?.context,
              ),
            ],
            child: MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(disableAnimations: reduceMotion),
              child: child!,
            ),
          ),
        ),
        home: ChatScreen(
          sessionID: 'session-1',
          // Per-test store so staged review references never leak between
          // cases through the app-wide singleton.
          handoffStore: handoffStore ?? ReviewHandoffStore(),
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump();
  return activeController;
}

Future<ConnectionController> _pumpProvisionalChat(
  WidgetTester tester,
  _FakeOpenCodeApi api, {
  double textScale = 1,
  List<PromptAttachment> attachments = const [],
}) async {
  final controller = await _controller(api);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,

        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: Builder(
          builder: (context) => Scaffold(
            key: const ValueKey('provisional-chat-host'),
            body: Center(
              child: FilledButton(
                key: const ValueKey('open-provisional-chat'),
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => ChatScreen(
                      sessionID: 'session-1',
                      discardIfUntouched: true,
                      initialAttachments: attachments,
                    ),
                  ),
                ),
                child: const Text('Open provisional chat'),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.byKey(const ValueKey('open-provisional-chat')));
  await tester.pump();
  await tester.pump();
  return controller;
}

Future<void> _pumpEvent(WidgetTester tester) async {
  await tester.pump();
  await tester.pump();
}

/// UX-P0-03: Commands, Attach, and Voice are collapsed behind one leading
/// tools button, so tests reach them through the tools sheet instead of
/// tapping three separate composer icons.
Future<void> _useComposerTool(WidgetTester tester, String tool) async {
  await tester.tap(find.byKey(const Key('composer-tools-button')));
  await tester.pumpAndSettle();
  await tester.ensureVisible(find.byKey(Key('composer-tool-$tool')));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(Key('composer-tool-$tool')));
  // The sheet resolves its choice on dismissal, so the tool it launches
  // needs a second settle.
  await tester.pumpAndSettle();
  await tester.pumpAndSettle();
}

/// Runs one of the command sheet's app commands by its slash word
/// (slice-P10.1: the display toggles, retry and the plan moved here from
/// the conversation menu).
Future<void> _runSheetCommand(WidgetTester tester, String slash) async {
  await _useComposerTool(tester, 'commands');
  await tester.enterText(
    find.byKey(const Key('command-launcher-search')),
    slash,
  );
  await tester.pump();
  final row = find.byKey(Key('command-mobile-$slash'));
  await tester.ensureVisible(row);
  await tester.pumpAndSettle();
  await tester.tap(row);
  await tester.pumpAndSettle();
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MessageWithParts historyMessage(String id, [String? text]) => _message(
    id,
    'user',
    [Part(id: 'part-$id', messageID: id, type: 'text', text: text ?? id)],
  );

  testWidgets(
    'UXCHAT uncached child Open rejects a project change during fetch',
    (tester) async {
      final api = _FakeOpenCodeApi();
      final parent = Session(id: 'session-1', title: 'Parent');
      final child = Session(
        id: 'child',
        parentID: parent.id,
        title: 'Child task',
      );
      final pending = Completer<Session>();
      final repo = _RelationsProductRepository(parent, [child])
        ..detailsHandler = (id) =>
            id == child.id ? pending.future : Future.value(parent);
      final conn = await _controller(api, savedProfile: true)
        ..sessionsById = {parent.id: parent};
      await _pumpChat(
        tester,
        api,
        repository: repo,
        controller: conn,
        reduceMotion: true,
      );
      expect(find.byKey(const Key('running-work-indicator')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('session-actions-button')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('session-menu-subagents')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('work-agent-child')), findsOneWidget);
      await tester.tap(find.byKey(const Key('work-agent-child')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      conn.directory = '/other-project';
      conn.locationRevision++;
      conn.notifyListeners();
      pending.complete(child);
      await tester.pumpAndSettle();
      expect(
        tester.widget<ChatScreen>(find.byType(ChatScreen)).sessionID,
        parent.id,
      );
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('UXCHAT child Open rejects a superseded location selection', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final parent = Session(id: 'session-1', title: 'Parent');
    final child = Session(
      id: 'child',
      parentID: parent.id,
      title: 'Child task',
      directory: '/child-project',
    );
    final saved = await _controller(api, savedProfile: true);
    final conn = _DelayedLocationController(saved.store)
      ..api = api
      ..status = StreamStatus.connected
      ..sessionsById = {parent.id: parent, child.id: child};
    saved.dispose();
    await _pumpChat(
      tester,
      api,
      repository: _RelationsProductRepository(parent, [child]),
      controller: conn,
      reduceMotion: true,
    );
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Keep my draft',
    );
    await tester.tap(find.byKey(const ValueKey('session-actions-button')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-subagents')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('work-agent-child')));
    await tester.pumpAndSettle();
    expect(conn.selectionStarted, isTrue);
    conn.directory = '/competing-project';
    conn.locationRevision++;
    conn.notifyListeners();
    conn.selection.complete();
    await tester.pumpAndSettle();
    expect(
      tester.widget<ChatScreen>(find.byType(ChatScreen)).sessionID,
      parent.id,
    );
    expect(find.text('Keep my draft'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'UXCHAT every parent inbox delivery loads canonical server result without sending',
    (tester) async {
      final api = _FakeOpenCodeApi();
      var reads = 0;
      var delivered = 0;
      api.messagesHandler = (_) async {
        reads++;
        return [
          for (var index = 1; index <= delivered; index++)
            _message('inbox-$index', 'assistant', [
              Part(
                id: 'notice-$index',
                messageID: 'inbox-$index',
                type: 'v2:notice',
                toolName: 'synthetic',
                filename: 'Background result $index',
                text:
                    '<subagent sessionID="child" state="completed">\nServer result $index\n</subagent>',
              ),
            ], created: index),
        ];
      };
      final conn = await _pumpChat(tester, api, reduceMotion: true);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'My unsent draft',
      );
      final before = reads;
      conn.handleEventForTesting(
        _event('session.inbox.delivered', {
          'sessionID': 'other-parent',
          'inboxID': 'unrelated',
        }),
      );
      await tester.pumpAndSettle();
      expect(reads, before);
      for (var index = 1; index <= 2; index++) {
        delivered = index;
        final previousReads = reads;
        conn.handleEventForTesting(
          _event('session.inbox.delivered', {
            'sessionID': 'session-1',
            'inboxID': 'inbox-$index',
          }),
        );
        await tester.pump(const Duration(milliseconds: 200));
        await tester.pumpAndSettle();
        expect(reads, greaterThan(previousReads));
        expect(find.textContaining('Background result $index'), findsOneWidget);
      }
      expect(find.text('My unsent draft'), findsOneWidget);
      expect(api.promptCalls, 0);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'UXCHAT server-returned child result appears in parent without a client prompt',
    (tester) async {
      final api = _FakeOpenCodeApi();
      final conn = await _pumpChat(tester, api, reduceMotion: true);
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'My unsent draft',
      );
      api.messagesHandler = (_) async => [
        _message('server-result', 'assistant', [
          Part(
            id: 'server-result-text',
            messageID: 'server-result',
            type: 'text',
            text: 'The delegated keyboard review is ready.',
          ),
        ], created: 2),
      ];
      conn.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'server-result',
            'sessionID': 'session-1',
            'role': 'assistant',
            'time': {'created': 2},
          },
        }),
      );
      conn.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': {
            'id': 'server-result-text',
            'messageID': 'server-result',
            'sessionID': 'session-1',
            'type': 'text',
            'text': 'The delegated keyboard review is ready.',
          },
        }),
      );
      await _pumpEvent(tester);
      await tester.pumpAndSettle();
      expect(
        find.text('The delegated keyboard review is ready.'),
        findsOneWidget,
      );
      expect(find.text('My unsent draft'), findsOneWidget);
      expect(api.promptCalls, 0);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('UXCHAT v2 subagent tools are summarized as delegated tasks', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('delegation', 'assistant', [
          for (final id in ['a', 'b'])
            Part(
              id: id,
              messageID: 'delegation',
              type: 'tool',
              toolName: 'subagent',
              toolState: ToolState.fromJson({
                'status': 'completed',
                'input': {'description': 'Review $id'},
                'output': 'Reviewed $id',
              }, toolName: 'subagent'),
            ),
        ]),
      ];
    await _pumpChat(tester, api, reduceMotion: true);
    await tester.pumpAndSettle();
    expect(find.textContaining('Delegated 2 tasks'), findsOneWidget);
    expect(find.textContaining('other call'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'UXCHAT single foreground Stop preserves draft and targets this session',
    (tester) async {
      final api = _FakeOpenCodeApi()..messagesHandler = _onePrompt;
      final conn = await _pumpChat(tester, api, reduceMotion: true);
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'Keep my draft',
      );
      conn.busySessions.add('session-1');
      conn.notifyListeners();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 250));
      // Send became Stop; the status is written in the turn.
      expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);
      await tester.tap(find.byKey(const Key('chat-stop-button')));
      await tester.pump();
      expect(api.abortCalls, 1);
      expect(find.text('Keep my draft'), findsOneWidget);
      expect(api.promptCalls, 0);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'staged revert skips hidden pages when reopening older boundary',
    (tester) async {
      final api = _FakeOpenCodeApi()
        ..sessionResult = Session(
          id: 'session-1',
          reverted: true,
          stagedRevert: SessionRevert(messageID: 'msg_02'),
        )
        ..pageHandler = (cursor) async => switch (cursor) {
          null => ServerPage(
            items: [historyMessage('msg_05')],
            nextCursor: 'hidden',
          ),
          'hidden' => const ServerPage(items: [], nextCursor: 'boundary'),
          _ => ServerPage(
            items: [historyMessage('msg_01'), historyMessage('msg_02')],
          ),
        };
      final controller = await _controller(api);
      controller.sessionsById['session-1'] = api.sessionResult!;
      await _pumpChat(
        tester,
        api,
        controller: controller,
        repository: _RevertProductRepository(),
      );
      await tester.pumpAndSettle();
      expect(api.pageCursors, containsAllInOrder([null, 'hidden', 'boundary']));
      expect(find.byKey(const ValueKey('message-msg_01')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-msg_02')), findsNothing);
      expect(find.byKey(const ValueKey('message-msg_05')), findsNothing);
      expect(find.text('Revert staged'), findsOneWidget);
    },
  );

  testWidgets(
    'staged revert commit never reveals removed cached messages on refresh failure',
    (tester) async {
      final api = _FakeOpenCodeApi()
        ..sessionResult = Session(
          id: 'session-1',
          reverted: true,
          stagedRevert: SessionRevert(messageID: 'msg_02'),
        )
        ..pageHandler = (_) async => ServerPage(
          items: [
            historyMessage('msg_01'),
            historyMessage('msg_02'),
            historyMessage('msg_03'),
          ],
        );
      final controller = await _controller(api);
      controller.sessionsById['session-1'] = api.sessionResult!;
      await _pumpChat(
        tester,
        api,
        controller: controller,
        repository: _RevertProductRepository(),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('message-msg_02')), findsNothing);
      api.sessionResult = Session(id: 'session-1');
      api.pageHandler = (_) async => throw StateError('history unavailable');
      controller.handleEventForTesting(
        _event('session.revert.committed', {
          'sessionID': 'session-1',
          'to': 'msg_02',
        }),
      );
      await _pumpEvent(tester);
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('message-msg_01')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-msg_02')), findsNothing);
      expect(find.byKey(const ValueKey('message-msg_03')), findsNothing);
      expect(find.text('Revert staged'), findsNothing);
    },
  );

  testWidgets(
    'staged revert keeps a composer draft until explicitly resolved',
    (tester) async {
      final api = _FakeOpenCodeApi()
        ..sessionResult = Session(
          id: 'session-1',
          reverted: true,
          stagedRevert: SessionRevert(messageID: 'msg_02'),
        )
        ..pageHandler = (_) async => ServerPage(
          items: [historyMessage('msg_01'), historyMessage('msg_02')],
        );
      final controller = await _controller(api);
      controller.sessionsById['session-1'] = api.sessionResult!;
      await _pumpChat(
        tester,
        api,
        controller: controller,
        repository: _RevertProductRepository(),
      );
      await tester.pumpAndSettle();
      final field = find.byKey(const Key('chat-composer-field'));
      await tester.enterText(field, 'My next change');
      await tester.pump();
      expect(
        tester
            .widget<GestureDetector>(find.byKey(const Key('chat-send-button')))
            .onTap,
        isNull,
      );
      expect(api.promptCalls, 0);
      expect(
        tester.widget<TextField>(_textField(field)).controller!.text,
        'My next change',
      );
      expect(find.text('Revert staged'), findsOneWidget);
      api.sessionResult = Session(id: 'session-1');
      controller.handleEventForTesting(
        _event('session.revert.cleared', {'sessionID': 'session-1'}),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('chat-send-button')));
      await tester.pumpAndSettle();
      expect(api.prompts.single.text, 'My next change');
    },
  );

  testWidgets('staged revert clear restores the hidden conversation', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..sessionResult = Session(
        id: 'session-1',
        reverted: true,
        stagedRevert: SessionRevert(messageID: 'msg_02'),
      )
      ..pageHandler = (_) async => ServerPage(
        items: [historyMessage('msg_01'), historyMessage('msg_02')],
      );
    final controller = await _controller(api);
    controller.sessionsById['session-1'] = api.sessionResult!;
    await _pumpChat(
      tester,
      api,
      controller: controller,
      repository: _RevertProductRepository(),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('message-msg_02')), findsNothing);
    api.sessionResult = Session(id: 'session-1');
    controller.handleEventForTesting(
      _event('session.revert.cleared', {'sessionID': 'session-1'}),
    );
    await _pumpEvent(tester);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('message-msg_02')), findsOneWidget);
    expect(find.text('Revert staged'), findsNothing);
  });

  testWidgets(
    'paged history preserves newest rows through older and duplicate pages',
    (tester) async {
      final api = _FakeOpenCodeApi()
        ..pageHandler = (cursor) async => switch (cursor) {
          null => ServerPage(
            items: [historyMessage('recent'), historyMessage('newest')],
            nextCursor: 'first',
          ),
          'first' => ServerPage(
            items: [historyMessage('older'), historyMessage('recent')],
            nextCursor: 'empty',
          ),
          'empty' => const ServerPage(items: [], nextCursor: 'last'),
          _ => ServerPage(items: [historyMessage('oldest')]),
        };
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      final older = find.byKey(const ValueKey('chat-load-older'));
      for (var i = 0; i < 3; i++) {
        await tester.ensureVisible(older);
        await tester.tap(older);
        await tester.pumpAndSettle();
      }
      expect(api.pageCursors, [null, 'first', 'empty', 'last']);
      expect(find.byKey(const ValueKey('message-newest')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-recent')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-oldest')), findsOneWidget);
      expect(older, findsNothing);
    },
  );

  testWidgets('paged history retry keeps the cursor and transcript', (
    tester,
  ) async {
    var fail = true;
    final api = _FakeOpenCodeApi()
      ..pageHandler = (cursor) async {
        if (cursor == null) {
          return ServerPage(
            items: [historyMessage('newest')],
            nextCursor: 'retry',
          );
        }
        if (fail) throw ApiException('Older request failed');
        return ServerPage(items: [historyMessage('older')]);
      };
    await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('chat-load-older')));
    await tester.pumpAndSettle();
    // Said in words; the raw ApiException text is details only.
    expect(find.text('Older request failed'), findsNothing);
    expect(
      find.text(
        "The server's answer didn't make sense to the app. Try again, or "
        'report the problem.',
      ),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('message-newest')), findsOneWidget);
    fail = false;
    await tester.tap(find.byKey(const ValueKey('chat-load-older')));
    await tester.pumpAndSettle();
    expect(api.pageCursors, [null, 'retry', 'retry']);
    expect(find.byKey(const ValueKey('message-older')), findsOneWidget);
  });

  testWidgets(
    'paged history does not resurrect a message removed during loading',
    (tester) async {
      final pending = Completer<ServerPage<MessageWithParts>>();
      final api = _FakeOpenCodeApi()
        ..pageHandler = (cursor) async => cursor == null
            ? ServerPage(items: [historyMessage('newest')], nextCursor: 'older')
            : pending.future;
      final controller = await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('chat-load-older')));
      await tester.pump();
      controller.handleEventForTesting(
        _event('message.removed', {
          'sessionID': 'session-1',
          'messageID': 'deleted',
        }),
      );
      await _pumpEvent(tester);
      pending.complete(
        ServerPage(items: [historyMessage('deleted'), historyMessage('older')]),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('message-deleted')), findsNothing);
      expect(find.byKey(const ValueKey('message-older')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-newest')), findsOneWidget);
    },
  );

  testWidgets('paged history preserves deltas newer than an older snapshot', (
    tester,
  ) async {
    final pending = Completer<ServerPage<MessageWithParts>>();
    final api = _FakeOpenCodeApi()
      ..pageHandler = (cursor) async => cursor == null
          ? ServerPage(
              items: [historyMessage('newest', 'latest')],
              nextCursor: 'older',
            )
          : pending.future;
    final controller = await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('chat-load-older')));
    await tester.pump();
    controller.handleEventForTesting(
      _event('message.part.delta', {
        'sessionID': 'session-1',
        'messageID': 'newest',
        'partID': 'part-newest',
        'field': 'text',
        'delta': ' live',
      }),
    );
    await _pumpEvent(tester);
    pending.complete(
      ServerPage(
        items: [historyMessage('older'), historyMessage('newest', 'stale')],
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('latest live'), findsOneWidget);
    expect(find.text('stale'), findsNothing);
  });

  testWidgets(
    'paged history anchors the reader while older rows and live messages arrive',
    (tester) async {
      final pending = Completer<ServerPage<MessageWithParts>>();
      final api = _FakeOpenCodeApi()
        ..pageHandler = (cursor) async => cursor == null
            ? ServerPage(
                items: [
                  // Enough one-line rows that reaching the oldest leaves the
                  // reader well over the 480px "away from latest" mark.
                  for (var i = 0; i < 40; i++) historyMessage('recent-$i'),
                ],
                nextCursor: 'older',
              )
            : pending.future;
      final controller = await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      final listFinder = find.byType(ScrollablePositionedList);
      for (
        var i = 0;
        i < 8 &&
            find
                .byKey(const ValueKey('chat-load-older'))
                .hitTestable()
                .evaluate()
                .isEmpty;
        i++
      ) {
        await tester.drag(listFinder, const Offset(0, 450));
        await tester.pumpAndSettle();
      }
      final anchor = find.byKey(const ValueKey('message-recent-0'));
      final before = tester.getTopLeft(anchor);
      await tester.tap(find.byKey(const ValueKey('chat-load-older')));
      await tester.pump();
      controller.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'live-newest',
            'sessionID': 'session-1',
            'role': 'user',
            'time': {'created': 100},
          },
        }),
      );
      controller.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': _partJson(
            id: 'live-text',
            messageID: 'live-newest',
            type: 'text',
            text: 'Live newest',
          ),
        }),
      );
      await _pumpEvent(tester);
      pending.complete(
        ServerPage(
          items: [for (var i = 0; i < 20; i++) historyMessage('older-$i')],
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(anchor).dy, closeTo(before.dy, 1));
      expect(find.text('Live newest'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('jump-to-latest')));
      await tester.pumpAndSettle();
      expect(find.text('Live newest'), findsOneWidget);
    },
  );

  testWidgets(
    'paged history retains older prefix on overlapping head refresh',
    (tester) async {
      var refreshed = false;
      final api = _FakeOpenCodeApi()
        ..pageHandler = (cursor) async => cursor == null
            ? ServerPage(
                items: [
                  historyMessage('recent'),
                  historyMessage(refreshed ? 'newest' : 'previous'),
                ],
                nextCursor: 'older',
              )
            : ServerPage(items: [historyMessage('oldest')]);
      final controller = await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('chat-load-older')));
      await tester.pumpAndSettle();
      refreshed = true;
      controller.handleEventForTesting(
        _event('session.shell.changed', {'sessionID': 'session-1'}),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('message-oldest')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-newest')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-previous')), findsNothing);
      expect(find.byKey(const ValueKey('chat-load-older')), findsNothing);
    },
  );

  testWidgets(
    'paged history reconnect invalidates pending older page and updates open timeline',
    (tester) async {
      final pending = Completer<ServerPage<MessageWithParts>>();
      var refreshed = false;
      final api = _FakeOpenCodeApi()
        ..pageHandler = (cursor) async => cursor == null
            ? ServerPage(
                items: [
                  historyMessage(refreshed ? 'new-window' : 'old-window'),
                ],
                nextCursor: 'older',
              )
            : pending.future;
      final controller = await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Conversation menu'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('timeline-search')),
        'window',
      );
      await tester.tap(find.byKey(const ValueKey('timeline-load-older')));
      await tester.pump();
      refreshed = true;
      controller.signalDataRefreshForTesting();
      await tester.pumpAndSettle();
      pending.complete(ServerPage(items: [historyMessage('stale-window')]));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('timeline-row-new-window')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('timeline-row-old-window')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey('timeline-row-stale-window')),
        findsNothing,
      );
      expect(
        tester
            .widget<TextField>(
              _textField(find.byKey(const ValueKey('timeline-search'))),
            )
            .controller!
            .text,
        'window',
      );
    },
  );

  testWidgets(
    'unloaded part edits wait for history and retain updates newer than its page',
    (tester) async {
      final pending = Completer<ServerPage<MessageWithParts>>();
      final api = _FakeOpenCodeApi()
        ..pageHandler = (cursor) async => cursor == null
            ? ServerPage(items: [historyMessage('newest')], nextCursor: 'older')
            : pending.future;
      final controller = await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('chat-load-older')));
      await tester.pump();
      controller.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': _partJson(
            id: 'part-old',
            messageID: 'old',
            type: 'text',
            text: 'Edited old prompt',
          ),
        }),
      );
      await _pumpEvent(tester);
      expect(find.byKey(const ValueKey('message-old')), findsNothing);
      controller.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'old',
            'sessionID': 'session-1',
            'role': 'user',
            'time': {'created': 0},
          },
        }),
      );
      await _pumpEvent(tester);
      expect(find.byKey(const ValueKey('message-old')), findsNothing);
      controller.handleEventForTesting(
        _event('message.part.delta', {
          'sessionID': 'session-1',
          'messageID': 'old',
          'partID': 'late-part',
          'field': 'text',
          'delta': ' plus live delta',
        }),
      );
      await _pumpEvent(tester);
      pending.complete(
        ServerPage(items: [historyMessage('old', 'stale prompt')]),
      );
      await tester.pumpAndSettle();
      expect(find.text('Edited old prompt'), findsOneWidget);
      expect(find.text('stale prompt'), findsNothing);
      expect(
        tester.getTopLeft(find.text('Edited old prompt')).dy,
        lessThan(tester.getTopLeft(find.text('newest')).dy),
      );
      controller.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': _partJson(
            id: 'late-part',
            messageID: 'old',
            type: 'text',
            text: 'Late base',
          ),
        }),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Late base plus live delta'), findsOneWidget);
    },
  );

  testWidgets(
    'provisional session with an empty page and cursor is not discarded',
    (tester) async {
      final api = _FakeOpenCodeApi()
        ..pageHandler = (_) async =>
            const ServerPage(items: [], nextCursor: 'more');
      await _pumpProvisionalChat(tester, api);
      await tester.pumpAndSettle();
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(api.deleteCalls, isEmpty);
    },
  );

  test('message errors surface nested data.message', () {
    final info = MessageInfo.fromJson({
      'id': 'assistant-1',
      'sessionID': 'session-1',
      'role': 'assistant',
      'error': {
        'name': 'ProviderError',
        'data': {'message': 'The selected model is unavailable'},
      },
    });

    expect(info.errorText, 'The selected model is unavailable');
  });

  testWidgets('back discards only the exact verified empty mobile session', (
    tester,
  ) async {
    final requested = <String>[];
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (id) async {
        requested.add(id);
        return [];
      };
    await _pumpProvisionalChat(tester, api);

    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(requested, everyElement('session-1'));
    expect(requested.length, greaterThanOrEqualTo(2));
    expect(api.deleteCalls, ['session-1']);
    expect(find.byKey(const ValueKey('provisional-chat-host')), findsOneWidget);
  });

  testWidgets('back preserves a newly created session after server messages', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-committed', 'user', [
          Part(type: 'text', text: 'Keep this session'),
        ]),
      ];
    await _pumpProvisionalChat(tester, api);

    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(api.deleteCalls, isEmpty);
    expect(find.byKey(const ValueKey('provisional-chat-host')), findsOneWidget);
  });

  testWidgets('failed empty-session verification keeps server state', (
    tester,
  ) async {
    var calls = 0;
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async {
        calls += 1;
        if (calls > 1) throw StateError('transport moved');
        return [];
      };
    await _pumpProvisionalChat(tester, api);

    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(api.deleteCalls, isEmpty);
    // Kept quietly: an empty conversation is harmless and the list shows
    // it, so nothing interrupts the way out.
    expect(find.byType(SnackBar), findsNothing);
    expect(find.byKey(const ValueKey('product-error-alert')), findsNothing);
  });

  testWidgets('leaving with a typed draft keeps it silently', (tester) async {
    tester.view.physicalSize = const Size(640, 1280);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final api = _FakeOpenCodeApi()..messagesHandler = (_) async => [];
    final controller = await _pumpProvisionalChat(tester, api, textScale: 2);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Keep this draft',
    );

    await tester.pageBack();
    await tester.pumpAndSettle();

    // No confirmation: the text persists as a per-session draft, and the
    // provisional session survives so the draft has a home to return to.
    expect(
      find.byKey(const ValueKey('discard-chat-draft-dialog')),
      findsNothing,
    );
    expect(find.byKey(const ValueKey('provisional-chat-host')), findsOneWidget);
    expect(api.deleteCalls, isEmpty);
    expect(controller.sessionDraft('session-1'), 'Keep this draft');
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'leaving an empty conversation keeps its pending photo destination',
    (tester) async {
      final api = _FakeOpenCodeApi()..messagesHandler = (_) async => [];
      final controller = await _pumpProvisionalChat(tester, api);
      await controller.store.prefs.setString(
        PromptPhotoStore.key,
        jsonEncode(
          const PendingPromptPhoto(
            id: 'pending',
            profileID: '',
            sessionID: 'session-1',
          ).toJson(),
        ),
      );
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(api.deleteCalls, isEmpty);
      expect(controller.promptPhotos.pending!.sessionID, 'session-1');
      expect(
        find.byKey(const ValueKey('provisional-chat-host')),
        findsOneWidget,
      );
    },
  );

  testWidgets('mobile new command stays when an attachment cannot persist', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()..messagesHandler = (_) async => [];
    await _pumpProvisionalChat(
      tester,
      api,
      attachments: const [
        PromptAttachment(
          mime: 'text/plain',
          filename: 'notes.txt',
          url: 'content://temporary/notes.txt',
        ),
      ],
    );

    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('command-mobile-new')));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('draft-save-error')), findsOneWidget);

    expect(api.createCalls, 0);
    expect(api.deleteCalls, isEmpty);
    expect(find.textContaining('notes.txt'), findsWidgets);
  });

  testWidgets('a running sibling agent appears in the switch strip', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(360, 720));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakeOpenCodeApi();
    final parent = Session(
      id: 'parent',
      title: 'Parent work',
      directory: '/work/acme',
      time: SessionTime(created: 1),
    );
    final child = Session(
      id: 'session-1',
      title: 'Explore mobile flow',
      parentID: parent.id,
      directory: '/work/acme',
      time: SessionTime(created: 2),
    );
    final sibling = Session(
      id: 'session-2',
      title: 'Review mobile flow',
      parentID: parent.id,
      directory: '/work/acme',
      time: SessionTime(created: 3),
    );
    final controller = await _controller(api)
      ..directory = '/work/acme'
      ..repository = _RelationsProductRepository(parent, [child, sibling])
      ..sessionsById = {parent.id: parent, child.id: child, sibling.id: sibling}
      ..busySessions = {'session-2'};
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    final work = find.byKey(const ValueKey('running-work-indicator'));
    expect(work, findsOneWidget);
    await tester.tap(work);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byKey(const ValueKey('work-agent-session-2')), findsOneWidget);
    expect(find.byKey(const ValueKey('work-agent-session-1')), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
    expect(tester.takeException(), isNull);
  });

  testWidgets('child chat exposes parent and sibling navigation', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakeOpenCodeApi();
    final parent = Session(
      id: 'parent',
      title: 'Parent work',
      directory: '/work/acme',
      time: SessionTime(created: 1),
    );
    final child = Session(
      id: 'session-1',
      title: 'Explore mobile flow',
      parentID: parent.id,
      directory: '/work/acme',
      time: SessionTime(created: 2),
    );
    final sibling = Session(
      id: 'session-2',
      title: 'Review mobile flow',
      parentID: parent.id,
      directory: '/work/acme',
      time: SessionTime(created: 3),
    );
    final controller = await _controller(api, savedProfile: true)
      ..directory = '/work/acme'
      ..repository = _RelationsProductRepository(parent, [child, sibling])
      ..sessionsById = {
        parent.id: parent,
        child.id: child,
        sibling.id: sibling,
      };
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Delegated conversation · 1 of 2'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('subagent-parent-session')),
      findsOneWidget,
    );
    // The chat's one status line (design standard §5): the siblings are
    // under its More menu.
    await tester.tap(find.byKey(const ValueKey('kit-status-more')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('subagent-session-list')));
    await tester.pumpAndSettle();

    expect(find.text('Subagents'), findsOneWidget);
    expect(find.text('Review mobile flow'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('offline chat keeps its transcript and shows recovery actions', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-1', 'assistant', [
          Part(
            id: 'part-1',
            type: 'text',
            text: 'Retained response',
            messageID: 'assistant-1',
          ),
        ]),
      ];
    // App-wide status belongs to an actual saved profile, like production.
    final controller = await _controller(api, savedProfile: true);
    await _pumpChat(tester, api, controller: controller);
    expect(find.text('Retained response'), findsOneWidget);

    controller
      ..status = StreamStatus.disconnected
      ..lastError = 'Endpoint is unavailable'
      ..signalDataRefreshForTesting();
    await tester.pump();
    await tester.pump();

    expect(find.text('Retained response'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('connection-status-banner')),
      findsOneWidget,
    );
    // The Work tab's words (design standard §5): the last attempt failed,
    // so the line says so at once.
    expect(find.text("Synthetic isn't answering"), findsOneWidget);
    expect(find.text('Reconnect to Synthetic'), findsOneWidget);
    // The raw error and the secondary action live behind Details, in the
    // status line's menu (design standard §5: one action per line).
    // The status line unfolds first (design standard §10).
    await tester.pump(KitMotion.standard);
    await tester.tap(find.byKey(const ValueKey('kit-status-more')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('connection-banner-details')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    // The raw error waits folded under the sheet's own Details.
    expect(find.textContaining('Endpoint is unavailable'), findsNothing);
    final fold = find.descendant(
      of: find.byKey(const ValueKey('connection-banner-details-sheet')),
      matching: find.byKey(const ValueKey('kit-details-toggle')),
    );
    await tester.ensureVisible(fold);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.tap(fold);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.textContaining('Endpoint is unavailable'), findsOneWidget);
    expect(find.text('Switch server'), findsOneWidget);
  });

  testWidgets('rehydrate never flashes a skeleton over existing messages', (
    tester,
  ) async {
    var loads = 0;
    Completer<List<MessageWithParts>>? pending;
    List<MessageWithParts> transcript() => [
      _message('assistant-1', 'assistant', [
        Part(
          id: 'part-1',
          type: 'text',
          text: 'Retained response',
          messageID: 'assistant-1',
        ),
      ]),
    ];
    final api = _FakeOpenCodeApi();
    api.messagesHandler = (_) {
      loads += 1;
      if (loads == 1) return Future.value(transcript());
      pending = Completer<List<MessageWithParts>>();
      return pending!.future;
    };

    final controller = await _pumpChat(tester, api);
    expect(find.text('Retained response'), findsOneWidget);
    expect(find.byType(KitSkeletonTranscript), findsNothing);

    controller.signalDataRefreshForTesting();
    await tester.pump();
    await tester.pump();

    // The reload is still in flight: the transcript stays rendered with no
    // skeleton and no full-screen error.
    expect(loads, 2);
    expect(find.text('Retained response'), findsOneWidget);
    expect(find.byType(KitSkeletonTranscript), findsNothing);
    expect(find.byType(KitStateView), findsNothing);

    // Even a failed refresh keeps the transcript instead of a dead end.
    pending!.completeError(StateError('stream reset during rehydrate'));
    await tester.pumpAndSettle();
    expect(find.text('Retained response'), findsOneWidget);
    expect(find.byType(KitSkeletonTranscript), findsNothing);
    expect(find.byType(KitStateView), findsNothing);
  });

  testWidgets('renders current OpenCode unified patches and server counts', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..diffs = [
        FileDiff.fromJson({
          'file': 'lib/main.dart',
          'patch': '@@ -1 +1 @@\n-old line\n+new line',
          'additions': 1,
          'deletions': 1,
          'status': 'modified',
        }),
      ];
    await _pumpChat(tester, api);

    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-changes')));
    await tester.pumpAndSettle();
    expect(find.text('+1 −1'), findsOneWidget);

    expect(find.text('old line', findRichText: true), findsOneWidget);
    expect(find.text('new line', findRichText: true), findsOneWidget);
    expect(find.byType(KitDiffView), findsOneWidget);
    expect(find.byKey(const Key('review-mode-split')), findsNothing);
    final diff = tester.widget<KitDiffView>(find.byType(KitDiffView));
    expect(diff.files.single.patch, '@@ -1 +1 @@\n-old line\n+new line');
  });

  testWidgets('stages a selected diff comment as a composer reference', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..diffs = [
        FileDiff.fromJson({
          'file': 'lib/client.dart',
          'patch': '@@ -8,2 +8,2 @@\n-old request\n+new request',
          'additions': 1,
          'deletions': 1,
          'status': 'modified',
        }),
      ];
    await _pumpChat(tester, api);

    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-changes')));
    await tester.pumpAndSettle();
    // KitDiffView paints compact numbers under an expanded gutter hit area.
    await tester.tapAt(
      tester.getCenter(find.byKey(const ValueKey('review-line-0-current-8'))),
    );
    await tester.pump();
    expect(find.byKey(const Key('review-selection-bar')), findsOneWidget);

    await tester.tap(find.text('Comment'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('review-comment-field')),
      'Keep the retry behavior explicit.',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('review-add-to-prompt')));
    await tester.pumpAndSettle();

    // UX-103: review stays open so a pass can stage several findings, and
    // says how many are waiting on the prompt.
    expect(find.byKey(const Key('review-workspace')), findsOneWidget);
    expect(find.textContaining('1 on prompt'), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();
    // Let the staging snack bar clear the composer before tapping Send.
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();

    // The finding is a removable chip, not pre-rendered composer text.
    expect(find.byKey(const Key('composer-reference-strip')), findsOneWidget);
    expect(find.textContaining('client.dart'), findsWidgets);
    final composer = tester.widget<TextField>(
      _textField(find.byKey(const Key('chat-composer-field'))),
    );
    expect(composer.controller?.text, isEmpty);

    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pumpAndSettle();

    final sent = api.prompts.single.text;
    expect(sent, contains('`lib/client.dart`'));
    expect(sent, contains('new line 8'));
    expect(sent, contains('Keep the retry behavior explicit.'));
    expect(sent, contains('```diff\nnew request\n```'));
    expect(find.byKey(const Key('composer-reference-strip')), findsNothing);
  });

  testWidgets('foreground data refresh rehydrates messages missed while away', (
    tester,
  ) async {
    var text = 'Before background';
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-1', 'assistant', [
          Part(
            id: 'text-1',
            messageID: 'assistant-1',
            type: 'text',
            text: text,
          ),
        ]),
      ];
    final controller = await _pumpChat(tester, api);
    expect(find.text('Before background'), findsOneWidget);

    text = 'Completed while backgrounded';
    controller.signalDataRefreshForTesting();
    await tester.pump();
    await tester.pump();

    expect(find.text('Completed while backgrounded'), findsOneWidget);
    expect(find.text('Before background'), findsNothing);
  });

  testWidgets('composer keeps focus when the Android keyboard opens', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(800, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewInsets);

    await _pumpChat(tester, _FakeOpenCodeApi());
    final fieldFinder = find.byKey(const Key('chat-composer-field'));
    await tester.tap(fieldFinder);
    await tester.pump();
    final before = tester.widget<TextField>(_textField(fieldFinder));
    expect(before.focusNode?.hasFocus, isTrue);

    tester.view.viewInsets = const FakeViewPadding(bottom: 400);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    final after = tester.widget<TextField>(_textField(fieldFinder));
    expect(after.focusNode, same(before.focusNode));
    expect(after.focusNode?.hasFocus, isTrue);
  });

  testWidgets('send waits for the wake-time replacement transport', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final retainedApi = _FakeOpenCodeApi();
    final replacementApi = _FakeOpenCodeApi();
    final readyApi = Completer<OpenCodeApi?>();
    final controller =
        _DelayedActionController(ProfileStore(prefs: prefs), readyApi)
          ..api = retainedApi
          // Connected: offline sends queue instead of hitting the transport, and
          // this test covers the wake path where the stream is already live but
          // the action transport is still being replaced.
          ..status = StreamStatus.connected;
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'send after wake',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();

    expect(retainedApi.promptCalls, 0);
    expect(replacementApi.promptCalls, 0);
    expect(
      tester
          .widget<GestureDetector>(find.byKey(const Key('chat-send-button')))
          .onTap,
      isNull,
    );

    readyApi.complete(replacementApi);
    await tester.pumpAndSettle();

    expect(retainedApi.promptCalls, 0);
    expect(replacementApi.promptCalls, 1);
    expect(replacementApi.prompts.single.text, 'send after wake');
  });

  testWidgets('stop waits for the wake-time replacement transport', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final retainedApi = _FakeOpenCodeApi()..messagesHandler = _onePrompt;
    final replacementApi = _FakeOpenCodeApi();
    final readyApi = Completer<OpenCodeApi?>();
    final controller = _DelayedActionController(
      ProfileStore(prefs: prefs),
      readyApi,
    )..api = retainedApi;
    controller.busySessions.add('session-1');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    await tester.pump(const Duration(milliseconds: 200));
    await tester.tap(find.byKey(const Key('chat-stop-button')));
    await tester.pump();

    expect(retainedApi.abortCalls, 0);
    expect(replacementApi.abortCalls, 0);

    readyApi.complete(replacementApi);
    // The session stays busy, so the composer's activity ring animates
    // forever: pump explicit frames instead of settling.
    for (var i = 0; i < 4; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }

    expect(retainedApi.abortCalls, 0);
    expect(replacementApi.abortCalls, 1);
  });

  testWidgets('stop failure remains visible instead of being swallowed', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = _onePrompt
      ..abortError = ApiException('server refused to stop');
    final controller = await _pumpChat(tester, api);
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();

    await tester.pump(const Duration(milliseconds: 200));
    await tester.tap(find.byKey(const Key('chat-stop-button')));
    // Still busy after the failed stop, so the composer keeps animating.
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }

    expect(api.abortCalls, 1);
    // Said in words; the raw ApiException text is details only.
    expect(find.text('server refused to stop'), findsNothing);
    expect(
      find.text(
        "The server's answer didn't make sense to the app. Try again, or "
        'report the problem.',
      ),
      findsOneWidget,
    );
  });

  test(
    'session mutations wait for the wake-time replacement transport',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final retainedApi = _FakeOpenCodeApi();
      final replacementApi = _FakeOpenCodeApi();
      final readyApi = Completer<OpenCodeApi?>();
      final controller = _DelayedActionController(
        ProfileStore(prefs: prefs),
        readyApi,
      )..api = retainedApi;
      addTearDown(controller.dispose);

      final create = controller.createSession();
      final rename = controller.renameSession('session-1', 'Renamed');
      final delete = controller.deleteSession('session-2');
      await Future<void>.delayed(Duration.zero);

      expect(retainedApi.createCalls, 0);
      expect(retainedApi.renameCalls, isEmpty);
      expect(retainedApi.deleteCalls, isEmpty);

      readyApi.complete(replacementApi);
      final created = await create;
      await Future.wait([rename, delete]);

      expect(created.id, 'session-created');
      expect(replacementApi.createCalls, 1);
      expect(replacementApi.renameCalls, [(id: 'session-1', title: 'Renamed')]);
      expect(replacementApi.deleteCalls, ['session-2']);
    },
  );

  testWidgets('renders a generated image from a tool output filePath', (
    tester,
  ) async {
    const path = '/tmp/opencode/shots/captcha-r1.png';
    final api = _FakeOpenCodeApi()
      ..fileContents[path] = const FileContent(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZKgAAAAASUVORK5CYII=',
        type: 'binary',
        encoding: 'base64',
        mimeType: 'image/png',
      )
      ..messagesHandler = (_) async => [
        _message('assistant-image', 'assistant', [
          Part(
            id: 'tool-image',
            messageID: 'assistant-image',
            type: 'tool',
            toolName: 'browser screenshot',
            toolState: ToolState.fromJson({
              'status': 'completed',
              'title': 'Capture CAPTCHA',
              'input': const <String, dynamic>{},
              'output': {'filePath': path},
            }),
          ),
        ]),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    expect(api.fileContentRequests, [path]);
    expect(find.byKey(const Key('tool-output-image')), findsOneWidget);
    expect(
      find.bySemanticsLabel('Preview generated image captcha-r1.png'),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const Key('tool-output-image')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-viewer-image')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-attach')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('kit-viewer-more')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-download')), findsOneWidget);
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('file-preview-attach')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsNothing);
    expect(find.bySemanticsLabel('Remove captcha-r1.png'), findsOneWidget);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Please inspect this CAPTCHA.',
    );
    await tester.pump();
    final send = find.byKey(const Key('chat-send-button'));
    expect(send.hitTestable(), findsOneWidget);
    await tester.tap(send);
    await tester.pumpAndSettle();

    expect(api.prompts.single.text, 'Please inspect this CAPTCHA.');
    expect(api.prompts.single.attachments.single.filename, 'captcha-r1.png');
    expect(api.prompts.single.attachments.single.mime, 'image/png');
    expect(
      api.prompts.single.attachments.single.url,
      startsWith('data:image/png;base64,'),
    );
  });

  testWidgets('tool output previews wait for the wake-time transport', (
    tester,
  ) async {
    const path = '/tmp/opencode/shots/after-wake.png';
    final retainedApi = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-after-wake', 'assistant', [
          Part(
            id: 'tool-after-wake',
            messageID: 'assistant-after-wake',
            type: 'tool',
            toolName: 'browser screenshot',
            toolState: ToolState.fromJson({
              'status': 'completed',
              'title': 'Capture after wake',
              'input': const <String, dynamic>{},
              'output': {'filePath': path},
            }),
          ),
        ]),
      ];
    final replacementApi = _FakeOpenCodeApi()
      ..fileContents[path] = const FileContent(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZKgAAAAASUVORK5CYII=',
        type: 'binary',
        encoding: 'base64',
        mimeType: 'image/png',
      );
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final readyApi = Completer<OpenCodeApi?>();
    final controller = _DelayedActionController(
      ProfileStore(prefs: prefs),
      readyApi,
    )..api = retainedApi;

    await _pumpChat(tester, retainedApi, controller: controller);

    expect(retainedApi.fileContentRequests, isEmpty);
    expect(replacementApi.fileContentRequests, isEmpty);

    readyApi.complete(replacementApi);
    await tester.pumpAndSettle();

    expect(retainedApi.fileContentRequests, isEmpty);
    expect(replacementApi.fileContentRequests, [path]);
    expect(find.byKey(const Key('tool-output-image')), findsOneWidget);
  });

  testWidgets('opens non-image tool files in the shared preview', (
    tester,
  ) async {
    const path = '/tmp/opencode/report.md';
    final api = _FakeOpenCodeApi()
      ..fileContents[path] = const FileContent(
        '# Review me\n\n| File | Status |\n| --- | --- |\n| api.dart | Ready |',
        mimeType: 'text/markdown',
      )
      ..messagesHandler = (_) async => [
        _message('assistant-file', 'assistant', [
          Part(
            id: 'tool-file',
            messageID: 'assistant-file',
            type: 'tool',
            toolName: 'write',
            toolState: ToolState.fromJson({
              'status': 'completed',
              'output': {'filePath': path},
            }),
          ),
        ]),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    expect(api.fileContentRequests, isEmpty);
    expect(find.byKey(const Key('tool-output-file')), findsOneWidget);
    await tester.tap(find.byKey(const Key('tool-output-file')));
    await tester.pumpAndSettle();

    expect(api.fileContentRequests, [path]);
    expect(find.byType(KitMarkdown), findsWidgets);
    expect(find.text('api.dart'), findsOneWidget);
    expect(find.byKey(const Key('file-preview-attach')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('kit-viewer-more')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-download')), findsOneWidget);
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('kit-viewer-more')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('kit-viewer-menu-source')));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('| File | Status |', findRichText: true),
      findsOneWidget,
    );
  });

  testWidgets(
    'generated CSV opens as a literal table without sending a prompt',
    (tester) async {
      const path = '/tmp/opencode/report.csv';
      final api = _FakeOpenCodeApi()
        ..fileContents[path] = const FileContent(
          'name,value\nentry,=SUM(A1)\n',
          mimeType: 'text/csv',
        )
        ..messagesHandler = (_) async => [
          _message('assistant-csv', 'assistant', [
            Part(
              id: 'tool-csv',
              messageID: 'assistant-csv',
              type: 'tool',
              toolName: 'write',
              toolState: ToolState.fromJson({
                'status': 'completed',
                'output': {'filePath': path},
              }),
            ),
          ]),
        ];
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('tool-output-file')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
      expect(find.byKey(const ValueKey('kit-viewer-table')), findsOneWidget);
      expect(find.text(KitBidi.auto('name')), findsOneWidget);
      expect(find.text(KitBidi.auto('value')), findsOneWidget);
      expect(find.text(KitBidi.auto('=SUM(A1)')), findsOneWidget);
      expect(api.prompts, isEmpty);
    },
  );

  testWidgets('a finished turn gathers its tool chain into one work line; '
      'the words it said stay in view', (tester) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-tools', 'assistant', [
          Part(
            id: 'read-1',
            messageID: 'assistant-tools',
            type: 'tool',
            toolName: 'read',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {'filePath': '/workspace/lib/main.dart'},
              'output': '<content>\n1: void main() {}\n</content>',
            }, toolName: 'read'),
          ),
          Part(
            id: 'grep-1',
            messageID: 'assistant-tools',
            type: 'tool',
            toolName: 'grep',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {'pattern': 'main', 'path': '/workspace/lib'},
              'output': 'lib/main.dart:1:void main() {}',
              'metadata': {'matches': 1},
            }, toolName: 'grep'),
          ),
          Part(
            id: 'shell-1',
            messageID: 'assistant-tools',
            type: 'tool',
            toolName: 'bash',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {'command': 'flutter test'},
              'output': 'All tests passed.',
              'metadata': {'exit': 0},
            }, toolName: 'bash'),
          ),
          Part(
            id: 'text-boundary',
            messageID: 'assistant-tools',
            type: 'text',
            text: 'Tool chain finished.',
          ),
          Part(
            id: 'edit-1',
            messageID: 'assistant-tools',
            type: 'tool',
            toolName: 'edit',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {
                'filePath': '/workspace/lib/main.dart',
                'oldString': 'old',
                'newString': 'new',
              },
              'output': 'done',
            }, toolName: 'edit'),
          ),
          Part(
            id: 'write-1',
            messageID: 'assistant-tools',
            type: 'tool',
            toolName: 'write',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {'filePath': '/workspace/notes.md', 'content': '# Done'},
              'output': 'done',
            }, toolName: 'write'),
          ),
        ]),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    // The turn is over: its work is one line (the turn model, gap 17), and
    // the words stay below it.
    expect(find.byKey(const Key('work-group')), findsOneWidget);
    // What was done is the title; no generic "Tools" beside it.
    expect(find.text('Tools'), findsNothing);
    expect(find.textContaining('Read 1 file'), findsOneWidget);
    expect(find.textContaining('dited 2 files'), findsOneWidget);
    expect(find.text('Tool chain finished.'), findsOneWidget);
    expect(
      tester.getTopLeft(find.byKey(const Key('work-group'))).dy,
      lessThan(tester.getTopLeft(find.text('Tool chain finished.')).dy),
    );
    expect(find.text('Shell'), findsNothing);
    expect(find.text('Read'), findsNothing);
    expect(find.text('Search text'), findsNothing);
    expect(find.text('Edit'), findsNothing);

    final headers = find.byKey(const Key('work-group-header'));
    expect(tester.getSize(headers.first).height, greaterThanOrEqualTo(48));
    await tester.tap(headers.first);
    await tester.pumpAndSettle();

    expect(find.text('Read'), findsOneWidget);
    expect(find.text('Search text'), findsOneWidget);
    expect(find.text('Shell'), findsOneWidget);

    // KitToolRow: a grouped row is a line on the ground surface, never a
    // nested card; at rest it paints no fill of its own.
    final rows = find.byKey(const Key('embedded-tool-row'));
    expect(rows, findsWidgets);
    for (final box in tester.widgetList<AnimatedContainer>(
      find.descendant(of: rows, matching: find.byType(AnimatedContainer)),
    )) {
      final decoration = box.decoration;
      if (decoration is ShapeDecoration) {
        expect(
          decoration.color?.a ?? 0,
          0,
          reason: 'Grouped tool rows must not render nested cards.',
        );
      }
    }
    expect(
      find.descendant(of: rows, matching: find.byType(Card)),
      findsNothing,
    );
  });

  testWidgets('renders grouped tool failures as flat inline results', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-errors', 'assistant', [
          Part(
            id: 'failed-edit',
            messageID: 'assistant-errors',
            type: 'tool',
            toolName: 'edit',
            toolState: ToolState.fromJson(const {
              'status': 'error',
              'input': {'filePath': '/workspace/lib/main.dart'},
              'error': 'Tool execution aborted',
            }, toolName: 'edit'),
          ),
          Part(
            id: 'failed-shell',
            messageID: 'assistant-errors',
            type: 'tool',
            toolName: 'bash',
            toolState: ToolState.fromJson(const {
              'status': 'error',
              'input': {'command': 'flutter test'},
              'error': 'Process exited before completion',
            }, toolName: 'bash'),
          ),
        ]),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('work-group')), findsOneWidget);
    expect(
      find.byKey(const Key('embedded-tool-error-output')),
      findsNWidgets(2),
    );
    expect(find.text('Tool execution aborted'), findsOneWidget);
    expect(find.text('Process exited before completion'), findsOneWidget);
    expect(find.byKey(const Key('standalone-tool-error-output')), findsNothing);

    // Each failure is the step's own capped output block (KitCodeBlock)
    // under its row, carrying the error text; the row already says Failed.
    final outputs = find.byKey(const Key('embedded-tool-error-output'));
    expect(
      find.descendant(of: outputs, matching: find.byType(KitCodeBlock)),
      findsNWidgets(2),
    );
    expect(
      find.descendant(
        of: outputs,
        matching: find.text('Process exited before completion'),
      ),
      findsOneWidget,
    );
  });

  testWidgets('keeps a tool chain growing across assistant records', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-edit', 'assistant', [
          Part(
            id: 'edit-across-message',
            messageID: 'assistant-edit',
            type: 'tool',
            toolName: 'edit',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {
                'filePath': '/workspace/lib/main.dart',
                'oldString': 'old',
                'newString': 'new',
              },
              'output': 'done',
            }, toolName: 'edit'),
          ),
        ], created: 1),
        _message('assistant-shell', 'assistant', [
          Part(
            id: 'shell-across-message',
            messageID: 'assistant-shell',
            type: 'tool',
            toolName: 'bash',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {'command': 'flutter test'},
              'output': 'All tests passed.',
              'metadata': {'exit': 0},
            }, toolName: 'bash'),
          ),
        ], created: 2),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('work-group')), findsOneWidget);
    expect(find.text('Edited 1 file · ran 1 command'), findsOneWidget);
    expect(find.text('Edit'), findsNothing);
    expect(find.text('Shell'), findsNothing);

    await tester.tap(find.byKey(const Key('work-group-header')));
    await tester.pumpAndSettle();

    expect(find.text('Edit'), findsOneWidget);
    expect(find.text('Shell'), findsOneWidget);
  });

  testWidgets('shows model changes and aggregates usage once per turn', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(800, 1400));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-1', 'user', [
          Part(type: 'text', text: 'Inspect this'),
        ], created: 1),
        _message(
          'assistant-0',
          'assistant',
          [Part(type: 'text', text: 'Earlier turn')],
          created: 2,
          providerID: 'provider',
          modelID: 'model-a',
          tokens: Tokens(input: 60, output: 15),
        ),
        _message('user-2', 'user', [
          Part(type: 'text', text: 'Continue'),
        ], created: 3),
        _message(
          'assistant-1',
          'assistant',
          [Part(type: 'text', text: 'First internal step')],
          created: 4,
          providerID: 'provider',
          modelID: 'model-a',
          tokens: Tokens(input: 70, output: 30),
        ),
        _message(
          'assistant-2',
          'assistant',
          [Part(type: 'text', text: 'Second internal step')],
          created: 5,
          providerID: 'provider',
          modelID: 'model-a',
          tokens: Tokens(input: 150, output: 50),
        ),
        _message(
          'assistant-3',
          'assistant',
          [Part(type: 'text', text: 'Model switched here')],
          created: 6,
          providerID: 'provider',
          modelID: 'model-b',
          tokens: Tokens(input: 40, output: 10),
        ),
      ];

    final controller = await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    // A model switch is named once, in the footer of the turn it happened
    // in; usage rides on the timestamps preference. Only the newest turn's
    // footer carries these words (older turns keep Copy and More).
    expect(
      find.textContaining('provider/model-a → provider/model-b'),
      findsOneWidget,
    );
    expect(find.textContaining('350 tok'), findsNothing);
    await controller.setTranscriptTimestampsVisible(true);
    await tester.pumpAndSettle();
    expect(find.textContaining('350 tok'), findsOneWidget);
    expect(find.textContaining('75 tok'), findsNothing);
    Finder usageSegment(String value) => find.byWidgetPredicate((widget) {
      if (widget is! Text || widget.data == null) return false;
      return widget.data!
          .split(RegExp(r'\s+·\s+'))
          .map((segment) => segment.trim())
          .contains(value);
    });
    expect(usageSegment('100 tok'), findsNothing);
    expect(usageSegment('200 tok'), findsNothing);
    expect(usageSegment('50 tok'), findsNothing);
  });

  testWidgets('applies split text, reasoning, and tool input deltas', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-1', 'assistant', [
          Part(id: 'text-1', messageID: 'assistant-1', type: 'text'),
          Part(id: 'reasoning-1', messageID: 'assistant-1', type: 'reasoning'),
          Part(
            id: 'tool-1',
            messageID: 'assistant-1',
            type: 'tool',
            toolName: 'search',
            toolState: ToolState(status: 'running'),
          ),
        ]),
      ];
    final controller = await _pumpChat(tester, api);

    void delta(String partID, String field, String value) {
      controller.handleEventForTesting(
        _event('message.part.delta', {
          'sessionID': 'session-1',
          'messageID': 'assistant-1',
          'partID': partID,
          'field': field,
          'delta': value,
        }),
      );
    }

    delta('text-1', 'text', 'Hel');
    delta('text-1', 'text', 'lo');
    delta('reasoning-1', 'text', '**why ');
    delta('reasoning-1', 'text', 'this works**');
    delta('tool-1', 'input', '{"query":');
    delta('tool-1', 'input', '"chat"}');
    await _pumpEvent(tester);

    expect(find.text('Hello'), findsOneWidget);
    // A one-line thought right before a tool call is that call's title: the
    // step is one row, not a heading row and a tool row.
    expect(find.byKey(const Key('reasoning-toggle')), findsNothing);
    expect(find.text('why this works'), findsOneWidget);
    expect(find.text('**why this works**'), findsNothing);
    expect(find.textContaining('search'), findsOneWidget);
    await tester.tap(find.text('why this works'));
    await _pumpEvent(tester);
    expect(
      jsonDecode(tester.widget<KitCodeBlock>(find.byType(KitCodeBlock)).text),
      {'query': 'chat'},
    );
    semantics.dispose();
  });

  testWidgets('merges consecutive reasoning and assistant text blocks', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-reasoning-1', 'assistant', [
          Part(
            id: 'reasoning-a',
            messageID: 'assistant-reasoning-1',
            type: 'reasoning',
            text: 'First reasoning fragment with enough detail to wrap.',
          ),
        ]),
        _message('assistant-reasoning-2', 'assistant', [
          Part(
            id: 'reasoning-b',
            messageID: 'assistant-reasoning-2',
            type: 'reasoning',
            text: 'Second reasoning fragment continues the same thought.',
          ),
        ], created: 2),
        _message('assistant-text-1', 'assistant', [
          Part(type: 'text', text: 'First answer paragraph.'),
        ], created: 3),
        _message('assistant-text-2', 'assistant', [
          Part(type: 'text', text: 'Second answer paragraph.'),
        ], created: 4),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('assistant-reasoning-block')), findsOneWidget);
    expect(find.byKey(const Key('assistant-text-block')), findsOneWidget);
    expect(find.byKey(const Key('reasoning-toggle')), findsOneWidget);
    await tester.tap(find.byKey(const Key('reasoning-toggle')));
    await tester.pumpAndSettle();
    // The fold is titled by the thought's first line, and opened it reads
    // whole: both fragments in the one block.
    expect(find.textContaining('First reasoning fragment'), findsWidgets);
    expect(find.textContaining('Second reasoning fragment'), findsOneWidget);
    expect(find.text('First answer paragraph.'), findsOneWidget);
    expect(find.text('Second answer paragraph.'), findsOneWidget);
  });

  testWidgets('transcript controls update old messages and persist state', (
    tester,
  ) async {
    const reasoning =
        'This is a deliberately long reasoning explanation that spans several lines on a phone and starts collapsed.';
    final created = DateTime.now().millisecondsSinceEpoch;
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-display', 'user', [
          Part(
            id: 'user-display-text',
            messageID: 'user-display',
            type: 'text',
            text: 'Explain the implementation',
          ),
        ], created: created),
        _message('assistant-display', 'assistant', [
          Part(
            id: 'assistant-display-reasoning',
            messageID: 'assistant-display',
            type: 'reasoning',
            text: reasoning,
          ),
          Part(
            id: 'assistant-display-text',
            messageID: 'assistant-display',
            type: 'text',
            text: 'Here is the implementation.',
          ),
        ], created: created + 1),
      ];

    final controller = await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    expect(find.text(reasoning), findsNothing);
    expect(
      tester
          .widgetList<KitMessage>(find.byType(KitMessage))
          .singleWhere(
            (message) =>
                message.bubbleKey == const ValueKey('user-prompt-user-display'),
          )
          .time,
      isNull,
    );

    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'thinking',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-thinking')));
    await tester.pumpAndSettle();

    expect(controller.transcriptReasoningExpanded, isTrue);
    expect(find.text(reasoning), findsOneWidget);
    // The toggle offers Undo; the bar floats above every sheet until its
    // window ends.
    expect(find.text('Reasoning expanded in the transcript'), findsOneWidget);
    await tester.pump(KitUndo.window);
    await tester.pumpAndSettle();

    // The timestamps toggle is a command in the sheet (slice-P10.1).
    await _runSheetCommand(tester, 'timestamps');

    expect(controller.transcriptTimestampsVisible, isTrue);
    expect(
      tester
          .widgetList<KitMessage>(find.byType(KitMessage))
          .singleWhere(
            (message) =>
                message.bubbleKey == const ValueKey('user-prompt-user-display'),
          )
          .time,
      DateTime.fromMillisecondsSinceEpoch(created),
    );
    expect(
      find.byKey(const Key('message-meta-assistant-display')),
      findsOneWidget,
    );
    await tester.pump(KitUndo.window);
    await tester.pumpAndSettle();
    await _runSheetCommand(tester, 'thinking');

    expect(controller.transcriptReasoningExpanded, isFalse);
    expect(find.text(reasoning), findsNothing);
    await tester.pump(KitUndo.window);
    await tester.pumpAndSettle();
  });

  testWidgets('the transcript-wide reasoning toggle replaces per-part choices '
      'instead of stamping over them', (tester) async {
    const first =
        'A deliberately long first reasoning explanation that spans several lines on a phone and so starts collapsed.';
    const second =
        'A deliberately long second reasoning explanation that also spans several lines and starts collapsed too.';
    final created = DateTime.now().millisecondsSinceEpoch;
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-one', 'assistant', [
          Part(
            id: 'reasoning-one',
            messageID: 'assistant-one',
            type: 'reasoning',
            text: first,
          ),
          Part(
            id: 'text-one',
            messageID: 'assistant-one',
            type: 'text',
            text: 'First answer.',
          ),
        ], created: created),
        // Two turns: within one, a finished turn folds its earlier thoughts.
        _message('user-two', 'user', [
          Part(
            id: 'user-two-text',
            messageID: 'user-two',
            type: 'text',
            text: 'And again',
          ),
        ], created: created),
        _message('assistant-two', 'assistant', [
          Part(
            id: 'reasoning-two',
            messageID: 'assistant-two',
            type: 'reasoning',
            text: second,
          ),
          Part(
            id: 'text-two',
            messageID: 'assistant-two',
            type: 'text',
            text: 'Second answer.',
          ),
        ], created: created + 1),
      ];

    final controller = await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    expect(find.text(first), findsNothing);
    expect(find.text(second), findsNothing);

    // One per-part expansion, which the session store remembers. The
    // transcript renders reversed, so assert on the count rather than on
    // which of the two blocks the first toggle belongs to.
    await tester.tap(find.byKey(const Key('reasoning-toggle')).first);
    await tester.pumpAndSettle();
    expect(
      find.text(first).evaluate().length + find.text(second).evaluate().length,
      1,
    );

    Future<void> flipGlobal() async {
      await _runSheetCommand(tester, 'thinking');
      await tester.pump(KitUndo.window);
      await tester.pumpAndSettle();
    }

    // The transcript-wide default wins while it is being set...
    await flipGlobal();
    expect(controller.transcriptReasoningExpanded, isTrue);
    expect(find.text(first), findsOneWidget);
    expect(find.text(second), findsOneWidget);

    // ...and flipping it back collapses everything, including the block the
    // user had opened, rather than leaving the store stamped with values the
    // user never chose.
    await flipGlobal();
    expect(controller.transcriptReasoningExpanded, isFalse);
    expect(find.text(first), findsNothing);
    expect(find.text(second), findsNothing);

    // Per-part control still works after the round trip.
    await tester.tap(find.byKey(const Key('reasoning-toggle')).last);
    await tester.pumpAndSettle();
    expect(
      find.text(first).evaluate().length + find.text(second).evaluate().length,
      1,
    );
  });

  testWidgets('command launcher maps diff to the native session viewer', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..diffs = [
        FileDiff(file: '/workspace/lib/chat.dart', additions: 7, deletions: 2),
      ]
      ..todoItems = [
        Todo(content: 'Flatten groups', status: 'completed'),
        Todo(content: 'Verify review', status: 'pending'),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    // Changes live in the conversation menu's Go to (slice-P10.2).
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-changes')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('review-workspace')), findsOneWidget);
    expect(find.text('\u2066chat.dart\u2069'), findsOneWidget);
    expect(find.text('+7 −2'), findsOneWidget);
  });

  testWidgets('command launcher maps context to the native usage surface', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-context', 'user', [
          Part(type: 'text', text: 'Inspect context'),
        ], created: 1),
        _message(
          'assistant-context',
          'assistant',
          [Part(type: 'text', text: 'Context ready')],
          created: 2,
          providerID: 'openai',
          modelID: 'gpt-context',
          tokens: Tokens(input: 700, output: 40, cacheRead: 260),
          cost: .031,
        ),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    // Details (the conversation's context page) is the menu's Go to.
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-details')));
    await tester.pumpAndSettle();

    expect(find.byType(SessionContextScreen), findsOneWidget);
    expect(
      find.text('1,000 tokens · limit unavailable'),
      findsOneWidget,
      reason: tester
          .widgetList<Text>(
            find.descendant(
              of: find.byType(SessionContextScreen),
              matching: find.byType(Text),
            ),
          )
          .map((w) => w.data ?? w.textSpan?.toPlainText())
          .join(' | '),
    );
  });

  testWidgets('debug command opens native app diagnostics', (tester) async {
    await _pumpChat(tester, _FakeOpenCodeApi());
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'debug',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-debug')));
    await tester.pumpAndSettle();

    expect(find.byType(AppDiagnosticsScreen), findsOneWidget);
    expect(find.text('Report a problem'), findsOneWidget);
    expect(
      find.text(
        'Say what went wrong. You see the whole report before anything leaves this phone.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('health command opens native project health', (tester) async {
    final repository = _DestinationRepository();
    await _pumpChat(tester, _FakeOpenCodeApi(), repository: repository);
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'health',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-health')));
    await tester.pumpAndSettle();

    expect(find.byType(ProjectHealthScreen), findsOneWidget);
    expect(find.text('feature/mobile'), findsOneWidget);
  });

  testWidgets('sessions command opens the all-project native finder', (
    tester,
  ) async {
    await _pumpChat(tester, _FakeOpenCodeApi());
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'sessions',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-sessions')));
    await tester.pumpAndSettle();

    expect(find.byType(GlobalSessionsScreen), findsOneWidget);
    expect(find.text('All conversations'), findsOneWidget);
  });

  // Navigation commands land on the same screens as the Settings hub rows,
  // so a typed alias and the hub never disagree about where a thing lives.
  for (final entry in <String, (Type, bool Function(Widget))>{
    'status': (ServerSettingsScreen, (_) => true),
    'connect': (
      IntegrationsScreen,
      (widget) =>
          (widget as IntegrationsScreen).mode == IntegrationsMode.providers,
    ),
    'mcps': (
      IntegrationsScreen,
      (widget) => (widget as IntegrationsScreen).mode == IntegrationsMode.mcp,
    ),
    'tools': (
      CapabilitiesScreen,
      (widget) => (widget as CapabilitiesScreen).initialTab == 1,
    ),
  }.entries) {
    testWidgets('${entry.key} command opens its Settings hub destination', (
      tester,
    ) async {
      await _pumpChat(
        tester,
        _FakeOpenCodeApi(),
        repository: _DestinationRepository(),
      );
      await _useComposerTool(tester, 'commands');
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('command-launcher-search')),
        entry.key,
      );
      await tester.pump();
      await tester.tap(find.byKey(Key('command-mobile-${entry.key}')));
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));

      final destination = find.byType(entry.value.$1);
      expect(destination, findsOneWidget);
      expect(entry.value.$2(tester.widget(destination)), isTrue);
      // Never the whole hub under a misleading name.
      expect(find.byType(SettingsScreen), findsNothing);
      if (entry.key == 'tools') {
        expect(find.byType(ToolsScreen), findsOneWidget);
      }
    });
  }

  testWidgets('themes command opens Settings › Appearance', (tester) async {
    final controller = await _pumpChat(tester, _FakeOpenCodeApi());
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'themes',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-themes')));
    await tester.pumpAndSettle();

    expect(find.byType(AppearanceSettingsScreen), findsOneWidget);
    // Light or dark is chosen inline and applies at once (the separate
    // light-or-dark sheet was removed by slice-P3.1).
    final light = find.byKey(const ValueKey('appearance-mode-light'));
    await tester.ensureVisible(light);
    await tester.pumpAndSettle();
    await tester.tap(light);
    await tester.pumpAndSettle();

    expect(controller.appearance.value, AppAppearance.light);
  });

  testWidgets('the menu\'s Tasks lands on the plan in the transcript, open', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-plan', 'assistant', [
          Part(
            id: 'tool-plan',
            messageID: 'assistant-plan',
            type: 'tool',
            toolName: 'todowrite',
            toolState: ToolState.fromJson({
              'status': 'completed',
              'input': {
                'todos': [
                  {
                    'content': 'Verify production release',
                    'status': 'pending',
                    'priority': 'high',
                  },
                ],
              },
              'output': '',
            }),
          ),
          Part(
            id: 'text-plan',
            messageID: 'assistant-plan',
            type: 'text',
            text: 'Planned the release check.',
          ),
        ]),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    // The plan's step shows closed until asked for.
    expect(find.text('Verify production release'), findsNothing);

    // The plan is a command in the sheet now (slice-P10.1).
    await _runSheetCommand(tester, 'plan');

    // No sheet: the transcript's own checklist, opened in place.
    expect(find.byKey(const Key('timeline-sheet')), findsNothing);
    expect(find.text('Verify production release'), findsOneWidget);
    expect(find.text('Pending'), findsWidgets);
  });

  testWidgets('no plan in the transcript, no Tasks entry in the menu', (
    tester,
  ) async {
    await _pumpChat(tester, _FakeOpenCodeApi());
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();

    expect(find.text('Tasks'), findsNothing);
  });

  testWidgets('launcher combines mobile actions with server commands', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final repository = _FakeProductRepository(const [
      CommandInfo(
        name: 'review',
        description: 'Review current changes',
        subtask: false,
      ),
      CommandInfo(
        name: 'init',
        description: 'Create project guidance',
        subtask: false,
      ),
    ]);

    await _pumpChat(tester, api, repository: repository);
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'models',
    );
    await tester.pump();
    expect(find.text('/models'), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'open',
    );
    await tester.pump();
    expect(find.text('/files'), findsOneWidget);
    expect(find.text('/editor'), findsNothing);
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'review',
    );
    await tester.pump();
    expect(find.textContaining('/review', findRichText: true), findsOneWidget);
    await tester.tap(find.byKey(const Key('command-server-review')));
    await tester.pumpAndSettle();
    final composer = tester.widget<TextField>(
      _textField(find.byKey(const Key('chat-composer-field'))),
    );
    expect(composer.controller?.text, '/review ');
  });

  testWidgets('/files attaches a project artifact back to the chat', (
    tester,
  ) async {
    const path = 'docs/review.md';
    final api = _FakeOpenCodeApi()
      ..projectFiles = [FileNode(name: 'review.md', path: path, isDir: false)]
      ..fileContents[path] = const FileContent(
        '# Review\n\nPlease check this file.',
        mimeType: 'text/markdown',
      );

    await _pumpChat(tester, api);
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'open',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-files')));
    await tester.pumpAndSettle();

    expect(find.text('review.md'), findsOneWidget);
    await tester.tap(find.text('review.md'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('kit-viewer-more')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('project-file-attach')), findsOneWidget);
    expect(find.byKey(const Key('project-file-download')), findsOneWidget);

    await tester.tap(find.byKey(const Key('project-file-attach')));
    await tester.pumpAndSettle();
    expect(
      find.text(
        'review.md attached. Return to the conversation to add your comment.',
      ),
      findsOneWidget,
    );

    // Attaching closes the viewer and returns to the file tree.
    expect(find.byKey(const ValueKey('files-viewer')), findsNothing);
    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(find.bySemanticsLabel('Remove review.md'), findsOneWidget);
  });

  testWidgets('move, warp, and org commands preserve their server semantics', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final repository = _DestinationRepository();
    final controller = await _controller(api, savedProfile: true);
    controller
      ..repository = repository
      ..directory = '/work/acme'
      ..workspace = 'workspace-1'
      ..sessionsById['session-1'] = Session(
        id: 'session-1',
        title: 'Mobile work',
        projectID: 'project-1',
        workspaceID: 'workspace-1',
        directory: '/work/acme',
      );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pumpAndSettle();

    Future<void> openCommand(String command) async {
      await _useComposerTool(tester, 'commands');
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('command-launcher-search')),
        command,
      );
      await tester.pump();
      await tester.tap(find.byKey(Key('command-mobile-$command')));
      await tester.pumpAndSettle();
    }

    await openCommand('move');
    expect(find.byKey(const Key('move-session-sheet')), findsOneWidget);
    expect(find.text('Current'), findsOneWidget);
    await tester.tap(find.byKey(const Key('move-destination-/work/acme-copy')));
    await tester.pumpAndSettle();
    expect(find.textContaining('1 changed file is present.'), findsOneWidget);
    await tester.tap(find.byKey(const Key('session-destination-confirm')));
    await tester.pumpAndSettle();
    expect(repository.movedDirectory, '/work/acme-copy');
    expect(repository.movedChanges, isTrue);
    expect(repository.reminderDirectory, '/work/acme-copy');
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();

    await openCommand('warp');
    expect(find.byKey(const Key('warp-session-sheet')), findsOneWidget);
    expect(find.text('Offline cloud'), findsOneWidget);
    await tester.tap(find.byKey(const Key('warp-destination-workspace-2')));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const Key('session-destination-without-changes')),
    );
    await tester.pumpAndSettle();
    expect(repository.warpedWorkspaceID, 'workspace-2');
    expect(repository.copiedChanges, isFalse);
    expect(repository.reminderDirectory, '/remote/review');
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();

    await openCommand('org');
    expect(find.byKey(const Key('console-organization-sheet')), findsOneWidget);
    expect(find.text('Current org'), findsOneWidget);
    await tester.tap(find.byKey(const Key('console-org-account-1-org-next')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('console-org-confirm')));
    await tester.pumpAndSettle();
    expect(repository.switchedOrganization?.orgID, 'org-next');
  });

  testWidgets('move fails closed when working changes cannot be inspected', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final repository = _DestinationRepository(healthError: true);
    final controller = await _controller(api, savedProfile: true);
    controller
      ..repository = repository
      ..directory = '/work/acme'
      ..sessionsById['session-1'] = Session(
        id: 'session-1',
        projectID: 'project-1',
        directory: '/work/acme',
      );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'move',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-move')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('move-destination-/work/acme-copy')));
    await tester.pumpAndSettle();

    expect(
      find.textContaining('could not inspect working changes'),
      findsOneWidget,
    );
    expect(
      find.byKey(const Key('session-destination-without-changes')),
      findsNothing,
    );
    await tester.tap(find.byKey(const Key('session-destination-confirm')));
    await tester.pumpAndSettle();
    expect(repository.movedChanges, isFalse);
  });

  testWidgets('prompt editor preserves selection, attachments, and cancel', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,

          home: ChatScreen(
            sessionID: 'session-1',
            initialAttachments: [
              PromptAttachment(
                mime: 'text/plain',
                filename: 'notes.txt',
                url: 'data:text/plain;base64,bm90ZXM=',
              ),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final composerFinder = find.byKey(const Key('chat-composer-field'));
    final composer = tester
        .widget<TextField>(_textField(composerFinder))
        .controller!;
    composer.value = const TextEditingValue(
      text: 'Original prompt draft',
      selection: TextSelection(baseOffset: 2, extentOffset: 10),
    );
    await tester.pump();

    expect(find.byKey(const Key('prompt-editor-button')), findsOneWidget);
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'editor',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-editor')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('prompt-editor-screen')), findsOneWidget);
    final editor = tester
        .widget<TextField>(
          _textField(find.byKey(const Key('prompt-editor-field'))),
        )
        .controller!;
    expect(editor.text, 'Original prompt draft');
    expect(
      editor.selection,
      const TextSelection(baseOffset: 2, extentOffset: 10),
    );
    editor.selection = const TextSelection.collapsed(offset: 4);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Discard prompt changes?'), findsNothing);
    expect(
      composer.selection,
      const TextSelection(baseOffset: 2, extentOffset: 10),
    );

    await tester.tap(find.byKey(const Key('prompt-editor-button')));
    await tester.pumpAndSettle();
    final discardEditor = tester
        .widget<TextField>(
          _textField(find.byKey(const Key('prompt-editor-field'))),
        )
        .controller!;
    discardEditor.value = const TextEditingValue(
      text: 'Discarded edit',
      selection: TextSelection.collapsed(offset: 5),
    );
    await tester.tap(find.bySemanticsLabel('Remove notes.txt'));
    await tester.pump();
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Discard prompt changes?'), findsOneWidget);
    await tester.tap(find.text('Keep editing'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('prompt-editor-screen')), findsOneWidget);

    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Discard changes'));
    await tester.pumpAndSettle();
    expect(composer.text, 'Original prompt draft');
    expect(find.bySemanticsLabel('Remove notes.txt'), findsOneWidget);

    await tester.tap(find.byKey(const Key('prompt-editor-button')));
    await tester.pumpAndSettle();
    final savedEditor = tester
        .widget<TextField>(
          _textField(find.byKey(const Key('prompt-editor-field'))),
        )
        .controller!;
    savedEditor.value = const TextEditingValue(
      text: 'Final edited prompt',
      selection: TextSelection.collapsed(offset: 7),
    );
    await tester.tap(find.bySemanticsLabel('Remove notes.txt'));
    await tester.pump();
    await tester.tap(find.byKey(const Key('prompt-editor-done')));
    await tester.pumpAndSettle();

    expect(composer.text, 'Final edited prompt');
    expect(composer.selection, const TextSelection.collapsed(offset: 7));
    expect(find.bySemanticsLabel('Remove notes.txt'), findsNothing);
    expect(api.promptCalls, 0);
  });

  testWidgets('timeline finds a stable anchor with reduced motion', (
    tester,
  ) async {
    final messages = <MessageWithParts>[];
    for (var index = 0; index < 30; index += 1) {
      messages.add(
        _message('user-$index', 'user', [
          Part(
            id: 'part-$index',
            messageID: 'user-$index',
            type: 'text',
            text: index == 0 ? 'oldest anchor prompt' : 'prompt $index',
          ),
        ], created: index + 1),
      );
    }
    final api = _FakeOpenCodeApi()..messagesHandler = (_) async => messages;

    await _pumpChat(tester, api, reduceMotion: true);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    // The sheet scrolls at 320dp with 2x text; the chip stays reachable.
    await tester.ensureVisible(
      find.byKey(const ValueKey('session-menu-timeline')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('timeline-search')),
      'oldest anchor',
    );
    await tester.pump();
    expect(find.byKey(const Key('timeline-row-user-0')), findsOneWidget);

    await tester.tap(find.byKey(const Key('timeline-row-user-0')));
    await tester.pumpAndSettle();

    // A match in a prompt's own words is highlighted in place: no excerpt
    // repeating the prompt above it (review board: find bar).
    expect(
      find.descendant(
        of: find.byType(KitMarkdown),
        matching: find.text('oldest anchor prompt'),
      ),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('transcript-match-user-0/0/0')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('user-prompt-user-0')).hitTestable(),
      findsOneWidget,
    );
    // The found turn carries the find band (painted around it, so nothing
    // moves), with no fade under reduced motion; its neighbour has none.
    Finder turnOf(String id) => find.ancestor(
      of: find.byKey(ValueKey('user-prompt-$id')),
      matching: find.byType(KitTurn),
    );
    bool banded(String id) => tester
        .widgetList<CustomPaint>(
          find.descendant(of: turnOf(id), matching: find.byType(CustomPaint)),
        )
        .any((paint) => paint.painter != null);
    expect(turnOf('user-0'), findsOneWidget);
    expect(banded('user-0'), isTrue);
    final band = tester.widget<TweenAnimationBuilder<double>>(
      find
          .descendant(
            of: turnOf('user-0'),
            matching: find.byType(TweenAnimationBuilder<double>),
          )
          .first,
    );
    expect(band.duration, Duration.zero);
    if (find
        .byKey(const ValueKey('user-prompt-user-1'))
        .evaluate()
        .isNotEmpty) {
      expect(banded('user-1'), isFalse);
    }
  });

  testWidgets('fork from prompt restores text and file in the new composer', (
    tester,
  ) async {
    final prompt = _message('user-restore', 'user', [
      Part(
        id: 'text-restore',
        messageID: 'user-restore',
        type: 'text',
        text: 'Review this design',
      ),
      Part(
        id: 'file-restore',
        messageID: 'user-restore',
        type: 'file',
        filename: 'design.png',
        mime: 'image/png',
        url: 'data:image/png;base64,iVBORw0KGgo=',
      ),
    ]);
    final api = _FakeOpenCodeApi()..messagesHandler = (_) async => [prompt];
    final repository = _FakeProductRepository(const []);

    await _pumpChat(tester, api, repository: repository);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('timeline-fork-user-restore')));
    await tester.pumpAndSettle();

    expect(repository.forkCalls, 1);
    expect(repository.forkMessageID, 'user-restore');
    final composer = tester.widget<TextField>(
      _textField(find.byKey(const Key('chat-composer-field'))),
    );
    expect(composer.controller?.text, 'Review this design');
    expect(find.bySemanticsLabel('Remove design.png'), findsOneWidget);
  });

  testWidgets('session repository actions wait for the wake-time replacement', (
    tester,
  ) async {
    final prompt = _message('user-after-wake', 'user', [
      Part(
        id: 'text-after-wake',
        messageID: 'user-after-wake',
        type: 'text',
        text: 'Fork after wake',
      ),
    ]);
    final api = _FakeOpenCodeApi()..messagesHandler = (_) async => [prompt];
    final retainedRepository = _FakeProductRepository(const []);
    final replacementRepository = _FakeProductRepository(const []);
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final readyRepository = Completer<ProductRepository?>();
    final controller = _DelayedRepositoryController(
      ProfileStore(prefs: prefs),
      readyRepository,
    )..api = api;

    await _pumpChat(
      tester,
      api,
      repository: retainedRepository,
      controller: controller,
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('timeline-fork-user-after-wake')));
    await tester.pump();

    expect(retainedRepository.forkCalls, 0);
    expect(replacementRepository.forkCalls, 0);

    readyRepository.complete(replacementRepository);
    await tester.pumpAndSettle();

    expect(retainedRepository.forkCalls, 0);
    expect(replacementRepository.forkCalls, 1);
    expect(replacementRepository.forkMessageID, 'user-after-wake');
  });

  testWidgets('/fork lists prompts and forks the selected message point', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-fork-command', 'user', [
          Part(
            id: 'fork-command-text',
            messageID: 'user-fork-command',
            type: 'text',
            text: 'Try another implementation',
          ),
        ]),
        _message('assistant-fork-command', 'assistant', [
          Part(
            id: 'assistant-command-text',
            messageID: 'assistant-fork-command',
            type: 'text',
            text: 'Current implementation',
          ),
        ]),
      ];
    final repository = _FakeProductRepository(const []);

    await _pumpChat(tester, api, repository: repository);
    await tester.pumpAndSettle();
    // "/fork" forks the whole conversation and lands in the copy, like the
    // menu's Fork (slice-P10.2: fork lands in one place).
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/fork',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pumpAndSettle();
    expect(repository.forkCalls, 1);
    expect(repository.forkMessageID, isNull);
    expect(
      tester.widget<ChatScreen>(find.byType(ChatScreen)).sessionID,
      'forked-session',
    );
    expect(find.text('Fork from prompt'), findsNothing);
  });

  testWidgets('a prompt picked in the timeline forks from that point', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-fork-command', 'user', [
          Part(
            id: 'fork-command-text',
            messageID: 'user-fork-command',
            type: 'text',
            text: 'Try another implementation',
          ),
        ]),
      ];
    final repository = _FakeProductRepository(const []);
    await _pumpChat(tester, api, repository: repository);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
    await tester.pumpAndSettle();
    final fork = find.byKey(const ValueKey('timeline-fork-user-fork-command'));
    await tester.ensureVisible(fork);
    await tester.pumpAndSettle();
    await tester.tap(fork);
    await tester.pumpAndSettle();

    expect(repository.forkMessageID, 'user-fork-command');
    final composer = tester.widget<TextField>(
      _textField(find.byKey(const Key('chat-composer-field'))),
    );
    expect(composer.controller?.text, 'Try another implementation');
  });

  testWidgets('timeline remains usable at 320dp with 2x text', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-narrow', 'user', [
          Part(
            id: 'part-narrow',
            messageID: 'user-narrow',
            type: 'text',
            text: 'A long prompt that must remain reachable on a narrow phone',
          ),
        ]),
      ];
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: const ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pumpAndSettle();

    // Typed, the command runs as the sheet's row would.
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/timestamps',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pumpAndSettle();
    expect(controller.transcriptTimestampsVisible, isTrue);
    expect(tester.takeException(), isNull);
    await _dismissSheetIfOpen(tester);

    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(
      find.byKey(const ValueKey('session-menu-timeline')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('timeline-search')), findsOneWidget);
    expect(find.byKey(const Key('timeline-row-user-narrow')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'project reference screen adds an upstream directory prompt part',
    (tester) async {
      final api = _FakeOpenCodeApi();
      final repository = _FakeProductRepository(
        const [],
        references: const [
          ReferenceInfo(
            name: 'docs',
            path: '/workspace/../shared-docs',
            description: 'Shared engineering documentation',
          ),
        ],
      );

      await _pumpChat(tester, api, repository: repository);
      await _useComposerTool(tester, 'commands');
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('command-launcher-search')),
        'references',
      );
      await tester.pump();
      await tester.tap(find.byKey(const Key('command-mobile-references')));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Shared engineering documentation'),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const Key('reference-docs')));
      await tester.pumpAndSettle();

      final composer = tester.widget<TextField>(
        _textField(find.byKey(const Key('chat-composer-field'))),
      );
      expect(composer.controller?.text, '@docs');
      expect(find.bySemanticsLabel('Folder, @docs'), findsOneWidget);
      expect(find.bySemanticsLabel('Remove @docs'), findsOneWidget);
      expect(find.bySemanticsLabel('Preview attachment docs'), findsNothing);

      await tester.tap(find.byTooltip('Send'));
      await tester.pumpAndSettle();

      expect(api.prompts.single.text, '@docs');
      expect(api.prompts.single.attachments.single.toJson(), {
        'type': 'file',
        'mime': 'application/x-directory',
        'filename': 'docs',
        'url': 'file:///shared-docs',
      });
    },
  );

  testWidgets('typing slash opens filtered inline command suggestions', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final repository = _FakeProductRepository(const [
      CommandInfo(
        name: 'review',
        description: 'Review current changes',
        subtask: false,
      ),
    ]);

    await _pumpChat(tester, api, repository: repository);
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/rev',
    );
    await tester.pump();

    expect(find.byKey(const Key('inline-command-suggestions')), findsOneWidget);
    expect(find.byKey(const Key('inline-command-review')), findsOneWidget);
    expect(find.text('/models'), findsNothing);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/too',
    );
    await tester.pump();
    expect(find.byKey(const Key('inline-command-tools')), findsOneWidget);
  });

  testWidgets('inline command suggestion rows meet 44dp targets', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final repository = _FakeProductRepository(const [
      CommandInfo(
        name: 'review',
        description: 'Review current changes',
        subtask: false,
      ),
    ]);

    await _pumpChat(tester, api, repository: repository);
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/rev',
    );
    await tester.pump();

    final row = find.byKey(const Key('inline-command-review'));
    expect(row, findsOneWidget);
    expect(tester.getSize(row).height, greaterThanOrEqualTo(44));
  });

  testWidgets(
    'nested code scrolling does not change follow latest and new events respect reading position',
    (tester) async {
      final messages = <MessageWithParts>[
        for (var index = 0; index < 35; index++)
          _message('user-$index', 'user', [
            Part(
              id: 'part-$index',
              messageID: 'user-$index',
              type: 'text',
              text: 'prompt $index',
            ),
          ], created: index + 1),
        _message('code-reply', 'assistant', [
          Part(
            id: 'code-part',
            messageID: 'code-reply',
            type: 'text',
            text: '```text\n${'wide ' * 150}\n```',
          ),
        ], created: 40),
      ];
      final api = _FakeOpenCodeApi()..messagesHandler = (_) async => messages;
      final controller = await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      final horizontal = find.descendant(
        of: find.byType(KitCodeBlock),
        matching: find.byWidgetPredicate(
          (w) =>
              w is SingleChildScrollView &&
              w.scrollDirection == Axis.horizontal,
        ),
      );
      expect(horizontal, findsOneWidget);
      await tester.drag(horizontal, const Offset(-650, 0));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('jump-to-latest')).hitTestable(),
        findsNothing,
      );
      await tester.drag(
        find.byType(ScrollablePositionedList),
        const Offset(0, 900),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('jump-to-latest')).hitTestable(),
        findsOneWidget,
      );
      controller.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'new-stream',
            'sessionID': 'session-1',
            'role': 'assistant',
            'time': {'created': 100},
          },
        }),
      );
      controller.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': _partJson(
            id: 'new-part',
            messageID: 'new-stream',
            type: 'text',
            text: 'Later streamed reply',
          ),
        }),
      );
      await _pumpEvent(tester);
      expect(
        find.text('Later streamed reply', findRichText: true),
        findsNothing,
      );
      await tester.tap(find.byKey(const ValueKey('jump-to-latest')));
      // The unfinished assistant deliberately keeps its streaming indicator
      // active. Advance the navigation animation without waiting for idle.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pump(const Duration(milliseconds: 500));
      expect(
        find.text('Later streamed reply', findRichText: true),
        findsOneWidget,
      );
    },
  );

  testWidgets('floating transcript pills meet tap-target minimums', (
    tester,
  ) async {
    final messages = <MessageWithParts>[
      for (var index = 0; index < 35; index += 1)
        _message('user-$index', 'user', [
          Part(
            id: 'part-$index',
            messageID: 'user-$index',
            type: 'text',
            text: 'prompt $index',
          ),
        ], created: index + 1),
    ];
    final api = _FakeOpenCodeApi()..messagesHandler = (_) async => messages;
    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    // Both pills gate on being scrolled away from the latest message.
    await tester.drag(
      find.text('prompt 34'),
      const Offset(0, 600),
      warnIfMissed: false,
    );
    await tester.pumpAndSettle();

    final earlier = find.byKey(const ValueKey('earlier-messages-pill'));
    expect(earlier, findsOneWidget);
    expect(tester.getSize(earlier).height, greaterThanOrEqualTo(44));

    final jump = find.byKey(const ValueKey('jump-to-latest'));
    expect(jump, findsOneWidget);
    final jumpSize = tester.getSize(jump);
    expect(jumpSize.width, greaterThanOrEqualTo(48));
    expect(jumpSize.height, greaterThanOrEqualTo(48));
  });

  testWidgets(
    'searchable sheets keep drag handles and dismiss the keyboard on drag',
    (tester) async {
      final messages = <MessageWithParts>[
        for (var index = 0; index < 8; index += 1)
          _message('user-$index', 'user', [
            Part(
              id: 'part-$index',
              messageID: 'user-$index',
              type: 'text',
              text: 'prompt $index',
            ),
          ], created: index + 1),
      ];
      final api = _FakeOpenCodeApi()..messagesHandler = (_) async => messages;
      // The product theme supplies the default drag handle under test.
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,

            theme: AppTheme.dark(),
            home: const ChatScreen(sessionID: 'session-1'),
          ),
        ),
      );
      await tester.pump();
      await tester.pump();
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('Conversation menu'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
      await tester.pumpAndSettle();

      // The sheet no longer opts out of the theme drag handle.
      expect(
        tester.widget<BottomSheet>(find.byType(BottomSheet)).showDragHandle,
        isNot(false),
      );
      // The kit sheet frame's one scroll carries the rows.
      final timelineList = tester.widget<CustomScrollView>(
        find.descendant(
          of: find.byKey(const ValueKey('timeline-sheet')),
          matching: find.byType(CustomScrollView),
        ),
      );
      expect(
        timelineList.keyboardDismissBehavior,
        ScrollViewKeyboardDismissBehavior.onDrag,
      );

      // Focus the search, then drag the results: the keyboard focus releases.
      await tester.tap(find.byKey(const Key('timeline-search')));
      await tester.pump();
      final editable = tester.widget<EditableText>(
        find.descendant(
          of: find.byKey(const Key('timeline-search')),
          matching: find.byType(EditableText),
        ),
      );
      expect(editable.focusNode.hasFocus, isTrue);
      // Dragging the rows scrolls the sheet's body, which releases the
      // keyboard focus.
      await tester.drag(
        find.byKey(const ValueKey('timeline-row-user-7')),
        const Offset(0, -300),
        warnIfMissed: false,
      );
      await tester.pumpAndSettle();
      await tester.drag(
        find.byKey(const ValueKey('timeline-row-user-7')),
        const Offset(0, -120),
        warnIfMissed: false,
      );
      await tester.pumpAndSettle();
      expect(editable.focusNode.hasFocus, isFalse);

      await tester.tap(find.byTooltip('Close'));
      await tester.pumpAndSettle();

      await _useComposerTool(tester, 'commands');
      await tester.pumpAndSettle();
      expect(
        tester.widget<BottomSheet>(find.byType(BottomSheet)).showDragHandle,
        isNot(false),
      );
      // Focus the launcher's search, then drag its list: the keyboard
      // focus releases here too.
      await tester.tap(find.byKey(const Key('command-launcher-search')));
      await tester.pump();
      final launcherSearch = tester.widget<EditableText>(
        find.descendant(
          of: find.byKey(const Key('command-launcher-search')),
          matching: find.byType(EditableText),
        ),
      );
      expect(launcherSearch.focusNode.hasFocus, isTrue);
      // The list is taller than the sheet: drag from its visible top.
      await tester.dragFrom(
        tester.getTopLeft(find.byKey(const Key('command-launcher-list'))) +
            const Offset(24, 24),
        const Offset(0, -120),
      );
      await tester.pumpAndSettle();
      expect(launcherSearch.focusNode.hasFocus, isFalse);
    },
  );

  testWidgets('composer tools delegates only to visible server subagents', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final controller = _StaticCatalogController(ProfileStore(prefs: prefs))
      ..api = _FakeOpenCodeApi()
      ..status = StreamStatus.connected
      ..catalog = const CatalogSnapshot(
        providers: [],
        models: [],
        agents: [
          CatalogAgent(id: 'build', mode: 'primary', hidden: false),
          CatalogAgent(
            id: 'explore',
            mode: 'subagent',
            hidden: false,
            description: 'Find code and explain how it works',
          ),
          CatalogAgent(id: 'internal', mode: 'subagent', hidden: true),
        ],
      );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: const ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('composer-tools-sheet')), findsNothing);
    expect(find.byKey(const Key('command-launcher-sheet')), findsOneWidget);
    expect(find.text('Commands and agents'), findsOneWidget);
    expect(tester.takeException(), isNull);
    // At 200% text the sheet header scrolls with the body. Reveal the
    // delegate tab before choosing it on this narrow phone viewport.
    final sheetScroll = find
        .descendant(
          of: find.byKey(const Key('command-launcher-sheet')),
          matching: find.byType(Scrollable),
        )
        .first;
    await tester.scrollUntilVisible(
      find.byKey(const Key('composer-tools-agents-tab')),
      100,
      scrollable: sheetScroll,
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('composer-tools-agents-tab')));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.byKey(const Key('composer-agent-explore')),
      100,
      scrollable: sheetScroll,
    );
    await tester.pumpAndSettle();
    expect(find.text('@${KitBidi.auto('explore')}'), findsOneWidget);
    expect(
      find.byKey(const Key('composer-agent-build'), skipOffstage: false),
      findsNothing,
    );
    expect(
      find.byKey(const Key('composer-agent-internal'), skipOffstage: false),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
    await tester.tap(find.byKey(const Key('composer-agent-explore')));
    await tester.pumpAndSettle();

    final composer = tester.widget<TextField>(
      _textField(find.byKey(const Key('chat-composer-field'))),
    );
    expect(composer.controller?.text, '@explore ');
    expect(tester.takeException(), isNull);
  });

  testWidgets('typed agent autocomplete sends exact OpenCode agent parts', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final controller = _StaticCatalogController(ProfileStore(prefs: prefs))
      ..api = api
      ..status = StreamStatus.connected
      ..catalog = const CatalogSnapshot(
        providers: [],
        models: [],
        agents: [
          CatalogAgent(id: 'build', mode: 'primary', hidden: false),
          CatalogAgent(id: 'general', mode: 'subagent', hidden: false),
        ],
      );
    await _pumpChat(tester, api, controller: controller);
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Please ask @gen',
    );
    await tester.pump();
    expect(find.byKey(const Key('inline-agent-general')), findsOneWidget);
    await tester.tap(find.byKey(const Key('inline-agent-general')));
    await tester.pump();
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Please ask @general to inspect this.',
    );
    await tester.tap(find.byTooltip('Send'));
    await tester.pumpAndSettle();

    expect(api.prompts.single.text, 'Please ask @general to inspect this.');
    expect(api.prompts.single.agentMentions, hasLength(1));
    final mention = api.prompts.single.agentMentions.single;
    expect(mention.name, 'general');
    expect(mention.value, '@general');
    expect(mention.start, 11);
    expect(mention.end, 19);
  });

  testWidgets('removes individual parts and complete messages', (tester) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-1', 'assistant', [
          Part(
            id: 'part-1',
            messageID: 'assistant-1',
            type: 'text',
            text: 'remove this part',
          ),
          Part(
            id: 'part-2',
            messageID: 'assistant-1',
            type: 'text',
            text: 'keep until message removal',
          ),
        ]),
      ];
    final controller = await _pumpChat(tester, api);

    controller.handleEventForTesting(
      _event('message.part.removed', {
        'sessionID': 'session-1',
        'messageID': 'assistant-1',
        'partID': 'part-1',
      }),
    );
    await _pumpEvent(tester);
    expect(find.text('remove this part'), findsNothing);
    expect(find.text('keep until message removal'), findsOneWidget);

    controller.handleEventForTesting(
      _event('message.removed', {
        'sessionID': 'session-1',
        'messageID': 'assistant-1',
      }),
    );
    await _pumpEvent(tester);
    expect(find.text('keep until message removal'), findsNothing);
  });

  testWidgets('newer deltas survive an older hydration response', (
    tester,
  ) async {
    final hydration = Completer<List<MessageWithParts>>();
    final api = _FakeOpenCodeApi()..messagesHandler = (_) => hydration.future;
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pump();

    controller.handleEventForTesting(
      _event('message.part.delta', {
        'sessionID': 'session-1',
        'messageID': 'assistant-1',
        'partID': 'part-1',
        'field': 'text',
        'delta': ' fresh',
      }),
    );
    hydration.complete([
      _message('assistant-1', 'assistant', [
        Part(
          id: 'part-1',
          messageID: 'assistant-1',
          type: 'text',
          text: 'stale',
        ),
      ]),
    ]);
    await tester.pumpAndSettle();

    expect(find.text('stale fresh'), findsOneWidget);
    expect(find.text('stale'), findsNothing);
  });

  testWidgets(
    'a prompt with photos is not shown twice when the server re-homes them',
    (tester) async {
      final prompt = Completer<void>();
      final api = _FakeOpenCodeApi()..promptCompleter = prompt;
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,

            home: ChatScreen(
              sessionID: 'session-1',
              initialAttachments: [
                PromptAttachment(
                  mime: 'image/jpeg',
                  filename: '1000101752.jpg',
                  url: 'data:image/jpeg;base64,/9j/4AAQ',
                ),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'match this UI',
      );
      await tester.pump();
      await tester.tap(find.byTooltip('Send'));
      await tester.pump();
      expect(find.text('match this UI'), findsOneWidget);

      // The server's copy: same text and file name, but the file now lives at
      // the server's own location and the type is spelled differently.
      for (final part in [
        _partJson(
          id: 'part-1',
          messageID: 'user-1',
          type: 'text',
          text: 'match this UI',
        ),
        {
          ..._partJson(
            id: 'file-0',
            messageID: 'user-1',
            type: 'file',
            text: '',
          ),
          'filename': '1000101752.jpg',
          'mime': 'image/jpg',
          'url': 'file:///srv/opencode/attachments/ab12/1000101752.jpg',
        },
      ]) {
        controller.handleEventForTesting(
          _event('message.part.updated', {
            'sessionID': 'session-1',
            'part': part,
          }),
        );
      }
      controller.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'user-1',
            'sessionID': 'session-1',
            'role': 'user',
            'time': {'created': DateTime.now().millisecondsSinceEpoch},
          },
        }),
      );
      await tester.pump();
      prompt.complete();
      await tester.pumpAndSettle();

      expect(find.text('match this UI'), findsOneWidget);
      expect(find.text('1000101752.jpg'), findsOneWidget);
    },
  );

  testWidgets('canonical user event replaces the optimistic bubble', (
    tester,
  ) async {
    final prompt = Completer<void>();
    final api = _FakeOpenCodeApi()..promptCompleter = prompt;
    final controller = await _pumpChat(tester, api);

    await tester.enterText(find.byType(TextField), 'hello server');
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await tester.pump();
    expect(find.text('hello server'), findsOneWidget);

    controller.handleEventForTesting(
      _event('message.part.updated', {
        'sessionID': 'session-1',
        'part': _partJson(
          id: 'part-1',
          messageID: 'user-1',
          type: 'text',
          text: 'hello server',
        ),
      }),
    );
    controller.handleEventForTesting(
      _event('message.updated', {
        'info': {
          'id': 'user-1',
          'sessionID': 'session-1',
          'role': 'user',
          'time': {'created': DateTime.now().millisecondsSinceEpoch},
        },
      }),
    );
    await tester.pump();

    expect(find.text('hello server'), findsOneWidget);
    prompt.complete();
    await tester.pump();
  });

  testWidgets(
    'out-of-order canonical users reconcile by prompt instead of timestamp',
    (tester) async {
      final api = _FakeOpenCodeApi();
      final controller = await _pumpChat(tester, api);

      await tester.enterText(find.byType(TextField), 'first prompt');
      await tester.pump();
      await tester.tap(find.byKey(const Key('chat-send-button')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'second prompt');
      await tester.pump();
      await tester.tap(find.byKey(const Key('chat-send-button')));
      await tester.pumpAndSettle();

      controller.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': _partJson(
            id: 'part-second',
            messageID: 'user-second',
            type: 'text',
            text: 'second prompt',
          ),
        }),
      );
      controller.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'user-second',
            'sessionID': 'session-1',
            'role': 'user',
            'time': {'created': DateTime.now().millisecondsSinceEpoch},
          },
        }),
      );
      await _pumpEvent(tester);

      expect(find.text('first prompt'), findsOneWidget);
      expect(find.text('second prompt'), findsOneWidget);

      controller.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'user-first',
            'sessionID': 'session-1',
            'role': 'user',
            'time': {'created': DateTime.now().millisecondsSinceEpoch - 1000},
          },
        }),
      );
      controller.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': _partJson(
            id: 'part-first',
            messageID: 'user-first',
            type: 'text',
            text: 'first prompt',
          ),
        }),
      );
      await _pumpEvent(tester);

      expect(find.text('first prompt'), findsOneWidget);
      expect(find.text('second prompt'), findsOneWidget);
    },
  );

  testWidgets(
    'failed send removes optimism, restores input, and blocks repeats',
    (tester) async {
      final prompt = Completer<void>();
      final api = _FakeOpenCodeApi()..promptCompleter = prompt;
      await _pumpChat(tester, api);

      await tester.enterText(find.byType(TextField), 'try once');
      await tester.pump();
      await tester.tap(find.byTooltip('Send'));
      await tester.tap(find.byTooltip('Send'));
      await tester.pump();
      expect(api.promptCalls, 1);
      expect(find.text('try once'), findsOneWidget);

      prompt.completeError(StateError('network failed'));
      await tester.pumpAndSettle();

      expect(find.text('try once'), findsOneWidget);
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller?.text,
        'try once',
      );
      expect(
        find.text(
          "That didn't work. Details show what happened. Try again, or report the problem.",
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets('session errors stop thinking and remain visible in chat', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _pumpChat(tester, api);

    await tester.enterText(find.byType(TextField), 'hello');
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await _pumpEvent(tester);

    controller.handleEventForTesting(
      _event('session.status', {
        'sessionID': 'session-1',
        'status': {'type': 'busy'},
      }),
    );
    await _pumpEvent(tester);
    expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);

    controller.handleEventForTesting(
      _event('session.error', {
        'sessionID': 'session-1',
        'error': {
          'name': 'ProviderError',
          'data': {'message': 'Sign in to the selected model provider.'},
        },
      }),
    );
    await _pumpEvent(tester);
    // The edge status leaves with a short exit animation.
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('chat-stop-button')), findsNothing);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsOneWidget);
    expect(find.text('The agent stopped because of an error.'), findsOneWidget);
  });

  testWidgets('an error a reply carries is not repeated in a banner', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _pumpChat(tester, api);
    controller.handleEventForTesting(
      _event('message.updated', {
        'info': {
          'id': 'assistant-1',
          'sessionID': 'session-1',
          'role': 'assistant',
          'time': {'created': 1, 'completed': 2},
          'error': {
            'name': 'ProviderError',
            'data': {'message': 'WebSocket inbound queue overflow'},
          },
        },
      }),
    );
    await _pumpEvent(tester);

    expect(
      find.text('This request was too large to send to the model.'),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsNothing);
  });

  testWidgets('a dropped connection is said in plain words, once, and the '
      'banner leaves when the turn moves on', (tester) async {
    const raw =
        'ECONNRESET: The socket connection was closed unexpectedly. For more '
        'information, pass `verbose: true` in the second argument to fetch()';
    final api = _FakeOpenCodeApi();
    final controller = await _pumpChat(tester, api);
    controller.handleEventForTesting(
      _event('session.error', {
        'sessionID': 'session-1',
        'error': {
          'name': 'UnknownError',
          'data': {'message': raw},
        },
      }),
    );
    await _pumpEvent(tester);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsOneWidget);
    expect(find.text('The connection to the model dropped.'), findsOneWidget);
    expect(find.byKey(const ValueKey('prompt-error-hint')), findsOneWidget);
    expect(find.textContaining('ECONNRESET'), findsNothing);
    expect(find.textContaining('fetch()'), findsNothing);
    // The server's exact words are one tap away.
    // The status line unfolds first (design standard §10).
    await tester.pump(KitMotion.standard);
    await tester.tap(find.byKey(const ValueKey('prompt-error-details')));
    await tester.pumpAndSettle();
    expect(find.textContaining('ECONNRESET'), findsOneWidget);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();

    // The reply carries the same problem (worded slightly differently by the
    // server): one place says it, not two.
    controller.handleEventForTesting(
      _event('message.updated', {
        'info': {
          'id': 'assistant-1',
          'sessionID': 'session-1',
          'role': 'assistant',
          'time': {'created': 1, 'completed': 2},
          'error': {
            'name': 'UnknownError',
            'data': {
              'message': 'The socket connection was closed unexpectedly.',
            },
          },
        },
      }),
    );
    await _pumpEvent(tester);
    // The status line folds away (design standard §10), leaving the reply.
    await tester.pump(KitMotion.standard);
    await tester.pump(KitMotion.standard);
    expect(find.text('The connection to the model dropped.'), findsOneWidget);

    // The agent carries on: the error becomes a quiet line of the turn and
    // no banner claims something is still wrong.
    controller.handleEventForTesting(
      _event('message.updated', {
        'info': {
          'id': 'assistant-2',
          'sessionID': 'session-1',
          'role': 'assistant',
          'time': {'created': 3},
        },
      }),
    );
    controller.handleEventForTesting(
      _event('message.part.updated', {
        'sessionID': 'session-1',
        'part': _partJson(
          id: 'p2',
          messageID: 'assistant-2',
          type: 'text',
          text: 'Retrying the build.',
        ),
      }),
    );
    await _pumpEvent(tester);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsNothing);
    expect(find.text('The agent carried on after this.'), findsOneWidget);
  });

  testWidgets('a model-not-found session error shows one line and Choose model', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _pumpChat(tester, api);
    controller.handleEventForTesting(
      _event('session.error', {
        'sessionID': 'session-1',
        'error': {
          'name': 'ProviderModelNotFoundError',
          'data': {
            'message':
                'ProviderModelNotFoundError: Model not found: openai/gpt-5.6. '
                'Did you mean: gpt-5.6, gpt-5.6-pro?\n'
                '    at <anonymous> (/\$bunfs/root/chunk.js:439:1)\n'
                '    at SessionPrompt.getModel (/\$bunfs/root/chunk.js:1096:2)',
          },
        },
      }),
    );
    await _pumpEvent(tester);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsOneWidget);
    // Words on the line; the server's text only under Details.
    expect(find.text("The server doesn't have this model."), findsOneWidget);
    expect(find.textContaining('Did you mean'), findsNothing);
    expect(find.textContaining('at <anonymous>'), findsNothing);
    expect(
      find.byKey(const ValueKey('prompt-error-choose-model')),
      findsOneWidget,
    );
    // Choose model is the line's action; Details is under its More menu.
    // The status line unfolds first (design standard §10).
    await tester.pump(KitMotion.standard);
    await tester.tap(find.byKey(const ValueKey('kit-status-more')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('prompt-error-details')));
    await tester.pumpAndSettle();
    expect(find.textContaining('at <anonymous>'), findsOneWidget);
    expect(find.textContaining('Did you mean'), findsOneWidget);
  });

  testWidgets('renders attachment-only and mixed user prompts accessibly', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-1', 'user', [
          Part(type: 'file', filename: 'report.pdf'),
        ]),
        _message('user-2', 'user', [
          Part(type: 'text', text: 'Review this image'),
          Part(
            type: 'file',
            mime: 'image/png',
            filename: 'diagram.png',
            url:
                'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZKgAAAAASUVORK5CYII=',
          ),
        ], created: 2),
      ];
    await _pumpChat(tester, api);

    expect(find.text('report.pdf'), findsOneWidget);
    expect(find.bySemanticsLabel('Preview report.pdf'), findsOneWidget);
    expect(find.text('Review this image'), findsOneWidget);
    expect(find.text('diagram.png'), findsOneWidget);
    expect(find.bySemanticsLabel('Preview diagram.png'), findsOneWidget);

    final diagram = find.bySemanticsLabel('Preview diagram.png');
    await tester.ensureVisible(diagram);
    await tester.pumpAndSettle();
    await tester.tap(diagram);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-viewer-image')), findsOneWidget);
    expect(find.byType(KitZoom), findsOneWidget);
    expect(find.byTooltip('Zoom in'), findsOneWidget);
  });

  testWidgets('retry preserves mixed and attachment-only file parts', (
    tester,
  ) async {
    const textUrl = 'data:text/plain;base64,bm90ZXM=';
    const imageUrl = 'data:image/png;base64,iVBORw0KGgo=';
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-files', 'user', [
          Part(
            type: 'file',
            mime: 'text/plain',
            filename: 'notes.txt',
            url: textUrl,
          ),
        ]),
        _message('user-mixed', 'user', [
          Part(type: 'text', text: 'Review this'),
          Part(
            type: 'file',
            mime: 'image/png',
            filename: 'diagram.png',
            url: imageUrl,
          ),
        ], created: 2),
      ];
    final controller = await _pumpChat(tester, api);
    controller.selectedVariant = 'fast';

    await _runSheetCommand(tester, 'retry');

    expect(api.prompts.single.text, 'Review this');
    expect(api.prompts.single.variant, 'fast');
    expect(api.prompts.single.attachments.single.toJson(), {
      'type': 'file',
      'mime': 'image/png',
      'filename': 'diagram.png',
      'url': imageUrl,
    });

    controller.handleEventForTesting(
      _event('message.removed', {
        'sessionID': 'session-1',
        'messageID': 'user-mixed',
      }),
    );
    await _pumpEvent(tester);
    await _runSheetCommand(tester, 'retry');

    expect(api.prompts.last.text, isEmpty);
    expect(api.prompts.last.attachments.single.toJson(), {
      'type': 'file',
      'mime': 'text/plain',
      'filename': 'notes.txt',
      'url': textUrl,
    });
  });

  testWidgets('typed server command passes arguments and selected model', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final repository = _FakeProductRepository(const [
      CommandInfo(
        name: 'review',
        description: 'Review current changes',
        subtask: false,
      ),
    ]);
    final controller = await _pumpChat(tester, api, repository: repository);
    controller.selectedModel = ModelRef(
      providerID: 'anthropic',
      modelID: 'claude-sonnet',
    );
    controller.selectedVariant = 'high';
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/review --staged',
    );
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await tester.pumpAndSettle();

    expect(api.slashCommandName, 'review');
    expect(api.slashArguments, '--staged');
    expect(api.slashModel?.providerID, 'anthropic');
    expect(api.slashModel?.modelID, 'claude-sonnet');
    expect(api.slashVariant, 'high');
  });

  testWidgets('attachment count limit is enforced before opening the picker', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,

          home: ChatScreen(
            sessionID: 'session-1',
            initialAttachments: List.generate(
              5,
              (index) => PromptAttachment(
                mime: 'text/plain',
                filename: 'file-$index.txt',
                url: 'data:text/plain;base64,WA==',
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _useComposerTool(tester, 'attach');
    await tester.pumpAndSettle();

    expect(find.textContaining('attach up to 5 files'), findsOneWidget);
  });

  testWidgets('attachment remove action is accessible and at least 48dp', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,

          home: ChatScreen(
            sessionID: 'session-1',
            initialAttachments: [
              PromptAttachment(
                mime: 'text/plain',
                filename: 'notes.txt',
                url: 'data:text/plain;base64,bm90ZXM=',
              ),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final remove = find.bySemanticsLabel('Remove notes.txt');
    expect(remove, findsOneWidget);
    final size = tester.getSize(remove);
    expect(size.width, greaterThanOrEqualTo(48));
    expect(size.height, greaterThanOrEqualTo(48));
    expect(find.bySemanticsLabel('Remove notes.txt'), findsOneWidget);

    final preview = find.bySemanticsLabel('Preview notes.txt');
    expect(
      tester.getSemantics(preview),
      matchesSemantics(
        label: 'Preview notes.txt',
        isButton: true,
        hasTapAction: true,
      ),
    );
    await tester.tap(preview);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
    expect(find.byType(KitCodeBlock), findsWidgets);
    expect(find.text('notes'), findsOneWidget);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();

    await tester.tap(remove);
    await tester.pumpAndSettle();
    expect(remove, findsNothing);
    semantics.dispose();
  });

  testWidgets('empty transcript starters fill the composer', (tester) async {
    final api = _FakeOpenCodeApi();
    // No directory is selected, so the project- and git-dependent starters
    // give way to ones that work in the server's default folder.
    await _pumpChat(tester, api);
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text(KitBidi.auto('Server folder')), findsOneWidget);
    expect(find.byKey(const ValueKey('chat-start-tip')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('chat-starter-What changed recently?')),
      findsNothing,
    );

    await tester.tap(
      find.byKey(const ValueKey("chat-starter-List what's in this folder")),
    );
    await tester.pump();

    expect(
      tester.widget<TextField>(find.byType(TextField)).controller?.text,
      "List what's in this folder",
    );
    expect(api.promptCalls, 0);
  });

  testWidgets('empty transcript starters are seeded from the active project', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..projectFiles = [
        FileNode(name: 'pubspec.yaml', path: 'pubspec.yaml', isDir: false),
      ];
    final controller = await _controller(api);
    controller.directory = '/work/oc_app';
    await _pumpChat(tester, api, controller: controller);
    await tester.pump(const Duration(milliseconds: 300));

    // The project is named once, by the header chip.
    expect(find.text(KitBidi.auto('oc_app')), findsOneWidget);
    expect(find.byKey(const ValueKey('chat-start-name')), findsNothing);
    expect(find.text('1 item'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('chat-starter-Explain this project')),
      findsOneWidget,
    );
    // No Git history is known here, so nothing asks what changed.
    expect(
      find.byKey(const ValueKey('chat-starter-What changed recently?')),
      findsNothing,
    );
  });

  testWidgets('long-pressing a user message offers copy and fork actions', (
    tester,
  ) async {
    String? copiedText;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copiedText = (call.arguments as Map)['text'] as String?;
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-1', 'user', [
          Part(type: 'text', text: 'Fix the login bug'),
        ]),
      ];
    await _pumpChat(tester, api);

    // The prompt bubble's own menu (KitMessage.prompt).
    await tester.longPress(find.text('Fix the login bug'));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('message-menu-copy')), findsOneWidget);
    expect(find.byKey(const ValueKey('message-menu-fork')), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('message-menu-copy')));
    await tester.pumpAndSettle();
    expect(copiedText, 'Fix the login bug');
    // The kit's copy feedback (a tick and an announcement), not a snackbar.
    expect(find.byType(SnackBar), findsNothing);
  });

  testWidgets('deleting a message confirms, calls the server, and prunes it', (
    tester,
  ) async {
    final serverMessages = <MessageWithParts>[
      _message('user-1', 'user', [Part(type: 'text', text: 'first prompt')]),
      _message('user-2', 'user', [
        Part(type: 'text', text: 'second prompt'),
      ], created: 2),
    ];
    final repository = _MessageDeleteRepository(serverMessages);
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => List.of(serverMessages);
    await _pumpChat(tester, api, repository: repository);

    await tester.longPress(find.text('first prompt'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('message-menu-delete')));
    await tester.pumpAndSettle();

    expect(find.text('Delete this message?'), findsOneWidget);
    expect(find.textContaining('File changes it made'), findsOneWidget);
    await tester.tap(find.text('Delete message'));
    await tester.pumpAndSettle();

    expect(repository.deleted, [('session-1', 'user-1')]);
    expect(find.text('first prompt'), findsNothing);
    expect(find.text('second prompt'), findsOneWidget);
  });

  testWidgets('a failed message delete keeps the transcript intact', (
    tester,
  ) async {
    final serverMessages = <MessageWithParts>[
      _message('user-1', 'user', [Part(type: 'text', text: 'only prompt')]),
    ];
    final repository = _MessageDeleteRepository(serverMessages)
      ..failure = const ProductException('Message deletion is unavailable');
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => List.of(serverMessages);
    await _pumpChat(tester, api, repository: repository);

    await tester.longPress(find.text('only prompt'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('message-menu-delete')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete message'));
    await tester.pumpAndSettle();

    expect(repository.deleted, isEmpty);
    expect(find.text('only prompt'), findsOneWidget);
    expect(
      find.textContaining('Message deletion is unavailable'),
      findsOneWidget,
    );
  });

  for (final percent in [25, 75]) {
    testWidgets(
      'context usage at $percent percent is disclosed at the right level',
      (tester) async {
        final api = _FakeOpenCodeApi()
          ..messagesHandler = (_) async => [
            _message(
              'assistant-1',
              'assistant',
              [Part(type: 'text', text: 'done')],
              providerID: 'p',
              modelID: 'm',
              tokens: Tokens(input: percent * 1000, output: 0),
            ),
          ];
        final controller = await _controller(api);
        controller.catalog = const CatalogSnapshot(
          providers: [
            CatalogProvider(id: 'p', name: 'Provider', enabled: true),
          ],
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
        await _pumpChat(tester, api, controller: controller);
        await tester.pumpAndSettle();
        // The kit composer discloses high usage on the model chip; the
        // separate meter was retired in chat-3.
        final contextPercent = find.byKey(
          const Key('composer-context-percent'),
        );
        expect(contextPercent, percent >= 70 ? findsOneWidget : findsNothing);
        if (percent >= 70) {
          expect(find.text('· $percent %'), findsOneWidget);
          expect(
            find.bySemanticsLabel(RegExp('Context.*$percent')),
            findsOneWidget,
          );
        }
        // Routine usage is still reachable, with the exact value, through
        // the same Context usage destination used for warnings.
        // The conversation menu's Details (slice-P10.2).
        await tester.tap(find.byKey(const ValueKey('session-actions-button')));
        await tester.pumpAndSettle();
        final details = find.byKey(const ValueKey('session-menu-details'));
        expect(details.hitTestable(), findsOneWidget);
        await tester.tap(details);
        await tester.pumpAndSettle();
        expect(find.byType(SessionContextScreen), findsOneWidget);
        expect(
          find.text(
            percent < 50 ? '$percent% used · plenty left' : '$percent% used',
          ),
          findsOneWidget,
        );
        expect(find.text('$percent,000 of 100,000 tokens'), findsOneWidget);
        expect(
          find.bySemanticsLabel(
            'Model, $percent percent, $percent,000 of 100,000 tokens',
          ),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('composer hides the context percentage without a known limit', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    await _pumpChat(tester, api);

    expect(find.byKey(const Key('composer-context-percent')), findsNothing);
  });

  testWidgets('Ctrl+Enter sends the drafted prompt', (tester) async {
    final api = _FakeOpenCodeApi();
    await _pumpChat(tester, api);

    await tester.enterText(find.byKey(const Key('chat-composer-field')), 'go');
    await tester.pump();

    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await _pumpEvent(tester);

    expect(api.promptCalls, 1);
    expect(api.prompts.single.text, 'go');
  });

  testWidgets('session sheets fit a 320dp phone at 2x text', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,

          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: const ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    // The menu scrolls at 320dp with 2x text; every entry stays reachable.
    for (final key in [
      'session-menu-find',
      'session-menu-details',
      'session-menu-rename',
    ]) {
      await tester.ensureVisible(find.byKey(ValueKey(key)));
      await tester.pumpAndSettle();
      expect(find.byKey(ValueKey(key)).hitTestable(), findsOneWidget);
    }
    expect(tester.takeException(), isNull);
  });
}
