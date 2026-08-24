import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:opencode_sdk/src/api.dart';
import 'package:opencode_sdk/src/http/errors.dart';
import 'package:opencode_sdk/src/http/wire.dart';

extension OpencodeSdkFilesystem on OpencodeSdk {
  /// Reads [path] through the wildcard `/api/fs/read/*` route.
  ///
  /// This replaces `FilesystemApi.v2FsRead()`, which sends a literal `*`
  /// because OpenAPI has no standard wildcard-tail parameter representation.
  Future<Response<Uint8List>> v2FsReadPath({
    required String path,
    String? locationDirectory,
    String? locationWorkspace,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    final encodedTail = _encodeWildcardTail(path);
    const operationId = 'v2.fs.read';
    final options = Options(
      method: 'GET',
      responseType: ResponseType.bytes,
      headers: <String, dynamic>{...?headers},
      extra: <String, dynamic>{
        'secure': <Map<String, String>>[],
        ...?extra,
        'operationId': operationId,
      },
      validateStatus: validateStatus,
    );
    late Response<Object> response;
    try {
      response = await dio.request<Object>(
        '/api/fs/read/$encodedTail',
        options: options,
        queryParameters: <String, dynamic>{
          if (locationDirectory != null)
            'location[directory]': locationDirectory,
          if (locationWorkspace != null)
            'location[workspace]': locationWorkspace,
        },
        cancelToken: cancelToken,
        onSendProgress: onSendProgress,
        onReceiveProgress: onReceiveProgress,
      );
    } on DioException catch (error) {
      rethrowOpenCodeApiException(error, operationId: operationId);
    }
    throwIfOpenCodeApiError(response, operationId: operationId);
    final data = response.data;
    if (data is! Uint8List) {
      throw DioException(
        requestOptions: response.requestOptions,
        response: response,
        type: DioExceptionType.unknown,
        error: StateError('Expected a byte response from v2.fs.read.'),
      );
    }
    return Response<Uint8List>(
      data: data,
      headers: response.headers,
      isRedirect: response.isRedirect,
      requestOptions: response.requestOptions,
      redirects: response.redirects,
      statusCode: response.statusCode,
      statusMessage: response.statusMessage,
      extra: response.extra,
    );
  }
}

String _encodeWildcardTail(String path) {
  if (path.isEmpty ||
      path.startsWith('/') ||
      path.endsWith('/') ||
      path.contains(r'\') ||
      path.contains('\x00') ||
      RegExp(r'^[A-Za-z]:').hasMatch(path)) {
    throw ArgumentError.value(
      path,
      'path',
      'Must be a non-empty slash-separated relative file path.',
    );
  }
  final segments = path.split('/');
  if (segments.any(
    (segment) => segment.isEmpty || segment == '.' || segment == '..',
  )) {
    throw ArgumentError.value(
      path,
      'path',
      'Empty and traversal path segments are not allowed.',
    );
  }
  return segments.map(encodeOpenCodePathSegment).join('/');
}
