import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/voice/controller.dart';
import 'package:opencode_mobile/voice/model_manager.dart';
import 'package:opencode_mobile/voice/notices.dart';
import 'package:opencode_mobile/voice/voice_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'voice_controller_test.dart';

class _VoiceChatApi extends OpenCodeApi {
  _VoiceChatApi() : super(baseUrl: 'http://localhost');

  int promptCalls = 0;

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
  }) async {
    promptCalls++;
  }
}

Future<ConnectionController> _connection(_VoiceChatApi api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))..api = api;
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

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

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

      await tester.tap(find.byKey(const Key('voice-input-button')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      expect(find.text('Audio stays on this device'), findsOneWidget);
      expect(find.byKey(const Key('stop-voice-recording')), findsOneWidget);
      await tester.tap(find.byKey(const Key('stop-voice-recording')));
      await tester.pump();
      expect(voice.controller.state, VoiceComposerState.draft);
      await tester.pump(const Duration(milliseconds: 200));
      await tester.tap(find.byKey(const Key('insert-voice-draft')));
      await tester.pump(const Duration(milliseconds: 300));

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
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
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
    expect(find.textContaining('Audio stays on this device'), findsOneWidget);
    expect(find.text('High accuracy'), findsOneWidget);
    final modelTarget = find.byKey(const Key('voice-model-base'));
    final modelNode = tester.getSemantics(modelTarget);
    expect(tester.getSize(modelTarget).height, greaterThanOrEqualTo(48));
    expect(modelNode.flagsCollection.isButton, isTrue);
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
    expect(
      tester
          .getSize(find.byKey(const Key('voice-model-primary-action')))
          .height,
      greaterThanOrEqualTo(48),
    );
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

      final modelNode = tester.getSemantics(
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

  testWidgets('voice draft actions stack at 320dp with 2x text', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final voice = await _voice();
    addTearDown(voice.controller.dispose);
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
            body: FilledButton(
              onPressed: () =>
                  showVoiceComposerSheet(context, voice.controller),
              child: const Text('Open voice'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open voice'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    await tester.tap(find.byKey(const Key('stop-voice-recording')));
    await tester.pump();

    final cancel = find.byKey(const Key('voice-composer-cancel'));
    final insert = find.byKey(const Key('insert-voice-draft'));
    expect(tester.getSize(cancel).height, greaterThanOrEqualTo(48));
    expect(tester.getSize(insert).height, greaterThanOrEqualTo(48));
    expect(tester.getTopLeft(cancel).dx, tester.getTopLeft(insert).dx);
    expect(
      tester.getTopLeft(insert).dy,
      greaterThan(tester.getTopLeft(cancel).dy),
    );
    expect(tester.takeException(), isNull);
  });

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
    await tester.tap(find.byKey(const Key('voice-input-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    final pausing = voice.controller.handleLifecyclePause();
    await tester.pump(const Duration(seconds: 1));
    await pausing;
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pump();

    expect(voice.recorder.cancelCalls, greaterThan(0));
    expect(voice.controller.state, VoiceComposerState.idle);
  });

  testWidgets('exported voice notices view renders bundled license texts', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: VoiceNoticesView())),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('ONNX Runtime'), findsOneWidget);
    expect(
      find.textContaining('Copyright (c) Microsoft Corporation'),
      findsOneWidget,
    );
    expect(find.textContaining('Copyright (c) 2022 OpenAI'), findsOneWidget);
  });
}
