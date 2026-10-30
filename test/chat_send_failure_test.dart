// A message that wasn't sent says why and what to do (emulator, 2026-10-27:
// a Kilo chat on the phone said "Your message wasn't sent · The request
// could not finish." with only Dismiss, and "No reply came back · Send
// again" under the prompt, forever).
//
// The first group drives the real Paseo gateway against a scripted helper
// (shapes from @getpaseo/server 0.11.2, see agent_run_failure_test.dart);
// the second checks each kind's words and fix on a fake gateway.
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart'
    show AgentSignInMethod;
import 'package:opencode_mobile/domain/agent_run_failure.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;
import 'support/complete_message_history.dart';
import 'support/fake_server_gateway.dart';

const _dir = '/work/app';
const _model = 'kilo/google/gemini-3-pro-image';
const _secret = 'sk-kilo-0123456789abcdefSECRET';

Future<void> _frames(WidgetTester tester, [int count = 20]) async {
  for (var i = 0; i < count; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _send(WidgetTester tester, String text) async {
  await tester.enterText(find.byKey(const Key('chat-composer-field')), text);
  await tester.pump();
  await tester.tap(find.byKey(const ValueKey('kit-composer-send')));
  await _frames(tester);
}

String _composerText(WidgetTester tester) => tester
    .widget<TextField>(
      find.descendant(
        of: find.byKey(const Key('chat-composer-field')),
        matching: find.byType(TextField),
      ),
    )
    .controller!
    .text;

Finder _inBanner(Finder finder) => find.descendant(
  of: find.byKey(const ValueKey('chat-status-send-error')),
  matching: finder,
);

// ---------------------------------------------------------------------------
// The Kilo chat on this phone, through the real gateway
// ---------------------------------------------------------------------------

Map<String, dynamic> _kilo({required bool loaded}) => {
  ...agentJson('a1', loaded: loaded),
  'provider': 'kilo',
  'persistence': {'provider': 'kilo', 'sessionId': 'ses_kilo'},
  'model': _model,
  'title': 'Say pong',
  'currentModeId': 'code',
  'availableModes': [
    {'id': 'code', 'label': 'Code'},
  ],
};

Future<(FakeDaemon, ConnectionController)> _openKilo(
  WidgetTester tester, {
  bool loaded = true,
  String refusal = 'Authentication required',
  // The helper loads a saved agent when its timeline is read and answers
  // with that snapshot (session.js handleFetchAgentTimelineRequest).
  bool loadsOnRead = false,
  // The helper takes the message and the turn then fails (Kilo signed out,
  // 0.11.2: the "[System Error]" row, then turn_failed, then the agent).
  bool turnFails = false,
  // This phone shows Kilo signed in (it runs its free models without an
  // account): a sign-in refusal then means the model needs the account.
  bool kiloSignedIn = false,
  // What the agent's payload carries besides (after a restart: `status`
  // "error" and an `error` attention, no `lastError`).
  Map<String, Object?> agentFields = const {},
  // The app's own saved state (AgentTurnFailureMemory).
  Map<String, Object> prefs = const {},
  // Keep the preferences of the last open (the app force-stopped and came
  // back: the same disk, a new controller and gateway).
  bool keepPrefs = false,
  // What the loaded snapshot carries instead of [agentFields] (after a
  // restart the loaded agent reads idle, agent-loading.js).
  Map<String, Object?>? loadedFields,
  // The timeline after "Say pong" (a reply that came later).
  List<Map<String, dynamic>> laterItems = const [],
  // Other records of this conversation (forks an older build's resumes made:
  // the same native session, other agent ids).
  List<Map<String, dynamic>> forks = const [],
}) async {
  if (!keepPrefs) SharedPreferences.setMockInitialValues(prefs);
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  final daemon = FakeDaemon();
  // Once loaded, the helper answers with the live snapshot everywhere.
  var held = loaded;
  Map<String, dynamic> agent() => {
    ..._kilo(loaded: held),
    ...(held ? loadedFields ?? agentFields : agentFields),
  };
  daemon.handlers['fetch_agents_request'] = (_) => (
    'fetch_agents_response',
    {
      'entries': [
        {'agent': agent()},
        for (final fork in forks) {'agent': fork},
      ],
      'pageInfo': {'hasMore': false},
    },
  );
  // As Paseo 0.11.2: a resume takes no id and makes a new agent. Nothing in
  // the app should ask for one.
  daemon.handlers['resume_agent_request'] = (_) => (
    'status',
    {
      'status': 'agent_resumed',
      'agent': {..._kilo(loaded: true), 'id': 'a-fork'},
    },
  );
  daemon.handlers['fetch_agent_request'] = (_) =>
      ('fetch_agent_response', {'agent': agent()});
  daemon.handlers['fetch_agent_timeline_request'] = (_) => (
    'fetch_agent_timeline_response',
    {
      'agentId': 'a1',
      if (loadsOnRead)
        'agent': {
          ..._kilo(loaded: held = true),
          ...(loadedFields ?? agentFields),
        },
      'epoch': 'e1',
      'endCursor': {'epoch': 'e1', 'seq': 1},
      'hasOlder': false,
      'hasNewer': false,
      'entries': [
        {
          'provider': 'kilo',
          'item': {
            'type': 'user_message',
            'text': 'Say pong',
            'messageId': 'u1',
          },
          'timestamp': '2026-10-25T17:32:41.714Z',
          'seqStart': 1,
          'turnId': 'turn-1',
        },
        for (final (i, item) in laterItems.indexed)
          {
            'provider': 'kilo',
            'item': item,
            'timestamp': '2026-10-25T17:33:0$i.000Z',
            'seqStart': 2 + i,
            'turnId': 'turn-1',
          },
      ],
    },
  );
  daemon.handlers['agent.provider_subagents.list.request'] = (_) =>
      ('agent.provider_subagents.list.response', {'subagents': <Object>[]});
  daemon.handlers['get_providers_snapshot_request'] = (_) => (
    'get_providers_snapshot_response',
    {
      'entries': [
        {
          'provider': 'kilo',
          'status': 'ready',
          'label': 'Kilo',
          'models': [
            {'provider': 'kilo', 'id': _model, 'label': 'Gemini 3 Pro Image'},
          ],
          'modes': [
            {'id': 'code', 'label': 'Code'},
          ],
        },
      ],
    },
  );
  Map<String, dynamic> stream(Map<String, dynamic> event, [int? seq]) => {
    'agentId': 'a1',
    'event': {'provider': 'kilo', 'turnId': 'turn-2', ...event},
    'timestamp': '2026-10-27T18:38:24.000Z',
    'seq': ?seq,
    if (seq != null) 'epoch': 'e1',
  };
  daemon.handlers['send_agent_message_request'] = (request) {
    if (!turnFails) {
      return (
        'send_agent_message_response',
        {'agentId': 'a1', 'accepted': false, 'error': refusal},
      );
    }
    final id = request['messageId'];
    daemon
      ..push('agent_stream', stream({'type': 'turn_started'}))
      ..push(
        'agent_stream',
        stream({
          'type': 'timeline',
          'item': {
            'type': 'user_message',
            'text': request['text'],
            'messageId': id,
            'clientMessageId': id,
          },
        }, 2),
      )
      ..push(
        'agent_stream',
        stream({
          'type': 'timeline',
          'item': {
            'type': 'assistant_message',
            'text':
                '[System Error] Internal error: You need to sign in to use '
                'this model.\n\ncode: -32603',
          },
        }, 3),
      )
      ..push(
        'agent_stream',
        stream({
          'type': 'turn_failed',
          'error': 'Internal error: You need to sign in to use this model.',
          'code': '-32603',
        }),
      )
      ..push('agent_update', {
        'kind': 'upsert',
        'agent': {
          ..._kilo(loaded: true),
          'status': 'error',
          'lastError': 'Internal error: You need to sign in to use this model.',
          'requiresAttention': true,
          'attentionReason': 'error',
        },
      });
    return (
      'send_agent_message_response',
      {'agentId': 'a1', 'accepted': true, 'error': null},
    );
  };
  final gateway = PaseoGateway(
    directory: _dir,
    transport: PaseoTransport(
      endpoint: 'ws://127.0.0.1:6767',
      socketFactory: (_, _) async => daemon,
    ),
  );
  final controller = _PhoneAgents(
    store,
    kiloSignedIn: kiloSignedIn,
    paseoGatewayFactory: (_) => (gateway: gateway, operations: gateway),
  );
  await controller.connect(
    ServerProfile(
      id: 'paseo-test',
      name: 'This phone',
      baseUrl: 'ws://127.0.0.1:6767',
      backend: ServerBackend.paseo,
      codexDirectory: _dir,
      transientTransport: true,
    ),
  );
  tester.view.physicalSize = const Size(412, 1400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(
        debugShowCheckedModeBanner: false,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ChatScreen(sessionID: 'a1'),
      ),
    ),
  );
  await _frames(tester, 25);
  return (daemon, controller);
}

/// Leaves the chat and lets the connection's timers end.
Future<void> _close(WidgetTester tester, ConnectionController c) async {
  await tester.pumpWidget(const SizedBox());
  c.dispose();
  await tester.pump(const Duration(seconds: 3));
}

/// The agents' connection, saying whether this phone shows Kilo signed in.
class _PhoneAgents extends ConnectionController {
  _PhoneAgents(
    super.store, {
    required this.kiloSignedIn,
    required super.paseoGatewayFactory,
  }) : super(isAgentBackend: true);

  final bool kiloSignedIn;

  @override
  AgentSignInState? agentSignInState(String agentId) =>
      kiloSignedIn && agentId == 'kilo'
      ? const AgentSignInState(
          phase: AgentSignInPhase.signedIn,
          method: AgentSignInMethod.browserOAuthHost,
          inspected: true,
        )
      : super.agentSignInState(agentId);
}

// ---------------------------------------------------------------------------
// Each kind on a fake gateway
// ---------------------------------------------------------------------------

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api(this.error) : super(baseUrl: 'http://localhost');

  final Object error;
  int sends = 0;
  final texts = <String>[];

  // A host whose sends fail straight back (no offline queue in between),
  // like the agents on this phone.
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(offlinePromptQueue: false);

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<Map<String, SessionRetryState>> sessionRetryStates() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => [
    MessageWithParts(
      info: MessageInfo(
        id: 'u1',
        sessionID: 'session-1',
        role: 'user',
        time: MsgTime(created: 1, completed: 2),
      ),
      parts: [Part(id: 'u1-text', messageID: 'u1', type: 'text', text: 'Hi')],
    ),
    MessageWithParts(
      info: MessageInfo(
        id: 'a1',
        sessionID: 'session-1',
        role: 'assistant',
        time: MsgTime(created: 10, completed: 11),
      ),
      parts: const [],
    ),
  ];

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<FileDiff>> diff(String id) async => const [];

  @override
  Future<List<Todo>> todos(String id) async => const [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];

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
    sends++;
    texts.add(text);
    throw error;
  }
}

PaseoFailure _refused(String reason, {String provider = 'kilo'}) =>
    PaseoFailure(
      PaseoFailureKind.unavailable,
      reason: reason,
      runFailure: AgentRunFailure(
        reason: reason,
        provider: provider,
        model: _model,
      ),
    );

Future<_Api> _pumpChat(WidgetTester tester, Object error) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles':
        '[{"id":"profile","name":"Test","baseUrl":"http://localhost"}]',
    'oc.activeProfile': 'profile',
  });
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(secure, (_) async => null);
  addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  final api = _Api(error);
  final controller = ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  tester.view.physicalSize = const Size(412, 1400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ChatScreen(
          sessionID: 'session-1',
          handoffStore: ReviewHandoffStore(),
        ),
      ),
    ),
  );
  await _frames(tester);
  return api;
}

const _actionKeys = [
  'chat-send-error-sign-in',
  'chat-send-error-choose-model',
  'chat-send-error-try-again',
  'chat-send-error-continue-with',
  'chat-send-error-details',
];

List<String> _shownActions() => [
  for (final key in _actionKeys)
    if (find.byKey(ValueKey(key)).evaluate().isNotEmpty) key,
];

/// The actions the banner keeps under More, in order.
Future<List<String>> _bannerActions(WidgetTester tester) async {
  final more = _inBanner(find.byKey(const ValueKey('kit-status-more')));
  if (more.evaluate().isEmpty) return const [];
  final inSight = _shownActions().toSet();
  await tester.tap(more);
  await tester.pumpAndSettle();
  final listed = [
    for (final key in _shownActions())
      if (!inSight.contains(key)) key,
  ];
  await tester.tapAt(const Offset(4, 4));
  await tester.pumpAndSettle();
  return listed;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('a Kilo chat on this phone', () {
    testWidgets('a refused send says Kilo needs a sign-in, leads with Sign '
        'in, and the prompt with no reply says it too', (tester) async {
      final (daemon, c) = await _openKilo(tester);
      expect(find.text('No reply came back'), findsOneWidget);
      await _send(tester, 'reply with the word banana');
      // The message reached the helper (it used to stop in the app).
      expect(daemon.of('send_agent_message_request'), hasLength(1));
      expect(find.text("Your message wasn't sent"), findsOneWidget);
      expect(_inBanner(find.text('Kilo needs you to sign in')), findsOneWidget);
      expect(find.text('The request could not finish.'), findsNothing);
      expect(
        find.byKey(const ValueKey('chat-send-error-sign-in')),
        findsOneWidget,
      );
      // Under the prompt: the same reason, the sign-in first.
      final notice = find.byKey(const ValueKey('prompt-no-reply-u1'));
      expect(
        find.descendant(
          of: notice,
          matching: find.text('Kilo needs you to sign in'),
        ),
        findsOneWidget,
      );
      // As for a failure seen live: the fix and Error details, not a Send
      // again into the same wall.
      expect(find.byKey(const Key('no-reply-sign-in')), findsOneWidget);
      expect(find.byKey(const Key('no-reply-details')), findsOneWidget);
      expect(find.byKey(const Key('no-reply-send-again')), findsNothing);
      await _close(tester, c);
    });

    testWidgets('a saved conversation the helper no longer holds says it '
        "can't continue and offers Continue with…, sending nothing", (
      tester,
    ) async {
      final (daemon, c) = await _openKilo(tester, loaded: false);
      await _send(tester, 'reply with the word banana');
      expect(daemon.of('send_agent_message_request'), isEmpty);
      expect(
        _inBanner(find.text("This agent can't continue this conversation")),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('chat-send-error-continue-with')),
        findsOneWidget,
      );
      expect(find.byKey(const Key('no-reply-continue-with')), findsOneWidget);
      await _close(tester, c);
    });
  });

  testWidgets('a saved Kilo conversation opens in place, and a turn that fails '
      'for a sign-in says Kilo needs you to sign in, with Sign in, under '
      'Kilo and its model', (tester) async {
    final (daemon, c) = await _openKilo(
      tester,
      loaded: false,
      loadsOnRead: true,
      turnFails: true,
    );
    await _send(tester, 'Say pong');
    // One agent: no resume that forks a new id, no new agent, the message
    // to the conversation that is open.
    expect(daemon.of('resume_agent_request'), isEmpty);
    expect(daemon.of('create_agent_request'), isEmpty);
    expect(daemon.of('send_agent_message_request').single['agentId'], 'a1');
    final row = find.byKey(const Key('error-card-agent-signIn'));
    expect(row, findsOneWidget);
    expect(
      find.descendant(
        of: row,
        matching: find.text('Kilo needs you to sign in'),
      ),
      findsOneWidget,
    );
    expect(find.byKey(const Key('error-action-agent-sign-in')), findsOneWidget);
    // Never the helper's words as copy, never a fallback to Claude Code.
    expect(find.textContaining('Internal error'), findsNothing);
    expect(find.textContaining('Claude Code'), findsNothing);
    expect(find.text('No answer yet'), findsNothing);
    // Details: the agent, the model and the helper's whole words.
    await tester.tap(find.byKey(const Key('error-action-details')));
    await tester.pumpAndSettle();
    final sheet = find.byKey(const ValueKey('chat-error-details'));
    Finder inSheet(String text) =>
        find.descendant(of: sheet, matching: find.textContaining(text));
    expect(inSheet('Internal error: You need to sign in'), findsWidgets);
    expect(inSheet(_model), findsWidgets);
    expect(inSheet('-32603'), findsWidgets);
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    await _close(tester, c);
  });

  testWidgets('Kilo reads signed in but the model needs its account: the turn '
      'says so, another model first, then Sign in to Kilo', (tester) async {
    final (_, c) = await _openKilo(
      tester,
      loaded: false,
      loadsOnRead: true,
      turnFails: true,
      kiloSignedIn: true,
    );
    await _send(tester, 'Say pong');
    final row = find.byKey(const Key('error-card-agent-account'));
    expect(row, findsOneWidget);
    expect(
      find.descendant(
        of: row,
        matching: find.text('This model needs a Kilo account'),
      ),
      findsOneWidget,
    );
    expect(find.text('Kilo needs you to sign in'), findsNothing);
    final choose = find.byKey(const Key('error-action-choose-model'));
    final signIn = find.byKey(const Key('error-action-agent-sign-in'));
    expect(choose, findsOneWidget);
    expect(
      find.descendant(of: signIn, matching: find.text('Sign in to Kilo')),
      findsOneWidget,
    );
    final first = tester.getTopLeft(choose);
    final second = tester.getTopLeft(signIn);
    expect(first.dx < second.dx || first.dy < second.dy, isTrue);
    await _close(tester, c);
  });

  testWidgets('a refused send to a signed-in Kilo: needs an account, Choose '
      'another model leads, Sign in to Kilo under More', (tester) async {
    final (_, c) = await _openKilo(tester, kiloSignedIn: true);
    await _send(tester, 'Say pong');
    expect(
      _inBanner(find.text('This model needs a Kilo account')),
      findsOneWidget,
    );
    expect(
      _inBanner(find.byKey(const ValueKey('chat-send-error-choose-model'))),
      findsOneWidget,
    );
    expect(await _bannerActions(tester), [
      'chat-send-error-sign-in',
      'chat-send-error-details',
    ]);
    await _close(tester, c);
  });

  group('after a restart', () {
    const signInError =
        'Internal error: You need to sign in to use this model.';
    // What Paseo 0.11.2 sends for the saved record after a restart: its last
    // status and attention, never `lastError` (buildStoredAgentPayload), and
    // loading it again starts without it (agent-loading.js).
    const failedRecord = {
      'status': 'error',
      'requiresAttention': true,
      'attentionReason': 'error',
    };
    String kept(String reason) =>
        jsonEncode({'s': 'a1', 'r': reason, 'p': 'kilo', 'm': _model});

    testWidgets('the prompt with no reply keeps the reason this chat saw: '
        'Kilo needs you to sign in, Sign in, Error details', (tester) async {
      final (_, c) = await _openKilo(
        tester,
        loaded: false,
        loadsOnRead: true,
        agentFields: failedRecord,
        prefs: {
          'oc.agentTurnFailures.paseo-test': [
            kept('[System Error] $signInError\n\ncode: -32603'),
          ],
        },
      );
      final notice = find.byKey(const ValueKey('prompt-no-reply-u1'));
      expect(
        find.descendant(
          of: notice,
          matching: find.text('Kilo needs you to sign in'),
        ),
        findsOneWidget,
      );
      expect(find.byKey(const Key('no-reply-sign-in')), findsOneWidget);
      await tester.tap(find.byKey(const Key('no-reply-details')));
      await tester.pumpAndSettle();
      final sheet = find.byKey(const ValueKey('chat-error-details'));
      expect(
        find.descendant(of: sheet, matching: find.textContaining(signInError)),
        findsWidgets,
      );
      expect(
        find.descendant(of: sheet, matching: find.textContaining(_model)),
        findsWidgets,
      );
      await tester.tapAt(const Offset(4, 4));
      await tester.pumpAndSettle();
      await _close(tester, c);
    });

    testWidgets('an error-status record opens in place (no resume, no new '
        'agent) and its kept reason shows', (tester) async {
      final (daemon, c) = await _openKilo(
        tester,
        loaded: false,
        loadsOnRead: true,
        agentFields: failedRecord,
        prefs: {
          'oc.agentTurnFailures.paseo-test': [kept(signInError)],
        },
      );
      expect(daemon.of('resume_agent_request'), isEmpty);
      expect(daemon.of('create_agent_request'), isEmpty);
      expect(
        daemon.of('fetch_agent_timeline_request').map((r) => r['agentId']),
        everyElement('a1'),
      );
      expect(find.text('Kilo needs you to sign in'), findsOneWidget);
      await _close(tester, c);
    });

    testWidgets('a reason kept under an older fork of the same conversation '
        'shows on the record the row opens', (tester) async {
      final (_, c) = await _openKilo(
        tester,
        loaded: false,
        loadsOnRead: true,
        agentFields: {'status': 'idle'},
        forks: [
          {
            ..._kilo(loaded: false),
            'id': 'a0',
            'status': 'error',
            'updatedAt': '2026-09-19T03:00:00.000Z',
          },
        ],
        prefs: {
          'oc.agentTurnFailures.paseo-test': [
            jsonEncode({'s': 'a0', 'r': signInError, 'p': 'kilo'}),
          ],
        },
      );
      expect(find.text('Kilo needs you to sign in'), findsOneWidget);
      await _close(tester, c);
    });

    testWidgets('signed-in Kilo: this model needs a Kilo account, another '
        'model first, then Sign in to Kilo', (tester) async {
      final (_, c) = await _openKilo(
        tester,
        loaded: false,
        loadsOnRead: true,
        kiloSignedIn: true,
        agentFields: failedRecord,
        prefs: {
          'oc.agentTurnFailures.paseo-test': [kept(signInError)],
        },
      );
      expect(find.text('This model needs a Kilo account'), findsOneWidget);
      final choose = find.byKey(const Key('no-reply-choose-model'));
      final signIn = find.byKey(const Key('no-reply-sign-in'));
      expect(choose, findsOneWidget);
      expect(
        find.descendant(of: signIn, matching: find.text('Sign in to Kilo')),
        findsOneWidget,
      );
      final first = tester.getTopLeft(choose);
      final second = tester.getTopLeft(signIn);
      expect(first.dx < second.dx || first.dy < second.dy, isTrue);
      await _close(tester, c);
    });

    testWidgets('while the helper still holds lastError, it is the reason, '
        'and the chat keeps it for later', (tester) async {
      final (_, c) = await _openKilo(
        tester,
        agentFields: {...failedRecord, 'lastError': signInError},
      );
      expect(find.text('Kilo needs you to sign in'), findsOneWidget);
      await tester.pump(const Duration(milliseconds: 100));
      final saved = c.store.prefs.getStringList(
        'oc.agentTurnFailures.paseo-test',
      );
      expect(saved, hasLength(1));
      expect(saved!.single, contains('You need to sign in'));
      await _close(tester, c);
    });

    testWidgets('a turn that answered later lets the reason go for good', (
      tester,
    ) async {
      final (_, c) = await _openKilo(
        tester,
        loaded: false,
        loadsOnRead: true,
        // The attention outlives the turn that answered.
        agentFields: {'requiresAttention': true, 'attentionReason': 'error'},
        prefs: {
          'oc.agentTurnFailures.paseo-test': [kept(signInError)],
        },
        laterItems: [
          {'type': 'assistant_message', 'text': 'pong', 'messageId': 'r1'},
        ],
      );
      expect(find.text('Kilo needs you to sign in'), findsNothing);
      expect(find.byKey(const ValueKey('prompt-no-reply-u1')), findsNothing);
      await tester.pump(const Duration(milliseconds: 100));
      expect(
        c.store.prefs.getStringList('oc.agentTurnFailures.paseo-test'),
        isEmpty,
      );
      await _close(tester, c);
    });

    testWidgets('force-stopped seconds after a live sign-in failure, the '
        'reopened chat still says why (emulator 3910)', (tester) async {
      final (_, live) = await _openKilo(
        tester,
        loaded: false,
        loadsOnRead: true,
        turnFails: true,
      );
      await _send(tester, 'pong');
      expect(find.text('Kilo needs you to sign in'), findsOneWidget);
      // Force-stop: the controller and gateway go; the helper restarts.
      await _close(tester, live);
      final (_, reopened) = await _openKilo(
        tester,
        keepPrefs: true,
        loaded: false,
        loadsOnRead: true,
        // The saved record: failed, no lastError; loaded again it reads
        // idle and keeps the unread attention it had before (Paseo sets a
        // new one only when none is unread).
        agentFields: {
          'status': 'error',
          'requiresAttention': true,
          'attentionReason': 'finished',
        },
        loadedFields: {
          'requiresAttention': true,
          'attentionReason': 'finished',
        },
      );
      final notice = find.byKey(const ValueKey('prompt-no-reply-u1'));
      expect(
        find.descendant(
          of: notice,
          matching: find.text('Kilo needs you to sign in'),
        ),
        findsOneWidget,
      );
      expect(find.byKey(const Key('no-reply-sign-in')), findsOneWidget);
      expect(find.byKey(const Key('no-reply-details')), findsOneWidget);
      await _close(tester, reopened);
    });

    testWidgets('a failure from before the app kept reasons: the record said '
        'Failed, so the row says the request could not finish', (tester) async {
      final (_, c) = await _openKilo(
        tester,
        loaded: false,
        loadsOnRead: true,
        agentFields: {'status': 'error'},
        loadedFields: const {},
      );
      final notice = find.byKey(const ValueKey('prompt-no-reply-u1'));
      expect(
        find.descendant(
          of: notice,
          matching: find.text('The request could not finish.'),
        ),
        findsOneWidget,
      );
      expect(find.byKey(const Key('no-reply-send-again')), findsOneWidget);
      await _close(tester, c);
    });

    testWidgets('a failed turn seen live is kept', (tester) async {
      final (_, c) = await _openKilo(
        tester,
        loaded: false,
        loadsOnRead: true,
        turnFails: true,
      );
      await _send(tester, 'Say pong');
      final saved = c.store.prefs.getStringList(
        'oc.agentTurnFailures.paseo-test',
      );
      expect(saved, hasLength(1));
      expect(saved!.single, contains('You need to sign in'));
      await _close(tester, c);
    });
  });

  group('each kind', () {
    testWidgets('no sign-in here: Kilo needs a sign-in, another model leads', (
      tester,
    ) async {
      await _pumpChat(tester, _refused('Authentication required'));
      await _send(tester, 'Say pong');
      expect(_inBanner(find.text('Kilo needs you to sign in')), findsOneWidget);
      // A computer's agent signs in there: no Sign in in this app.
      expect(
        find.byKey(const ValueKey('chat-send-error-sign-in')),
        findsNothing,
      );
      expect(
        _inBanner(find.byKey(const ValueKey('chat-send-error-choose-model'))),
        findsOneWidget,
      );
      expect(await _bannerActions(tester), ['chat-send-error-details']);
    });

    testWidgets('no credits: says so, another model and Details', (
      tester,
    ) async {
      await _pumpChat(tester, _refused('402 Payment Required: add credits'));
      await _send(tester, 'Say pong');
      expect(
        _inBanner(find.text('Kilo has no credits for this model')),
        findsOneWidget,
      );
      expect(
        _inBanner(find.byKey(const ValueKey('chat-send-error-choose-model'))),
        findsOneWidget,
      );
    });

    testWidgets('model unavailable: Choose another model leads, here and '
        'under the prompt', (tester) async {
      await _pumpChat(
        tester,
        _refused('Model $_model is not available on the free plan'),
      );
      await _send(tester, 'Say pong');
      expect(
        _inBanner(find.text("This model isn't available")),
        findsOneWidget,
      );
      expect(
        _inBanner(find.byKey(const ValueKey('chat-send-error-choose-model'))),
        findsOneWidget,
      );
      expect(find.byKey(const Key('no-reply-choose-model')), findsOneWidget);
      expect(find.text("This model isn't available"), findsNWidgets(2));
    });

    testWidgets('busy: Kilo is busy, and Try again sends again', (
      tester,
    ) async {
      final api = await _pumpChat(tester, _refused('429 Too Many Requests'));
      await _send(tester, 'Say pong');
      expect(api.sends, 1);
      expect(_inBanner(find.text('Kilo is busy')), findsOneWidget);
      final retry = _inBanner(
        find.byKey(const ValueKey('chat-send-error-try-again')),
      );
      expect(retry, findsOneWidget);
      // The words are back in the message box; Try again sends them.
      await tester.tap(retry);
      await _frames(tester);
      expect(api.sends, 2);
      // Busy passes by itself: Send again stays the way under the prompt.
      expect(find.byKey(const Key('no-reply-choose-model')), findsNothing);
      expect(find.byKey(const Key('no-reply-sign-in')), findsNothing);
    });

    testWidgets('unknown: the plain line, with Try again, another model and '
        'Details', (tester) async {
      await _pumpChat(tester, _refused('Agent a1 has no pending run'));
      await _send(tester, 'Say pong');
      expect(
        _inBanner(find.text('The request could not finish.')),
        findsOneWidget,
      );
      expect(
        _inBanner(find.byKey(const ValueKey('chat-send-error-try-again'))),
        findsOneWidget,
      );
      expect(await _bannerActions(tester), [
        'chat-send-error-choose-model',
        'chat-send-error-details',
      ]);
      // Nothing more is known: the prompt's line stays as it was.
      expect(find.text('No reply came back'), findsOneWidget);
    });

    testWidgets('a failed Send again leaves the message box as it was, so '
        'the next words are sent alone; Try again repeats it once', (
      tester,
    ) async {
      // Emulator 2026-10-27: a failed chip/Send again left its words in the
      // box, and the next message went as "pongSay pong".
      final api = await _pumpChat(
        tester,
        _refused('Agent a1 has no pending run'),
      );
      await tester.tap(find.byKey(const Key('no-reply-send-again')));
      await _frames(tester);
      expect(api.texts, ['Hi']);
      expect(_composerText(tester), '');
      await tester.tap(
        _inBanner(find.byKey(const ValueKey('chat-send-error-try-again'))),
      );
      await _frames(tester);
      expect(api.texts, ['Hi', 'Hi']);
      expect(_composerText(tester), '');
      await _send(tester, 'Say pong');
      expect(api.texts.last, 'Say pong');
    });

    testWidgets('a failed Send again gives a half-typed message back', (
      tester,
    ) async {
      final api = await _pumpChat(
        tester,
        _refused('Agent a1 has no pending run'),
      );
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'half a thought',
      );
      await tester.pump();
      await tester.tap(find.byKey(const Key('no-reply-send-again')));
      await _frames(tester);
      expect(api.texts, ['Hi']);
      expect(_composerText(tester), 'half a thought');
    });

    testWidgets('a failed send puts its words back once, and the next send '
        'sends just them', (tester) async {
      final api = await _pumpChat(
        tester,
        _refused('Agent a1 has no pending run'),
      );
      await _send(tester, 'Say pong');
      expect(_composerText(tester), 'Say pong');
      await tester.tap(find.byKey(const ValueKey('kit-composer-send')));
      await _frames(tester);
      expect(api.texts, ['Say pong', 'Say pong']);
      expect(_composerText(tester), 'Say pong');
    });

    testWidgets('Details shows the agent, model, code and the host\'s words, '
        'with the credential masked', (tester) async {
      await _pumpChat(
        tester,
        _refused('Authentication required (code: -32000) api_key=$_secret'),
      );
      await _send(tester, 'Say pong');
      await tester.tap(
        _inBanner(find.byKey(const ValueKey('kit-status-more'))),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('chat-send-error-details')));
      await tester.pumpAndSettle();
      final sheet = find.byKey(const ValueKey('chat-error-details'));
      expect(sheet, findsOneWidget);
      Finder inSheet(String text) =>
          find.descendant(of: sheet, matching: find.textContaining(text));
      expect(inSheet('Authentication required'), findsWidgets);
      expect(inSheet('kilo'), findsWidgets);
      expect(inSheet(_model), findsWidgets);
      expect(inSheet('-32000'), findsWidgets);
      expect(inSheet('•••'), findsWidgets);
      expect(find.textContaining(_secret), findsNothing);
      expect(find.textContaining('SECRET'), findsNothing);
    });
  });
}
