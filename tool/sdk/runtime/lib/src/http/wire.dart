import 'package:dio/dio.dart';

abstract interface class OpenCodeRawJsonValue {
  Object? get value;
}

Object? serializeOpenCodeQueryParameter(Object? value) =>
    value is OpenCodeRawJsonValue ? value.value : value;

/// Encodes one RFC 3986 path segment with the canonical unreserved set only.
String encodeOpenCodePathSegment(String value) => Uri.encodeComponent(value)
    .replaceAll('!', '%21')
    .replaceAll("'", '%27')
    .replaceAll('(', '%28')
    .replaceAll(')', '%29')
    .replaceAll('*', '%2A');

Future<Response<T>> requestOpenCode<T>(
  Dio dio,
  String path, {
  required Options options,
  required bool includeBody,
  Object? bodyData,
  Map<String, dynamic>? queryParameters,
  CancelToken? cancelToken,
  ProgressCallback? onSendProgress,
  ProgressCallback? onReceiveProgress,
}) {
  if (includeBody) {
    return dio.request<T>(
      path,
      data: bodyData,
      options: options,
      queryParameters: queryParameters,
      cancelToken: cancelToken,
      onSendProgress: onSendProgress,
      onReceiveProgress: onReceiveProgress,
    );
  }
  final requestOptions = options.compose(
    dio.options,
    path,
    queryParameters: queryParameters,
    cancelToken: cancelToken,
    onSendProgress: onSendProgress,
    onReceiveProgress: onReceiveProgress,
  );
  requestOptions.contentType = null;
  return dio.fetch<T>(requestOptions);
}
