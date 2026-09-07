import 'dart:convert';
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/voice/controller.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';
import '../tool/capture/fixtures.dart'
    show capturePng, captureTheme, loadCaptureFonts;
import 'voice_controller_test.dart'
    show FakeVoiceRecorder, FakeVoiceRecognizer, readyVoiceModelManager;

const _speech = MethodChannel('oc/read-aloud');
const _secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
const _oldReply = 'An older reply that must stay silent.';
const _newReply = 'The answer to what you just said.';
const _transcript = 'A synthetic private utterance';

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');
  final prompts = <String>[];
  Completer<void>? promptGate;

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<MessageWithParts>> messages(String id) async => [
    MessageWithParts(
      info: MessageInfo(
        id: 'old-user',
        sessionID: id,
        role: 'user',
        time: MsgTime(created: 1, completed: 1),
      ),
      parts: [Part(type: 'text', text: 'an earlier question')],
    ),
    MessageWithParts(
      info: MessageInfo(
        id: 'old-reply',
        sessionID: id,
        role: 'assistant',
        parentID: 'old-user',
        time: MsgTime(created: 2, completed: 2),
      ),
      parts: [Part(type: 'text', text: _oldReply)],
    ),
  ];

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
    await promptGate?.future;
  }
}

class _Voice extends VoiceComposerController {
  _Voice({required super.models})
    : super(recorder: FakeVoiceRecorder(), recognizer: FakeVoiceRecognizer());
  int listens = 0;

  @override
  Future<void> startListening() async {
    listens++;
    state = VoiceComposerState.listening;
    notifyListeners();
  }

  @override
  Future<void> stopListening() async {
    draft = _transcript;
    state = VoiceComposerState.draft;
    notifyListeners();
  }
}

EventEnvelope _event(String type, Map<String, dynamic> properties) =>
    EventEnvelope(type: type, properties: properties);

Map<String, dynamic> _info(
  String id,
  String role, {
  String? parentID,
  bool completed = false,
}) => {
  'id': id,
  'sessionID': 'session',
  'role': role,
  'parentID': ?parentID,
  'time': {
    'created': DateTime.now().millisecondsSinceEpoch,
    'completed': ?(completed ? DateTime.now().millisecondsSinceEpoch : null),
  },
};

Map<String, dynamic> _textPart(String id, String messageID, String text) => {
  'id': id,
  'sessionID': 'session',
  'messageID': messageID,
  'type': 'text',
  'text': text,
};

Future<ConnectionController> _pumpChat(
  WidgetTester tester,
  _Api api,
  VoiceComposerController voice, {
  GlobalKey? captureBoundary,
  bool light = false,
  double textScale = 1,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {'id': 'profile', 'name': 'Synthetic', 'baseUrl': 'http://localhost'},
    ]),
    'oc.activeProfile': 'profile',
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final connection = ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected;
  connection.sessionsById['session'] = Session(id: 'session');
  addTearDown(connection.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(connection)],
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: captureBoundary == null ? null : captureTheme(light: light),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: RepaintBoundary(
          key: captureBoundary,
          child: MediaQuery(
            data: MediaQueryData(
              size: tester.view.physicalSize / tester.view.devicePixelRatio,
              textScaler: TextScaler.linear(textScale),
            ),
            child: ChatScreen(sessionID: 'session', voiceController: voice),
          ),
        ),
      ),
    ),
  );
  await _settle(tester);
  return connection;
}

// Waiting and speaking deliberately animate. Advance finite frames instead
// of waiting for the progress indicator to become idle.
Future<void> _settle(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 350));
  await tester.pump(const Duration(milliseconds: 350));
}

void _status(ConnectionController connection, String status) {
  connection.handleEventForTesting(
    _event('session.status', {'sessionID': 'session', 'status': status}),
  );
}

void _user(ConnectionController connection, String id, String text) {
  connection.handleEventForTesting(
    _event('message.part.updated', {
      'sessionID': 'session',
      'part': _textPart('part-$id', id, text),
    }),
  );
  connection.handleEventForTesting(
    _event('message.updated', {'info': _info(id, 'user')}),
  );
}

void _reply(ConnectionController connection, {String? parent = 'user-1'}) {
  connection.handleEventForTesting(
    _event('message.updated', {
      'info': _info('reply-1', 'assistant', parentID: parent, completed: true),
    }),
  );
  connection.handleEventForTesting(
    _event('message.part.updated', {
      'sessionID': 'session',
      'part': _textPart('p-reply', 'reply-1', _newReply),
    }),
  );
}

Future<void> _completeTurn(
  WidgetTester tester,
  ConnectionController connection,
) async {
  _status(connection, 'busy');
  _user(connection, 'user-1', _transcript);
  _reply(connection);
  // Deliver completed parts while busy, before the terminal idle event.
  await tester.pump();
  _status(connection, 'idle');
  await _settle(tester);
}

Future<void> _nativeStatus(
  WidgetTester tester,
  List<MethodCall> calls,
  String status,
) async {
  final speak = calls.lastWhere((call) => call.method == 'speak');
  await tester.binding.defaultBinaryMessenger.handlePlatformMessage(
    _speech.name,
    _speech.codec.encodeMethodCall(
      MethodCall('status', {
        'operationID': (speak.arguments as Map)['operationID'],
        'status': status,
      }),
    ),
    (_) {},
  );
  await _settle(tester);
}

/// Enters voice conversation (which opens the voice sheet), listens, reviews
/// the transcript and inserts it, leaving the strip visible with the
/// transcript in the composer and nothing sent.
Future<void> _enterAndListen(WidgetTester tester, _Api api) async {
  await tester.tap(find.byKey(const Key('composer-tools-button')));
  await _settle(tester);
  await tester.tap(find.byKey(const Key('composer-tool-conversation')));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
  await tester.pump();
  await tester.tap(find.text('Start listening'));
  await tester.pump();
  await tester.tap(find.byKey(const Key('stop-voice-recording')));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 200));
  await tester.tap(find.byKey(const Key('insert-voice-draft')));
  await _settle(tester);
  expect(
    tester.widget<TextField>(find.byType(TextField).first).controller!.text,
    _transcript,
  );
  expect(api.prompts, isEmpty);
}

/// Turns "Speak replies" on through the consent and voice sheets.
Future<void> _optIn(WidgetTester tester, List<MethodCall> calls) async {
  final toggle = find.byKey(const Key('voice-speak-replies'));
  expect(toggle, findsOneWidget);
  await tester.ensureVisible(toggle);
  expect(tester.widget<SwitchListTile>(toggle).value, isFalse);
  await tester.tap(toggle);
  await _settle(tester);
  expect(find.text('Use the system speech engine?'), findsOneWidget);
  expect(calls, isEmpty);
  await tester.tap(find.text('Choose voice'));
  await _settle(tester);
  expect(calls.map((call) => call.method), ['voices']);
  await tester.tap(find.text('Installed voice'));
  await _settle(tester);
  expect(tester.widget<SwitchListTile>(toggle).value, isTrue);
  // Opting in never speaks anything by itself.
  expect(calls.map((call) => call.method), ['voices']);
}

/// The explicit Send of the reviewed transcript.
Future<void> _send(WidgetTester tester, _Api api) async {
  await tester.tap(find.byTooltip('Send'));
  await _settle(tester);
  expect(api.prompts, [_transcript]);
}

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  final calls = <MethodCall>[];

  setUp(() {
    calls.clear();
    debugPlatformCapabilities = const PlatformCapabilities.android();
    binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    binding.defaultBinaryMessenger.setMockMethodCallHandler(
      _secure,
      (_) async => null,
    );
    binding.defaultBinaryMessenger.setMockMethodCallHandler(_speech, (
      call,
    ) async {
      calls.add(call);
      if (call.method == 'voices') {
        return [
          {'id': 'offline', 'label': 'Installed voice', 'locale': 'en-US'},
        ];
      }
      if (call.method == 'speak') {
        return {
          'operationID': (call.arguments as Map)['operationID'],
          'status': 'accepted',
        };
      }
      return null;
    });
  });

  tearDown(() {
    binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    binding.defaultBinaryMessenger.setMockMethodCallHandler(_speech, null);
    binding.defaultBinaryMessenger.setMockMethodCallHandler(_secure, null);
    debugPlatformCapabilities = null;
  });

  testWidgets('conversation is default off across Exit and re-entry', (
    tester,
  ) async {
    final voice = _Voice(models: await readyVoiceModelManager());
    addTearDown(voice.dispose);
    final api = _Api();
    final connection = await _pumpChat(tester, api, voice);
    await _enterAndListen(tester, api);
    expect(
      tester
          .widget<SwitchListTile>(find.byKey(const Key('voice-speak-replies')))
          .value,
      isFalse,
    );
    await _send(tester, api);
    await _completeTurn(tester, connection);
    expect(calls, isEmpty);
    expect(voice.listens, 1);
    await tester.tap(find.text('Exit voice mode'));
    await _settle(tester);
    api.prompts.clear();
    await _enterAndListen(tester, api);
    expect(
      tester
          .widget<SwitchListTile>(find.byKey(const Key('voice-speak-replies')))
          .value,
      isFalse,
    );
    expect(calls, isEmpty);
    expect(voice.listens, 2);
  });

  for (final interruption in [
    'Stop',
    'background',
    'scope',
    'disconnect',
    'approval',
  ]) {
    testWidgets('late reply stays silent after $interruption', (tester) async {
      final voice = _Voice(models: await readyVoiceModelManager());
      addTearDown(voice.dispose);
      final api = _Api();
      final connection = await _pumpChat(tester, api, voice);
      await _enterAndListen(tester, api);
      await _optIn(tester, calls);
      await _send(tester, api);
      _status(connection, 'busy');
      await _settle(tester);
      switch (interruption) {
        case 'Stop':
          // Stop pending playback also preserves independently typed work.
          await tester.enterText(
            find.byType(TextField).first,
            'Keep this draft',
          );
          await tester.tap(find.byKey(const Key('voice-reply-stop')));
        case 'background':
          binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
          binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
          binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
          await tester.pump();
          binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
          binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
          binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
        case 'scope':
          connection.locationRevision++;
          _status(connection, 'busy');
        case 'disconnect':
          connection.status = StreamStatus.disconnected;
          _status(connection, 'busy');
          await tester.pump();
          connection.status = StreamStatus.connected;
        case 'approval':
          connection.handleEventForTesting(
            _event('permission.asked', {
              'id': 'permission-1',
              'sessionID': 'session',
              'permission': 'edit',
              'patterns': ['lib/example.dart'],
              'metadata': <String, Object?>{},
              'always': <String>[],
            }),
          );
          await tester.pump();
          connection.handleEventForTesting(
            _event('permission.replied', {
              'requestID': 'permission-1',
              'sessionID': 'session',
              'reply': 'once',
            }),
          );
      }
      await _settle(tester);
      await _completeTurn(tester, connection);
      expect(calls.where((call) => call.method == 'speak'), isEmpty);
      expect(api.prompts, [_transcript]);
      expect(voice.listens, 1);
      expect(find.text(_newReply), findsOneWidget);
      if (interruption == 'Stop') {
        expect(
          tester
              .widget<TextField>(find.byType(TextField).first)
              .controller!
              .text,
          'Keep this draft',
        );
      }
    });
  }

  for (final ambiguity in [
    'intervening user',
    'duplicate echo',
    'missing parent',
    'mixed reply parents',
  ]) {
    testWidgets('$ambiguity keeps playback manual', (tester) async {
      final voice = _Voice(models: await readyVoiceModelManager());
      addTearDown(voice.dispose);
      final api = _Api();
      final connection = await _pumpChat(tester, api, voice);
      await _enterAndListen(tester, api);
      await _optIn(tester, calls);
      await _send(tester, api);
      _status(connection, 'busy');
      _user(connection, 'user-1', _transcript);
      if (ambiguity == 'intervening user' || ambiguity == 'duplicate echo') {
        _user(
          connection,
          'user-2',
          ambiguity == 'duplicate echo'
              ? _transcript
              : 'Another client message',
        );
      }
      _reply(
        connection,
        parent: ambiguity == 'missing parent' ? null : 'user-1',
      );
      if (ambiguity == 'mixed reply parents') {
        connection.handleEventForTesting(
          _event('message.updated', {
            'info': _info('other-reply', 'assistant', completed: true),
          }),
        );
      }
      await tester.pump();
      _status(connection, 'idle');
      await _settle(tester);
      expect(calls.where((call) => call.method == 'speak'), isEmpty);
      expect(find.byKey(const Key('voice-reply-read')), findsOneWidget);
      expect(find.text(_newReply), findsOneWidget);
      expect(voice.listens, 1);
    });
  }

  testWidgets(
    'native failure leaves text and explicit retry without resending',
    (tester) async {
      final voice = _Voice(models: await readyVoiceModelManager());
      addTearDown(voice.dispose);
      final api = _Api();
      final connection = await _pumpChat(tester, api, voice);
      await _enterAndListen(tester, api);
      await _optIn(tester, calls);
      await _send(tester, api);
      await _completeTurn(tester, connection);
      await _nativeStatus(tester, calls, 'engineUnavailable');
      expect(find.text('The reply could not be read aloud.'), findsOneWidget);
      expect(find.text(_newReply), findsOneWidget);
      expect(find.byKey(const Key('voice-reply-read')), findsOneWidget);
      expect(api.prompts, [_transcript]);
      expect(voice.listens, 1);
      _reply(connection);
      _status(connection, 'idle');
      await _settle(tester);
      expect(calls.where((call) => call.method == 'speak'), hasLength(1));
      await tester.tap(find.byKey(const Key('voice-reply-read')));
      await _settle(tester);
      expect(calls.where((call) => call.method == 'speak'), hasLength(2));
      expect(api.prompts, [_transcript]);
      expect(voice.listens, 1);
    },
  );

  testWidgets(
    'completed playback and duplicate reconnect events never replay',
    (tester) async {
      final voice = _Voice(models: await readyVoiceModelManager());
      addTearDown(voice.dispose);
      final api = _Api();
      final connection = await _pumpChat(tester, api, voice);
      await _enterAndListen(tester, api);
      await _optIn(tester, calls);
      await _send(tester, api);
      await _completeTurn(tester, connection);
      await _nativeStatus(tester, calls, 'completed');
      connection.status = StreamStatus.disconnected;
      _status(connection, 'idle');
      await tester.pump();
      connection.status = StreamStatus.connected;
      connection.dataRefreshRevision++;
      _status(connection, 'idle');
      await _settle(tester);
      _user(connection, 'user-1', _transcript);
      _reply(connection);
      _status(connection, 'idle');
      await _settle(tester);
      expect(calls.where((call) => call.method == 'speak'), hasLength(1));
      expect(api.prompts, [_transcript]);
      expect(voice.listens, 1);
    },
  );

  testWidgets('Stop during pending dispatch is not rearmed by acceptance', (
    tester,
  ) async {
    final voice = _Voice(models: await readyVoiceModelManager());
    addTearDown(voice.dispose);
    final api = _Api()..promptGate = Completer<void>();
    final connection = await _pumpChat(tester, api, voice);
    await _enterAndListen(tester, api);
    await _optIn(tester, calls);
    await _send(tester, api);
    _status(connection, 'busy');
    await _settle(tester);
    await tester.tap(find.byKey(const Key('voice-reply-stop')));
    await _settle(tester);
    api.promptGate!.complete();
    await _settle(tester);
    await _completeTurn(tester, connection);
    expect(calls.where((call) => call.method == 'speak'), isEmpty);
    expect(api.prompts, [_transcript]);
    expect(voice.listens, 1);
  });

  testWidgets('fast reply waits for dispatch acceptance before speech', (
    tester,
  ) async {
    final voice = _Voice(models: await readyVoiceModelManager());
    addTearDown(voice.dispose);
    final api = _Api()..promptGate = Completer<void>();
    final connection = await _pumpChat(tester, api, voice);
    await _enterAndListen(tester, api);
    await _optIn(tester, calls);
    await _send(tester, api);
    await _completeTurn(tester, connection);
    expect(calls.where((call) => call.method == 'speak'), isEmpty);
    api.promptGate!.complete();
    await _settle(tester);
    expect(calls.where((call) => call.method == 'speak'), hasLength(1));
    expect(voice.listens, 1);
  });

  final captureDirectory = Platform.environment['OC_VOICE_REPLY_CAPTURE_DIR'];
  for (final variant in ['dark', 'light', 'large']) {
    testWidgets('capture production voice states $variant', (tester) async {
      await loadCaptureFonts();
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final boundary = GlobalKey();
      final voice = _Voice(models: await readyVoiceModelManager());
      addTearDown(voice.dispose);
      final api = _Api();
      final connection = await _pumpChat(
        tester,
        api,
        voice,
        captureBoundary: boundary,
        light: variant == 'light',
        textScale: variant == 'large' ? 2 : 1,
      );
      Future<void> capture(String state) async {
        await _settle(tester);
        if (state != 'off') {
          await tester.ensureVisible(
            find.byKey(const Key('voice-reply-status')),
          );
          await _settle(tester);
        }
        expect(tester.takeException(), isNull);
        final png = await capturePng(tester, boundary, pixelRatio: 1);
        File('$captureDirectory/$variant-$state.png').writeAsBytesSync(png);
      }

      await _enterAndListen(tester, api);
      await capture('off');
      if (variant == 'large') {
        await tester.ensureVisible(find.text('Exit voice mode'));
        await _settle(tester);
        final png = await capturePng(tester, boundary, pixelRatio: 1);
        File('$captureDirectory/large-off-actions.png').writeAsBytesSync(png);
      }
      await _optIn(tester, calls);
      await _send(tester, api);
      await capture('waiting');
      await _completeTurn(tester, connection);
      await capture('speaking');
      await _nativeStatus(tester, calls, 'engineUnavailable');
      await capture('error');
      expect(voice.listens, 1);
    }, skip: captureDirectory == null || captureDirectory.trim().isEmpty);
  }

  testWidgets('the reply to the sent turn is spoken once after it completes; '
      'Stop only stops', (tester) async {
    final voice = _Voice(models: await readyVoiceModelManager());
    addTearDown(voice.dispose);
    final api = _Api();
    final connection = await _pumpChat(tester, api, voice);
    await _enterAndListen(tester, api);
    await _optIn(tester, calls);
    await _send(tester, api);
    expect(find.text('Waiting for the reply…'), findsOneWidget);

    // The turn runs: busy, the server echoes the user message, the reply
    // streams, then completes, then the session goes idle.
    connection.handleEventForTesting(
      _event('session.status', {'sessionID': 'session', 'status': 'busy'}),
    );
    await tester.pump();
    connection.handleEventForTesting(
      _event('message.part.updated', {
        'sessionID': 'session',
        'part': _textPart('p-user', 'user-1', _transcript),
      }),
    );
    connection.handleEventForTesting(
      _event('message.updated', {'info': _info('user-1', 'user')}),
    );
    connection.handleEventForTesting(
      _event('message.updated', {
        'info': _info('reply-1', 'assistant', parentID: 'user-1'),
      }),
    );
    connection.handleEventForTesting(
      _event('message.part.updated', {
        'sessionID': 'session',
        'part': _textPart('p-reply', 'reply-1', _newReply),
      }),
    );
    await tester.pump();
    // Streaming, not finished: nothing spoken yet.
    expect(calls.where((c) => c.method == 'speak'), isEmpty);
    expect(find.text('Waiting for the reply…'), findsOneWidget);

    connection.handleEventForTesting(
      _event('message.updated', {
        'info': _info(
          'reply-1',
          'assistant',
          parentID: 'user-1',
          completed: true,
        ),
      }),
    );
    await tester.pump();
    // Completed but the session is still busy: still nothing.
    expect(calls.where((c) => c.method == 'speak'), isEmpty);

    connection.handleEventForTesting(
      _event('session.status', {'sessionID': 'session', 'status': 'idle'}),
    );
    await _settle(tester);
    final speaks = calls.where((c) => c.method == 'speak').toList();
    expect(speaks, hasLength(1));
    expect((speaks.single.arguments as Map)['text'], _newReply);
    expect((speaks.single.arguments as Map)['voiceID'], 'offline');
    expect(find.text('Speaking the reply'), findsOneWidget);

    // Stop ends playback and nothing else: no resend, composer untouched.
    await tester.tap(find.byKey(const Key('voice-reply-stop')));
    await _settle(tester);
    expect(calls.last.method, 'stop');
    expect(api.prompts, [_transcript]);
    expect(
      tester.widget<TextField>(find.byType(TextField).first).controller!.text,
      isEmpty,
    );
    expect(find.byKey(const Key('voice-reply-stop')), findsNothing);

    // A later idle/refresh cycle never replays the reply.
    connection.handleEventForTesting(
      _event('session.status', {'sessionID': 'session', 'status': 'idle'}),
    );
    await _settle(tester);
    expect(calls.where((c) => c.method == 'speak'), hasLength(1));
    // The plain text stays on screen.
    expect(find.text(_newReply), findsOneWidget);
  });

  testWidgets(
    'a reply that cannot be matched to the sent turn is offered, not spoken',
    (tester) async {
      final voice = _Voice(models: await readyVoiceModelManager());
      addTearDown(voice.dispose);
      final api = _Api();
      final connection = await _pumpChat(tester, api, voice);
      await _enterAndListen(tester, api);
      await _optIn(tester, calls);
      await _send(tester, api);

      // The server never echoes a user message matching the send; a reply
      // appears and the session finishes anyway.
      connection.handleEventForTesting(
        _event('session.status', {'sessionID': 'session', 'status': 'busy'}),
      );
      connection.handleEventForTesting(
        _event('message.updated', {
          'info': _info('reply-x', 'assistant', completed: true),
        }),
      );
      connection.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session',
          'part': _textPart('p-x', 'reply-x', 'Some text of uncertain origin.'),
        }),
      );
      connection.handleEventForTesting(
        _event('session.status', {'sessionID': 'session', 'status': 'idle'}),
      );
      await _settle(tester);
      expect(calls.where((c) => c.method == 'speak'), isEmpty);
      expect(
        find.textContaining('could not be matched to your message'),
        findsOneWidget,
      );
      expect(find.byKey(const Key('voice-reply-read')), findsOneWidget);

      // Reading stays explicit and, once tapped, reads the latest reply.
      await tester.tap(find.byKey(const Key('voice-reply-read')));
      await _settle(tester);
      final speaks = calls.where((c) => c.method == 'speak').toList();
      expect(speaks, hasLength(1));
      expect(
        (speaks.single.arguments as Map)['text'],
        'Some text of uncertain origin.',
      );
    },
  );

  testWidgets(
    'Exit revokes the opt-in and a pending reply is never spoken after it',
    (tester) async {
      final voice = _Voice(models: await readyVoiceModelManager());
      addTearDown(voice.dispose);
      final api = _Api();
      final connection = await _pumpChat(tester, api, voice);
      await _enterAndListen(tester, api);
      await _optIn(tester, calls);
      await _send(tester, api);
      connection.handleEventForTesting(
        _event('session.status', {'sessionID': 'session', 'status': 'busy'}),
      );
      await tester.pump();

      await tester.tap(find.text('Exit voice mode'));
      await _settle(tester);
      expect(find.byKey(const Key('voice-speak-replies')), findsNothing);

      connection.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session',
          'part': _textPart('p-user', 'user-1', _transcript),
        }),
      );
      connection.handleEventForTesting(
        _event('message.updated', {'info': _info('user-1', 'user')}),
      );
      connection.handleEventForTesting(
        _event('message.updated', {
          'info': _info(
            'reply-1',
            'assistant',
            parentID: 'user-1',
            completed: true,
          ),
        }),
      );
      connection.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session',
          'part': _textPart('p-reply', 'reply-1', _newReply),
        }),
      );
      connection.handleEventForTesting(
        _event('session.status', {'sessionID': 'session', 'status': 'idle'}),
      );
      await _settle(tester);
      expect(calls.where((c) => c.method == 'speak'), isEmpty);
      // The reply is still on screen as text.
      expect(find.text(_newReply), findsOneWidget);
    },
  );
}
