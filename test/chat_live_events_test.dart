import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeOpenCodeApi extends OpenCodeApi {
  _FakeOpenCodeApi() : super(baseUrl: 'http://localhost');

  Future<List<MessageWithParts>> Function(String id)? messagesHandler;
  Completer<void>? promptCompleter;
  int promptCalls = 0;
  final List<
    ({
      String text,
      ModelRef? model,
      String? variant,
      List<PromptAttachment> attachments,
    })
  >
  prompts = [];
  String? slashCommandName;
  String? slashArguments;
  ModelRef? slashModel;
  String? slashVariant;
  int abortCalls = 0;
  Object? abortError;
  bool failRename = false;
  bool failDelete = false;
  int createCalls = 0;
  final List<({String id, String title})> renameCalls = [];
  final List<String> deleteCalls = [];
  List<FileDiff> diffs = const [];
  List<Todo> todoItems = const [];
  final Map<String, FileContent> fileContents = {};
  final List<String> fileContentRequests = [];
  List<FileNode> projectFiles = const [];

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) =>
      messagesHandler?.call(id) ?? Future.value([]);

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<Session> createSession() async {
    createCalls += 1;
    return Session(id: 'session-created');
  }

  @override
  Future<List<FileDiff>> diff(String id) async => diffs;

  @override
  Future<List<Todo>> todos(String id) async => todoItems;

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
  }) {
    promptCalls += 1;
    prompts.add((
      text: text,
      model: model,
      variant: variant,
      attachments: attachments,
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
    if (failRename) throw StateError('rename failed');
  }

  @override
  Future<void> deleteSession(String id) async {
    deleteCalls.add(id);
    if (failDelete) throw StateError('delete failed');
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

Future<ConnectionController> _controller(_FakeOpenCodeApi api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))..api = api;
}

class _DelayedActionController extends ConnectionController {
  _DelayedActionController(super.store, this.readyApi);

  final Completer<OpenCodeApi?> readyApi;

  @override
  Future<OpenCodeApi?> prepareActionTransport() => readyApi.future;
}

class _DelayedRepositoryController extends ConnectionController {
  _DelayedRepositoryController(super.store, this.readyRepository);

  final Completer<ProductRepository?> readyRepository;

  @override
  Future<ProductRepository?> prepareActionRepository() =>
      readyRepository.future;
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
}) async {
  final activeController = controller ?? await _controller(api);
  activeController.repository = repository;
  addTearDown(activeController.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(activeController)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pump();
  await tester.pump();
  return activeController;
}

Future<void> _pumpEvent(WidgetTester tester) async {
  await tester.pump();
  await tester.pump();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

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

    await tester.tap(find.byTooltip('Session views'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Changes'));
    await tester.pumpAndSettle();
    expect(find.text('+1'), findsOneWidget);
    expect(find.text('-1'), findsOneWidget);

    expect(find.text('@@ -1 +1 @@'), findsOneWidget);
    expect(find.text('-old line'), findsOneWidget);
    expect(find.text('+new line'), findsOneWidget);
    expect(find.text('Copy patch'), findsOneWidget);
    expect(find.byKey(const Key('review-mode-split')), findsOneWidget);
  });

  testWidgets('adds a selected diff comment back to the chat composer', (
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

    await tester.tap(find.byTooltip('Session views'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Changes'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('review-line-2')));
    await tester.pump();
    expect(find.byKey(const Key('review-selection-bar')), findsOneWidget);

    await tester.tap(find.byKey(const Key('review-comment-action')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('review-comment-field')),
      'Keep the retry behavior explicit.',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('review-add-to-prompt')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('review-workspace')), findsNothing);
    final composer = tester.widget<TextField>(
      find.byKey(const Key('chat-composer-field')),
    );
    expect(composer.controller?.text, contains('Review `lib/client.dart`'));
    expect(composer.controller?.text, contains('new line 8'));
    expect(
      composer.controller?.text,
      contains('Keep the retry behavior explicit.'),
    );
    expect(composer.controller?.text, contains('+new request'));
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
    final before = tester.widget<TextField>(fieldFinder);
    expect(before.focusNode?.hasFocus, isTrue);

    tester.view.viewInsets = const FakeViewPadding(bottom: 400);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    final after = tester.widget<TextField>(fieldFinder);
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
    final controller = _DelayedActionController(
      ProfileStore(prefs: prefs),
      readyApi,
    )..api = retainedApi;
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
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
          .widget<IconButton>(find.byKey(const Key('chat-send-button')))
          .onPressed,
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
    final retainedApi = _FakeOpenCodeApi();
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
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await tester.pump();
    await tester.pump();

    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();

    expect(retainedApi.abortCalls, 0);
    expect(replacementApi.abortCalls, 0);

    readyApi.complete(replacementApi);
    await tester.pumpAndSettle();

    expect(retainedApi.abortCalls, 0);
    expect(replacementApi.abortCalls, 1);
  });

  testWidgets('stop failure remains visible instead of being swallowed', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..abortError = ApiException('server refused to stop');
    final controller = await _pumpChat(tester, api);
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();

    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pumpAndSettle();

    expect(api.abortCalls, 1);
    expect(
      find.textContaining('Could not stop generation: server refused to stop'),
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
    expect(find.text('captcha-r1.png'), findsOneWidget);

    await tester.tap(find.byKey(const Key('tool-output-image')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-image')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-download')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-attach')), findsOneWidget);

    await tester.tap(find.byKey(const Key('file-preview-attach')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsNothing);
    expect(
      find.bySemanticsLabel('Remove attachment captcha-r1.png'),
      findsOneWidget,
    );

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Please inspect this CAPTCHA.',
    );
    await tester.pump();
    final sendButton = tester.widget<IconButton>(
      find.byKey(const Key('chat-send-button')),
    );
    expect(sendButton.onPressed, isNotNull);
    sendButton.onPressed!();
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
    expect(find.byKey(const Key('file-preview-text')), findsOneWidget);
    expect(find.byType(DataTable), findsOneWidget);
    expect(find.text('api.dart'), findsOneWidget);
    expect(find.byKey(const Key('file-preview-download')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-attach')), findsOneWidget);

    await tester.tap(find.byKey(const Key('file-preview-raw-mode')));
    await tester.pump();
    expect(find.byType(DataTable), findsNothing);
    expect(find.textContaining('| File | Status |'), findsOneWidget);
  });

  testWidgets('groups a tool chain until assistant text appears', (
    tester,
  ) async {
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

    expect(find.byKey(const Key('tool-call-group')), findsNWidgets(2));
    expect(find.text('Tools'), findsNWidgets(2));
    expect(find.text('3 calls · read · search · shell'), findsOneWidget);
    expect(find.text('2 calls · edit · write'), findsOneWidget);
    expect(find.text('Tool chain finished.'), findsOneWidget);
    expect(find.text('Shell'), findsNothing);
    expect(find.text('Read'), findsNothing);
    expect(find.text('Search text'), findsNothing);
    expect(find.text('Edit'), findsNothing);

    final headers = find.byKey(const Key('tool-call-group-header'));
    expect(tester.getSize(headers.first).height, greaterThanOrEqualTo(48));
    await tester.tap(headers.first);
    await tester.pumpAndSettle();

    expect(find.text('Read'), findsOneWidget);
    expect(find.text('Search text'), findsOneWidget);
    expect(find.text('Shell'), findsOneWidget);
    expect(find.text('Edit'), findsNothing);

    for (final row in tester.widgetList<Container>(
      find.byKey(const Key('embedded-tool-row')),
    )) {
      expect(
        row.decoration,
        isNull,
        reason: 'Grouped tool rows must not render nested cards.',
      );
    }
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

    expect(find.byKey(const Key('tool-call-group')), findsOneWidget);
    expect(find.text('2 calls · edit · shell'), findsOneWidget);
    expect(find.text('Edit'), findsNothing);
    expect(find.text('Shell'), findsNothing);

    await tester.tap(find.byKey(const Key('tool-call-group-header')));
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
          'assistant-1',
          'assistant',
          [Part(type: 'text', text: 'First internal step')],
          created: 2,
          providerID: 'provider',
          modelID: 'model-a',
          tokens: Tokens(input: 70, output: 30),
        ),
        _message(
          'assistant-2',
          'assistant',
          [Part(type: 'text', text: 'Second internal step')],
          created: 3,
          providerID: 'provider',
          modelID: 'model-a',
          tokens: Tokens(input: 150, output: 50),
        ),
        _message(
          'assistant-3',
          'assistant',
          [Part(type: 'text', text: 'Model switched here')],
          created: 4,
          providerID: 'provider',
          modelID: 'model-b',
          tokens: Tokens(input: 40, output: 10),
        ),
        _message('user-2', 'user', [
          Part(type: 'text', text: 'Continue'),
        ], created: 5),
        _message(
          'assistant-4',
          'assistant',
          [Part(type: 'text', text: 'Same model, next turn')],
          created: 6,
          providerID: 'provider',
          modelID: 'model-b',
          tokens: Tokens(input: 60, output: 15),
        ),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    expect(find.textContaining('provider/model-a'), findsOneWidget);
    expect(find.textContaining('provider/model-b'), findsOneWidget);
    expect(find.textContaining('350 tok'), findsOneWidget);
    expect(find.textContaining('75 tok'), findsOneWidget);
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
    delta('reasoning-1', 'text', 'why ');
    delta('reasoning-1', 'text', 'this works');
    delta('tool-1', 'input', '{"query":');
    delta('tool-1', 'input', '"chat"}');
    await _pumpEvent(tester);

    expect(find.text('Hello'), findsOneWidget);
    expect(find.byKey(const Key('reasoning-inline')), findsOneWidget);
    expect(find.byKey(const Key('reasoning-toggle')), findsNothing);
    expect(find.text('why this works'), findsOneWidget);
    await tester.tap(find.text('search'));
    await _pumpEvent(tester);
    expect(find.textContaining('"query": "chat"'), findsOneWidget);
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
    expect(find.textContaining('First reasoning fragment'), findsOneWidget);
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
    expect(find.byKey(const Key('message-meta-user-display')), findsNothing);

    await tester.tap(find.byKey(const Key('command-launcher-button')));
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

    await tester.tap(find.byTooltip('Session views'));
    await tester.pumpAndSettle();
    expect(find.text('Collapse reasoning'), findsOneWidget);
    await tester.tap(find.byKey(const Key('session-view-timestamps')));
    await tester.pumpAndSettle();

    expect(controller.transcriptTimestampsVisible, isTrue);
    expect(find.byKey(const Key('message-meta-user-display')), findsOneWidget);
    expect(
      find.byKey(const Key('message-meta-assistant-display')),
      findsOneWidget,
    );

    await tester.tap(find.byTooltip('Session views'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('session-view-thinking')));
    await tester.pumpAndSettle();

    expect(controller.transcriptReasoningExpanded, isFalse);
    expect(find.text(reasoning), findsNothing);
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
    await tester.tap(find.byKey(const Key('command-launcher-button')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'diff',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-diff')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('review-workspace')), findsOneWidget);
    expect(find.text('chat.dart'), findsOneWidget);
    expect(find.text('+7'), findsOneWidget);
    expect(find.text('-2'), findsOneWidget);
  });

  testWidgets('session todo view shows server status and priority', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..todoItems = [
        Todo(
          content: 'Verify production release',
          status: 'in_progress',
          priority: 'high',
        ),
      ];

    await _pumpChat(tester, api);
    await tester.tap(find.byTooltip('Session views'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Todos'));
    await tester.pumpAndSettle();

    expect(find.text('Verify production release'), findsOneWidget);
    expect(find.text('in progress · high priority'), findsOneWidget);
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
    await tester.tap(find.byKey(const Key('command-launcher-button')));
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
    expect(find.text('/review'), findsOneWidget);
    await tester.tap(find.byKey(const Key('command-server-review')));
    await tester.pumpAndSettle();
    final composer = tester.widget<TextField>(
      find.byKey(const Key('chat-composer-field')),
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
    await tester.tap(find.byKey(const Key('command-launcher-button')));
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
    expect(find.byKey(const Key('project-file-attach')), findsOneWidget);
    expect(find.byKey(const Key('project-file-download')), findsOneWidget);

    await tester.tap(find.byKey(const Key('project-file-attach')));
    await tester.pumpAndSettle();
    expect(
      find.text('review.md attached. Return to the chat to add your comment.'),
      findsOneWidget,
    );

    Navigator.of(
      tester.element(find.byKey(const Key('project-file-attach'))),
    ).pop();
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(
      find.bySemanticsLabel('Remove attachment review.md'),
      findsOneWidget,
    );
  });

  testWidgets('move, warp, and org commands preserve their server semantics', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final repository = _DestinationRepository();
    final controller = await _controller(api);
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
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await tester.pumpAndSettle();

    Future<void> openCommand(String command) async {
      await tester.tap(find.byKey(const Key('command-launcher-button')));
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
    final controller = await _controller(api);
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
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('command-launcher-button')));
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
    final composer = tester.widget<TextField>(composerFinder).controller!;
    composer.value = const TextEditingValue(
      text: 'Original prompt draft',
      selection: TextSelection(baseOffset: 2, extentOffset: 10),
    );
    await tester.pump();

    expect(find.byKey(const Key('prompt-editor-button')), findsOneWidget);
    await tester.tap(find.byKey(const Key('command-launcher-button')));
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
        .widget<TextField>(find.byKey(const Key('prompt-editor-field')))
        .controller!;
    expect(editor.text, 'Original prompt draft');
    expect(
      editor.selection,
      const TextSelection(baseOffset: 2, extentOffset: 10),
    );
    editor.selection = const TextSelection.collapsed(offset: 4);
    await tester.tap(find.byTooltip('Close prompt editor'));
    await tester.pumpAndSettle();
    expect(find.text('Discard prompt changes?'), findsNothing);
    expect(
      composer.selection,
      const TextSelection(baseOffset: 2, extentOffset: 10),
    );

    await tester.tap(find.byKey(const Key('prompt-editor-button')));
    await tester.pumpAndSettle();
    final discardEditor = tester
        .widget<TextField>(find.byKey(const Key('prompt-editor-field')))
        .controller!;
    discardEditor.value = const TextEditingValue(
      text: 'Discarded edit',
      selection: TextSelection.collapsed(offset: 5),
    );
    await tester.tap(find.byTooltip('Remove attachment notes.txt'));
    await tester.pump();
    await tester.tap(find.byTooltip('Close prompt editor'));
    await tester.pumpAndSettle();
    expect(find.text('Discard prompt changes?'), findsOneWidget);
    await tester.tap(find.text('Keep editing'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('prompt-editor-screen')), findsOneWidget);

    await tester.tap(find.byTooltip('Close prompt editor'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Discard'));
    await tester.pumpAndSettle();
    expect(composer.text, 'Original prompt draft');
    expect(find.byTooltip('Remove attachment notes.txt'), findsOneWidget);

    await tester.tap(find.byKey(const Key('prompt-editor-button')));
    await tester.pumpAndSettle();
    final savedEditor = tester
        .widget<TextField>(find.byKey(const Key('prompt-editor-field')))
        .controller!;
    savedEditor.value = const TextEditingValue(
      text: 'Final edited prompt',
      selection: TextSelection.collapsed(offset: 7),
    );
    await tester.tap(find.byTooltip('Remove attachment notes.txt'));
    await tester.pump();
    await tester.tap(find.byKey(const Key('prompt-editor-done')));
    await tester.pumpAndSettle();

    expect(composer.text, 'Final edited prompt');
    expect(composer.selection, const TextSelection.collapsed(offset: 7));
    expect(find.byTooltip('Remove attachment notes.txt'), findsNothing);
    expect(api.promptCalls, 0);
  });

  testWidgets('timeline searches old messages and jumps to a stable anchor', (
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

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Session views'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Timeline'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('timeline-search')),
      'oldest anchor',
    );
    await tester.pump();
    expect(find.byKey(const Key('timeline-row-user-0')), findsOneWidget);

    await tester.tap(find.byKey(const Key('timeline-row-user-0')));
    await tester.pumpAndSettle();

    expect(find.text('oldest anchor prompt'), findsOneWidget);
    expect(
      find.byKey(const Key('message-highlight-user-0-true')),
      findsOneWidget,
    );
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
    await tester.tap(find.byTooltip('Session views'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Timeline'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('timeline-fork-user-restore')));
    await tester.pumpAndSettle();

    expect(repository.forkCalls, 1);
    expect(repository.forkMessageID, 'user-restore');
    final composer = tester.widget<TextField>(
      find.byKey(const Key('chat-composer-field')),
    );
    expect(composer.controller?.text, 'Review this design');
    expect(find.byTooltip('Remove attachment design.png'), findsOneWidget);
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
    await tester.tap(find.byTooltip('Session views'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Timeline'));
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
    await tester.tap(find.byKey(const Key('command-launcher-button')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'fork',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-fork')));
    await tester.pumpAndSettle();

    expect(find.text('Fork from prompt'), findsOneWidget);
    expect(
      find.byKey(const Key('timeline-row-assistant-fork-command')),
      findsNothing,
    );
    await tester.tap(find.byKey(const Key('timeline-row-user-fork-command')));
    await tester.pumpAndSettle();

    expect(repository.forkMessageID, 'user-fork-command');
    final composer = tester.widget<TextField>(
      find.byKey(const Key('chat-composer-field')),
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
        child: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2)),
          child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Session views'));
    await tester.pumpAndSettle();
    final timestamps = find.byKey(const Key('session-view-timestamps'));
    await tester.ensureVisible(timestamps);
    await tester.pumpAndSettle();
    await tester.tap(timestamps);
    await tester.pumpAndSettle();
    expect(controller.transcriptTimestampsVisible, isTrue);
    expect(tester.takeException(), isNull);

    await tester.tap(find.byTooltip('Session views'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Timeline'));
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
      await tester.tap(find.byKey(const Key('command-launcher-button')));
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
        find.byKey(const Key('chat-composer-field')),
      );
      expect(composer.controller?.text, '@docs');
      expect(find.bySemanticsLabel('Reference @docs'), findsOneWidget);
      expect(find.byTooltip('Remove reference @docs'), findsOneWidget);
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
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
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
      await tester.tap(find.byTooltip('Send'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'second prompt');
      await tester.pump();
      await tester.tap(find.byTooltip('Send'));
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
      expect(find.textContaining('Send failed:'), findsOneWidget);
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
    expect(find.text('thinking…'), findsOneWidget);

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

    expect(find.text('thinking…'), findsNothing);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsOneWidget);
    expect(
      find.text('Sign in to the selected model provider.'),
      findsOneWidget,
    );
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
    expect(find.text('PDF · prompt attachment'), findsOneWidget);
    expect(find.text('Review this image'), findsOneWidget);
    expect(find.text('diagram.png'), findsOneWidget);
    expect(find.text('PNG · prompt attachment'), findsOneWidget);
    expect(
      find.bySemanticsLabel('Preview attachment report.pdf'),
      findsOneWidget,
    );
    expect(
      find.bySemanticsLabel('Preview attachment diagram.png'),
      findsOneWidget,
    );

    final diagram = find.text('diagram.png');
    await tester.ensureVisible(diagram);
    await tester.pumpAndSettle();
    await tester.tap(diagram);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-image')), findsOneWidget);
    expect(find.text('Pinch to zoom'), findsOneWidget);
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

    await tester.tap(find.byType(PopupMenuButton<String>).last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Retry last prompt'));
    await tester.pumpAndSettle();

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
    await tester.tap(find.byType(PopupMenuButton<String>).last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Retry last prompt'));
    await tester.pumpAndSettle();

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

  testWidgets('session rename and delete failures preserve the session', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..failRename = true
      ..failDelete = true;
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    controller.sessionsById = {
      'session-1': Session(
        id: 'session-1',
        title: 'Original title',
        time: SessionTime(created: 1, updated: 1),
      ),
    };
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: SessionsTab(controller: controller)),
      ),
    );

    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();
    expect(find.text('Delete chat?'), findsOneWidget);
    expect(controller.sessionsById, contains('session-1'));
    await tester.tap(find.widgetWithText(FilledButton, 'Delete'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Could not delete chat:'), findsOneWidget);
    expect(controller.sessionsById, contains('session-1'));
    expect(find.text('Original title'), findsOneWidget);
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();

    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Rename'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Changed title');
    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Could not rename chat:'), findsOneWidget);
    expect(controller.sessionsById['session-1']?.title, 'Original title');
    expect(find.text('Original title'), findsOneWidget);
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

    await tester.tap(find.byTooltip('Attach file'));
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

    final remove = find.byTooltip('Remove attachment notes.txt');
    expect(remove, findsOneWidget);
    final size = tester.getSize(remove);
    expect(size.width, greaterThanOrEqualTo(48));
    expect(size.height, greaterThanOrEqualTo(48));
    expect(
      find.bySemanticsLabel('Remove attachment notes.txt'),
      findsOneWidget,
    );

    final preview = find.bySemanticsLabel('Preview attachment notes.txt');
    expect(
      tester.getSemantics(preview),
      matchesSemantics(
        label: 'Preview attachment notes.txt',
        isButton: true,
        hasTapAction: true,
      ),
    );
    await tester.tap(preview);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-text')), findsOneWidget);
    expect(find.text('notes'), findsOneWidget);
    await tester.tap(find.byTooltip('Close preview'));
    await tester.pumpAndSettle();

    await tester.tap(remove);
    await tester.pumpAndSettle();
    expect(remove, findsNothing);
    semantics.dispose();
  });
}
