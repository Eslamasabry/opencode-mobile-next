import 'package:flutter/services.dart';

/// One bounded raster request. The platform owns cancellation and descriptor cleanup.
class LocalPdf {
  static const channel = MethodChannel('oc/local_pdf');
  static const maxBytes = 10 * 1024 * 1024;
  static const maxPages = 200;
  static int _sequence = 0;
  static String requestID() => 'pdf-${++_sequence}';

  static Future<PdfPageImage> render(
    Uint8List bytes,
    int page,
    String requestID,
  ) async {
    if (bytes.isEmpty ||
        bytes.length > maxBytes ||
        page < 0 ||
        page >= maxPages) {
      throw PlatformException(code: 'limit');
    }
    final value = await channel.invokeMapMethod<String, dynamic>('render', {
      'bytes': bytes,
      'page': page,
      'requestID': requestID,
    });
    final png = value?['png'];
    final count = value?['pageCount'];
    final width = value?['width'];
    final height = value?['height'];
    if (png is! Uint8List ||
        png.isEmpty ||
        png.length > 10 * 1024 * 1024 ||
        count is! int ||
        count < 1 ||
        width is! int ||
        height is! int ||
        width < 1 ||
        height < 1 ||
        width > 1536 ||
        height > 1536 ||
        width * height > 2000000 ||
        value?['page'] != page) {
      throw PlatformException(code: 'invalid_result');
    }
    // Validate the actual PNG header before the UI's image codec allocates.
    const signature = [
      137,
      80,
      78,
      71,
      13,
      10,
      26,
      10,
      0,
      0,
      0,
      13,
      73,
      72,
      68,
      82,
    ];
    if (png.length < 24 ||
        List.generate(
          signature.length,
          (i) => png[i] == signature[i],
        ).contains(false) ||
        ByteData.sublistView(png).getUint32(16) != width ||
        ByteData.sublistView(png).getUint32(20) != height) {
      throw PlatformException(code: 'invalid_result');
    }
    return PdfPageImage(png, count, page);
  }

  static Future<void> cancel(String requestID) async {
    try {
      await channel.invokeMethod<void>('cancel', {'requestID': requestID});
    } on PlatformException {
      /* A stopped service has already released this request. */
    } on MissingPluginException {
      /* Unsupported platform, no request exists. */
    }
  }
}

class PdfPageImage {
  const PdfPageImage(this.png, this.pageCount, this.page);
  final Uint8List png;
  final int pageCount;
  final int page;
}
