import 'support/complete_message_history.dart';
import 'dart:convert';
import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show PromptDelivery;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/voice/controller.dart';
import 'package:opencode_mobile/voice/model_manager.dart';
import 'package:opencode_mobile/voice/notices.dart';
import 'package:opencode_mobile/voice/voice_ui.dart'
    show showVoiceModelSetupSheet;
import 'package:shared_preferences/shared_preferences.dart';

import 'support/composer_tools.dart';
import 'support/part_semantics.dart';
import 'voice_controller_test.dart';

class _VoiceChatApi extends FakeServerGateway with CompleteMessageHistory {
  _VoiceChatApi() : super(baseUrl: 'http://localhost');

  int promptCalls = 0;

  @override
  Future<Session> session(String id) async => Session(id: id);

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
    promptCalls++;
  }
}

Future<ConnectionController> _connection(_VoiceChatApi api) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Synthetic',
        'flavor': 'v2',
        'baseUrl': 'http://localhost',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final connection = ConnectionController(store)..api = api;
  connection.sessionsById['session-1'] = Session(id: 'session-1');
  return connection;
}

class _WidgetVoiceController extends VoiceComposerController {
  _WidgetVoiceController({
    required super.models,
    required super.recorder,
    required super.recognizer,
  });

  bool _testDisposed = false;

  @override
  Future<void> startListening() async {
    state = VoiceComposerState.listening;
    notifyListeners();
  }

  @override
  Future<void> stopListening() async {
    draft = 'local transcript';
    state = VoiceComposerState.draft;
    notifyListeners();
  }

  @override
  Future<void> cancel({String? reason, bool clearError = false}) async {
    await recorder.cancel();
    if (_testDisposed) return;
    draft = '';
    state = reason == null ? VoiceComposerState.idle : VoiceComposerState.error;
    notifyListeners();
  }

  @override
  void dispose() {
    _testDisposed = true;
    super.dispose();
  }
}

Future<
  ({
    VoiceComposerController controller,
    FakeVoiceRecorder recorder,
    FakeVoiceRecognizer recognizer,
  })
>
_voice() async {
  final recorder = FakeVoiceRecorder();
  final recognizer = FakeVoiceRecognizer()
    ..automaticResult = 'local transcript';
  return (
    controller: _WidgetVoiceController(
      models: await readyVoiceModelManager(),
      recorder: recorder,
      recognizer: recognizer,
    ),
    recorder: recorder,
    recognizer: recognizer,
  );
}

/// UX-P0-03: with text in the composer the mic gives way to Send, so voice
/// input is chosen from the tools sheet. The tool runs once the sheet has
/// finished dismissing, hence the explicit pumps rather than a settle: voice
/// mode's level meter moves while it listens.
Future<void> _openVoiceTool(WidgetTester tester) async {
  await openComposerTools(tester);
  await tester.pumpAndSettle();
  // The sheet leads with the conversation's own settings; scroll to the row.
  await tester.ensureVisible(find.byKey(const Key('composer-tool-voice')));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('composer-tool-voice')));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
  await tester.pump();
}

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  const secureStorage = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );

  setUp(() {
    binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secureStorage,
      (_) async => null,
    );
  });

  tearDown(() {
    binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secureStorage,
      null,
    );
  });

  testWidgets(
    'voice draft preserves typed composer text and never auto-sends',
    (tester) async {
      final api = _VoiceChatApi();
      final connection = await _connection(api);
      final voice = await _voice();
      addTearDown(connection.dispose);
      addTearDown(voice.controller.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(connection)],
          child: MaterialApp(
            home: ChatScreen(
              sessionID: 'session-1',
              voiceController: voice.controller,
              initialAttachments: const [
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
      await tester.enterText(find.byType(TextField).first, 'typed first');
      expect(find.text('notes.txt'), findsOneWidget);

      await _openVoiceTool(tester);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      // Voice is the composer's own mode: no sheet, Done in the send slot.
      expect(find.byKey(const Key('voice-mode')), findsOneWidget);
      final done = find.byKey(const ValueKey('kit-voice-stop-listening'));
      expect(done, findsOneWidget);
      await tester.tap(done);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byKey(const Key('voice-mode')), findsNothing);

      expect(api.promptCalls, 0);
      expect(find.text('notes.txt'), findsOneWidget);
      expect(
        tester.widget<TextField>(find.byType(TextField).first).controller?.text,
        'typed first local transcript',
      );
    },
  );

  testWidgets('model setup renders at 320dp with 2x text', (tester) async {
    final semantics = tester.ensureSemantics();
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final manager = await readyVoiceModelManager();
    addTearDown(manager.dispose);
    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2)),
          child: child!,
        ),
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: FilledButton(
                onPressed: () => showVoiceModelSetupSheet(context, manager),
                child: const Text('Open setup'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open setup'));
    await tester.pumpAndSettle();

    expect(find.text('Local voice input'), findsOneWidget);
    expect(find.text('Audio never leaves this phone.'), findsOneWidget);
    expect(find.text('High accuracy'), findsOneWidget);
    final modelTarget = find.byKey(const Key('voice-model-base'));
    await tester.ensureVisible(modelTarget);
    await tester.pumpAndSettle();
    final modelNode = tester.getSemantics(
      find.descendant(of: modelTarget, matching: find.text('Balanced')),
    );
    expect(tester.getSize(modelTarget).height, greaterThanOrEqualTo(48));
    expect(modelNode.flagsCollection.isInMutuallyExclusiveGroup, isTrue);
    expect(modelNode.flagsCollection.isSelected, Tristate.isTrue);
    expect(modelNode.flagsCollection.isEnabled, Tristate.isTrue);

    final redownloadTarget = find.byKey(const Key('voice-redownload-base'));
    final redownloadNode = tester.getSemantics(redownloadTarget);
    expect(tester.getSize(redownloadTarget).height, greaterThanOrEqualTo(48));
    for (
      var ancestor = redownloadNode.parent;
      ancestor != null;
      ancestor = ancestor.parent
    ) {
      expect(
        ancestor.flagsCollection.isButton,
        isFalse,
        reason:
            'Model maintenance actions must not be nested in a button: $ancestor',
      );
    }
    expect(
      tester
          .getSize(find.byKey(const Key('voice-model-secondary-action')))
          .height,
      greaterThanOrEqualTo(48),
    );
    // The model is on the phone and in use: no primary, the secondary is
    // Done.
    expect(find.byKey(const Key('voice-model-primary-action')), findsNothing);
    expect(find.text('Done'), findsOneWidget);
    expect(tester.takeException(), isNull);
    semantics.dispose();
  });

  testWidgets(
    'busy model setup exposes disabled cards without nested actions',
    (tester) async {
      final semantics = tester.ensureSemantics();
      final manager = await readyVoiceModelManager()
        ..state = VoiceModelState.downloading;
      addTearDown(manager.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: FilledButton(
                onPressed: () => showVoiceModelSetupSheet(context, manager),
                child: const Text('Open setup'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open setup'));
      await tester.pump();

      final modelNode = partSemantics(
        tester,
        find.byKey(const Key('voice-model-base')),
      );
      expect(modelNode.flagsCollection.isEnabled, isNot(Tristate.none));
      expect(modelNode.flagsCollection.isEnabled, Tristate.isFalse);
      expect(find.byKey(const Key('voice-redownload-base')), findsNothing);
      expect(find.byKey(const Key('voice-delete-base')), findsNothing);
      expect(
        tester
            .getSize(find.byKey(const Key('voice-model-secondary-action')))
            .height,
        greaterThanOrEqualTo(48),
      );
      semantics.dispose();
    },
  );

  testWidgets(
    'voice mode keeps Done and Leave reachable at 320dp with 2x text',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final api = _VoiceChatApi();
      final connection = await _connection(api);
      final voice = await _voice();
      addTearDown(connection.dispose);
      addTearDown(voice.controller.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(connection)],
          child: MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(2)),
              child: child!,
            ),
            home: ChatScreen(
              sessionID: 'session-1',
              voiceController: voice.controller,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('composer-voice-button')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));

      final done = find.byKey(const ValueKey('kit-voice-stop-listening'));
      final leave = find.byTooltip('Leave voice mode');
      for (final action in [done, leave]) {
        expect(tester.getSize(action).height, greaterThanOrEqualTo(48));
        expect(tester.getRect(action).left, greaterThanOrEqualTo(0));
        expect(tester.getRect(action).right, lessThanOrEqualTo(320));
      }
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('app background cancels an active microphone session', (
    tester,
  ) async {
    final api = _VoiceChatApi();
    final connection = await _connection(api);
    final voice = await _voice();
    addTearDown(connection.dispose);
    addTearDown(voice.controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(connection)],
        child: MaterialApp(
          home: ChatScreen(
            sessionID: 'session-1',
            voiceController: voice.controller,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await _openVoiceTool(tester);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(voice.controller.state, VoiceComposerState.listening);
    expect(find.byKey(const Key('voice-mode')), findsOneWidget);
    final cancellationsBeforePause = voice.recorder.cancelCalls;
    final pausing = voice.controller.handleLifecyclePause();
    await tester.pump(const Duration(seconds: 1));
    await pausing;
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pump();

    expect(voice.recorder.cancelCalls, greaterThan(cancellationsBeforePause));
    expect(voice.controller.state, VoiceComposerState.idle);
    // A cancelled recording leaves voice mode; the composer is for typing.
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('voice-mode')), findsNothing);
  });

  testWidgets('exported voice notices view renders bundled license texts', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: VoiceNoticesView()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('ONNX Runtime'), findsOneWidget);
    await tester.tap(find.text('ONNX Runtime'));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Copyright (c) Microsoft Corporation'),
      findsOneWidget,
    );
    Navigator.of(
      tester.element(
        find.textContaining('Copyright (c) Microsoft Corporation'),
      ),
    ).pop();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Whisper speech models'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Copyright (c) 2022 OpenAI'), findsOneWidget);
  });
}
