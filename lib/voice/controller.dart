import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'audio.dart';
import 'model_manager.dart';
import 'recognizer.dart';
import '../platform/platform_capabilities.dart';

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

  /// The words of this recording's chunks, in order ('' until a chunk is
  /// written down). A chunk is 30 s of audio ([voiceChunkDuration]).
  final List<String> _chunks = [];
  int _pendingChunks = 0;
  Future<void> _recognitionTail = Future<void>.value();

  /// Audio already handed to the recognizer in earlier chunks.
  Duration _recordedBefore = Duration.zero;
  Object? _chunkFailure;
  int _generation = 0;
  bool _disposed = false;
  bool _starting = false;

  static Future<VoiceComposerController> create() async {
    if (!platformCapabilities.supportsVoice) {
      throw StateError('Local voice input is unavailable on this platform.');
    }
    final models = await VoiceModelManager.shared();
    return VoiceComposerController(
      models: models,
      recorder: RecordVoiceRecorder(),
      recognizer: const SherpaVoiceRecognizer(),
      ownsModels: false,
    );
  }

  /// What this recording has said so far: the chunks written down while
  /// listening goes on. The host shows it in the draft as it grows, so what
  /// was said survives the app going away mid-recording.
  String get transcript => _chunks.where((text) => text.isNotEmpty).join(' ');

  /// Chunks still waiting for the recognizer.
  int get pendingChunks => _pendingChunks;

  /// True while [startListening] runs: its own clean-up passes through
  /// idle on the way to listening.
  bool get starting => _starting;

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
    if (!platformCapabilities.supportsVoice) {
      if (!_disposed) {
        error = StateError(
          'Local voice input is unavailable on this platform.',
        );
        state = VoiceComposerState.error;
        notifyListeners();
      }
      return;
    }
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
    final cancellation = cancel(clearError: true);
    final startingGeneration = _generation;
    await cancellation;
    if (_disposed || startingGeneration != _generation) {
      _starting = false;
      return;
    }
    final generation = ++_generation;
    final audio = Pcm16Accumulator();
    _audio = audio;
    _chunks.clear();
    _pendingChunks = 0;
    _chunkFailure = null;
    _recognitionTail = Future<void>.value();
    _recordedBefore = Duration.zero;
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
          // No cap: a full 30 s chunk goes to the recognizer and the rest
          // of these bytes start the next one.
          var offset = 0;
          while (offset < bytes.length) {
            offset += audio.add(bytes, offset);
            if (audio.isFull) {
              final heard = audio.level;
              _recordedBefore += audio.duration;
              _enqueueChunk(audio.takeSamples(), generation);
              audio.level = heard;
            }
          }
          elapsed = _recordedBefore + audio.duration;
          level = audio.level;
          notifyListeners();
        },
        onError: (Object exception, StackTrace stackTrace) {
          if (generation == _generation) {
            unawaited(cancel(reason: 'Microphone error: $exception'));
          }
          if (_audioDone?.isCompleted == false) _audioDone?.complete();
        },
        onDone: () {
          if (_audioDone?.isCompleted == false) _audioDone?.complete();
          if (generation == _generation &&
              state == VoiceComposerState.listening) {
            unawaited(stopListening());
          }
        },
      );
      _clock = Timer.periodic(const Duration(milliseconds: 100), (_) {
        if (generation == _generation &&
            state == VoiceComposerState.listening) {
          elapsed = _recordedBefore + audio.duration;
          notifyListeners();
        }
      });
      notifyListeners();
    } catch (exception) {
      if (generation != _generation) return;
      error = exception;
      state = VoiceComposerState.error;
      await recorder.cancel();
      if (!_disposed && generation == _generation) notifyListeners();
    } finally {
      _starting = false;
    }
  }

  /// Queues [samples] behind the chunks before it: the recognizer runs one
  /// at a time, and chunk order is kept.
  void _enqueueChunk(Float32List samples, int generation) {
    final index = _chunks.length;
    _chunks.add('');
    _pendingChunks++;
    _recognitionTail = _recognitionTail.then(
      (_) => _recognizeChunk(index, samples, generation),
    );
  }

  Future<void> _recognizeChunk(
    int index,
    Float32List samples,
    int generation,
  ) async {
    try {
      if (_disposed || generation != _generation || _chunkFailure != null) {
        return;
      }
      final pack = models.selectedPack;
      final request = VoiceRecognitionRequest(
        encoderPath: models.downloader.filePath(
          models.root,
          pack,
          pack.encoder,
        ),
        decoderPath: models.downloader.filePath(
          models.root,
          pack,
          pack.decoder,
        ),
        tokensPath: models.downloader.filePath(models.root, pack, pack.tokens),
        language: models.language,
        samples: samples,
        numThreads: conservativeVoiceThreadCount(),
      );
      final handle = await recognizer.start(
        request,
        onLoaded: () {
          if (!_disposed &&
              generation == _generation &&
              state == VoiceComposerState.loading) {
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
      if (identical(_recognition, handle)) _recognition = null;
      if (_disposed || generation != _generation) return;
      await handle.finished;
      if (_disposed || generation != _generation) return;
      _chunks[index] = text.trim();
      notifyListeners();
    } catch (exception) {
      if (_disposed || generation != _generation) return;
      _recognition = null;
      _chunkFailure = exception;
      // A chunk that could not be written down stops the recording: the
      // words so far stay in [transcript]; the rest would be lost silently.
      if (state == VoiceComposerState.listening) {
        unawaited(_failListening(exception, generation));
      }
    } finally {
      if (generation == _generation && _pendingChunks > 0) _pendingChunks--;
    }
  }

  Future<void> _failListening(Object exception, int generation) async {
    _clock?.cancel();
    final subscription = _audioSubscription;
    _audioSubscription = null;
    _audio?.clear();
    _audio = null;
    unawaited(subscription?.cancel());
    try {
      await recorder.cancel();
    } catch (_) {}
    if (_disposed || generation != _generation) return;
    level = 0;
    if (exception is VoiceRecognitionCancelled) {
      state = models.isReady
          ? VoiceComposerState.idle
          : VoiceComposerState.modelRequired;
    } else {
      error = exception;
      state = VoiceComposerState.error;
    }
    notifyListeners();
  }

  /// Ends the recording: the last chunk is written down after the ones
  /// before it, then [draft] holds everything that was said.
  Future<void> stopListening() async {
    if (state != VoiceComposerState.listening) return;
    final generation = _generation;
    final audioDone = _audioDone;
    state = VoiceComposerState.loading;
    models.markLoading();
    _clock?.cancel();
    notifyListeners();
    try {
      await recorder.stop();
      await audioDone?.future.timeout(const Duration(seconds: 2));
    } catch (_) {
      // The stream may already have closed after an interruption.
    }
    if (_disposed || generation != _generation) return;
    unawaited(_audioSubscription?.cancel());
    _audioSubscription = null;
    final samples = _audio?.takeSamples() ?? Float32List(0);
    _audio = null;
    if (_disposed || generation != _generation) return;
    if (samples.isNotEmpty) _enqueueChunk(samples, generation);
    if (_chunks.isEmpty) {
      models.markReady();
      error = StateError('No audio was captured.');
      state = VoiceComposerState.error;
      notifyListeners();
      return;
    }
    if (samples.isEmpty && state == VoiceComposerState.loading) {
      // Only earlier chunks are left; the model is already loaded.
      state = VoiceComposerState.transcribing;
      notifyListeners();
    }
    await _recognitionTail;
    if (_disposed || generation != _generation) return;
    models.markReady();
    final failure = _chunkFailure;
    if (failure is VoiceRecognitionCancelled) {
      state = models.isReady
          ? VoiceComposerState.idle
          : VoiceComposerState.modelRequired;
    } else if (failure != null) {
      error = failure;
      state = VoiceComposerState.error;
    } else {
      draft = transcript;
      state = VoiceComposerState.draft;
    }
    notifyListeners();
  }

  Future<void> cancel({String? reason, bool clearError = false}) async {
    if (_disposed) return;
    ++_generation;
    final generation = _generation;
    _clock?.cancel();
    _clock = null;
    _chunks.clear();
    _pendingChunks = 0;
    _chunkFailure = null;
    _recordedBefore = Duration.zero;
    final recognition = _recognition;
    recognition?.cancel();
    _recognition = null;
    final finishing = recognition?.finished;
    final subscription = _audioSubscription;
    _audioSubscription = null;
    _audio?.clear();
    _audio = null;
    draft = '';
    // Cancelling stops delivery at once; its future only reports clean-up
    // (and is a root-zone future, which a test's fake clock never runs).
    unawaited(subscription?.cancel());
    try {
      await recorder.cancel();
    } catch (_) {}
    if (_disposed || generation != _generation) return;
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
    _chunks.clear();
    _audio?.clear();
    _audio = null;
    draft = '';
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
