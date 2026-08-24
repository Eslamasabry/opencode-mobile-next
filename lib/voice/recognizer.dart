import 'dart:async';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:sherpa_onnx/sherpa_onnx.dart' as sherpa;

import 'audio.dart';
import 'model_manifest.dart';

class VoiceRecognitionRequest {
  const VoiceRecognitionRequest({
    required this.encoderPath,
    required this.decoderPath,
    required this.tokensPath,
    required this.language,
    required this.samples,
    required this.numThreads,
  });

  final String encoderPath;
  final String decoderPath;
  final String tokensPath;
  final VoiceLanguage language;
  final Float32List samples;
  final int numThreads;
}

int conservativeVoiceThreadCount([int? processors]) {
  final count = processors ?? Platform.numberOfProcessors;
  return count >= 4 ? 2 : 1;
}

sherpa.OfflineRecognizerConfig buildWhisperRecognizerConfig(
  VoiceRecognitionRequest request,
) => sherpa.OfflineRecognizerConfig(
  model: sherpa.OfflineModelConfig(
    whisper: sherpa.OfflineWhisperModelConfig(
      encoder: request.encoderPath,
      decoder: request.decoderPath,
      language: request.language.whisperCode,
      task: 'transcribe',
    ),
    tokens: request.tokensPath,
    numThreads: request.numThreads,
    debug: false,
    provider: 'cpu',
    modelType: 'whisper',
  ),
);

abstract interface class VoiceRecognitionHandle {
  Future<String> get result;
  Future<void> get finished;
  void cancel();
}

abstract interface class VoiceRecognizer {
  Future<VoiceRecognitionHandle> start(
    VoiceRecognitionRequest request, {
    required void Function() onLoaded,
  });
}

class SherpaVoiceRecognizer implements VoiceRecognizer {
  const SherpaVoiceRecognizer();

  static Future<void> _decodeTail = Future.value();

  @override
  Future<VoiceRecognitionHandle> start(
    VoiceRecognitionRequest request, {
    required void Function() onLoaded,
  }) async {
    final previousDecode = _decodeTail;
    final releaseDecode = Completer<void>();
    _decodeTail = releaseDecode.future;
    await previousDecode.catchError((_) {});

    final receive = ReceivePort();
    final errors = ReceivePort();
    final exits = ReceivePort();
    final completer = Completer<String>();
    final finished = Completer<void>();
    Isolate? isolate;
    late final StreamSubscription<dynamic> messageSubscription;
    late final StreamSubscription<dynamic> errorSubscription;
    late final StreamSubscription<dynamic> exitSubscription;
    void finishError(Object error, [StackTrace? stackTrace]) {
      if (!completer.isCompleted) completer.completeError(error, stackTrace);
    }

    messageSubscription = receive.listen((message) {
      if (message == 'loaded') {
        onLoaded();
      } else if (message is Map && message['result'] is String) {
        if (!completer.isCompleted) {
          completer.complete(message['result'] as String);
        }
      } else if (message is Map && message['error'] != null) {
        finishError(StateError(message['error'].toString()));
      } else if (message == 'done' && !completer.isCompleted) {
        finishError(
          StateError('Transcription worker exited without a result.'),
        );
      }
    });
    errorSubscription = errors.listen((message) {
      if (message is List && message.isNotEmpty) {
        finishError(
          StateError(message.first.toString()),
          message.length > 1
              ? StackTrace.fromString(message[1].toString())
              : null,
        );
      }
    });
    exitSubscription = exits.listen((_) {
      if (!completer.isCompleted) {
        finishError(StateError('Transcription worker exited unexpectedly.'));
      }
      if (!finished.isCompleted) finished.complete();
    });
    try {
      isolate = await Isolate.spawn<Map<String, Object>>(
        _sherpaWorker,
        {
          'sendPort': receive.sendPort,
          'encoder': request.encoderPath,
          'decoder': request.decoderPath,
          'tokens': request.tokensPath,
          'language': request.language.whisperCode,
          'threads': request.numThreads,
          'samples': TransferableTypedData.fromList([
            request.samples.buffer.asUint8List(
              request.samples.offsetInBytes,
              request.samples.lengthInBytes,
            ),
          ]),
        },
        onError: errors.sendPort,
        onExit: exits.sendPort,
        errorsAreFatal: true,
      );
    } catch (error, stackTrace) {
      finishError(error, stackTrace);
      if (!finished.isCompleted) finished.complete();
    }

    unawaited(
      finished.future.whenComplete(() async {
        await messageSubscription.cancel();
        await errorSubscription.cancel();
        await exitSubscription.cancel();
        receive.close();
        errors.close();
        exits.close();
        if (!releaseDecode.isCompleted) releaseDecode.complete();
      }),
    );
    return _IsolateRecognitionHandle(completer.future, finished.future, () {
      finishError(const VoiceRecognitionCancelled());
      isolate?.kill(priority: Isolate.immediate);
    });
  }
}

class VoiceRecognitionCancelled implements Exception {
  const VoiceRecognitionCancelled();
}

class _IsolateRecognitionHandle implements VoiceRecognitionHandle {
  const _IsolateRecognitionHandle(this.result, this.finished, this._cancel);

  @override
  final Future<String> result;
  @override
  final Future<void> finished;
  final void Function() _cancel;

  @override
  void cancel() => _cancel();
}

void _sherpaWorker(Map<String, Object> message) {
  final sendPort = message['sendPort']! as SendPort;
  sherpa.OfflineRecognizer? recognizer;
  sherpa.OfflineStream? stream;
  try {
    sherpa.initBindings();
    final data = (message['samples']! as TransferableTypedData).materialize();
    final samples = data.asFloat32List();
    final request = VoiceRecognitionRequest(
      encoderPath: message['encoder']! as String,
      decoderPath: message['decoder']! as String,
      tokensPath: message['tokens']! as String,
      language: VoiceLanguage.values.firstWhere(
        (value) => value.whisperCode == message['language'],
        orElse: () => VoiceLanguage.auto,
      ),
      samples: samples,
      numThreads: message['threads']! as int,
    );
    recognizer = sherpa.OfflineRecognizer(
      buildWhisperRecognizerConfig(request),
    );
    sendPort.send('loaded');
    stream = recognizer.createStream();
    stream.acceptWaveform(samples: samples, sampleRate: voiceSampleRate);
    recognizer.decode(stream);
    sendPort.send({'result': recognizer.getResult(stream).text.trim()});
  } catch (error, stackTrace) {
    sendPort.send({'error': '$error\n$stackTrace'});
  } finally {
    stream?.free();
    recognizer?.free();
    sendPort.send('done');
  }
}
