import 'dart:typed_data';

import 'package:zxing2/qrcode.dart';

/// Decodes one QR code from a greyscale (luminance) frame, or returns null
/// when the frame holds none. Pure Dart (zxing2, a port of ZXing), so it needs
/// no Google ML Kit model and no native library.
///
/// [luma] is the camera's Y plane: [rowStride] bytes per row, of which the
/// first [width] are pixels. It is a top-level function with plain arguments so
/// it can run in an isolate. A frame may be rotated or mirrored; QR finder
/// patterns do not care. Inverted codes (light on dark) are tried second.
String? decodeQrFromLuma(
  Uint8List luma,
  int width,
  int height, {
  int? rowStride,
}) {
  final stride = rowStride ?? width;
  if (width <= 0 ||
      height <= 0 ||
      luma.length < stride * (height - 1) + width) {
    return null;
  }
  final pixels = Int32List(width * height);
  for (var y = 0; y < height; y++) {
    final row = y * stride;
    for (var x = 0; x < width; x++) {
      final v = luma[row + x];
      pixels[y * width + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
    }
  }
  final source = RGBLuminanceSource(width, height, pixels);
  final hints = DecodeHints()..put(DecodeHintType.tryHarder);
  for (final candidate in [source, InvertedLuminanceSource(source)]) {
    try {
      final bitmap = BinaryBitmap(HybridBinarizer(candidate));
      return QRCodeReader().decode(bitmap, hints: hints).text;
    } on ReaderException {
      // Nothing readable in this frame (NotFound, Checksum, Format).
    }
  }
  return null;
}
