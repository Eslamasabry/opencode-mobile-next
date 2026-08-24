import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/voice/model_manifest.dart';
import 'package:opencode_mobile/voice/recognizer.dart';

void main() {
  test(
    'Whisper worker config maps local paths, language, task, and CPU policy',
    () {
      final request = VoiceRecognitionRequest(
        encoderPath: '/private/encoder.onnx',
        decoderPath: '/private/decoder.onnx',
        tokensPath: '/private/tokens.txt',
        language: VoiceLanguage.arabic,
        samples: Float32List(1),
        numThreads: 2,
      );
      final config = buildWhisperRecognizerConfig(request);

      expect(config.model.whisper.encoder, request.encoderPath);
      expect(config.model.whisper.decoder, request.decoderPath);
      expect(config.model.whisper.language, 'ar');
      expect(config.model.whisper.task, 'transcribe');
      expect(config.model.tokens, request.tokensPath);
      expect(config.model.modelType, 'whisper');
      expect(config.model.provider, 'cpu');
      expect(config.model.numThreads, 2);
      expect(config.model.debug, isFalse);
      expect(conservativeVoiceThreadCount(2), 1);
      expect(conservativeVoiceThreadCount(8), 2);
    },
  );

  test('auto language maps to sherpa Whisper auto detection', () {
    final request = VoiceRecognitionRequest(
      encoderPath: 'encoder',
      decoderPath: 'decoder',
      tokensPath: 'tokens',
      language: VoiceLanguage.auto,
      samples: Float32List(0),
      numThreads: 1,
    );
    expect(
      buildWhisperRecognizerConfig(request).model.whisper.language,
      isEmpty,
    );
  });
}
