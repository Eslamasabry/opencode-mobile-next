import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Records what the composer actually asked the server to do, so a test can
/// tell a slash command apart from a plain prompt that merely starts with a
/// slash.
class _RecordingApi extends OpenCodeApi {
  _RecordingApi() : super(baseUrl: 'http://localhost');

  final List<String> prompts = [];
  final List<({String command, String arguments})> slashCommands = [];

  /// Thrown by the next [promptAsync]; a transport failure (no status code)
  /// is what the composer treats as "the server vanished mid-send".
  Object? promptFailure;

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  // Growable on purpose: the chat screen sorts what it gets back, which a
  // const list refuses.
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<Session> session(String id) async => Session(id: id);

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
    final failure = promptFailure;
    if (failure != null) throw failure;
    prompts.add(text);
  }

  @override
  Future<void> slashCommand(
    String sessionID,
    String command,
    String args, {
    ModelRef? model,
    String? variant,
  }) async {
    slashCommands.add((command: command, arguments: args));
  }
}

class _CommandRepository implements ProductRepository {
  _CommandRepository(this.commands);

  final List<CommandInfo> commands;

  @override
  Future<List<CommandInfo>> listCommands() async => commands;

  @override
  Future<List<ReferenceInfo>> listReferences() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(_RecordingApi api) async {
  // Seed the profile through SharedPreferences rather than ProfileStore
  // .upsert: upsert writes through flutter_secure_storage, whose channel
  // never answers inside testWidgets (see the mock installed in setUp).
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final preferences = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: preferences);
  await store.load();
  return ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected;
}

/// Bounded pump: the chat screen keeps looping indicators alive, so
/// pumpAndSettle never settles here.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<ReviewHandoffStore> _pumpChat(
  WidgetTester tester,
  ConnectionController controller, {
  ReviewHandoffStore? handoffStore,
}) async {
  final store = handoffStore ?? ReviewHandoffStore();
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        home: ChatScreen(sessionID: 'session-1', handoffStore: store),
      ),
    ),
  );
  await _settle(tester);
  return store;
}

ReviewReference _reference({
  String id = 'ref-1',
  String path = 'lib/client.dart',
  String? snippet = '@@ -8,2 +8,2 @@\n-old request\n+new request',
}) => ReviewReference(
  id: id,
  kind: ReviewReferenceKind.hunk,
  path: path,
  lineLabel: 'new lines 8–9',
  snippet: snippet,
  scope: ReviewReferenceScope.workingTree,
);

Future<void> _send(WidgetTester tester) async {
  await tester.tap(find.byKey(const Key('chat-send-button')));
  await _settle(tester);
}

Future<void> _type(WidgetTester tester, String text) async {
  await tester.enterText(find.byKey(const Key('chat-composer-field')), text);
  await tester.pump();
}

String _composerText(WidgetTester tester) =>
    tester
        .widget<TextField>(find.byKey(const Key('chat-composer-field')))
        .controller
        ?.text ??
    '';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('a staged reference does not turn a slash command into a '
      'prompt', (tester) async {
    final api = _RecordingApi();
    final controller = await _controller(api);
    controller.repository = _CommandRepository(const [
      CommandInfo(
        name: 'review',
        description: 'Review current changes',
        subtask: false,
      ),
    ]);
    addTearDown(controller.dispose);
    final handoff = await _pumpChat(tester, controller);

    handoff.stage('session-1', _reference());
    await tester.pump();
    await _type(tester, '/review');
    await _send(tester);

    // The user typed a command, so a command is what runs.
    expect(api.slashCommands.single.command, 'review');
    expect(api.prompts, isEmpty);
    // Nothing is silently swallowed: the references survive for the prompt
    // the user writes next.
    expect(handoff.referencesFor('session-1'), hasLength(1));
    expect(
      find.byKey(const Key('references-kept-notice')),
      findsOneWidget,
      reason: 'the surviving chips have to be explained, not just left',
    );
  });

  testWidgets('the composer says staged references are not saved', (
    tester,
  ) async {
    final api = _RecordingApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    final handoff = await _pumpChat(tester, controller);

    handoff.stage('session-1', _reference());
    await tester.pump();

    // References live in memory only, so the composer says so rather than
    // letting a restart lose them silently.
    final note = tester.widget<Text>(
      find.byKey(const Key('composer-reference-note')),
    );
    expect(note.data, contains('Not saved with your draft.'));
  });

  testWidgets('references still ride along on an ordinary prompt', (
    tester,
  ) async {
    final api = _RecordingApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    final handoff = await _pumpChat(tester, controller);

    handoff.stage('session-1', _reference());
    await tester.pump();
    await _type(tester, 'Why did this change?');
    await _send(tester);

    expect(api.prompts.single, contains('Why did this change?'));
    expect(api.prompts.single, contains('`lib/client.dart`'));
    expect(api.prompts.single, contains('+new request'));
    expect(handoff.referencesFor('session-1'), isEmpty);
  });

  testWidgets('a failed send gives the reference text back to the composer', (
    tester,
  ) async {
    final api = _RecordingApi()
      ..promptFailure = ApiException('server refused', statusCode: 500);
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    final handoff = await _pumpChat(tester, controller);

    handoff.stage('session-1', _reference());
    await tester.pump();
    await _type(tester, 'Explain this hunk');
    await _send(tester);

    // The send failed with a declared server error, so the text — the
    // reference block included — comes back rather than disappearing.
    final restored = _composerText(tester);
    expect(restored, contains('Explain this hunk'));
    expect(restored, contains('`lib/client.dart`'));
    expect(restored, contains('+new request'));
    expect(controller.queuedPromptsFor('session-1'), isEmpty);
  });

  testWidgets('composing offline queues the prompt with its references', (
    tester,
  ) async {
    final api = _RecordingApi();
    final controller = await _controller(api);
    controller.status = StreamStatus.disconnected;
    addTearDown(controller.dispose);
    final handoff = await _pumpChat(tester, controller);

    handoff.stage('session-1', _reference());
    await tester.pump();
    await _type(tester, 'Queue this for later');
    await _send(tester);

    final queued = controller.queuedPromptsFor('session-1').single;
    expect(queued.text, contains('Queue this for later'));
    expect(queued.text, contains('`lib/client.dart`'));
    expect(queued.text, contains('+new request'));
    expect(handoff.referencesFor('session-1'), isEmpty);
    expect(_composerText(tester), isEmpty);
  });

  testWidgets('a transport failure queues the reference text for retry', (
    tester,
  ) async {
    final api = _RecordingApi()
      ..promptFailure = ApiException('connection closed');
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    final handoff = await _pumpChat(tester, controller);

    handoff.stage('session-1', _reference());
    await tester.pump();
    await _type(tester, 'Retry me');
    await _send(tester);

    final queued = controller.queuedPromptsFor('session-1').single;
    expect(queued.text, contains('Retry me'));
    expect(queued.text, contains('`lib/client.dart`'));
    expect(queued.text, contains('+new request'));
    expect(_composerText(tester), isEmpty);
  });
}
