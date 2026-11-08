import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/pairing.dart';
import 'package:opencode_mobile/ui/kit/kit_qr_decoder.dart';
import 'package:qr/qr.dart';

/// What `opencode2 pair` prints (lib/state/pairing.dart): the JSON the QR holds.
const _pairing =
    '{"urls":["http://192.168.1.20:49374","http://100.64.0.7:49374"],'
    '"username":"opencode","password":"correct-horse-battery-staple-123"}';

/// A greyscale camera-style frame of [data]'s QR: [scale] pixels per module,
/// a 4-module quiet zone, mid-grey padding of [pad] bytes per row (a camera's
/// row stride is often wider than its width), optionally light on dark.
({Uint8List luma, int width, int height, int stride}) _frame(
  String data, {
  int scale = 4,
  int pad = 0,
  bool inverted = false,
}) {
  final image = QrImage(
    QrCode.fromData(data: data, errorCorrectLevel: QrErrorCorrectLevel.M),
  );
  final modules = image.moduleCount + 8;
  final size = modules * scale;
  final stride = size + pad;
  final luma = Uint8List(stride * size)..fillRange(0, stride * size, 128);
  for (var y = 0; y < size; y++) {
    for (var x = 0; x < size; x++) {
      final row = y ~/ scale - 4;
      final col = x ~/ scale - 4;
      final dark =
          row >= 0 &&
          col >= 0 &&
          row < image.moduleCount &&
          col < image.moduleCount &&
          image.isDark(row, col);
      luma[y * stride + x] = (dark != inverted) ? 0 : 255;
    }
  }
  return (luma: luma, width: size, height: size, stride: stride);
}

/// The QR scanner's decoder (kit_qr_decoder.dart), the part that used to be
/// Google ML Kit's bundled model.
void main() {
  test('reads the pairing QR and the payload still parses', () {
    final f = _frame(_pairing);
    final raw = decodeQrFromLuma(f.luma, f.width, f.height);
    expect(raw, _pairing);
    final parsed = parsePairingPayload(raw!);
    expect(parsed.ok, isTrue);
    expect(parsed.payload!.username, 'opencode');
  });

  test('reads a frame whose rows are padded, as a camera delivers them', () {
    final f = _frame(_pairing, pad: 24);
    expect(
      decodeQrFromLuma(f.luma, f.width, f.height, rowStride: f.stride),
      _pairing,
    );
  });

  test('reads a light-on-dark code', () {
    final f = _frame(_pairing, inverted: true);
    expect(decodeQrFromLuma(f.luma, f.width, f.height), _pairing);
  });

  test('reads a small, coarse frame like a medium-resolution preview', () {
    final f = _frame(_pairing, scale: 3);
    expect(f.width, lessThan(480));
    expect(decodeQrFromLuma(f.luma, f.width, f.height), _pairing);
  });

  test('a frame with no code gives null, and so does a short buffer', () {
    final blank = Uint8List(200 * 200)..fillRange(0, 200 * 200, 200);
    expect(decodeQrFromLuma(blank, 200, 200), isNull);
    expect(decodeQrFromLuma(Uint8List(10), 200, 200), isNull);
  });

  test('a code that is not a pairing code decodes but is refused', () {
    final f = _frame('https://example.com/not-a-pairing-code');
    final raw = decodeQrFromLuma(f.luma, f.width, f.height);
    expect(raw, isNotNull);
    expect(parsePairingPayload(raw!).ok, isFalse);
  });
}
