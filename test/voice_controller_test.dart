import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/voice/audio.dart';
import 'package:opencode_mobile/voice/controller.dart';
import 'package:opencode_mobile/voice/device.dart';
import 'package:opencode_mobile/voice/model_download.dart';
import 'package:opencode_mobile/voice/model_manager.dart';
import 'package:opencode_mobile/voice/model_manifest.dart';
import 'package:opencode_mobile/voice/recognizer.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _UnusedStore implements VoiceFileStore {
  @override
  Future<void> createDirectory(String path) async {}
  @override
  Future<void> delete(String path) async {}
  @override
  Future<bool> exists(String path) async => false;
  @override
  Future<int> length(String path) async => 0;
  @override
  Future<void> move(String from, String to) async {}
  @override
  Future<VoiceByteSink> openWrite(String path, {required bool append}) =>
      throw UnimplementedError();
  @override
  Stream<List<int>> read(String path) => const Stream.empty();
  @override
  Future<Uint8List> readBytes(String path) async => Uint8List(0);
  @override
  Future<void> writeAtomic(String path, List<int> bytes) async {}
}

class _UnusedHttp implements VoiceHttpTransport {
  @override
  void close() {}
  @override
  Future<VoiceHttpResponse> get(
    Uri uri, {
    Map<String, String> headers = const {},
    VoiceCancellationToken? cancellation,
  }) => throw UnimplementedError();
}

class ReadyVoiceModelManager extends VoiceModelManager {
  ReadyVoiceModelManager(SharedPreferences preferences)
    : super(
        root: '/private/models',
        preferences: preferences,
        downloader: VoiceModelDownloader(
          store: _UnusedStore(),
          http: _UnusedHttp(),
        ),
      ) {
    state = VoiceModelState.ready;
  }

  @override
  bool isInstalled(VoiceModelPack pack) => true;

  @override
  bool get isReady => state == VoiceModelState.ready;
}

Future<ReadyVoiceModelManager> readyVoiceModelManager() async {
  SharedPreferences.setMockInitialValues({});
  return ReadyVoiceModelManager(await SharedPreferences.getInstance());
}

class FakeVoiceRecorder implements VoiceRecorder {
  final StreamController<VoiceRecorderEvent> eventController =
      StreamController.broadcast();
  MultiStreamController<Uint8List>? audioController;
  bool audioClosed = true;
  int cancelCalls = 0;
  int stopCalls = 0;
  int startCalls = 0;
  Completer<void>? startGate;

  @override
  Stream<VoiceRecorderEvent> get events => eventController.stream;

  @override
  Future<Stream<Uint8List>> start() async {
    startCalls++;
    await startGate?.future;
    audioClosed = false;
    return Stream<Uint8List>.multi((controller) {
      audioController = controller;
    });
  }

  void addSamples([List<int> bytes = const [0, 0, 1, 0]]) {
    audioController!.add(Uint8List.fromList(bytes));
  }

  @override
  Future<void> stop() async {
    stopCalls++;
    audioController?.close();
    audioClosed = true;
  }

  @override
  Future<void> cancel() async {
    cancelCalls++;
    if (!audioClosed) audioController?.close();
    audioClosed = true;
  }

  @override
  Future<void> dispose() async {
    if (!eventController.isClosed) await eventController.close();
  }
}

class FakeRecognitionHandle implements VoiceRecognitionHandle {
  final Completer<String> completer = Completer<String>();
  int cancelCalls = 0;

  @override
  Future<String> get result => completer.future;

  @override
  Future<void> get finished => completer.future.then<void>(
    (_) {},
    onError: (Object error, StackTrace stackTrace) {},
  );

  @override
  void cancel() => cancelCalls++;
}

class FakeVoiceRecognizer implements VoiceRecognizer {
  final List<FakeRecognitionHandle> handles = [];
  VoiceRecognitionRequest? request;
  String? automaticResult;

  @override
  Future<VoiceRecognitionHandle> start(
    VoiceRecognitionRequest request, {
    required void Function() onLoaded,
  }) async {
    this.request = request;
    final handle = FakeRecognitionHandle();
    handles.add(handle);
    onLoaded();
    if (automaticResult case final result?) {
      scheduleMicrotask(() => handle.completer.complete(result));
    }
    return handle;
  }
}

void main() {
  test(
    'selected model pack and language persist across manager instances',
    () async {
      final manager = await readyVoiceModelManager();
      await manager.selectPack(voiceModelPack('small'));
      await manager.setLanguage(VoiceLanguage.arabic);

      final restored = VoiceModelManager(
        root: '/private/models',
        preferences: manager.preferences,
        downloader: VoiceModelDownloader(
          store: _UnusedStore(),
          http: _UnusedHttp(),
        ),
      );
      await restored.initialize();

      expect(restored.selectedPack.id, 'small');
      expect(restored.language, VoiceLanguage.arabic);
      manager.dispose();
      restored.dispose();
    },
  );

  test(
    'cancel kills active recognition and stale result cannot become a draft',
    () async {
      final manager = await readyVoiceModelManager();
      final recorder = FakeVoiceRecorder();
      final recognizer = FakeVoiceRecognizer();
      final controller = VoiceComposerController(
        models: manager,
        recorder: recorder,
        recognizer: recognizer,
      );
      addTearDown(controller.dispose);

      await controller.startListening();
      recorder.addSamples();
      final stopping = controller.stopListening();
      await Future<void>.delayed(Duration.zero);
      expect(controller.state, VoiceComposerState.transcribing);
      expect(recognizer.request?.samples, isNotEmpty);

      await controller.cancel();
      expect(recognizer.handles.single.cancelCalls, 1);
      expect(controller.state, VoiceComposerState.finishingCancellation);
      recognizer.handles.single.completer.complete('stale transcript');
      await stopping;
      await Future<void>.delayed(Duration.zero);

      expect(controller.state, VoiceComposerState.idle);
      expect(controller.draft, isEmpty);
    },
  );

  test(
    'audio interruption cancels listening and discards captured audio',
    () async {
      final recorder = FakeVoiceRecorder();
      final controller = VoiceComposerController(
        models: await readyVoiceModelManager(),
        recorder: recorder,
        recognizer: FakeVoiceRecognizer(),
      );
      addTearDown(controller.dispose);

      await controller.startListening();
      recorder.addSamples();
      recorder.eventController.add(VoiceRecorderEvent.interrupted);
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);

      expect(recorder.cancelCalls, greaterThan(0));
      expect(controller.state, VoiceComposerState.error);
      expect(controller.draft, isEmpty);
    },
  );

  test(
    'simultaneous starts and cancellation during startup are guarded',
    () async {
      final recorder = FakeVoiceRecorder()..startGate = Completer<void>();
      final controller = VoiceComposerController(
        models: await readyVoiceModelManager(),
        recorder: recorder,
        recognizer: FakeVoiceRecognizer(),
      );
      addTearDown(controller.dispose);

      final firstStart = controller.startListening();
      await Future<void>.delayed(Duration.zero);
      final overlappingStart = controller.startListening();
      await Future<void>.delayed(Duration.zero);

      expect(recorder.startCalls, 1);
      expect(controller.state, VoiceComposerState.initializing);
      await controller.cancel();
      recorder.startGate!.complete();
      await Future.wait([firstStart, overlappingStart]);

      expect(recorder.startCalls, 1);
      expect(recorder.cancelCalls, greaterThanOrEqualTo(2));
      expect(controller.state, VoiceComposerState.idle);
    },
  );

  test('recorder state stream errors terminate an active recording', () async {
    final recorder = FakeVoiceRecorder();
    final controller = VoiceComposerController(
      models: await readyVoiceModelManager(),
      recorder: recorder,
      recognizer: FakeVoiceRecognizer(),
    );
    addTearDown(controller.dispose);
    await controller.startListening();

    recorder.eventController.addError(StateError('state stream failed'));
    await Future<void>.delayed(Duration.zero);
    await Future<void>.delayed(Duration.zero);

    expect(controller.state, VoiceComposerState.error);
    expect(controller.error.toString(), contains('Microphone state error'));
    expect(recorder.cancelCalls, greaterThan(0));
  });

  test(
    'device preflight disables High accuracy but keeps Balanced available',
    () async {
      final manager = await readyVoiceModelManager();
      addTearDown(manager.dispose);
      manager.deviceInfo = const VoiceDeviceInfo(
        availableStorageBytes: 1024 * 1024 * 1024,
        memoryClassMb: 256,
        supportedAbis: ['arm64-v8a'],
        hasMicrophone: true,
      );

      expect(manager.supportFor(voiceModelPack('base')).supported, isTrue);
      final high = manager.supportFor(voiceModelPack('small'));
      expect(high.supported, isFalse);
      expect(high.reason, contains('512 MB'));

      manager.deviceInfo = const VoiceDeviceInfo(
        availableStorageBytes: 10,
        memoryClassMb: 1024,
        supportedAbis: ['arm64-v8a'],
        hasMicrophone: true,
      );
      expect(
        manager.supportFor(voiceModelPack('base'), replacing: true).reason,
        contains('free'),
      );
    },
  );

  test('draft merge preserves existing text around the current selection', () {
    expect(
      mergeVoiceDraft(
        'before after',
        const TextSelection(baseOffset: 7, extentOffset: 7),
        'spoken',
      ),
      'before spoken after',
    );
    expect(
      mergeVoiceDraft(
        'keep selected text',
        const TextSelection(baseOffset: 5, extentOffset: 13),
        'voice',
      ),
      'keep voice text',
    );
  });
}
