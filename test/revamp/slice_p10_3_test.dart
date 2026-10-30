// P10.3 "Voice as a composer mode": the mic turns the composer into voice
// mode; recording has no cap and goes to the recognizer in 30 s chunks; the
// words land in the draft as they are written down and survive the app
// going away; a denied microphone is explained inside the mode with its fix.
//
// The long-dictation tests run the real VoiceComposerController, the real
// PCM16 chunking and the real chat host. Only the two native ends are fakes:
// the recorder streams a 185-second PCM16 fixture in odd-sized buffers, and
// the recognizer (sherpa-onnx is native) decodes the fixture back into
// words, so a lost or reordered sample shows up as a wrong word.
import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import '../support/fake_server_gateway.dart';
import '../support/fake_event_channel.dart' show StreamStatus;
import 'package:opencode_mobile/domain/server_gateway.dart' show PromptDelivery;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_drafts.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/voice/audio.dart';
import 'package:opencode_mobile/voice/controller.dart';
import 'package:opencode_mobile/voice/recognizer.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/complete_message_history.dart';
import '../support/composer_tools.dart';
import '../voice_controller_test.dart'
    show FakeRecognitionHandle, FakeVoiceRecorder, readyVoiceModelManager;

const _secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
const _voiceChannel = MethodChannel('oc/voice');

/// The fixture's level for second [s]: every sample of that second holds
/// it, so the recognizer can tell which second it heard.
int _level(int second) => (second + 1) * 100;

/// [seconds] of 16 kHz PCM16 audio, little-endian, from [from].
Uint8List _pcm(int from, int seconds) {
  final bytes = ByteData(seconds * voiceSampleRate * 2);
  var offset = 0;
  for (var s = from; s < from + seconds; s++) {
    for (var i = 0; i < voiceSampleRate; i++) {
      bytes.setInt16(offset, _level(s), Endian.little);
      offset += 2;
    }
  }
  return bytes.buffer.asUint8List();
}

String _words(int from, int to) =>
    [for (var s = from; s < to; s++) 'w$s'].join(' ');

/// Decodes the fixture: one word per second of audio, `w<second>`. A second
/// whose samples disagree (a chunk edge off by a byte) decodes to "BAD".
class _FixtureRecognizer implements VoiceRecognizer {
  final List<int> chunkSamples = [];
  int running = 0;
  int maxRunning = 0;
  int? failChunk;

  @override
  Future<VoiceRecognitionHandle> start(
    VoiceRecognitionRequest request, {
    required void Function() onLoaded,
  }) async {
    final index = chunkSamples.length;
    chunkSamples.add(request.samples.length);
    running++;
    maxRunning = math.max(maxRunning, running);
    onLoaded();
    final words = <String>[];
    final samples = request.samples;
    for (var i = 0; i < samples.length; i += voiceSampleRate) {
      final end = math.min(i + voiceSampleRate, samples.length);
      final first = (samples[i] * 32768).round();
      final same = [
        for (var j = i; j < end; j++) (samples[j] * 32768).round(),
      ].every((value) => value == first);
      words.add(same ? 'w${first ~/ 100 - 1}' : 'BAD');
    }
    final handle = FakeRecognitionHandle();
    scheduleMicrotask(() {
      running--;
      if (index == failChunk) {
        handle.completer.completeError(StateError('decoder stopped'));
      } else {
        handle.completer.complete(words.join(' '));
      }
    });
    return handle;
  }
}

class _DeniedRecorder extends FakeVoiceRecorder {
  Object? refuse;

  @override
  Future<Stream<Uint8List>> start() async {
    final error = refuse;
    if (error != null) {
      startCalls++;
      throw error;
    }
    return super.start();
  }
}

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');
  final prompts = <String>[];

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

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
}

Future<SharedPreferences> _prefs() async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Laptop',
        'flavor': 'v2',
        'baseUrl': 'http://localhost',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  return SharedPreferences.getInstance();
}

/// A connection on [prefs]: a second one on the same preferences is the
/// app after it was swiped away and opened again.
Future<ConnectionController> _connection(
  SharedPreferences prefs,
  _Api api,
) async {
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final connection = ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected;
  connection.sessionsById['session-1'] = Session(id: 'session-1');
  return connection;
}

Future<void> _pumpChat(
  WidgetTester tester,
  ConnectionController connection,
  VoiceComposerController? voice,
) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(connection)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ChatScreen(sessionID: 'session-1', voiceController: voice),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// Streams [bytes] the way the recorder does, in 4095-byte buffers so
/// samples and chunk edges split across buffers.
Future<void> _speak(
  WidgetTester tester,
  FakeVoiceRecorder recorder,
  Uint8List bytes,
) async {
  const buffer = 4095;
  var sent = 0;
  for (var offset = 0; offset < bytes.length; offset += buffer) {
    recorder.audioController!.add(
      Uint8List.sublistView(
        bytes,
        offset,
        math.min(offset + buffer, bytes.length),
      ),
    );
    if (++sent % 64 == 0) await tester.pump();
  }
  await tester.pump();
  await tester.pump();
}

String _field(WidgetTester tester) => tester
    .widget<TextField>(
      find.descendant(
        of: find.byKey(const Key('chat-composer-field')),
        matching: find.byType(TextField),
      ),
    )
    .controller!
    .text;

final _mic = find.byKey(const Key('composer-voice-button'));
final _voiceMode = find.byKey(const Key('voice-mode'));
final _done = find.byKey(const ValueKey('kit-voice-stop-listening'));

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  final settingsCalls = <String>[];

  setUp(() {
    settingsCalls.clear();
    debugPlatformCapabilities = const PlatformCapabilities.android();
    binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    binding.defaultBinaryMessenger.setMockMethodCallHandler(
      _secure,
      (_) async => null,
    );
    binding.defaultBinaryMessenger.setMockMethodCallHandler(_voiceChannel, (
      call,
    ) async {
      settingsCalls.add(call.method);
      return null;
    });
  });

  tearDown(() {
    binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    binding.defaultBinaryMessenger.setMockMethodCallHandler(_secure, null);
    binding.defaultBinaryMessenger.setMockMethodCallHandler(
      _voiceChannel,
      null,
    );
    debugPlatformCapabilities = null;
  });

  group('the controller', () {
    test('a 3-minute dictation is written down in 30 s chunks, in order, '
        'with no cap', () async {
      final recorder = FakeVoiceRecorder();
      final recognizer = _FixtureRecognizer();
      final voice = VoiceComposerController(
        models: await readyVoiceModelManager(),
        recorder: recorder,
        recognizer: recognizer,
      );
      addTearDown(voice.dispose);

      await voice.startListening();
      final audio = _pcm(0, 185);
      for (var offset = 0; offset < audio.length; offset += 4095) {
        recorder.audioController!.add(
          Uint8List.sublistView(
            audio,
            offset,
            math.min(offset + 4095, audio.length),
          ),
        );
      }
      await pumpEventQueue();

      // Still listening past the old 30 s cap; six chunks already written.
      expect(voice.state, VoiceComposerState.listening);
      expect(voice.elapsed, const Duration(seconds: 185));
      expect(voice.transcript, _words(0, 180));
      expect(recognizer.chunkSamples, List.filled(6, voiceChunkSamples));

      await voice.stopListening();
      expect(voice.state, VoiceComposerState.draft);
      expect(voice.draft, _words(0, 185));
      expect(voice.draft, isNot(contains('BAD')));
      expect(recognizer.chunkSamples.last, 5 * voiceSampleRate);
      // One chunk at a time: the recognizer never runs two decodes.
      expect(recognizer.maxRunning, 1);
      expect(recorder.stopCalls, 1);
    });

    test('a chunk that cannot be written down stops the recording and keeps '
        'the words before it', () async {
      final recorder = FakeVoiceRecorder();
      final recognizer = _FixtureRecognizer()..failChunk = 1;
      final voice = VoiceComposerController(
        models: await readyVoiceModelManager(),
        recorder: recorder,
        recognizer: recognizer,
      );
      addTearDown(voice.dispose);

      await voice.startListening();
      recorder.audioController!.add(_pcm(0, 65));
      await pumpEventQueue();

      expect(voice.state, VoiceComposerState.error);
      expect(voice.transcript, _words(0, 30));
      expect(recorder.cancelCalls, greaterThanOrEqualTo(2));
    });
  });

  group('the chat', () {
    testWidgets('the mic turns the composer into voice mode; a 3-minute '
        'dictation lands in the draft and survives swipe-away and reopen', (
      tester,
    ) async {
      final prefs = await _prefs();
      final api = _Api();
      final connection = await _connection(prefs, api);
      addTearDown(connection.dispose);
      final recorder = FakeVoiceRecorder();
      final voice = VoiceComposerController(
        models: await readyVoiceModelManager(),
        recorder: recorder,
        recognizer: _FixtureRecognizer(),
      );
      addTearDown(voice.dispose);
      await _pumpChat(tester, connection, voice);

      await tester.tap(_mic);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      // The composer itself: no sheet, the field gives way to voice mode.
      expect(_voiceMode, findsOneWidget);
      expect(find.byType(BottomSheet), findsNothing);
      expect(find.text('Listening…'), findsOneWidget);
      expect(_done, findsOneWidget);

      await _speak(tester, recorder, _pcm(0, 95));
      await tester.pump(const Duration(seconds: 1));
      // Three chunks written down so far; the draft store already has them.
      expect(connection.sessionDraft('session-1'), _words(0, 90));

      await _speak(tester, recorder, _pcm(95, 90));
      await tester.tap(_done);
      await tester.pump();
      await tester.pumpAndSettle();

      expect(_voiceMode, findsNothing);
      expect(_field(tester), _words(0, 185));
      expect(api.prompts, isEmpty, reason: 'dictation never sends');
      expect(connection.sessionDraft('session-1'), _words(0, 185));

      // Swiped away: the process dies. A new app on the same storage opens
      // the conversation with the dictated draft.
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
      final reopened = await _connection(prefs, _Api());
      addTearDown(reopened.dispose);
      expect(
        SessionDraftStore(
          prefs: prefs,
        ).load().values.map((draft) => draft.text),
        contains(_words(0, 185)),
      );
      await _pumpChat(tester, reopened, null);
      expect(_field(tester), _words(0, 185));
    });

    testWidgets('leaving the app mid-dictation stops the microphone at once '
        'and still writes what was said into the draft', (tester) async {
      final prefs = await _prefs();
      final api = _Api();
      final connection = await _connection(prefs, api);
      addTearDown(connection.dispose);
      final recorder = FakeVoiceRecorder();
      final voice = VoiceComposerController(
        models: await readyVoiceModelManager(),
        recorder: recorder,
        recognizer: _FixtureRecognizer(),
      );
      addTearDown(voice.dispose);
      await _pumpChat(tester, connection, voice);
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'Typed first.',
      );
      await tester.pump();
      // With text, voice is in the "+" sheet; it adds at the caret.
      await openComposerTools(tester);
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.byKey(const Key('composer-tool-voice')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('composer-tool-voice')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      expect(_voiceMode, findsOneWidget);

      await _speak(tester, recorder, _pcm(0, 64));
      // A notification shade (inactive) does not stop dictation.
      binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      await tester.pump();
      expect(voice.state, VoiceComposerState.listening);
      // Swiping to another app does, at once.
      binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pump();
      expect(recorder.stopCalls, 1);
      await tester.pump(const Duration(seconds: 1));
      expect(
        connection.sessionDraft('session-1'),
        'Typed first. ${_words(0, 64)}',
      );
      binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(_voiceMode, findsNothing);
      expect(_field(tester), 'Typed first. ${_words(0, 64)}');
    });

    testWidgets('Leave keeps the words already written down', (tester) async {
      final prefs = await _prefs();
      final connection = await _connection(prefs, _Api());
      addTearDown(connection.dispose);
      final recorder = FakeVoiceRecorder();
      final voice = VoiceComposerController(
        models: await readyVoiceModelManager(),
        recorder: recorder,
        recognizer: _FixtureRecognizer(),
      );
      addTearDown(voice.dispose);
      await _pumpChat(tester, connection, voice);
      await tester.tap(_mic);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      await _speak(tester, recorder, _pcm(0, 40));

      await tester.tap(find.byTooltip('Leave voice mode'));
      await tester.pumpAndSettle();
      expect(_voiceMode, findsNothing);
      expect(_field(tester), _words(0, 30));
      expect(voice.state, VoiceComposerState.idle);
    });

    testWidgets('a denied microphone is explained inside the mode, and Allow '
        'microphone asks again', (tester) async {
      final prefs = await _prefs();
      final connection = await _connection(prefs, _Api());
      addTearDown(connection.dispose);
      final recorder = _DeniedRecorder()
        ..refuse = const VoicePermissionDenied();
      final voice = VoiceComposerController(
        models: await readyVoiceModelManager(),
        recorder: recorder,
        recognizer: _FixtureRecognizer(),
      );
      addTearDown(voice.dispose);
      await _pumpChat(tester, connection, voice);
      await tester.tap(_mic);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));

      expect(_voiceMode, findsOneWidget);
      expect(find.byType(BottomSheet), findsNothing);
      expect(find.text('The microphone is off for this app'), findsOneWidget);
      expect(
        find.text(
          'Voice typing needs the microphone. Tap Allow microphone, then '
          'choose Allow.',
        ),
        findsOneWidget,
      );
      // Plain words only: the exception's own text never shows.
      expect(find.textContaining('permission is required'), findsNothing);

      recorder.refuse = null;
      await tester.tap(find.text('Allow microphone'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      expect(recorder.startCalls, 2);
      expect(voice.state, VoiceComposerState.listening);
      expect(find.text('Listening…'), findsOneWidget);
    });

    testWidgets('a microphone blocked for good sends the person to Android '
        'settings and listens when they come back', (tester) async {
      final prefs = await _prefs();
      final connection = await _connection(prefs, _Api());
      addTearDown(connection.dispose);
      final recorder = _DeniedRecorder()
        ..refuse = const VoicePermissionDenied(permanent: true);
      final voice = VoiceComposerController(
        models: await readyVoiceModelManager(),
        recorder: recorder,
        recognizer: _FixtureRecognizer(),
      );
      addTearDown(voice.dispose);
      await _pumpChat(tester, connection, voice);
      await tester.tap(_mic);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));

      expect(
        find.textContaining('Android blocks the microphone for this app'),
        findsOneWidget,
      );
      await tester.tap(find.text('Allow microphone in Android settings'));
      await tester.pump();
      expect(settingsCalls, ['openAppSettings']);

      // In Android settings the person allows the microphone, then returns.
      recorder.refuse = null;
      binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pump();
      expect(_voiceMode, findsOneWidget, reason: 'the mode waits');
      binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      expect(voice.state, VoiceComposerState.listening);
    });

    testWidgets('voice conversation is the same mode: Send sends what was '
        'said, and a request on screen pauses it with one primary', (
      tester,
    ) async {
      final prefs = await _prefs();
      final api = _Api();
      final connection = await _connection(prefs, api);
      addTearDown(connection.dispose);
      final recorder = FakeVoiceRecorder();
      final voice = VoiceComposerController(
        models: await readyVoiceModelManager(),
        recorder: recorder,
        recognizer: _FixtureRecognizer(),
      );
      addTearDown(voice.dispose);
      await _pumpChat(tester, connection, voice);
      await openComposerTools(tester);
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const Key('composer-tools-advanced')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('composer-tools-advanced')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const Key('composer-tool-conversation')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('composer-tool-conversation')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));

      expect(_voiceMode, findsOneWidget);
      expect(find.byTooltip('Send'), findsOneWidget);
      await _speak(tester, recorder, _pcm(0, 3));
      await tester.tap(_done);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      expect(api.prompts, [_words(0, 3)]);

      connection.handleEventForTesting(
        EventEnvelope(
          type: 'permission.asked',
          properties: {
            'id': 'permission-1',
            'sessionID': 'session-1',
            'permission': 'edit',
            'patterns': ['lib/example.dart'],
            'metadata': <String, Object?>{},
            'always': <String>[],
          },
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      expect(find.text('Paused · the agent needs you'), findsOneWidget);
      // Listen waits: the request's answer is the one primary on screen.
      expect(find.byKey(const ValueKey('kit-voice-listen')), findsNothing);
      final primaries = tester
          .widgetList<KitButton>(find.byType(KitButton))
          .where((button) => button.role == KitButtonRole.primary);
      expect(primaries.length, lessThanOrEqualTo(1));
      expect(tester.takeException(), isNull);
    });
  });
}
