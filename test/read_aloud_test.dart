import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/voice/controller.dart';
import 'package:opencode_mobile/voice/read_aloud.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';
import 'voice_controller_test.dart'
    show FakeVoiceRecorder, FakeVoiceRecognizer, readyVoiceModelManager;

const _channel = MethodChannel('oc/read-aloud');
const _secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
const _reply = 'A synthetic reply for reading.';
const _transcript = 'A synthetic private utterance';

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');
  final prompts = <String>[];

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<MessageWithParts>> messages(String id) async => [
    MessageWithParts(
      info: MessageInfo(id: 'reply', sessionID: id, role: 'assistant'),
      parts: [Part(type: 'text', text: _reply)],
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
  }) async => prompts.add(text);
}

class _Voice extends VoiceComposerController {
  _Voice({required super.models})
    : super(recorder: FakeVoiceRecorder(), recognizer: FakeVoiceRecognizer());

  @override
  Future<void> startListening() async {
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

Future<ConnectionController> _pumpChat(
  WidgetTester tester,
  _Api api, {
  VoiceComposerController? voice,
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
        home: ChatScreen(sessionID: 'session', voiceController: voice),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return connection;
}

Future<void> _openReadReply(WidgetTester tester) async {
  // Assistant prose is selectable; use its explicit message action target.
  await tester.tap(find.byTooltip('Message actions'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Read reply prose'));
  await tester.pumpAndSettle();
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
    binding.defaultBinaryMessenger.setMockMethodCallHandler(_channel, (
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
    binding.defaultBinaryMessenger.setMockMethodCallHandler(_channel, null);
    binding.defaultBinaryMessenger.setMockMethodCallHandler(_secure, null);
    debugPlatformCapabilities = null;
  });

  test(
    'construction and disposal without playback never access the engine',
    () {
      final speech = ReadAloudController();
      expect(speech.speaking, isFalse);
      speech.dispose();
      expect(calls, isEmpty);
    },
  );

  test(
    'unsupported platforms cannot query voices or send reply text',
    () async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      final speech = ReadAloudController();
      addTearDown(speech.dispose);
      final unsupported = throwsA(
        isA<ReadAloudException>().having(
          (error) => error.failure,
          'failure',
          ReadAloudFailure.unsupported,
        ),
      );
      await expectLater(speech.voices(), unsupported);
      await expectLater(speech.speak('reply', _reply), unsupported);
      expect(calls, isEmpty);
    },
  );

  test(
    'backgrounding cancels the active operation and prevents new speech',
    () async {
      final speech = ReadAloudController();
      addTearDown(speech.dispose);
      await speech.speak('private-session/reply', _reply);
      final payload = calls.single.arguments as Map;
      expect(payload.values, isNot(contains('private-session/reply')));
      expect(speech.speaking, isTrue);
      binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await Future<void>.delayed(Duration.zero);
      expect(speech.speaking, isFalse);
      expect(speech.activeID, isNull);
      expect(calls.last.method, 'stop');
      expect(calls.last.arguments, {'operationID': payload['operationID']});
      await expectLater(
        speech.speak('another-reply', _reply),
        throwsA(
          isA<ReadAloudException>().having(
            (error) => error.failure,
            'failure',
            ReadAloudFailure.busy,
          ),
        ),
      );
      expect(calls.where((call) => call.method == 'speak'), hasLength(1));
    },
  );

  test(
    'disposing pending playback stops it and late acceptance stays cancelled',
    () async {
      final accepted = Completer<Object?>();
      binding.defaultBinaryMessenger.setMockMethodCallHandler(_channel, (
        call,
      ) async {
        calls.add(call);
        return call.method == 'speak' ? accepted.future : null;
      });
      final speech = ReadAloudController();
      final pending = speech.speak('reply', _reply);
      await Future<void>.delayed(Duration.zero);
      final operation = (calls.single.arguments as Map)['operationID'];
      speech.dispose();
      accepted.complete({'operationID': operation, 'status': 'accepted'});
      await pending;
      await Future<void>.delayed(Duration.zero);
      expect(speech.speaking, isFalse);
      expect(speech.activeID, isNull);
      expect(calls.last.method, 'stop');
      expect(calls.last.arguments, {'operationID': operation});
    },
  );

  test(
    'a native terminal failure during pending acceptance reaches the caller',
    () async {
      final accepted = Completer<Object?>();
      binding.defaultBinaryMessenger.setMockMethodCallHandler(_channel, (
        call,
      ) async {
        calls.add(call);
        return call.method == 'speak' ? accepted.future : null;
      });
      final speech = ReadAloudController();
      addTearDown(speech.dispose);
      final pending = speech.speak('reply', _reply);
      await Future<void>.delayed(Duration.zero);
      final operation = (calls.single.arguments as Map)['operationID'];

      await binding.defaultBinaryMessenger.handlePlatformMessage(
        _channel.name,
        _channel.codec.encodeMethodCall(
          MethodCall('status', {
            'operationID': operation,
            'status': 'engineUnavailable',
          }),
        ),
        (_) {},
      );
      accepted.complete({'operationID': operation, 'status': 'accepted'});

      await expectLater(
        pending,
        throwsA(
          isA<ReadAloudException>().having(
            (error) => error.failure,
            'failure',
            ReadAloudFailure.engineUnavailable,
          ),
        ),
      );
      expect(speech.speaking, isFalse);
    },
  );

  testWidgets(
    'reply reading asks consent before engine access and stops on leaving chat',
    (tester) async {
      await _pumpChat(tester, _Api());
      await _openReadReply(tester);
      expect(find.text('Use the system speech engine?'), findsOneWidget);
      expect(calls, isEmpty);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(calls, isEmpty);

      await _openReadReply(tester);
      await tester.tap(find.text('Choose voice'));
      await tester.pumpAndSettle();
      expect(calls.map((call) => call.method), ['voices']);
      await tester.tap(find.text('Installed voice'));
      await tester.pumpAndSettle();
      expect(calls.map((call) => call.method), ['voices', 'speak']);
      expect((calls.last.arguments as Map)['text'], _reply);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();
      expect(calls.last.method, 'stop');
    },
  );

  testWidgets(
    'unsent voice conversation is never persisted or sent and is cleared on background',
    (tester) async {
      final voice = _Voice(models: await readyVoiceModelManager());
      addTearDown(voice.dispose);
      final api = _Api();
      await _pumpChat(tester, api, voice: voice);
      await tester.tap(find.byKey(const Key('composer-tools-button')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('composer-tool-conversation')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pump();
      // Conversation mode deliberately requires an explicit first listen.
      expect(voice.state, VoiceComposerState.idle);
      await tester.tap(find.text('Start listening'));
      await tester.pump();
      await tester.tap(find.byKey(const Key('stop-voice-recording')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      await tester.tap(find.byKey(const Key('insert-voice-draft')));
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(find.byType(TextField).first).controller!.text,
        _transcript,
      );
      await tester.pump(const Duration(seconds: 2));
      final prefs = await SharedPreferences.getInstance();
      void expectPrivate() {
        expect(api.prompts, isEmpty);
        expect(calls, isEmpty);
        for (final key in prefs.getKeys()) {
          expect(
            prefs.get(key).toString(),
            isNot(contains(_transcript)),
            reason: key,
          );
        }
      }

      expectPrivate();
      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      await tester.pump();
      for (final state in [
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(find.byType(TextField).first).controller!.text,
        isEmpty,
      );
      expectPrivate();
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();
      expectPrivate();
    },
  );
}
