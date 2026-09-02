// Fixtures and helpers for the marketing capture pipeline.
//
// Run with `flutter test tool/capture/capture_test.dart`. Nothing here is
// imported by the app or by test/; the fakes are copies of the patterns the
// widget tests use, trimmed to what the captured screens actually call.
//
// Sample data is invented: a fictional "shopfront" project with sessions and
// an assistant turn that exercise the widgets. Nothing private.

import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show StreamStatus;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:shared_preferences/shared_preferences.dart';

// ---------------------------------------------------------------------------
// Fonts and viewport
// ---------------------------------------------------------------------------

/// Logical phone size behind the captures: 390x844 at 3x = 1170x2532.
const captureLogicalSize = Size(390, 844);
const captureDevicePixelRatio = 3.0;

/// Where the Flutter SDK keeps the Material icon font; flutter_test does not
/// load it, so icons would otherwise render as boxes.
String materialIconsPath() {
  final flutterRoot =
      Platform.environment['FLUTTER_ROOT'] ??
      _sdkRootFromExecutable() ??
      '/opt/flutter-sdk/flutter';
  return '$flutterRoot/bin/cache/artifacts/material_fonts/'
      'MaterialIcons-Regular.otf';
}

String? _sdkRootFromExecutable() {
  // `flutter test` runs Dart from <sdk>/bin/cache/dart-sdk/bin/dart.
  final marker = '${Platform.pathSeparator}bin${Platform.pathSeparator}cache';
  final executable = Platform.resolvedExecutable;
  final index = executable.indexOf(marker);
  return index < 0 ? null : executable.substring(0, index);
}

/// Loads the bundled display/mono faces, Roboto (for Android body text) and
/// the Material icon font so `flutter test` renders real glyphs instead of
/// the Ahem box font.
Future<void> loadCaptureFonts() async {
  Future<void> load(String family, List<String> paths) async {
    final loader = FontLoader(family);
    for (final path in paths) {
      final bytes = File(path).readAsBytesSync();
      loader.addFont(Future.value(ByteData.sublistView(bytes)));
    }
    await loader.load();
  }

  await load(AppTheme.monoFamily, const [
    'assets/fonts/JetBrainsMono-Regular.ttf',
    'assets/fonts/JetBrainsMono-Medium.ttf',
    'assets/fonts/JetBrainsMono-SemiBold.ttf',
    'assets/fonts/JetBrainsMono-Bold.ttf',
    'assets/fonts/JetBrainsMono-Italic.ttf',
  ]);
  await load(AppTheme.displayFamily, const [
    'assets/fonts/SpaceGrotesk-Regular.ttf',
    'assets/fonts/SpaceGrotesk-Medium.ttf',
    'assets/fonts/SpaceGrotesk-SemiBold.ttf',
    'assets/fonts/SpaceGrotesk-Bold.ttf',
  ]);
  await load('Roboto', const [
    'tool/capture/fonts/Roboto-Regular.ttf',
    'tool/capture/fonts/Roboto-Medium.ttf',
    'tool/capture/fonts/Roboto-Bold.ttf',
  ]);
  await load('MaterialIcons', [materialIconsPath()]);
}

/// Rasterises the [RepaintBoundary] behind [key] and returns PNG bytes.
Future<Uint8List> capturePng(
  WidgetTester tester,
  GlobalKey key, {
  double pixelRatio = captureDevicePixelRatio,
}) async {
  final boundary =
      key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
  Uint8List? bytes;
  await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: pixelRatio);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    bytes = data!.buffer.asUint8List();
    image.dispose();
  });
  return bytes!;
}

Future<void> writePng(String path, Uint8List bytes) async {
  final file = File(path);
  file.parent.createSync(recursive: true);
  file.writeAsBytesSync(bytes, flush: true);
}

/// Decodes a PNG from disk into a [ui.Image] the frame can draw with
/// [RawImage]; `Image.memory` would decode asynchronously and never settle
/// under the test binding's fake clock.
Future<ui.Image> decodeImageFile(WidgetTester tester, String path) async {
  ui.Image? image;
  await tester.runAsync(() async {
    final codec = await ui.instantiateImageCodec(File(path).readAsBytesSync());
    final frame = await codec.getNextFrame();
    image = frame.image;
  });
  return image!;
}

// ---------------------------------------------------------------------------
// App shell used by every capture
// ---------------------------------------------------------------------------

const captionHeight = 44.0;

/// The theme the captures use: the app's own palette on the Android path so
/// body text lands on Roboto.
ThemeData captureTheme({bool light = false}) =>
    (light ? AppTheme.light() : AppTheme.dark()).copyWith(
      platform: TargetPlatform.android,
    );

/// Wraps [home] the way main.dart does (theme, localisation, chat route) under
/// a [RepaintBoundary] so a frame can be rasterised. When [caption] is given,
/// the app is letterboxed above a caption strip that names the demo step.
Widget captureApp({
  required Widget home,
  required GlobalKey boundaryKey,
  required ConnectionController controller,
  ProfileStore? store,
  bool light = false,
  ValueListenable<String>? caption,
  GlobalKey<NavigatorState>? navigatorKey,
  Map<String, WidgetBuilder> routes = const {},
}) {
  final theme = captureTheme(light: light);
  return RepaintBoundary(
    key: boundaryKey,
    child: ProviderScope(
      // Always both: the demo swaps trees under one boundary key, and
      // ProviderScope refuses to change its override count in place.
      overrides: [
        bootstrapProvider.overrideWithValue(
          AppBootstrap(store ?? controller.store),
        ),
        connProvider.overrideWithValue(controller),
      ],
      child: MaterialApp(
        navigatorKey: navigatorKey,
        debugShowCheckedModeBanner: false,
        theme: theme,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        routes: routes,
        onGenerateRoute: (settings) {
          final name = settings.name ?? '';
          if (!name.startsWith('/chat/')) return null;
          return MaterialPageRoute<void>(
            builder: (_) => chatScreenFor(name.substring('/chat/'.length)),
          );
        },
        builder: (context, child) {
          final media = MediaQuery.of(
            context,
          ).copyWith(disableAnimations: false);
          final body = child ?? const SizedBox.shrink();
          if (caption == null) {
            return MediaQuery(data: media, child: body);
          }
          return ColoredBox(
            color: theme.scaffoldBackgroundColor,
            child: Column(
              children: [
                Expanded(
                  child: MediaQuery(
                    data: media.copyWith(
                      size: Size(
                        media.size.width,
                        media.size.height - captionHeight,
                      ),
                    ),
                    child: body,
                  ),
                ),
                CaptionStrip(caption: caption),
              ],
            ),
          );
        },
        home: home,
      ),
    ),
  );
}

/// Hook the route factory uses so the capture test can decide how a chat
/// screen is constructed (it lives in the test to keep this file free of
/// screen imports the fixtures do not otherwise need).
late Widget Function(String sessionID) chatScreenFor;

/// The semi-transparent strip under the app naming the current demo step.
class CaptionStrip extends StatelessWidget {
  const CaptionStrip({super.key, required this.caption});

  final ValueListenable<String> caption;

  @override
  Widget build(BuildContext context) {
    // Material supplies the DefaultTextStyle the strip lives outside of.
    return Material(
      color: Colors.black.withValues(alpha: .62),
      child: Container(
        height: captionHeight,
        width: double.infinity,
        alignment: Alignment.center,
        child: ValueListenableBuilder<String>(
          valueListenable: caption,
          builder: (context, value, _) => AnimatedSwitcher(
            duration: const Duration(milliseconds: 260),
            child: Text(
              value,
              key: ValueKey(value),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontFamily: AppTheme.displayFamily,
                fontSize: 16,
                fontWeight: FontWeight.w500,
                color: Colors.white,
                letterSpacing: .2,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Sample data: the "shopfront" project
// ---------------------------------------------------------------------------

const projectDirectory = '/home/dev/shopfront';
const checkoutSessionID = 'ses_checkout';
const darkModeSessionID = 'ses_darkmode';
const paymentsSessionID = 'ses_payments';
const ciSessionID = 'ses_ci';

const checkoutCommand = 'flutter test test/checkout_test.dart';

Session _session(
  String id,
  String title, {
  required int updated,
  double? cost,
  SessionDiffSummary? summary,
}) => Session(
  id: id,
  title: title,
  directory: projectDirectory,
  time: SessionTime(created: updated - 25 * 60 * 1000, updated: updated),
  cost: cost,
  summary: summary,
  model: 'anthropic/claude-sonnet-4',
  agent: 'build',
);

/// One busy session and three recent ones, all carrying usage labels.
Map<String, Session> sampleSessions() {
  final now = DateTime.now().millisecondsSinceEpoch;
  const minute = 60 * 1000;
  const hour = 60 * minute;
  return {
    checkoutSessionID: _session(
      checkoutSessionID,
      'Fix flaky checkout test',
      updated: now - 2 * minute,
      cost: 0.42,
      summary: const SessionDiffSummary(
        additions: 120,
        deletions: 34,
        files: 6,
      ),
    ),
    darkModeSessionID: _session(
      darkModeSessionID,
      'Add dark mode to settings',
      updated: now - 3 * hour,
      cost: 1.18,
      summary: const SessionDiffSummary(
        additions: 212,
        deletions: 40,
        files: 9,
      ),
    ),
    paymentsSessionID: _session(
      paymentsSessionID,
      'Explain the payments module',
      updated: now - 26 * hour,
      cost: 0.09,
    ),
    ciSessionID: _session(
      ciSessionID,
      'Speed up the CI pipeline',
      updated: now - 3 * 24 * hour,
      cost: 0.31,
      summary: const SessionDiffSummary(additions: 18, deletions: 7, files: 2),
    ),
  };
}

const userPrompt = 'Fix the flaky checkout test';

/// The first paragraph of the assistant's answer, before the tools run.
const answerIntro =
    'The flakiness comes from `CheckoutBloc` racing the price refresh: '
    '`applyCoupon()` awaits the network before the cart total has settled, '
    'so the assertion sometimes reads the stale total.\n\n'
    'I made the test wait on the settled state instead of a fixed delay:';

/// The closing part of the answer: a Dart snippet, a summary and a choices
/// block the app renders as buttons.
const answerOutro =
    '```dart\n'
    'await tester.pumpUntil(\n'
    '  () => bloc.state is CheckoutSettled,\n'
    '  timeout: const Duration(seconds: 5),\n'
    ');\n'
    'expect(find.text(\'\\\$42.00\'), findsOneWidget);\n'
    '```\n\n'
    'The checkout tests now pass 20 runs in a row. '
    'Want me to run the rest?\n\n'
    '```choices\n'
    'Run the full test suite\n'
    'Only the checkout tests\n'
    '```';

/// Where the streaming screenshot cuts the outro (mid-sentence).
final answerOutroStreamingCut = answerOutro.indexOf('Want me');

const editPatch =
    '--- a/test/checkout_test.dart\n'
    '+++ b/test/checkout_test.dart\n'
    '@@ -41,9 +41,12 @@ void main() {\n'
    '   testWidgets(\'applies a coupon to the total\', (tester) async {\n'
    '     await tester.pumpWidget(app(bloc));\n'
    '     await tester.tap(find.byKey(const Key(\'apply-coupon\')));\n'
    '-    await tester.pump(const Duration(milliseconds: 300));\n'
    '-    expect(find.text(\'\$42.00\'), findsOneWidget);\n'
    '+    await tester.pumpUntil(\n'
    '+      () => bloc.state is CheckoutSettled,\n'
    '+      timeout: const Duration(seconds: 5),\n'
    '+    );\n'
    '+    expect(find.text(\'\$42.00\'), findsOneWidget);\n'
    '   });\n';

const blocPatch =
    '--- a/lib/checkout/checkout_bloc.dart\n'
    '+++ b/lib/checkout/checkout_bloc.dart\n'
    '@@ -88,8 +88,12 @@ class CheckoutBloc extends Bloc<CheckoutEvent, CheckoutState> {\n'
    '   Future<void> _onApplyCoupon(ApplyCoupon event, Emitter<CheckoutState> emit) async {\n'
    '     emit(state.copyWith(status: CheckoutStatus.applying));\n'
    '-    final total = await _repository.applyCoupon(event.code);\n'
    '-    emit(state.copyWith(total: total, status: CheckoutStatus.settled));\n'
    '+    final settled = await Future.wait([\n'
    '+      _repository.applyCoupon(event.code),\n'
    '+      _repository.refreshPrices(state.cart),\n'
    '+    ]);\n'
    '+    final total = settled.first;\n'
    '+    emit(CheckoutSettled(cart: state.cart, total: total));\n'
    '   }\n';

/// The two-file patch behind the diff screen and the demo's diff step.
List<FileDiff> sampleDiffs() => [
  FileDiff(
    file: 'test/checkout_test.dart',
    additions: 5,
    deletions: 2,
    patch: editPatch,
  ),
  FileDiff(
    file: 'lib/checkout/checkout_bloc.dart',
    additions: 6,
    deletions: 2,
    patch: blocPatch,
  ),
];

MessageInfo messageInfo(
  String id,
  String role, {
  required int created,
  int? completed,
}) => MessageInfo(
  id: id,
  sessionID: checkoutSessionID,
  role: role,
  providerID: role == 'assistant' ? 'anthropic' : null,
  modelID: role == 'assistant' ? 'claude-sonnet-4' : null,
  cost: role == 'assistant' ? 0.07 : 0,
  tokens: role == 'assistant'
      ? Tokens(input: 6420, output: 812, cacheRead: 3100)
      : Tokens(),
  time: MsgTime(created: created, completed: completed),
);

Part _readPart(String id, String path, {String status = 'completed'}) => Part(
  id: id,
  messageID: 'msg_assistant',
  type: 'tool',
  callID: id,
  toolName: 'read',
  toolState: ToolState(
    status: status,
    input: {'filePath': '$projectDirectory/$path'},
    output: status == 'completed' ? '<file>\n…\n</file>' : null,
    metadata: const {
      'display': {'lineStart': 1, 'lineEnd': 120},
    },
  ),
);

Part editPart({String status = 'completed'}) => Part(
  id: 'tool_edit',
  messageID: 'msg_assistant',
  type: 'tool',
  callID: 'tool_edit',
  toolName: 'edit',
  toolState: ToolState(
    status: status,
    input: const {'filePath': '$projectDirectory/test/checkout_test.dart'},
    output: status == 'completed' ? 'Edited test/checkout_test.dart' : null,
    metadata: const {
      'filediff': {'patch': editPatch, 'additions': 5, 'deletions': 2},
    },
  ),
);

const shellOutput =
    '00:03 +0: applies a coupon to the total\n'
    '00:04 +1: keeps the total when the coupon is invalid\n'
    '00:05 +2: clears the cart after payment\n'
    '00:06 +12: All tests passed!';

Part shellPart({String status = 'completed'}) => Part(
  id: 'tool_bash',
  messageID: 'msg_assistant',
  type: 'tool',
  callID: 'tool_bash',
  toolName: 'bash',
  toolState: ToolState(
    status: status,
    input: const {
      'command': checkoutCommand,
      'description': 'Run the checkout tests',
    },
    output: status == 'completed' ? shellOutput : null,
    metadata: status == 'completed' ? const {'exit': 0} : null,
  ),
);

/// The tool group: three reads, one edit, one command.
List<Part> toolParts({
  String editStatus = 'completed',
  String shellStatus = 'completed',
}) => [
  _readPart('tool_read_1', 'test/checkout_test.dart'),
  _readPart('tool_read_2', 'lib/checkout/checkout_bloc.dart'),
  _readPart('tool_read_3', 'lib/cart/cart_repository.dart'),
  editPart(status: editStatus),
  shellPart(status: shellStatus),
];

Part textPart(String id, String text) =>
    Part(id: id, messageID: 'msg_assistant', type: 'text', text: text);

/// The transcript as the API would hydrate it.
///
/// [streaming] leaves the assistant turn unfinished and cuts the outro
/// mid-sentence; [awaitingPermission] stops before the shell command
/// finished, which is the moment the permission card appears.
List<MessageWithParts> sampleTranscript({
  bool streaming = false,
  bool awaitingPermission = false,
}) {
  final now = DateTime.now().millisecondsSinceEpoch;
  final user = MessageWithParts(
    info: messageInfo(
      'msg_user',
      'user',
      created: now - 95 * 1000,
      completed: now - 95 * 1000,
    ),
    parts: [
      Part(
        id: 'part_user',
        messageID: 'msg_user',
        type: 'text',
        text: userPrompt,
      ),
    ],
  );
  final unfinished = streaming || awaitingPermission;
  final assistant = MessageWithParts(
    info: messageInfo(
      'msg_assistant',
      'assistant',
      created: now - 80 * 1000,
      completed: unfinished ? null : now - 4 * 1000,
    ),
    parts: [
      textPart('part_intro', answerIntro),
      ...toolParts(shellStatus: awaitingPermission ? 'running' : 'completed'),
      if (!awaitingPermission)
        textPart(
          'part_outro',
          streaming
              ? answerOutro.substring(0, answerOutroStreamingCut)
              : answerOutro,
        ),
    ],
  );
  return [user, assistant];
}

PermissionRequest samplePermission() => PermissionRequest(
  id: 'perm_checkout',
  sessionID: checkoutSessionID,
  permission: 'bash',
  patterns: const [checkoutCommand],
  metadata: const {'command': checkoutCommand},
  always: const ['flutter test *'],
);

PendingQuestion sampleQuestion() => const PendingQuestion(
  id: 'q_darkmode',
  sessionID: darkModeSessionID,
  prompts: [
    QuestionPrompt(
      title: 'Theme source',
      question:
          'Should dark mode follow the system setting, or add a manual '
          'toggle in Settings?',
      multiple: false,
      custom: true,
      choices: [
        QuestionChoice(
          label: 'Follow the system',
          description: 'Use MediaQuery.platformBrightness',
        ),
        QuestionChoice(
          label: 'Manual toggle',
          description: 'A switch in Settings, remembered per device',
        ),
      ],
    ),
  ],
);

/// Five providers, two connected.
List<IntegrationInfo> sampleIntegrations() {
  IntegrationInfo provider(String id, String name, {bool connected = false}) =>
      IntegrationInfo(
        id: id,
        name: name,
        methods: const [
          IntegrationMethodInfo(type: 'api', id: 'api', label: 'API key'),
        ],
        connections: connected
            ? const [
                IntegrationConnectionInfo(
                  type: 'credential',
                  id: 'key-1',
                  label: 'API key',
                ),
              ]
            : const [],
        connectionCount: connected ? 1 : 0,
      );
  return [
    provider('anthropic', 'Anthropic', connected: true),
    provider('openai', 'OpenAI', connected: true),
    provider('google', 'Google'),
    provider('groq', 'Groq'),
    provider('ollama', 'Ollama'),
  ];
}

/// A catalog with a handful of models per connected provider so the provider
/// rows read "Connected · N models".
CatalogSnapshot sampleCatalog() {
  CatalogModel model(String providerID, String id, String name) => CatalogModel(
    id: id,
    providerID: providerID,
    name: name,
    enabled: true,
    status: 'active',
    contextLimit: 200000,
    outputLimit: 32000,
    reasoning: true,
    attachments: true,
    tools: true,
    variants: const [],
    cost: const ModelCost(inputPerMillion: 3, outputPerMillion: 15),
  );
  return CatalogSnapshot(
    providers: const [
      CatalogProvider(id: 'anthropic', name: 'Anthropic', enabled: true),
      CatalogProvider(id: 'openai', name: 'OpenAI', enabled: true),
    ],
    models: [
      model('anthropic', 'claude-sonnet-4', 'Claude Sonnet 4'),
      model('anthropic', 'claude-opus-4', 'Claude Opus 4'),
      model('anthropic', 'claude-haiku-3-5', 'Claude Haiku 3.5'),
      model('anthropic', 'claude-sonnet-3-7', 'Claude Sonnet 3.7'),
      model('openai', 'gpt-4-1', 'GPT-4.1'),
      model('openai', 'gpt-4-1-mini', 'GPT-4.1 mini'),
      model('openai', 'o3', 'o3'),
      model('openai', 'o4-mini', 'o4-mini'),
      model('openai', 'gpt-4o', 'GPT-4o'),
      model('openai', 'gpt-4o-mini', 'GPT-4o mini'),
    ],
    agents: const [],
  );
}

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

/// The v1 transport the captured screens talk to. Everything returns sample
/// data synchronously; prompts are accepted and forgotten, because the demo
/// drives the reply itself through [ConnectionController.handleEventForTesting].
class CaptureApi extends OpenCodeApi {
  CaptureApi() : super(baseUrl: 'http://localhost:4096');

  Map<String, Session> sessionsById = sampleSessions();
  Set<String> busy = {checkoutSessionID};
  Future<List<MessageWithParts>> Function(String id)? messagesHandler;
  List<FileDiff> diffs = const [];
  int createdSessions = 0;
  final List<String> prompts = [];

  @override
  Future<List<Session>> sessions() async => sessionsById.values.toList();

  @override
  Future<Map<String, String>> sessionStatuses() async => {
    for (final id in sessionsById.keys) id: busy.contains(id) ? 'busy' : 'idle',
  };

  @override
  Future<List<MessageWithParts>> messages(String id) =>
      messagesHandler?.call(id) ?? Future.value([]);

  @override
  Future<Session> session(String id) async =>
      sessionsById[id] ?? Session(id: id, directory: projectDirectory);

  @override
  Future<Session> createSession() async {
    createdSessions += 1;
    final now = DateTime.now().millisecondsSinceEpoch;
    final session = Session(
      id: 'ses_new_$createdSessions',
      directory: projectDirectory,
      time: SessionTime(created: now, updated: now),
    );
    sessionsById[session.id] = session;
    return session;
  }

  @override
  Future<List<FileDiff>> diff(String id) async => diffs;

  @override
  Future<List<Todo>> todos(String id) async => const [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];

  @override
  Future<FileContent> fileContent(String path) =>
      Future.error(StateError('no fixture for $path'));

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
    prompts.add(text);
  }

  @override
  Future<void> abort(String sessionID) async {}

  @override
  Future<void> renameSession(String id, String title) async {}

  @override
  Future<void> deleteSession(String id) async {}
}

/// The product repository behind Workspace, Chat and Integrations.
class CaptureRepository implements ProductRepository {
  List<IntegrationInfo> integrations = const [];

  @override
  void setLocation({String? directory, String? workspace}) {}

  // Growable on purpose: the workspace sorts the list it gets back.
  @override
  Future<List<WorkspaceProject>> listProjects() async => [
    const WorkspaceProject(
      id: 'project_shopfront',
      name: 'shopfront',
      directory: projectDirectory,
      worktrees: [],
      updatedAt: 1,
    ),
  ];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<List<TerminalProcess>> listTerminals() async => const [];

  @override
  Future<CatalogSnapshot> loadCatalog() async => sampleCatalog();

  @override
  Future<List<CommandInfo>> listCommands() async => const [];

  @override
  Future<List<ReferenceInfo>> listReferences() async => const [];

  @override
  Future<List<McpServerInfo>> listMcpServers() async => const [];

  @override
  Future<List<McpResourceInfo>> listMcpResources() async => const [];

  @override
  Future<List<IntegrationInfo>> listIntegrations() async => integrations;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// A connection that never reaches for the network: hydration is a no-op and
/// answering a permission just clears it, so the demo's "Allow once" tap lands
/// without a server.
class CaptureController extends ConnectionController {
  CaptureController(super.store);

  final List<({String id, String reply})> answered = [];

  @override
  Future<void> refreshPendingPermissions() async {}

  @override
  Future<void> refreshPendingQuestions() async {}

  @override
  Future<void> refreshPendingForms() async {}

  @override
  Future<void> refreshCatalog() async {}

  @override
  Future<void> answerPermission(
    String id,
    String reply, {
    String? message,
  }) async {
    answered.add((id: id, reply: reply));
    permissions.remove(id);
    notifyListeners();
  }
}

/// A store presenting one saved profile without touching secure storage.
class SeededProfileStore extends ProfileStore {
  SeededProfileStore({required super.prefs, required this.seeded});

  final List<ServerProfile> seeded;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(seeded);

  @override
  String? get activeId => seeded.isEmpty ? null : seeded.first.id;
}

/// A connected controller over [api] and [repository] with the sample
/// sessions loaded and the checkout session busy.
Future<CaptureController> captureController({
  required SharedPreferences prefs,
  CaptureApi? api,
  CaptureRepository? repository,
  bool connected = true,
}) async {
  final store = SeededProfileStore(
    prefs: prefs,
    seeded: connected
        ? [
            ServerProfile(
              id: 'laptop',
              name: 'Laptop',
              baseUrl: 'http://192.168.1.20:4096',
            ),
          ]
        : const [],
  );
  final controller = CaptureController(store);
  if (!connected) return controller;
  final activeApi = api ?? CaptureApi();
  controller
    ..api = activeApi
    ..repository = repository ?? CaptureRepository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..sessionsById = Map.of(activeApi.sessionsById)
    ..busySessions = Set.of(activeApi.busy);
  return controller;
}

// ---------------------------------------------------------------------------
// Live event helpers (the shapes test/chat_live_events_test.dart uses)
// ---------------------------------------------------------------------------

EventEnvelope captureEvent(String type, Map<String, dynamic> properties) =>
    EventEnvelope(type: type, properties: properties);

Map<String, dynamic> partJson({
  required String id,
  required String messageID,
  required String type,
  String text = '',
  String? tool,
  Map<String, dynamic>? state,
}) => {
  'id': id,
  'sessionID': checkoutSessionID,
  'messageID': messageID,
  'type': type,
  'text': text,
  'tool': ?tool,
  if (tool != null) 'callID': id,
  'state': ?state,
};

/// Serialises a fixture [Part]'s tool state the way the server sends it.
Map<String, dynamic> toolStateJson(ToolState state) => {
  'status': state.status,
  'input': state.input,
  'output': ?state.output,
  'metadata': ?state.metadata,
  'title': ?state.title,
};
