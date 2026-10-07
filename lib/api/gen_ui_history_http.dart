import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

import '../domain/server_gateway.dart';

const genUiHistoryByteLimit = 1024 * 1024;
const genUiHistoryTimeout = Duration(seconds: 10);
const genUiHistoryFailure = ProductException(
  'Agent card history is unavailable. Refresh the conversation.',
);

void validateGenUiHistoryRequest(String id, String? cursor, int limit) {
  if (id.isEmpty ||
      id.length > 512 ||
      limit < 1 ||
      limit > 50 ||
      (cursor != null && (cursor.isEmpty || cursor.length > 4096))) {
    throw genUiHistoryFailure;
  }
}

/// Streams through the byte budget before JSON decoding, including error bodies.
/// The deadline cancels the actual request, not merely the caller's Future.
Future<({dynamic json, Headers headers})> readGenUiHistoryJson(
  Dio dio,
  String path, {
  required Map<String, dynamic> query,
}) async {
  final cancel = CancelToken();
  final timer = Timer(genUiHistoryTimeout, () => cancel.cancel());
  try {
    final response = await dio.get<ResponseBody>(
      path,
      queryParameters: query,
      cancelToken: cancel,
      options: Options(
        responseType: ResponseType.stream,
        receiveTimeout: genUiHistoryTimeout,
        followRedirects: false,
        validateStatus: (_) => true,
      ),
    );
    final body = response.data;
    if (body == null || response.statusCode != 200) {
      cancel.cancel();
      throw genUiHistoryFailure;
    }
    final declared = int.tryParse(
      response.headers.value(Headers.contentLengthHeader) ?? '',
    );
    if (declared != null && declared > genUiHistoryByteLimit) {
      cancel.cancel();
      throw genUiHistoryFailure;
    }
    final bytes = BytesBuilder(copy: false);
    await for (final chunk in body.stream) {
      if (bytes.length + chunk.length > genUiHistoryByteLimit) {
        cancel.cancel();
        throw genUiHistoryFailure;
      }
      bytes.add(chunk);
    }
    return (
      json: jsonDecode(utf8.decode(bytes.takeBytes())),
      headers: response.headers,
    );
  } catch (_) {
    throw genUiHistoryFailure;
  } finally {
    timer.cancel();
  }
}
