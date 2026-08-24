import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:opencode_sdk/src/deserialize.dart';

part 'error_contracts.g.dart';

class OpenCodeErrorContractKey {
  const OpenCodeErrorContractKey({
    required this.operationId,
    required this.status,
    required this.mediaType,
    required this.schemaJson,
  });

  final String operationId;
  final int status;
  final String mediaType;
  final String schemaJson;

  @override
  bool operator ==(Object other) =>
      other is OpenCodeErrorContractKey &&
      operationId == other.operationId &&
      status == other.status &&
      mediaType == other.mediaType &&
      schemaJson == other.schemaJson;

  @override
  int get hashCode => Object.hash(operationId, status, mediaType, schemaJson);
}

typedef OpenCodeErrorDecoder =
    Object Function(Object? rawPayload, OpenCodeErrorContract contract);

class OpenCodeErrorContract {
  const OpenCodeErrorContract(
    this.key, {
    required this.payloadType,
    required this.decoder,
    this.isDeclared = true,
  });

  final OpenCodeErrorContractKey key;
  final String payloadType;
  final OpenCodeErrorDecoder decoder;
  final bool isDeclared;

  Object get schemaDescriptor => jsonDecode(key.schemaJson) as Object;

  Object decode(Object? rawPayload) => decoder(rawPayload, this);
}

/// Lossless payload used when a response contract has no generated model.
class OpenCodeLosslessErrorPayload {
  const OpenCodeLosslessErrorPayload({
    required this.rawPayload,
    required this.schemaJson,
  });

  final Object? rawPayload;
  final String schemaJson;

  Object get schemaDescriptor => jsonDecode(schemaJson) as Object;
}

/// Lossless fallback when a generated model rejects the received payload.
class OpenCodeUndecodableErrorPayload extends OpenCodeLosslessErrorPayload {
  const OpenCodeUndecodableErrorPayload({
    required super.rawPayload,
    required super.schemaJson,
    required this.expectedType,
    required this.error,
  });

  final String expectedType;
  final Object error;
}

class OpenCodeApiException implements Exception {
  OpenCodeApiException({
    required this.contract,
    required this.rawPayload,
    required this.requestOptions,
    required this.headers,
    this.statusMessage,
  }) : decodedPayload = contract.decode(rawPayload);

  final OpenCodeErrorContract contract;
  final Object? rawPayload;
  final Object decodedPayload;
  final RequestOptions requestOptions;
  final Headers headers;
  final String? statusMessage;

  String get operationId => contract.key.operationId;
  int get statusCode => contract.key.status;
  String get mediaType => contract.key.mediaType;
  String get schemaJson => contract.key.schemaJson;
  Object get schemaDescriptor => contract.schemaDescriptor;

  /// Returns the generated model or explicit lossless wrapper for this error.
  T? payloadAs<T>() => decodedPayload is T ? decodedPayload as T : null;

  @override
  String toString() =>
      'OpenCodeApiException($operationId, $statusCode, $mediaType): '
      '$rawPayload';
}

Object decodeOpenCodeErrorModel(
  Object? rawPayload,
  String targetType,
  OpenCodeErrorContract contract,
) {
  try {
    return deserialize<Object, Object>(rawPayload, targetType);
  } catch (error) {
    return OpenCodeUndecodableErrorPayload(
      rawPayload: rawPayload,
      schemaJson: contract.key.schemaJson,
      expectedType: targetType,
      error: error,
    );
  }
}

Object decodeOpenCodeLosslessErrorPayload(
  Object? rawPayload,
  OpenCodeErrorContract contract,
) => OpenCodeLosslessErrorPayload(
  rawPayload: rawPayload,
  schemaJson: contract.key.schemaJson,
);

class OpenCodeApiErrorInterceptor extends Interceptor {
  @override
  void onError(DioException error, ErrorInterceptorHandler handler) {
    final exception = _exceptionFromResponse(
      error.response,
      operationId: error.requestOptions.extra['operationId'] as String?,
    );
    if (exception == null || error.error is OpenCodeApiException) {
      handler.next(error);
      return;
    }
    handler.next(
      DioException(
        requestOptions: error.requestOptions,
        response: error.response,
        type: error.type,
        error: exception,
        stackTrace: error.stackTrace,
        message: error.message,
      ),
    );
  }
}

Never rethrowOpenCodeApiException(
  DioException error, {
  required String operationId,
}) {
  final installed = error.error;
  if (installed is OpenCodeApiException) throw installed;
  final exception = _exceptionFromResponse(
    error.response,
    operationId: operationId,
  );
  if (exception != null) throw exception;
  throw error;
}

void throwIfOpenCodeApiError(
  Response<Object?> response, {
  required String operationId,
}) {
  final status = response.statusCode;
  if (status == null || (status >= 200 && status < 300)) return;
  final exception = _exceptionFromResponse(response, operationId: operationId);
  if (exception != null) throw exception;
}

OpenCodeApiException? _exceptionFromResponse(
  Response<Object?>? response, {
  required String? operationId,
}) {
  final status = response?.statusCode;
  if (response == null || operationId == null || status == null) return null;
  final contentType = response.headers.value(Headers.contentTypeHeader);
  final mediaType = contentType?.split(';').first.trim().toLowerCase();
  final matches = openCodeErrorContracts.values.where(
    (contract) =>
        contract.key.operationId == operationId &&
        contract.key.status == status,
  );
  OpenCodeErrorContract? contract;
  if (mediaType != null) {
    for (final candidate in matches) {
      if (candidate.key.mediaType.toLowerCase() == mediaType) {
        contract = candidate;
        break;
      }
    }
  }
  if (mediaType == null && matches.length == 1) {
    contract = matches.single;
  }
  if (contract == null &&
      openCodeErrorFallbackOperations.contains(operationId)) {
    final fallbackKey = OpenCodeErrorContractKey(
      operationId: operationId,
      status: status,
      mediaType: mediaType ?? '',
      schemaJson: '{}',
    );
    contract = OpenCodeErrorContract(
      fallbackKey,
      payloadType: 'OpenCodeLosslessErrorPayload',
      decoder: decodeOpenCodeLosslessErrorPayload,
      isDeclared: false,
    );
  }
  if (contract == null) return null;
  return OpenCodeApiException(
    contract: contract,
    rawPayload: response.data,
    requestOptions: response.requestOptions,
    headers: response.headers,
    statusMessage: response.statusMessage,
  );
}
