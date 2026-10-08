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

part 'chat_live_events/history_navigation_tests.dart';
part 'chat_live_events/transport_tools_tests.dart';
part 'chat_live_events/commands_timeline_tests.dart';
part 'chat_live_events/composer_errors_tests.dart';

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

  _historyAndNavigationTests();
  _transportAndToolTests();
  _commandAndTimelineTests();
  _composerAndErrorTests();
}
