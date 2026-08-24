import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/voice/audio.dart';

void main() {
  test(
    'PCM16 little-endian samples normalize correctly across split chunks',
    () {
      final pcm = Pcm16Accumulator(maximumSamples: 4);
      pcm.add(Uint8List.fromList([0x00, 0x80, 0xff]));
      pcm.add(Uint8List.fromList([0x7f, 0x00, 0x00, 0x00, 0xc0]));

      expect(pcm.takeSamples(), [
        -1.0,
        closeTo(32767 / 32768, 1e-8),
        0.0,
        -0.5,
      ]);
    },
  );

  test('recording buffer is strictly bounded at 30 seconds', () {
    final pcm = Pcm16Accumulator();
    final bytes = Uint8List((voiceMaximumSamples + 100) * 2);
    pcm.add(bytes);

    expect(pcm.length, voiceMaximumSamples);
    expect(pcm.duration, voiceMaximumDuration);
    expect(pcm.isFull, isTrue);
    expect(pcm.takeSamples(), hasLength(voiceMaximumSamples));
  });
}
