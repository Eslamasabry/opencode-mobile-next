import 'dart:typed_data';

import 'package:dio/dio.dart' show CancelToken, ProgressCallback;

/// Complete server-generated transfer data, including messages not loaded in UI.
abstract interface class SessionExportGateway {
  bool get sessionExportSupported;
  Future<Uint8List> exportSession(
    String sessionID, {
    bool sanitize = true,
    CancelToken? cancelToken,
    ProgressCallback? onReceiveProgress,
  });
}

class SessionExportUnsupported implements Exception {
  const SessionExportUnsupported();
}
