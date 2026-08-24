import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'audio.dart';
import 'model_manager.dart';
import 'recognizer.dart';

enum VoiceComposerState {
  modelRequired,
  downloading,
  verifying,
  initializing,
  loading,
  idle,
  listening,
  transcribing,
  finishingCancellation,
  draft,
  error,
}

class VoiceComposerController extends ChangeNotifier {
  VoiceComposerController({
    required this.models,
    required this.recorder,
    required this.recognizer,
    this.ownsModels = true,
  }) {
    models.addListener(_onModelStateChanged);
    _recorderEvents = recorder.events.listen(
      (event) {
        if (event == VoiceRecorderEvent.interrupted &&
            state == VoiceComposerState.listening) {
          unawaited(cancel(reason: 'Recording was interrupted.'));
        }
      },
      onError: (Object exception, StackTrace stackTrace) {
        if (state == VoiceComposerState.listening ||
            state == VoiceComposerState.initializing) {
          unawaited(cancel(reason: 'Microphone state error: $exception'));
        }
      },
    );
    _onModelStateChanged();
  }

  final VoiceModelManager models;
  final VoiceRecorder recorder;
  final VoiceRecognizer recognizer;
  final bool ownsModels;
  late final StreamSubscription<VoiceRecorderEvent> _recorderEvents;

  VoiceComposerState state = VoiceComposerState.loading;
  Duration elapsed = Duration.zero;
  double level = 0;
  String draft = '';
  Object? error;
  Pcm16Accumulator? _audio;
  StreamSubscription<Uint8List>? _audioSubscription;
  Completer<void>? _audioDone;
  Timer? _clock;
  VoiceRecognitionHandle? _recognition;
  int _generation = 0;
  bool _disposed = false;
  bool _starting = false;

  static Future<VoiceComposerController> create() async {
    final models = await VoiceModelManager.shared();
    return VoiceComposerController(
      models: models,
      recorder: RecordVoiceRecorder(),
      recognizer: const SherpaVoiceRecognizer(),
      ownsModels: false,
    );
  }

  void _onModelStateChanged() {
    if (_disposed ||
        state == VoiceComposerState.listening ||
        state == VoiceComposerState.initializing ||
        state == VoiceComposerState.finishingCancellation ||
        state == VoiceComposerState.transcribing ||
        state == VoiceComposerState.draft) {
      return;
    }
    state = switch (models.state) {
      VoiceModelState.downloading => VoiceComposerState.downloading,
      VoiceModelState.verifying => VoiceComposerState.verifying,
      VoiceModelState.ready => VoiceComposerState.idle,
      VoiceModelState.loading ||
      VoiceModelState.checking => VoiceComposerState.loading,
      VoiceModelState.required => VoiceComposerState.modelRequired,
      VoiceModelState.error => VoiceComposerState.error,
    };
    error = models.error;
    notifyListeners();
  }

  Future<void> startListening() async {
    if (_disposed ||
        _starting ||
        state == VoiceComposerState.listening ||
        state == VoiceComposerState.initializing ||
        state == VoiceComposerState.finishingCancellation ||
        state == VoiceComposerState.loading ||
        state == VoiceComposerState.transcribing) {
      return;
    }
    if (!models.isReady) {
      state = VoiceComposerState.modelRequired;
      notifyListeners();
      return;
    }
    _starting = true;
    await cancel(clearError: true);
    if (_disposed) {
      _starting = false;
      return;
    }
    final generation = ++_generation;
    final audio = Pcm16Accumulator();
    _audio = audio;
    elapsed = Duration.zero;
    level = 0;
    draft = '';
    error = null;
    state = VoiceComposerState.initializing;
    notifyListeners();
    try {
      final stream = await recorder.start();
      if (_disposed || generation != _generation) {
        await recorder.cancel();
        return;
      }
      state = VoiceComposerState.listening;
      _audioDone = Completer<void>();
      _audioSubscription = stream.listen(
        (bytes) {
          if (generation != _generation) return;
          audio.add(bytes);
          elapsed = audio.duration;
          level = audio.level;
          notifyListeners();
          if (audio.isFull) unawaited(stopListening());
        },
        onError: (Object exception, StackTrace stackTrace) {
          if (generation == _generation) {
            unawaited(cancel(reason: 'Microphone error: $exception'));
          }
          if (_audioDone?.isCompleted == false) _audioDone?.complete();
        },
        onDone: () {
          if (_audioDone?.isCompleted == false) _audioDone?.complete();
        },
      );
      _clock = Timer.periodic(const Duration(milliseconds: 100), (_) {
        if (generation == _generation &&
            state == VoiceComposerState.listening) {
          elapsed = audio.duration;
          notifyListeners();
        }
      });
      notifyListeners();
    } catch (exception) {
      if (generation != _generation) return;
      error = exception;
      state = VoiceComposerState.error;
      await recorder.cancel();
      notifyListeners();
    } finally {
      _starting = false;
    }
  }

  Future<void> stopListening() async {
    if (state != VoiceComposerState.listening) return;
    final generation = _generation;
    state = VoiceComposerState.loading;
    models.markLoading();
    _clock?.cancel();
    notifyListeners();
    try {
      await recorder.stop();
      await _audioDone?.future.timeout(const Duration(seconds: 2));
    } catch (_) {
      // The stream may already have closed after an interruption.
    }
    await _audioSubscription?.cancel();
    _audioSubscription = null;
    final samples = _audio?.takeSamples() ?? Float32List(0);
    _audio = null;
    if (_disposed || generation != _generation) return;
    if (samples.isEmpty) {
      models.markReady();
      error = StateError('No audio was captured.');
      state = VoiceComposerState.error;
      notifyListeners();
      return;
    }
    final pack = models.selectedPack;
    final request = VoiceRecognitionRequest(
      encoderPath: models.downloader.filePath(models.root, pack, pack.encoder),
      decoderPath: models.downloader.filePath(models.root, pack, pack.decoder),
      tokensPath: models.downloader.filePath(models.root, pack, pack.tokens),
      language: models.language,
      samples: samples,
      numThreads: conservativeVoiceThreadCount(),
    );
    try {
      final handle = await recognizer.start(
        request,
        onLoaded: () {
          if (!_disposed && generation == _generation) {
            state = VoiceComposerState.transcribing;
            notifyListeners();
          }
        },
      );
      if (_disposed || generation != _generation) {
        handle.cancel();
        return;
      }
      _recognition = handle;
      final text = await handle.result;
      if (_disposed || generation != _generation) return;
      _recognition = null;
      await handle.finished;
      if (_disposed || generation != _generation) return;
      models.markReady();
      draft = text;
      state = VoiceComposerState.draft;
      notifyListeners();
    } on VoiceRecognitionCancelled {
      // Cancellation deliberately discards stale worker output.
    } catch (exception) {
      if (_disposed || generation != _generation) return;
      _recognition = null;
      models.markReady();
      error = exception;
      state = VoiceComposerState.error;
      notifyListeners();
    }
  }

  Future<void> cancel({String? reason, bool clearError = false}) async {
    if (_disposed) return;
    ++_generation;
    final generation = _generation;
    _clock?.cancel();
    _clock = null;
    final recognition = _recognition;
    recognition?.cancel();
    _recognition = null;
    final finishing = recognition?.finished;
    await _audioSubscription?.cancel();
    _audioSubscription = null;
    _audio?.clear();
    _audio = null;
    try {
      await recorder.cancel();
    } catch (_) {}
    if (finishing == null) models.markReady();
    elapsed = Duration.zero;
    level = 0;
    draft = '';
    if (finishing != null) {
      state = VoiceComposerState.finishingCancellation;
      unawaited(
        finishing.whenComplete(() {
          if (_disposed || generation != _generation) return;
          models.markReady();
          if (reason != null) {
            error = StateError(reason);
            state = VoiceComposerState.error;
          } else {
            state = models.isReady
                ? VoiceComposerState.idle
                : VoiceComposerState.modelRequired;
          }
          notifyListeners();
        }),
      );
    } else if (reason != null) {
      error = StateError(reason);
      state = VoiceComposerState.error;
    } else {
      if (clearError) error = null;
      state = models.isReady
          ? VoiceComposerState.idle
          : VoiceComposerState.modelRequired;
    }
    if (!_disposed) notifyListeners();
  }

  Future<void> handleLifecyclePause() => cancel();

  @override
  void dispose() {
    _disposed = true;
    ++_generation;
    _clock?.cancel();
    _recognition?.cancel();
    unawaited(_audioSubscription?.cancel());
    unawaited(recorder.cancel());
    unawaited(_recorderEvents.cancel());
    unawaited(recorder.dispose());
    models.removeListener(_onModelStateChanged);
    if (ownsModels) models.dispose();
    super.dispose();
  }
}

String mergeVoiceDraft(String existing, TextSelection selection, String draft) {
  final trimmed = draft.trim();
  if (trimmed.isEmpty) return existing;
  final start = selection.isValid
      ? selection.start.clamp(0, existing.length)
      : existing.length;
  final end = selection.isValid
      ? selection.end.clamp(start, existing.length)
      : existing.length;
  final before = existing.substring(0, start);
  final after = existing.substring(end);
  final leadingSpace = before.isNotEmpty && !RegExp(r'\s$').hasMatch(before)
      ? ' '
      : '';
  final trailingSpace = after.isNotEmpty && !RegExp(r'^\s').hasMatch(after)
      ? ' '
      : '';
  return '$before$leadingSpace$trimmed$trailingSpace$after';
}
